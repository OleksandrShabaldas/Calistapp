package com.calistapp.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.insights.InsightsPrefsRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.profile.WeightRepository
import com.calistapp.app.data.recommend.SleepRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.insights.MedalsRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.insights.InsightsEngine
import com.calistapp.core.insights.InsightsInput
import com.calistapp.core.insights.InsightsPeriod
import com.calistapp.core.insights.InsightsReport
import com.calistapp.core.insights.Move
import com.calistapp.core.medals.MedalReport
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.UserProfile
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.WeightEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Feeds the Insights tab: one [InsightsReport] for the chosen period, rebuilt whenever a session,
 * the schedule, the profile or a weigh-in changes.
 *
 * The engine is pure; this class only gathers its inputs and remembers which phrases each issue
 * used, so the next issue picks different words.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    profileRepository: ProfileRepository,
    weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val medals: MedalsRepository,
    private val sleepRepository: SleepRepository,
    private val prefs: InsightsPrefsRepository,
    private val planDraft: PlanDraftRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val period: StateFlow<InsightsPeriod> = prefs.period.stateIn(viewModelScope, SharingStarted.Eagerly, InsightsPeriod.FOUR_WEEKS)

    fun setPeriod(value: InsightsPeriod) {
        viewModelScope.launch { prefs.setPeriod(value) }
    }

    private val exercises: Flow<Map<String, Exercise>> = exerciseRepository.observeAll().map { list -> list.associateBy { it.id } }

    private val planned: Flow<Set<LocalDate>> = medals.plannedDays

    /** Every medal line as it stands, for the achievements section and the case. */
    val medalReport: StateFlow<MedalReport?> = medals.report

    /** Award keys already shown, so a medal earned since the last visit can read as new. */
    val seenMedals: StateFlow<Set<String>> = medals.seen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Sleep the night before each date, read once per screen visit; empty until (and unless) it loads. */
    private val sleep: Flow<Map<LocalDate, Int>> = flow {
        emit(emptyMap())
        val now = Instant.now()
        emit(runCatching { sleepRepository.asleepMinutesByNight(now.minus(Duration.ofDays(SLEEP_LOOKBACK_DAYS)), now) }.getOrDefault(emptyMap()))
    }

    private data class Base(
        val sessions: List<PerformedSession>,
        val profile: UserProfile,
        val weights: List<WeightEntry>,
        val exercises: Map<String, Exercise>,
        val planned: Set<LocalDate>,
    )

    private val base: Flow<Base> = combine(
        sessionRepository.observePerformed(), profileRepository.profile, weightRepository.entries, exercises, planned,
    ) { s, p, w, e, d -> Base(s, p, w, e, d) }

    val report: StateFlow<InsightsReport?> =
        combine(base, sleep, prefs.period, prefs.phraseHistory) { b, sl, per, history -> Triple(b, sl, per) to history }
            .mapLatest { (t, history) ->
                val (b, sl, per) = t
                val now = System.currentTimeMillis()
                val key = issueKey(per, now)
                val recent = history.filterKeys { it != key }.values.flatten().toSet()
                key to InsightsEngine.report(
                    InsightsInput(
                        sessions = b.sessions, exercises = b.exercises, plannedDays = b.planned, sleepBefore = sl,
                        weights = b.weights, profile = b.profile, period = per, nowMs = now, zone = zone, recentPhraseIds = recent,
                    ),
                )
            }
            .flowOn(Dispatchers.Default)
            .onEach { (key, r) -> prefs.remember(key, r.phraseIds) }
            .map { it.second }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun issueKey(period: InsightsPeriod, nowMs: Long): String =
        "${period.name}@${InsightsEngine.windows(period, nowMs, zone).first.start}"

    // ------------------------------------------------------------------ past issues
    private val _past = MutableStateFlow<List<InsightsReport>>(emptyList())
    val past: StateFlow<List<InsightsReport>> = _past.asStateFlow()

    /** The previous [count] issues of the current period, newest first. Built on demand for the Past recaps sheet. */
    fun loadPast(count: Int = 6) {
        viewModelScope.launch {
            val b = base.first()
            val per = period.value
            val history = prefs.phraseHistory.first()
            _past.value = withContext(Dispatchers.Default) {
                val now = System.currentTimeMillis()
                (1..count).map { k -> now - k * per.days * DAY_MS }
                    .filter { t -> b.sessions.any { it.startMs < t } }
                    .map { t ->
                        val key = issueKey(per, t)
                        InsightsEngine.report(
                            InsightsInput(
                                sessions = b.sessions, exercises = b.exercises, plannedDays = b.planned, weights = b.weights,
                                profile = b.profile, period = per, nowMs = t, zone = zone,
                                recentPhraseIds = history.filterKeys { it != key }.values.flatten().toSet(),
                            ),
                        )
                    }
            }
        }
    }

    // ------------------------------------------------------------------ a question's own page
    /** Session titles and dates by id, for "the sessions behind this". */
    val overviews: StateFlow<Map<String, SessionOverview>> = sessionRepository.observeSessions()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _byPeriod = MutableStateFlow<Map<InsightsPeriod, InsightsReport>>(emptyMap())
    /** Reports for other periods, built when a question page's range tabs ask for one. */
    val byPeriod: StateFlow<Map<InsightsPeriod, InsightsReport>> = _byPeriod.asStateFlow()

    fun loadPeriod(period: InsightsPeriod) {
        if (period in _byPeriod.value) return
        viewModelScope.launch {
            val b = base.first()
            val history = prefs.phraseHistory.first()
            val now = System.currentTimeMillis()
            val key = issueKey(period, now)
            val r = withContext(Dispatchers.Default) {
                InsightsEngine.report(
                    InsightsInput(
                        sessions = b.sessions, exercises = b.exercises, plannedDays = b.planned, weights = b.weights,
                        profile = b.profile, period = period, nowMs = now, zone = zone,
                        recentPhraseIds = history.filterKeys { it != key }.values.flatten().toSet(),
                    ),
                )
            }
            _byPeriod.value = _byPeriod.value + (period to r)
        }
    }

    // ------------------------------------------------------------------ acting on a move
    /** Fill the planner with a move's exercises (e.g. "Add a leg day") and hand back for navigation. */
    fun prepareWorkout(move: Move, then: () -> Unit) {
        viewModelScope.launch {
            val picks = exerciseRepository.getByIds(move.exerciseIds)
            planDraft.clear()
            move.group?.let { planDraft.rename(it.dayNoun.replaceFirstChar(Char::uppercase) + " day") }
            picks.forEach(planDraft::add)
            then()
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val SLEEP_LOOKBACK_DAYS = 120L
    }
}
