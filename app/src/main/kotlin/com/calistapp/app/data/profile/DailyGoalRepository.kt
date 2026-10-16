package com.calistapp.app.data.profile

import com.calistapp.app.data.fitpal.StepsImportRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.progress.DailyEnergyGoal
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One source of daily targets for the dashboard, calendar, and streaks, including month edges. */
@Singleton
class DailyGoalRepository @Inject constructor(
    profiles: ProfileRepository,
    steps: StepsImportRepository,
    sessions: SessionRepository,
) {
    val history = combine(profiles.goals, profiles.profile, steps.observeAll(), sessions.observeSessions()) { goals, profile, days, workouts ->
        val rate = days.filter { it.steps > 0 && it.calories > 0.0 }.maxByOrNull { it.date }
            ?.let { DailyEnergyGoal.perStepRate(it.steps, it.calories) }
            ?: DailyEnergyGoal.fallbackPerStepRate(profile.weightKg)
        val earned = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.calories }
        }.toMap().toMutableMap()
        workouts.forEach { workout ->
            val date = Instant.ofEpochMilli(workout.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            earned[date] = (earned[date] ?: 0.0) + workout.totalKcal
        }
        DailyGoalHistory(goals, rate, earned)
    }
}

data class DailyGoalHistory(
    val goals: TrainingGoals = TrainingGoals(),
    val rate: Double = DailyEnergyGoal.fallbackPerStepRate(75.0),
    val earned: Map<LocalDate, Double> = emptyMap(),
) {
    private val original = goals.dailyStepGoal * rate
    private val targets = DailyEnergyGoal.reducedTargets(earned, original, goals.reduceGoalAfterActiveDay)
    val originalKcal = DailyEnergyGoal.dailyTargetKcal(goals.dailyStepGoal, rate)
    fun targetOn(date: LocalDate): Int = DailyEnergyGoal.roundedTarget(targets[date] ?: original, original)
    fun stepsOn(date: LocalDate): Int = DailyEnergyGoal.roundedTarget(
        (targets[date] ?: original) / rate, goals.dailyStepGoal.toDouble(),
    )
}
