package com.calistapp.core.progress

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The daily energy goal derived from a step target, and the streak of days that met it.
 *
 * The user sets a daily **step** goal. We convert it to a **calorie** goal using FitPal's own
 * per-step rate, then credit both walking and workout calories against it — so on a day you train,
 * the workout fills the goal and you need fewer steps to keep the streak. This is the maths the
 * dashboard's goal ring and streak pill read; kept here (pure, no Android) so it's unit-tested.
 */
object DailyEnergyGoal {

    /**
     * kcal earned per step, taken straight from a recent FitPal day (`calories ÷ steps`). This reuses
     * FitPal's formula (`steps × 0.04 × weight/70`) *and* its user-set over-count trim in one number,
     * rather than re-deriving either. Null when the day has no usable steps.
     */
    fun perStepRate(recentSteps: Int, recentCalories: Double): Double? =
        if (recentSteps > 0 && recentCalories > 0.0) recentCalories / recentSteps else null

    /**
     * The rate to use before any FitPal day has been imported: FitPal's own formula at a default
     * trim. Only a stand-in until a real day lands and [perStepRate] takes over.
     */
    fun fallbackPerStepRate(weightKg: Double, trimPercent: Int = DEFAULT_TRIM_PERCENT): Double =
        0.04 * (weightKg / 70.0) * (1 - trimPercent.coerceIn(0, 90) / 100.0)

    /** The day's calorie goal, from a step goal and a per-step rate. */
    fun dailyTargetKcal(stepGoal: Int, perStepRate: Double): Int =
        (stepGoal * perStepRate).roundToInt().coerceAtLeast(1)

    /** How far through today's goal, 0f..1f, for the ring. */
    fun progress(earnedKcal: Double, targetKcal: Int): Float =
        if (targetKcal <= 0) 0f else (earnedKcal / targetKcal).toFloat().coerceIn(0f, 1f)

    /** A day met the goal when steps + workout calories reach the target. */
    fun hit(earnedKcal: Double, targetKcal: Int): Boolean =
        targetKcal > 0 && earnedKcal >= targetKcal

    /**
     * Consecutive days that met the goal, counting back from [today].
     *
     * A [today] that hasn't hit the goal yet does **not** break a streak earned through yesterday —
     * the day isn't over. So we start the count at today if it's already met, otherwise at yesterday,
     * and walk backwards while each day is a hit. [earnedByDate] is total earned kcal (steps +
     * workouts) per calendar day; missing days count as 0.
     */
    fun currentStreak(
        earnedByDate: Map<LocalDate, Double>,
        targetKcal: Int,
        today: LocalDate,
    ): Int {
        if (targetKcal <= 0) return 0
        var cursor = if (hit(earnedByDate[today] ?: 0.0, targetKcal)) today else today.minusDays(1)
        var streak = 0
        while (hit(earnedByDate[cursor] ?: 0.0, targetKcal)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** FitPal's default over-count trim, used only by [fallbackPerStepRate]. */
    const val DEFAULT_TRIM_PERCENT = 15
}
