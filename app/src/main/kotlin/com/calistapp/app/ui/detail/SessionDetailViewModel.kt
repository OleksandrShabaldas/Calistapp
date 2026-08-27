package com.calistapp.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.InsightsRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.calorie.CalorieAudit
import com.calistapp.core.calorie.CalorieEngine
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.progress.PersonalRecord
import com.calistapp.core.progress.personalRecords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Error(val message: String) : AiUiState
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val insightsRepository: InsightsRepository,
    private val engine: CalorieEngine,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<WorkoutSession?> = sessionRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val profile: StateFlow<UserProfile> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    /**
     * The derivation behind this session's calorie figure.
     *
     * Re-run rather than stored: the raw heart-rate samples and segments are all persisted, so the
     * engine can reproduce its own working on demand, and there's no risk of a saved explanation
     * describing a computation the code no longer does. The screen cross-checks the recomputed total
     * against the one recorded at the time and says so if the profile has changed since.
     */
    val audit: StateFlow<CalorieAudit?> = combine(session, profile) { s, p ->
        s?.let { engine.explain(it, p) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Records this session beat, judged against everything performed before it. */
    val records: StateFlow<List<PersonalRecord>> = sessionRepository.observePerformed()
        .map { personalRecords(it, sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    fun generateInsight() {
        val current = session.value ?: return
        val summary = current.summary ?: return
        if (_aiState.value is AiUiState.Loading) return
        _aiState.value = AiUiState.Loading
        viewModelScope.launch {
            when (val result = insightsRepository.analyzeSession(current, summary, profile.value)) {
                is AiResult.Success -> {
                    sessionRepository.updateInsight(sessionId, result.text)
                    _aiState.value = AiUiState.Idle
                }
                is AiResult.Failure -> _aiState.value = AiUiState.Error(result.message)
            }
        }
    }

    /**
     * How the session felt, in the athlete's own words.
     *
     * Written straight through on every edit rather than behind a Save button: there is nothing to
     * validate, and a note lost because you navigated away without pressing something is a worse
     * outcome than a few extra writes. Already carried into the AI prompt, which is what makes it
     * worth capturing at all.
     */
    fun setNotes(notes: String) {
        viewModelScope.launch { sessionRepository.updateNotes(sessionId, notes) }
    }

    /** Rating of perceived exertion, 1–10. Null clears it. */
    fun setRpe(rpe: Int?) {
        viewModelScope.launch { sessionRepository.updateRpe(sessionId, rpe) }
    }

    /**
     * Correct the reps recorded against performed sets, keyed by when each set began.
     *
     * Two things go wrong often enough to need this: a miscount, and forgetting to log a set at all
     * — the second leaves a block of real work sitting at zero reps, which costs it the mechanical
     * floor the engine would otherwise apply. Editing writes through to the *segments*, not just the
     * set logs, because the segments are what the engine scores; the logs are rebuilt from them so
     * the two can't disagree afterwards.
     *
     * The session is then rescored and restored. Note this rescores against your profile as it is
     * now — if your weight has changed since, the figure moves for that reason too.
     */
    fun applySetEdits(repsBySetStart: Map<Long, Int>) {
        if (repsBySetStart.isEmpty()) return
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId) ?: return@launch

            val segments = session.segments.map { segment ->
                if (segment.type != SegmentType.ACTIVE) return@map segment
                val reps = repsBySetStart[segment.startMs] ?: return@map segment
                segment.copy(reps = reps.coerceAtLeast(0))
            }

            val summary = engine.compute(
                samples = session.samples,
                segments = segments,
                profile = profile.value,
                endMs = session.endMs,
            )
            sessionRepository.saveSession(
                session.copy(
                    segments = segments,
                    setLogs = rebuildSetLogs(segments, session.plan),
                    summary = summary,
                ),
            )
        }
    }

    /**
     * Derive the set log from the segments, so an edited session's "what I performed" matches what
     * was actually scored. Blocks left at zero reps produce no log — they happened, but there is
     * nothing to record about them beyond the time, which the segment already holds.
     */
    private fun rebuildSetLogs(segments: List<Segment>, plan: WorkoutPlan): List<SetLog> {
        val setCounts = mutableMapOf<String, Int>()
        return segments.mapNotNull { segment ->
            if (segment.type != SegmentType.ACTIVE) return@mapNotNull null
            val slotId = segment.slotId ?: return@mapNotNull null
            val index = (setCounts[slotId] ?: 0) + 1
            setCounts[slotId] = index
            if (segment.reps <= 0) return@mapNotNull null
            SetLog(
                slotId = slotId,
                exerciseId = plan.slot(slotId)?.exerciseId.orEmpty(),
                exerciseName = segment.exerciseName.orEmpty(),
                setIndex = index,
                reps = segment.reps,
                startMs = segment.startMs,
                endMs = segment.endMs ?: segment.startMs,
            )
        }
    }

    fun deleteSession(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            onDone()
        }
    }
}
