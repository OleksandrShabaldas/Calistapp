package com.calistapp.app.ui.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.ai.AiResult
import com.calistapp.app.data.ai.ExerciseCoachRepository
import com.calistapp.app.data.ai.FaqAnswerResult
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.Faq
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.SetLog
import com.calistapp.core.progress.ExerciseProgress
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.summarizeProgress
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

sealed interface ExerciseAiState {
    data object Idle : ExerciseAiState
    data object Loading : ExerciseAiState
    data class Error(val message: String) : ExerciseAiState
}

/** State of the "Ask AI" FAQ box — separate from [ExerciseAiState] so the two can run independently. */
sealed interface FaqAskState {
    data object Idle : FaqAskState
    data object Loading : FaqAskState
    data class Error(val message: String) : FaqAskState
}

/** One session's best set of this movement, for the Progress trend. */
data class ExerciseTrendPoint(
    val atMs: Long,
    val bestReps: Int,
    val bestWeightKg: Double,
    val bestVolume: Double,
)

/** One past session that included this movement, with the sets performed. */
data class ExerciseHistoryEntry(
    val sessionId: String,
    val atMs: Long,
    val sets: List<SetLog>,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val coach: ExerciseCoachRepository,
    private val prefs: ExercisePrefsRepository,
    private val drafts: PlanDraftRepository,
    sessionRepository: SessionRepository,
    savedWorkoutRepository: SavedWorkoutRepository,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])

    /**
     * How this screen was opened. When it's the planner's exercise picker, the bottom action becomes
     * "Add to workout" (drops the movement into the draft and returns) instead of "Start workout" —
     * the picker is where adding is the whole intent. Null everywhere else.
     */
    val openedFromPicker: Boolean =
        savedStateHandle.get<String>(Routes.EXERCISE_DETAIL_ORIGIN) == Routes.ORIGIN_PICKER

    val exercise: StateFlow<Exercise?> = repository.observe(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val performed = sessionRepository.observePerformed()

    /** This movement's records and totals, or null until it's been performed. */
    val progress: StateFlow<ExerciseProgress?> =
        combine(exercise, performed) { ex, sessions -> progressFor(ex, sessions) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Best set per session over time, oldest first — the Progress trend. */
    val trend: StateFlow<List<ExerciseTrendPoint>> =
        performed.map { trendFor(exerciseId, it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Past sessions with this movement, newest first. */
    val history: StateFlow<List<ExerciseHistoryEntry>> =
        performed.map { historyFor(exerciseId, it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved workouts whose plan contains this movement. */
    val appearsIn: StateFlow<List<SavedWorkout>> =
        savedWorkoutRepository.saved
            .map { list -> list.filter { w -> w.plan.exercises.any { it.exerciseId == exerciseId } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favourite: StateFlow<Boolean> = prefs.favourites
        .map { exerciseId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** This movement's Q&A for the Guide tab — authored entries plus any the user has generated. */
    val faqs: StateFlow<List<Faq>> = exercise
        .map { it?.faqs.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Opening a movement's detail is the clearest signal it's on your mind — surface it in Recent.
        viewModelScope.launch { prefs.markRecent(exerciseId) }
    }

    fun toggleFavourite() = viewModelScope.launch { prefs.toggleFavourite(exerciseId) }

    /**
     * User-added movements (their ids are `custom_…`) are the user's own, so deleting one removes it
     * for good. A dataset movement can't be truly deleted — the sync re-seeds it — so it's hidden
     * instead, reversible from the restore list in Profile. Same button, honest about which it is via
     * [isUserAdded].
     */
    val isUserAdded: Boolean get() = exerciseId.startsWith("custom_")

    fun deleteOrHide(onDone: () -> Unit) {
        viewModelScope.launch {
            if (isUserAdded) repository.delete(exerciseId) else prefs.hide(exerciseId)
            onDone()
        }
    }

    private val _aiState = MutableStateFlow<ExerciseAiState>(ExerciseAiState.Idle)
    val aiState: StateFlow<ExerciseAiState> = _aiState.asStateFlow()

    fun enrich() {
        val current = exercise.value ?: return
        if (_aiState.value is ExerciseAiState.Loading) return
        _aiState.value = ExerciseAiState.Loading
        viewModelScope.launch {
            when (val result = coach.enrich(current)) {
                is AiResult.Success -> _aiState.value = ExerciseAiState.Idle
                is AiResult.Failure -> _aiState.value = ExerciseAiState.Error(result.message)
            }
        }
    }

    private val _faqAsk = MutableStateFlow<FaqAskState>(FaqAskState.Idle)
    val faqAsk: StateFlow<FaqAskState> = _faqAsk.asStateFlow()

    /**
     * Answer a user's typed question and cache it on this movement for good. The new answer streams
     * back in through the [exercise] flow (the repository upsert re-emits), so [faqs] updates itself —
     * this only has to drive the box's loading/error state.
     */
    fun askFaq(question: String) {
        val current = exercise.value ?: return
        if (_faqAsk.value is FaqAskState.Loading) return
        _faqAsk.value = FaqAskState.Loading
        viewModelScope.launch {
            when (val result = coach.answerFaq(current, question)) {
                is FaqAnswerResult.Success -> _faqAsk.value = FaqAskState.Idle
                is FaqAnswerResult.Failure -> _faqAsk.value = FaqAskState.Error(result.message)
            }
        }
    }

    fun clearFaqError() {
        if (_faqAsk.value is FaqAskState.Error) _faqAsk.value = FaqAskState.Idle
    }

    /** Picker action: drop this movement into the draft the planner is building, then the screen pops. */
    fun addToDraft() {
        exercise.value?.let { drafts.add(it) }
    }
}

private fun progressFor(ex: Exercise?, sessions: List<PerformedSession>): ExerciseProgress? {
    if (ex == null) return null
    val relevant = sessions.filter { s ->
        s.setLogs.any { it.exerciseId == ex.id || it.exerciseName.equals(ex.name, ignoreCase = true) }
    }
    if (relevant.isEmpty()) return null
    return summarizeProgress(relevant, System.currentTimeMillis()).exercises
        .firstOrNull { it.key == ex.id || it.exerciseName.equals(ex.name, ignoreCase = true) }
}

private fun trendFor(exerciseId: String, sessions: List<PerformedSession>): List<ExerciseTrendPoint> =
    sessions.mapNotNull { s ->
        val sets = s.setLogs.filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) return@mapNotNull null
        // Added load lives on the plan slot for older sessions; prefer it, fall back to the set's own.
        fun weight(log: SetLog) = s.plan.slot(log.slotId)?.addedWeightKg ?: log.weightKg
        ExerciseTrendPoint(
            atMs = s.startMs,
            bestReps = sets.maxOf { it.reps },
            bestWeightKg = sets.maxOf { weight(it) },
            bestVolume = sets.maxOf { weight(it) * it.reps },
        )
    }.sortedBy { it.atMs }

private fun historyFor(exerciseId: String, sessions: List<PerformedSession>): List<ExerciseHistoryEntry> =
    sessions.mapNotNull { s ->
        val sets = s.setLogs.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }
        if (sets.isEmpty()) null else ExerciseHistoryEntry(s.id, s.startMs, sets)
    }.sortedByDescending { it.atMs }
