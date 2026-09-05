package com.calistapp.core.model

import kotlinx.serialization.Serializable

/**
 * Per-exercise slice of a session's energy cost — what "300 kcal" was actually spent on.
 * Populated when the session was built from a plan; empty for free-form workouts.
 */
@Serializable
data class ExerciseBreakdown(
    val slotId: String?,
    val exerciseName: String,
    val kcal: Double,
    val reps: Int,
    val sets: Int,
    val activeDurationMs: Long,
)

/**
 * A single measured recovery: how far the heart fell in the minute after one set's rest began.
 *
 * Kept per-rest (not just averaged) so the summary can show the athlete *which* efforts they bounced
 * back from and which they didn't — a hard set late in a session usually recovers slower, and that
 * shape is the interesting part.
 */
@Serializable
data class RestDrop(
    /** The movement whose set this rest followed, if the block carried one. */
    val afterExercise: String?,
    /** Highest reading in the run-up to the rest — what the heart was recovering from. */
    val peakBpm: Int,
    /** Reading one minute into the rest. */
    val endBpm: Int,
    /** [peakBpm] − [endBpm]; always positive (a rise isn't a recovery). */
    val dropBpm: Int,
    /** When the rest began — orders the drops along the session. */
    val atMs: Long,
)

/**
 * How fast heart rate fell in the minute after sets ended — a marker of aerobic fitness that the
 * work/rest segmentation makes measurable without asking the user for anything.
 *
 * Under about 12 bpm is the threshold clinical work treats as blunted; trained people usually see
 * 20–40. Averaged across the session's rests, because a single one might just be a trip to the
 * water fountain.
 */
@Serializable
data class HrRecovery(
    val meanDropBpm: Int,
    val bestDropBpm: Int,
    /** How many rest blocks were long enough and well-sampled enough to measure. */
    val measuredRests: Int,
    /** Each measured rest in session order. Absent from summaries stored before this was tracked. */
    val drops: List<RestDrop> = emptyList(),
)

/**
 * The computed result of running a [WorkoutSession] through the CalorieEngine.
 * This is what the UI shows and what the AI layer reasons over.
 */
@Serializable
data class SessionSummary(
    val totalKcal: Double,
    val activeKcal: Double,
    val restKcal: Double,
    val activeDurationMs: Long,
    val restDurationMs: Long,
    val avgHr: Int,
    val peakHr: Int,
    /** Average HR during ACTIVE segments only — a cleaner intensity signal than overall avg. */
    val avgActiveHr: Int,
    val minHr: Int,
    val timeInZonesMs: Map<HrZone, Long> = emptyMap(),
    /** Energy attributed to each planned exercise, ordered by cost. */
    val perExercise: List<ExerciseBreakdown> = emptyList(),
    /** Total reps logged across the session. */
    val totalReps: Int = 0,
    /** Null when no rest block was long enough to measure one. Absent from older stored summaries. */
    val hrRecovery: HrRecovery? = null,
) {
    val totalDurationMs: Long get() = activeDurationMs + restDurationMs

    /** Fraction of the session actually spent working (0..1). */
    val activeRatio: Double
        get() = if (totalDurationMs == 0L) 0.0 else activeDurationMs.toDouble() / totalDurationMs

    companion object {
        val EMPTY = SessionSummary(
            totalKcal = 0.0, activeKcal = 0.0, restKcal = 0.0,
            activeDurationMs = 0, restDurationMs = 0,
            avgHr = 0, peakHr = 0, avgActiveHr = 0, minHr = 0,
        )
    }
}
