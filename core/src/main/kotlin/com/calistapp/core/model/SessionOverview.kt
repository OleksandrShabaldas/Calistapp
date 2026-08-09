package com.calistapp.core.model

/**
 * A session as a list needs it: what it was, when, and how it scored.
 *
 * Distinct from [WorkoutSession] because that carries the entire heart-rate stream — thousands of
 * readings per workout — and every screen that shows a *list* of sessions was paying to deserialize
 * all of them to render two lines of text. Loading the full session stays the detail screen's job.
 */
data class SessionOverview(
    val id: String,
    val exerciseType: ExerciseType,
    val startMs: Long,
    val endMs: Long?,
    val exerciseName: String?,
    val summary: SessionSummary?,
) {
    val totalKcal: Int get() = summary?.totalKcal?.toInt() ?: 0
    val totalReps: Int get() = summary?.totalReps ?: 0
    val avgHr: Int get() = summary?.avgHr ?: 0
    val activeDurationMs: Long get() = summary?.activeDurationMs ?: 0L

    /** What to call it in a list — the exercise if there was one, otherwise the workout type. */
    val title: String get() = exerciseName ?: exerciseType.displayName
}
