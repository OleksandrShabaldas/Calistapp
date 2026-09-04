package com.calistapp.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.ScheduleRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.progress.DailyEnergyGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** One day in the monthly grid, with its calorie rings and dots. */
data class MonthDayCell(
    val date: LocalDate,
    val earnedKcal: Int,
    val goalKcal: Int,
    /** Goal progress 0..1 (the orange ring). */
    val orangeFraction: Float,
    /** Over-goal progress 0..1, where 1 = twice the goal (the red ring on top). */
    val redFraction: Float,
    val trained: Boolean,
    val planned: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class MonthView(
    val title: String = "",
    /** Monday-based blank cells before day 1. */
    val leadingBlanks: Int = 0,
    val days: List<MonthDayCell> = emptyList(),
    val isCurrentMonth: Boolean = true,
    val totalBurned: Int = 0,
    val avgSteps: Int = 0,
    val exercisesLogged: Int = 0,
    val daysHitGoal: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    profileRepository: ProfileRepository,
    private val stepsImportRepository: StepsImportRepository,
    scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val started = SharingStarted.WhileSubscribed(5_000)
    private val monthOffset = MutableStateFlow(0)

    private val selectedMonth: StateFlow<YearMonth> = monthOffset
        .map { YearMonth.now(zone).plusMonths(it.toLong()) }
        .stateIn(viewModelScope, started, YearMonth.now(zone))

    fun previousMonth() { monthOffset.value = (monthOffset.value - 1).coerceAtLeast(-MAX_MONTHS_BACK) }
    fun nextMonth() { monthOffset.value = (monthOffset.value + 1).coerceAtMost(0) }
    fun resetToCurrentMonth() { monthOffset.value = 0 }
    fun jumpTo(date: LocalDate) {
        val target = YearMonth.from(date)
        val delta = ((target.year - YearMonth.now(zone).year) * 12 + (target.monthValue - YearMonth.now(zone).monthValue))
        monthOffset.value = delta.coerceIn(-MAX_MONTHS_BACK, 0)
    }

    private val sessions = sessionRepository.observeSessions().stateIn(viewModelScope, started, emptyList())
    private val goals = profileRepository.goals.stateIn(viewModelScope, started, com.calistapp.core.model.TrainingGoals())
    private val recurringWeekdays = scheduleRepository.recurring
        .map { it.filterValues { items -> items.isNotEmpty() }.keys }
        .stateIn(viewModelScope, started, emptySet())

    private val monthSteps = selectedMonth.flatMapLatest { ym ->
        stepsImportRepository.observeRange(ym.atDay(1).format(ISO), ym.atEndOfMonth().format(ISO))
    }.stateIn(viewModelScope, started, emptyList())

    val monthView: StateFlow<MonthView> =
        combine(selectedMonth, monthSteps, sessions, goals, recurringWeekdays) { ym, steps, sess, g, plannedDays ->
            val stepByDate = steps.mapNotNull { s ->
                runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it to s }
            }.toMap()
            val rate = steps.filter { it.steps > 0 }.maxByOrNull { it.date }
                ?.let { DailyEnergyGoal.perStepRate(it.steps, it.calories) }
                ?: DailyEnergyGoal.fallbackPerStepRate(75.0)
            val goalKcal = DailyEnergyGoal.dailyTargetKcal(g.dailyStepGoal, rate)

            val today = LocalDate.now(zone)
            val workoutByDate = HashMap<LocalDate, Double>()
            sess.forEach { o ->
                val d = Instant.ofEpochMilli(o.startMs).atZone(zone).toLocalDate()
                if (YearMonth.from(d) == ym) workoutByDate[d] = (workoutByDate[d] ?: 0.0) + o.totalKcal
            }
            val trainedDates = workoutByDate.keys

            val first = ym.atDay(1)
            val days = (0 until ym.lengthOfMonth()).map { i ->
                val d = first.plusDays(i.toLong())
                val earned = (stepByDate[d]?.calories ?: 0.0) + (workoutByDate[d] ?: 0.0)
                MonthDayCell(
                    date = d,
                    earnedKcal = earned.roundToInt(),
                    goalKcal = goalKcal,
                    orangeFraction = (earned / goalKcal).toFloat().coerceIn(0f, 1f),
                    redFraction = ((earned - goalKcal) / goalKcal).toFloat().coerceIn(0f, 1f),
                    trained = d in trainedDates,
                    planned = d.dayOfWeek in plannedDays && d !in trainedDates,
                    isToday = d == today,
                    isFuture = d.isAfter(today),
                )
            }

            val stepDaysWithData = steps.filter { it.steps > 0 }
            MonthView(
                title = "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}",
                leadingBlanks = first.dayOfWeek.value - 1,
                days = days,
                isCurrentMonth = ym == YearMonth.now(zone),
                totalBurned = days.sumOf { it.earnedKcal },
                avgSteps = if (stepDaysWithData.isEmpty()) 0 else stepDaysWithData.sumOf { it.steps } / stepDaysWithData.size,
                exercisesLogged = trainedDates.size,
                daysHitGoal = days.count { !it.isFuture && it.earnedKcal >= goalKcal },
            )
        }.stateIn(viewModelScope, started, MonthView())

    private companion object {
        const val MAX_MONTHS_BACK = 60
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
