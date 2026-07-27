package com.calistapp.app.session

import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.WorkoutPlan

/**
 * The full, continuously-updated state of an in-progress workout.
 *
 * Note there is no heart-rate sample list here. The raw stream is retained privately by
 * [SessionController] for persistence; copying a growing list into this state on every reading was
 * pure overhead, and no live screen needs more than the latest value.
 */
data class LiveSession(
    val id: String,
    val exerciseType: ExerciseType,
    val startMs: Long,
    val status: SessionStatus,
    val currentSegment: SegmentType,
    /** The exercises this workout was built from; empty for a free-form session. */
    val plan: WorkoutPlan,
    val currentSlotId: String?,
    /** Reps counted for the block in progress. */
    val currentReps: Int,
    /** Which set of the current exercise this is, 1-based. */
    val setIndex: Int,
    /** Sets already completed, per slot. */
    val completedSets: Map<String, Int>,
    val summary: SessionSummary,
    val lastBpm: Int,
    val nowMs: Long,
    /** Whether heart rate is currently arriving from the watch. */
    val receivingHr: Boolean,
    /**
     * When the lead-in to the next work block elapses, or null when not counting down.
     *
     * Work doesn't begin the instant you tap — you still have to get to the bar. Counting the gap as
     * exercise time credits calories for walking across the room and, worse, starts the cardiac-lag
     * correction early, which distorts a short set's score.
     */
    val countdownUntilMs: Long? = null,
) {
    val elapsedMs: Long get() = nowMs - startMs
    val currentExercise: PlannedExercise? get() = plan.slot(currentSlotId)
    val nextExercise: PlannedExercise? get() = plan.next(currentSlotId)
    val isWorking: Boolean get() = currentSegment == SegmentType.ACTIVE

    /** Seconds still to run on the lead-in, or null when not counting down. */
    val countdownSeconds: Int?
        get() = countdownUntilMs?.let {
            (((it - nowMs) + 999) / 1000).toInt().coerceAtLeast(0)
        }

    /** Total sets completed across the whole plan. */
    val completedSetCount: Int get() = completedSets.values.sum()

    /** Round in progress, for a circuit. Always 1 for an exercise-by-exercise split. */
    val currentRound: Int get() = plan.roundOf(completedSets)
}
