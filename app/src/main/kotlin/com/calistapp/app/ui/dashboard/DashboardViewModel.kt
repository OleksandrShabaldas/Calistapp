package com.calistapp.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.local.StepDayEntity
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.recommend.RecommendationState
import com.calistapp.app.data.recommend.RecommendationsRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.ScheduleRepository
import com.calistapp.app.data.session.ScheduledItem
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.progress.DailyEnergyGoal
import com.calistapp.core.time.startOfWeekMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    sessionRepository: SessionRepository,
    sessionController: SessionController,
    stepsImportRepository: StepsImportRepository,
    scheduleRepository: ScheduleRepository,
    savedWorkoutRepository: SavedWorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val recommendationsRepository: RecommendationsRepository,
    private val watchConnection: WatchConnectionMonitor,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val started = SharingStarted.WhileSubscribed(5_000)

    // ---- passthrough state ----

    val profile: StateFlow<UserProfile> = profileRepository.profile
        .stateIn(viewModelScope, started, UserProfile())
    val goals: StateFlow<TrainingGoals> = profileRepository.goals
        .stateIn(viewModelScope, started, TrainingGoals())
    val isOnboarded: StateFlow<Boolean> = profileRepository.isOnboarded
        .stateIn(viewModelScope, started, true)
    val live: StateFlow<LiveSession?> = sessionController.live
    val watchLink: StateFlow<WatchLinkState> = watchConnection.state
    val recommendations: StateFlow<RecommendationState> = recommendationsRepository.state

    // ---- source flows ----

    /** Today, re-emitting when the day turns over so an app left open keeps up with the clock. */
    private val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(TODAY_CHECK_MS)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, started, LocalDate.now(zone))

    private val sessions: StateFlow<List<SessionOverview>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, started, emptyList())

    /** A wide window of imported step-days — enough for both the week strip and a long streak. */
    private val stepDays: StateFlow<List<StepDayEntity>> = today.flatMapLatest { t ->
        stepsImportRepository.observeRange(
            t.minusDays(STREAK_WINDOW_DAYS).format(ISO_DATE),
            t.format(ISO_DATE),
        )
    }.stateIn(viewModelScope, started, emptyList())

    private val weekStartMs: StateFlow<Long> = today
        .map { startOfWeekMs(it.atStartOfDay(zone).toInstant().toEpochMilli(), zone) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, started, startOfWeekMs(System.currentTimeMillis(), zone))

    private val weekPlan: StateFlow<Map<DayOfWeek, List<ScheduledItem>>> = weekStartMs
        .flatMapLatest { scheduleRepository.weekPlan(it) }
        .stateIn(viewModelScope, started, emptyMap())

    /** Total kcal earned per calendar day (walking + workouts) across the window. */
    private val earnedByDate: StateFlow<Map<LocalDate, Double>> =
        combine(sessions, stepDays) { s, steps ->
            val map = HashMap<LocalDate, Double>()
            steps.forEach { d ->
                val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: return@forEach
                map[date] = (map[date] ?: 0.0) + d.calories
            }
            s.forEach { o ->
                val date = Instant.ofEpochMilli(o.startMs).atZone(zone).toLocalDate()
                map[date] = (map[date] ?: 0.0) + o.totalKcal
            }
            map
        }.stateIn(viewModelScope, started, emptyMap())

    /** kcal earned per step — from the most recent FitPal day, else the formula fallback. */
    private val perStepRate: StateFlow<Double> = combine(stepDays, profile) { steps, prof ->
        steps.filter { it.steps > 0 }.maxByOrNull { it.date }
            ?.let { DailyEnergyGoal.perStepRate(it.steps, it.calories) }
            ?: DailyEnergyGoal.fallbackPerStepRate(prof.weightKg)
    }.stateIn(viewModelScope, started, DailyEnergyGoal.fallbackPerStepRate(75.0))

    // ---- derived UI state ----

    val header: StateFlow<HeaderState> =
        combine(profile, today, earnedByDate, goals, perStepRate) { prof, t, earned, g, rate ->
            val target = DailyEnergyGoal.dailyTargetKcal(g.dailyStepGoal, rate)
            HeaderState(
                name = prof.name,
                greeting = greetingFor(LocalTime.now(zone)),
                dateLabel = t.format(DATE_LABEL).uppercase(Locale.getDefault()),
                streak = DailyEnergyGoal.currentStreak(earned, target, t),
            )
        }.stateIn(viewModelScope, started, HeaderState())

    val steps: StateFlow<StepsState> =
        combine(today, stepDays, earnedByDate, goals, perStepRate) { t, days, earned, g, rate ->
            val todayStr = t.format(ISO_DATE)
            val target = DailyEnergyGoal.dailyTargetKcal(g.dailyStepGoal, rate)
            val earnedToday = earned[t] ?: 0.0
            StepsState(
                steps = days.firstOrNull { it.date == todayStr }?.steps ?: 0,
                stepGoal = g.dailyStepGoal,
                earnedKcal = earnedToday.roundToInt(),
                targetKcal = target,
                progress = DailyEnergyGoal.progress(earnedToday, target),
                goalMet = DailyEnergyGoal.hit(earnedToday, target),
            )
        }.stateIn(viewModelScope, started, StepsState())

    val week: StateFlow<WeekState> =
        combine(today, earnedByDate, sessions, weekPlan) { t, earned, s, plan ->
            val weekStart = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val trained = s.mapTo(HashSet()) { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            val days = (0..6).map { i ->
                val d = weekStart.plusDays(i.toLong())
                DayCell(
                    date = d,
                    letter = DAY_LETTERS[i],
                    kcal = (earned[d] ?: 0.0).roundToInt(),
                    isToday = d == t,
                    isFuture = d.isAfter(t),
                    trained = d in trained,
                    planned = plan[d.dayOfWeek]?.isNotEmpty() == true,
                )
            }
            WeekState(days, days.sumOf { it.kcal })
        }.stateIn(viewModelScope, started, WeekState())

    val nextUp: StateFlow<NextUpState?> =
        combine(today, weekPlan, savedWorkoutRepository.saved, sessions) { t, plan, saved, s ->
            chooseNextUp(t, plan, saved, s)
        }.distinctUntilChanged().flatMapLatest { base ->
            when {
                base == null -> flowOf(null)
                base.firstExerciseId == null -> flowOf(base.toState(emptyList()))
                else -> exerciseRepository.observe(base.firstExerciseId)
                    .map { ex -> base.toState(ex?.imageUrls ?: emptyList()) }
            }
        }.stateIn(viewModelScope, started, null)

    init {
        refreshRecommendations()
        viewModelScope.launch {
            // This-week overrides for past weeks can never fire again.
            runCatching { scheduleRepository.pruneOldOverrides(startOfWeekMs(System.currentTimeMillis(), zone)) }
        }
    }

    fun reconnectWatch() = watchConnection.reconnect()

    fun refreshRecommendations(force: Boolean = false) {
        viewModelScope.launch { runCatching { recommendationsRepository.refresh(force) } }
    }

    // ---- helpers ----

    private data class NextUpBase(
        val savedWorkoutId: String,
        val name: String,
        val meta: String,
        val firstExerciseId: String?,
        val whenLabel: String,
        val scheduled: Boolean,
    ) {
        fun toState(imageUrls: List<String>) =
            NextUpState(savedWorkoutId, name, meta, imageUrls, whenLabel, scheduled)
    }

    private fun chooseNextUp(
        today: LocalDate,
        plan: Map<DayOfWeek, List<ScheduledItem>>,
        saved: List<SavedWorkout>,
        sessions: List<SessionOverview>,
    ): NextUpBase? {
        val trainedToday = sessions.any {
            Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == today
        }
        // Look from today through the rest of this week for the next planned workout.
        for (offset in 0..(DayOfWeek.SUNDAY.value - today.dayOfWeek.value)) {
            val d = today.plusDays(offset.toLong())
            val item = plan[d.dayOfWeek].orEmpty().firstOrNull() ?: continue
            if (offset == 0 && trainedToday) continue
            return base(item.workout, whenLabel(d, today), scheduled = true)
        }
        // Nothing planned ahead this week — offer the most-recent saved workout instead.
        return saved.firstOrNull()?.let { base(it, "Saved", scheduled = false) }
    }

    private fun base(w: SavedWorkout, whenLabel: String, scheduled: Boolean) = NextUpBase(
        savedWorkoutId = w.id,
        name = w.name,
        meta = metaFor(w.plan),
        firstExerciseId = w.plan.exercises.firstOrNull()?.exerciseId,
        whenLabel = whenLabel,
        scheduled = scheduled,
    )

    private fun metaFor(plan: WorkoutPlan): String = buildString {
        val ex = plan.exercises.size
        val sets = plan.totalSets
        append("$ex ${if (ex == 1) "exercise" else "exercises"}")
        append(" · $sets ${if (sets == 1) "set" else "sets"}")
        if (plan.isCircuit) append(" · ${plan.rounds} rounds")
    }

    private fun whenLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    private fun greetingFor(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    private companion object {
        const val TODAY_CHECK_MS = 5 * 60_000L
        const val STREAK_WINDOW_DAYS = 130L
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.getDefault())
        val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
    }
}
