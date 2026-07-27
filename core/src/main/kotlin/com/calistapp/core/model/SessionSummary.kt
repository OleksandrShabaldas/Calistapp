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
