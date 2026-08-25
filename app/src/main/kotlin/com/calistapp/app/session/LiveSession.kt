package com.calistapp.app.session

import com.calistapp.core.model.EffortTarget
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.NextUp
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.PlannedSet
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
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
    /** When the block in progress began — what the rest countdown is measured from. */
    val segmentStartMs: Long,
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
    /** Recent heart-rate readings (oldest first, newest last) for the live HUD sparkline. Bounded. */
    val recentBpm: List<Int> = emptyList(),
    /** Sets banked so far this session — what the live journal shows and lets you annotate. */
    val setLogs: List<SetLog> = emptyList(),
    /** Effective max heart rate (measured, or estimated from age) — drives the HUD's zone read-out. */
    val maxHr: Int = 190,
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

    /** How long the current block has run. */
    val segmentElapsedMs: Long get() = (nowMs - segmentStartMs).coerceAtLeast(0)

    /**
     * Seconds left of the prescribed rest, negative once it's overrun, or null when resting isn't
     * what's happening or this movement's rest is untimed.
     *
     * Deliberately allowed to go negative rather than stopping at zero: knowing you're forty
     * seconds over is the information that gets you back to the bar.
     */
    val restRemainingSeconds: Int?
        get() {
            if (isWorking || countdownUntilMs != null) return null
            val target = currentExercise?.takeIf { it.isRestTimed }?.restSeconds ?: return null
            return target - (segmentElapsedMs / 1000).toInt()
        }

    /** Round in progress, for a circuit. Always 1 for an exercise-by-exercise split. */
    val currentRound: Int get() = plan.roundOf(completedSets)

    /**
     * How long the current rest has run, in seconds, counting up — or null while working or during
     * the lead-in. This is the number the screen shows: a stopwatch of the recovery, not a countdown
     * from a preset the user never asked for.
     */
    val restElapsedSeconds: Int?
        get() = if (isWorking || countdownUntilMs != null) null else (segmentElapsedMs / 1000).toInt()

    /** What the *next* work block will run, or null once every planned set is banked. */
    val upNext: NextUp? get() = plan.nextUp(currentSlotId, completedSets)

    /** The movement the next work block will run — the current one again, or a new exercise. */
    val upNextExercise: PlannedExercise? get() = plan.slot(upNext?.slotId)

    /**
     * The movement the hero video should show: what you're working now, or — while resting or during
     * the lead-in — the one you're about to do, so the demo is of what's coming rather than what's done.
     */
    val heroExercise: PlannedExercise? get() = if (isWorking) currentExercise else (upNextExercise ?: currentExercise)

    /** True when the next work block moves on to a different exercise rather than another set. */
    val nextIsNewExercise: Boolean
        get() = upNext?.let { it.slotId != currentSlotId } ?: false

    /**
     * Every planned set is done. The signal the workout has actually finished — without it the
     * "start next set" control looped back onto the last exercise and the session never ended.
     */
    val allSetsDone: Boolean get() = !plan.isEmpty && upNext == null

    /** Whether banking the set in progress would complete the plan — nothing left after it. */
    val bankingEndsWorkout: Boolean
        get() {
            if (!isWorking) return false
            val slot = currentSlotId ?: return false
            val after = completedSets + (slot to ((completedSets[slot] ?: 0) + 1))
            return plan.nextUp(slot, after) == null
        }

    /** Whether the set in progress (or about to start) is a warm-up set of the current exercise. */
    val isCurrentSetWarmup: Boolean get() = currentExercise?.isWarmup(setIndex) == true

    /** The current set's plan — per-set target when the plan carries one, else the uniform synthesis. */
    val currentSet: PlannedSet? get() = currentExercise?.sets()?.getOrNull(setIndex - 1)

    /** Added load for the set in progress, in kg — per-set when present, else the slot's nominal. */
    val currentSetWeightKg: Double
        get() = currentSet?.weightKg ?: currentExercise?.addedWeightKg ?: 0.0

    /** Target effort for the set in progress, or null — surfaced as the journal's pre-fill. */
    val currentEffortTarget: EffortTarget? get() = currentSet?.effort

    /**
     * The opening warm-up: the very first rest, before any set has been worked. Treated as a warm-up
     * window with a running stopwatch rather than a rest countdown you never asked to sit through.
     */
    val isOpeningWarmup: Boolean
        get() = !isWorking && countdownUntilMs == null && completedSetCount == 0 && currentExercise != null
}
