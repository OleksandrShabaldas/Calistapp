package com.calistapp.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.local.StepDayEntity
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.recommend.RecommendationsRepository
import com.calistapp.app.data.recommend.RecommendationsUi
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.ScheduleRepository
import com.calistapp.app.data.session.ScheduledItem
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.MediaType
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.progress.DailyEnergyGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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
    private val scheduleRepository: ScheduleRepository,
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
    val recommendations: StateFlow<RecommendationsUi> = recommendationsRepository.ui

    // ---- source flows ----

    private val today: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(TODAY_CHECK_MS)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, started, LocalDate.now(zone))

    private val sessions: StateFlow<List<SessionOverview>> = sessionRepository.observeSessions()
        .stateIn(viewModelScope, started, emptyList())

    private val stepDays: StateFlow<List<StepDayEntity>> = today.flatMapLatest { t ->
        stepsImportRepository.observeRange(
            t.minusDays(STREAK_WINDOW_DAYS).format(ISO_DATE),
            t.format(ISO_DATE),
        )
    }.stateIn(viewModelScope, started, emptyList())

    /** Walking calories per calendar day (FitPal's already-trimmed figure). */
    private val stepKcalByDate: StateFlow<Map<LocalDate, Double>> = stepDays.map { days ->
        days.mapNotNull { d -> runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to d.calories } }.toMap()
    }.stateIn(viewModelScope, started, emptyMap())

    /** Workout calories per calendar day (Calistapp's HR-based total). */
    private val workoutKcalByDate: StateFlow<Map<LocalDate, Double>> = sessions.map { list ->
        val map = HashMap<LocalDate, Double>()
        list.forEach { o ->
            val d = Instant.ofEpochMilli(o.startMs).atZone(zone).toLocalDate()
            map[d] = (map[d] ?: 0.0) + o.totalKcal
        }
        map
    }.stateIn(viewModelScope, started, emptyMap())

    private val earnedByDate: StateFlow<Map<LocalDate, Double>> =
        combine(stepKcalByDate, workoutKcalByDate) { step, workout ->
            val map = HashMap<LocalDate, Double>(step)
            workout.forEach { (d, k) -> map[d] = (map[d] ?: 0.0) + k }
            map
        }.stateIn(viewModelScope, started, emptyMap())

    private val perStepRate: StateFlow<Double> = combine(stepDays, profile) { days, prof ->
        days.filter { it.steps > 0 }.maxByOrNull { it.date }
            ?.let { DailyEnergyGoal.perStepRate(it.steps, it.calories) }
            ?: DailyEnergyGoal.fallbackPerStepRate(prof.weightKg)
    }.stateIn(viewModelScope, started, DailyEnergyGoal.fallbackPerStepRate(75.0))

    // ---- week navigation (swipe back/forward through the strip) ----

    private val weekOffset = MutableStateFlow(0)
    val isCurrentWeek: StateFlow<Boolean> = weekOffset
        .map { it == 0 }.stateIn(viewModelScope, started, true)

    /** A past day the user tapped to inspect (its steps + that day's session log). Null = live/today. */
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = selectedDay.asStateFlow()

    private val selectedWeekStart: StateFlow<LocalDate> = combine(today, weekOffset) { t, off ->
        t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(off.toLong())
    }.distinctUntilChanged().stateIn(viewModelScope, started, LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    private val selectedWeekPlan: StateFlow<Map<DayOfWeek, List<ScheduledItem>>> = selectedWeekStart
        .map { it.atStartOfDay(zone).toInstant().toEpochMilli() }
        .distinctUntilChanged()
        .flatMapLatest { scheduleRepository.weekPlan(it) }
        .stateIn(viewModelScope, started, emptyMap())

    private val currentWeekPlan: StateFlow<Map<DayOfWeek, List<ScheduledItem>>> = today
        .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli() }
        .distinctUntilChanged()
        .flatMapLatest { scheduleRepository.weekPlan(it) }
        .stateIn(viewModelScope, started, emptyMap())

    fun previousWeek() { selectedDay.value = null; weekOffset.value = (weekOffset.value - 1).coerceAtLeast(-MAX_WEEKS_BACK) }
    fun nextWeek() { selectedDay.value = null; weekOffset.value = (weekOffset.value + 1).coerceAtMost(0) }
    fun resetToCurrentWeek() { selectedDay.value = null; weekOffset.value = 0 }

    /** Inspect a specific past day — scrolls the strip to its week and highlights it. Today clears it. */
    fun selectDay(date: LocalDate) {
        val today = LocalDate.now(zone)
        if (date.isAfter(today)) return
        selectedDay.value = if (date == today) null else date
        val curWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val selWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        weekOffset.value = ChronoUnit.WEEKS.between(curWeek, selWeek).toInt().coerceIn(-MAX_WEEKS_BACK, 0)
    }

    fun clearSelectedDay() { selectedDay.value = null; weekOffset.value = 0 }

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

    /** The inspected past day, or null when viewing today. Drives the day-detail mode on the screen. */
    val dayView: StateFlow<DayView?> =
        combine(selectedDay, stepDays, sessions, goals, perStepRate) { sel, days, sess, g, rate ->
            if (sel == null) return@combine null
            val today = LocalDate.now(zone)
            if (!sel.isBefore(today)) return@combine null
            val row = days.firstOrNull { it.date == sel.format(ISO_DATE) }
            val stepK = row?.calories ?: 0.0
            val daySessions = sess.filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == sel }
            val workoutK = daySessions.sumOf { it.totalKcal.toDouble() }
            val earned = stepK + workoutK
            val target = DailyEnergyGoal.dailyTargetKcal(g.dailyStepGoal, rate)
            DayView(
                date = sel,
                dateLabel = sel.format(DAY_VIEW_LABEL),
                steps = row?.steps ?: 0,
                stepGoal = g.dailyStepGoal,
                earnedKcal = earned.roundToInt(),
                targetKcal = target,
                progress = DailyEnergyGoal.progress(earned, target),
                sessions = daySessions,
            )
        }.stateIn(viewModelScope, started, null)

    val week: StateFlow<WeekState> =
        combine(selectedWeekStart, stepKcalByDate, workoutKcalByDate, sessions, selectedWeekPlan) { weekStart, stepMap, workoutMap, s, plan ->
            val todayDate = LocalDate.now(zone)
            val currentWeekStart = todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val trained = s.mapTo(HashSet()) { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            var stepTotal = 0.0
            var workoutTotal = 0.0
            val days = (0..6).map { i ->
                val d = weekStart.plusDays(i.toLong())
                val stepK = stepMap[d] ?: 0.0
                val workoutK = workoutMap[d] ?: 0.0
                stepTotal += stepK
                workoutTotal += workoutK
                DayCell(
                    date = d,
                    letter = DAY_LETTERS[i],
                    kcal = (stepK + workoutK).roundToInt(),
                    isToday = d == todayDate,
                    isFuture = d.isAfter(todayDate),
                    trained = d in trained,
                    planned = plan[d.dayOfWeek]?.isNotEmpty() == true,
                    isSunday = i == 6,
                )
            }
            WeekState(
                days = days,
                totalKcal = days.sumOf { it.kcal },
                stepKcal = stepTotal.roundToInt(),
                workoutKcal = workoutTotal.roundToInt(),
                title = weekTitle(weekStart, currentWeekStart),
                isCurrentWeek = weekStart == currentWeekStart,
            )
        }.stateIn(viewModelScope, started, WeekState())

    val nextUp: StateFlow<NextUpState?> =
        combine(today, currentWeekPlan, savedWorkoutRepository.saved, sessions) { t, plan, saved, s ->
            chooseNextUp(t, plan, saved, s)
        }.distinctUntilChanged().mapLatest { base ->
            if (base == null) {
                null
            } else {
                val byId = runCatching { exerciseRepository.getByIds(base.exerciseIds) }
                    .getOrDefault(emptyList()).associateBy { it.id }
                val ordered = base.exerciseIds.mapNotNull { byId[it] }
                val videos = ordered.mapNotNull { ex -> ex.media.firstOrNull { it.type == MediaType.VIDEO }?.url }
                val images = ordered.firstOrNull { it.imageUrls.isNotEmpty() }?.imageUrls ?: emptyList()
                base.toState(videos, images)
            }
        }.stateIn(viewModelScope, started, null)

    init {
        refreshRecommendations()
        viewModelScope.launch {
            runCatching {
                scheduleRepository.pruneOldOverrides(
                    LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .atStartOfDay(zone).toInstant().toEpochMilli(),
                )
            }
        }
    }

    fun reconnectWatch() = watchConnection.reconnect()

    fun refreshRecommendations(forceConditions: Boolean = false) {
        viewModelScope.launch { runCatching { recommendationsRepository.refresh(forceConditions) } }
    }

    /** The conditions detail card's manual "regenerate" (and a fresh location grant). */
    fun regenerateConditions() = refreshRecommendations(forceConditions = true)

    // ---- helpers ----

    private data class NextUpBase(
        val savedWorkoutId: String,
        val name: String,
        val meta: String,
        val exerciseIds: List<String>,
        val whenLabel: String,
        val scheduled: Boolean,
    ) {
        fun toState(videoUrls: List<String>, imageUrls: List<String>) =
            NextUpState(savedWorkoutId, name, meta, videoUrls, imageUrls, whenLabel, scheduled)
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
        for (offset in 0..(DayOfWeek.SUNDAY.value - today.dayOfWeek.value)) {
            val d = today.plusDays(offset.toLong())
            val item = plan[d.dayOfWeek].orEmpty().firstOrNull() ?: continue
            if (offset == 0 && trainedToday) continue
            return base(item.workout, whenLabel(d, today), scheduled = true)
        }
        return saved.firstOrNull()?.let { base(it, "Saved", scheduled = false) }
    }

    private fun base(w: SavedWorkout, whenLabel: String, scheduled: Boolean) = NextUpBase(
        savedWorkoutId = w.id,
        name = w.name,
        meta = metaFor(w.plan),
        exerciseIds = w.plan.exercises.map { it.exerciseId },
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

    private fun weekTitle(weekStart: LocalDate, currentWeekStart: LocalDate): String = when (weekStart) {
        currentWeekStart -> "This week"
        currentWeekStart.minusWeeks(1) -> "Last week"
        else -> {
            val end = weekStart.plusDays(6)
            val sm = weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val em = end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            if (weekStart.month == end.month) "${weekStart.dayOfMonth}–${end.dayOfMonth} $em"
            else "${weekStart.dayOfMonth} $sm – ${end.dayOfMonth} $em"
        }
    }

    private companion object {
        const val TODAY_CHECK_MS = 5 * 60_000L
        const val STREAK_WINDOW_DAYS = 130L
        const val MAX_WEEKS_BACK = 26
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.getDefault())
        val DAY_VIEW_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
    }
}
