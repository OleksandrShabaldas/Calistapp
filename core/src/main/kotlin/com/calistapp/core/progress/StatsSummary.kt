package com.calistapp.core.progress

import com.calistapp.core.time.startOfWeekMs
import java.time.Instant
import java.time.ZoneId

/**
 * The window the statistics grid is scoped to.
 *
 * Calendar boundaries, not sliding windows — "this week" is the thing you compare against last week,
 * and a window that quietly slides forward every time you open the app is neither comparable nor
 * plannable. (Same reasoning as [startOfWeekMs].)
 */
enum class StatsPeriod(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    ALL("All time"),
}

/**
 * The headline numbers for one [StatsPeriod], each destined for a coloured tile on the stats screen.
 *
 * Derived on demand from stored sessions like the rest of [TrainingProgress] — nothing tallied
 * incrementally, so nothing can drift from the history it claims to describe. Volume is added-load
 * volume (Σ added-kg × reps over working sets); for pure bodyweight training it is honestly zero,
 * which is why reps and exercise count sit beside it rather than behind it.
 */
data class StatsSummary(
    val period: StatsPeriod,
    val workouts: Int,
    /** Distinct movements performed in the window. */
    val exercises: Int,
    val totalReps: Int,
    /** Σ (added weight × reps) over working sets. Zero for unweighted work — by design, not a bug. */
    val totalVolumeKg: Double,
    val totalActiveMs: Long,
    /** Active time per workout, for the "avg session" tile. */
    val avgActiveMs: Long,
    val totalKcal: Int,
) {
    val isEmpty: Boolean get() = workouts == 0
}

/**
 * Roll the sessions falling inside [period] up into [StatsSummary].
 *
 * Pure and deterministic — [nowMs] and [zone] are parameters, not ambient reads, so a period
 * boundary means the same thing here as everywhere else and the whole thing stays testable. Warm-up
 * sets are excluded from reps and volume for the same reason [exerciseProgress] excludes them: they
 * cost energy but they aren't training volume.
 */
fun statsSummary(
    sessions: List<PerformedSession>,
    period: StatsPeriod,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): StatsSummary {
    val from = when (period) {
        StatsPeriod.WEEK -> startOfWeekMs(nowMs, zone)
        StatsPeriod.MONTH -> startOfMonthMs(nowMs, zone)
        StatsPeriod.ALL -> Long.MIN_VALUE
    }
    val inPeriod = sessions.filter { it.startMs >= from }

    var reps = 0
    var volume = 0.0
    val movements = HashSet<String>()
    for (session in inPeriod) {
        for (log in session.setLogs) {
            val slot = session.plan.slot(log.slotId)
            if (slot?.isWarmup(log.setIndex) == true) continue
            reps += log.reps
            volume += (slot?.addedWeightKg ?: 0.0) * log.reps
            val key = log.exerciseId.ifBlank { log.exerciseName }
            if (key.isNotBlank()) movements += key
        }
    }

    val workouts = inPeriod.size
    val activeMs = inPeriod.sumOf { it.activeDurationMs }
    return StatsSummary(
        period = period,
        workouts = workouts,
        exercises = movements.size,
        totalReps = reps,
        totalVolumeKg = volume,
        totalActiveMs = activeMs,
        avgActiveMs = if (workouts > 0) activeMs / workouts else 0L,
        totalKcal = inPeriod.sumOf { it.kcal },
    )
}

/** First millisecond of the calendar month containing [nowMs]. */
private fun startOfMonthMs(nowMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().withDayOfMonth(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
