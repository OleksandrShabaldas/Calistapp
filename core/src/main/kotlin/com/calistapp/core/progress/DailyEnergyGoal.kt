package com.calistapp.core.progress

import java.time.DayOfWeek
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

    /**
     * The streak stats the heatmap popup shows, computed across `[firstDay, today]` where firstDay is
     * the earliest day with any burn. Pure so it can be unit-tested.
     */
    fun stats(earnedByDate: Map<LocalDate, Double>, targetKcal: Int, today: LocalDate): StreakStats {
        val current = currentStreak(earnedByDate, targetKcal, today)
        val firstDay = earnedByDate.entries.filter { it.value > 0.0 }.minByOrNull { it.key }?.key
            ?: return StreakStats(current, 0, 0, null, 0, null, 0, 0, 0, emptyMap(), null, 0)

        var longest = 0
        var run = 0
        var longestMiss = 0
        var missRun = 0
        var hits = 0
        var totalDays = 0
        var sumEarned = 0.0
        var best: LocalDate? = null
        var bestKcal = 0.0
        val weekdayHits = HashMap<DayOfWeek, Int>()
        val weekdayCount = HashMap<DayOfWeek, Int>()

        var d = firstDay
        while (!d.isAfter(today)) {
            val earned = earnedByDate[d] ?: 0.0
            totalDays++
            sumEarned += earned
            if (earned > bestKcal) { bestKcal = earned; best = d }
            val wd = d.dayOfWeek
            weekdayCount[wd] = (weekdayCount[wd] ?: 0) + 1
            if (hit(earned, targetKcal)) {
                hits++; run++; longest = maxOf(longest, run); missRun = 0
                weekdayHits[wd] = (weekdayHits[wd] ?: 0) + 1
            } else {
                missRun++; longestMiss = maxOf(longestMiss, missRun); run = 0
            }
            d = d.plusDays(1)
        }

        // The most recent hit→miss transition — the day the latest streak ended.
        var lastBroken: LocalDate? = null
        var back = today
        while (back.isAfter(firstDay)) {
            if (!hit(earnedByDate[back] ?: 0.0, targetKcal) && hit(earnedByDate[back.minusDays(1)] ?: 0.0, targetKcal)) {
                lastBroken = back; break
            }
            back = back.minusDays(1)
        }

        val monthStart = today.withDayOfMonth(1)
        var daysHitThisMonth = 0
        var m = monthStart
        while (!m.isAfter(today)) {
            if (hit(earnedByDate[m] ?: 0.0, targetKcal)) daysHitThisMonth++
            m = m.plusDays(1)
        }

        val weekdayRate = DayOfWeek.entries.associateWith { wd ->
            val c = weekdayCount[wd] ?: 0
            if (c == 0) 0f else (weekdayHits[wd] ?: 0).toFloat() / c
        }

        return StreakStats(
            current = current,
            longest = longest,
            longestMiss = longestMiss,
            lastBrokenDay = lastBroken,
            daysHitThisMonth = daysHitThisMonth,
            bestDay = best,
            bestDayKcal = bestKcal.roundToInt(),
            goalHitPercent = if (totalDays == 0) 0 else hits * 100 / totalDays,
            avgDailyBurn = if (totalDays == 0) 0 else (sumEarned / totalDays).roundToInt(),
            weekdayHitRate = weekdayRate,
            firstDay = firstDay,
            totalDaysTracked = totalDays,
        )
    }

    /** FitPal's default over-count trim, used only by [fallbackPerStepRate]. */
    const val DEFAULT_TRIM_PERCENT = 15
}

/** Everything the streak popup shows beyond the day-by-day heatmap. */
data class StreakStats(
    val current: Int,
    val longest: Int,
    /** Longest run of consecutive days *below* the goal. */
    val longestMiss: Int,
    /** The most recent day a streak ended (a hit→miss transition), or null if never broken. */
    val lastBrokenDay: LocalDate?,
    val daysHitThisMonth: Int,
    val bestDay: LocalDate?,
    val bestDayKcal: Int,
    val goalHitPercent: Int,
    val avgDailyBurn: Int,
    val weekdayHitRate: Map<DayOfWeek, Float>,
    val firstDay: LocalDate?,
    val totalDaysTracked: Int,
)
