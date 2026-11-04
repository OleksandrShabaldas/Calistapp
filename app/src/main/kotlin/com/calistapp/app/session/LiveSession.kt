package com.calistapp.app.session

import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.EffortTarget
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HrLinkHealth
import com.calistapp.core.model.NextUp
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.PlannedSet
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SetSkip
import com.calistapp.core.model.SkipReason
import com.calistapp.core.model.SlotPhase
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
    /**
     * Sets *resolved* per slot — performed or skipped. This is what the next-up rule reads, which is
     * why a skipped set no longer leaves its exercise owed (and the workout looping back to it).
     */
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
    /** Sets skipped (or dropped with their exercise) so far — the journal shows them as such. */
    val skips: List<SetSkip> = emptyList(),
    /** Slots put off with "do it later": still owed, but after everything else in their round. */
    val deferred: Set<String> = emptySet(),
    /** How continuous the watch's heart-rate stream is, for the HR panel. */
    val hrLink: HrLinkHealth = HrLinkHealth(),
    /** Effective max heart rate (measured, or estimated from age) — drives the HUD's zone read-out. */
    val maxHr: Int = 190,
    /**
     * Effort rated for the set in progress, before it's banked — so effort is logged on the exercise
     * screen while the set is fresh, not tucked into the rest screen after. Copied onto the [SetLog]
     * when the set is banked, then cleared for the next set. Null until rated.
     */
    val pendingEffortScale: EffortScale? = null,
    val pendingEffortValue: Double? = null,
) {
    /** Whether the set in progress has had its effort rated yet. */
    val hasPendingEffort: Boolean get() = pendingEffortScale != null && pendingEffortValue != null

    /** "8 RPE" / "2 RIR" / null — the rated effort for the set in progress, for the live chip. */
    val pendingEffortLabel: String?
        get() {
            val scale = pendingEffortScale ?: return null
            val v = pendingEffortValue ?: return null
            val n = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
            return if (scale == EffortScale.PERCENT_RM) "$n %RM" else "$n ${scale.label}"
        }
    val elapsedMs: Long get() = nowMs - startMs
    val currentExercise: PlannedExercise? get() = plan.slot(currentSlotId)
    val isWorking: Boolean get() = currentSegment == SegmentType.ACTIVE

    /** Seconds still to run on the lead-in, or null when not counting down. */
    val countdownSeconds: Int?
        get() = countdownUntilMs?.let {
            (((it - nowMs) + 999) / 1000).toInt().coerceAtLeast(0)
        }

    /** Total sets resolved across the whole plan. */
    val completedSetCount: Int get() = completedSets.values.sum()

    /** How long the current block has run. */
    val segmentElapsedMs: Long get() = (nowMs - segmentStartMs).coerceAtLeast(0)

    /** Round in progress, for a circuit. Always 1 for an exercise-by-exercise split. */
    val currentRound: Int get() = plan.roundOf(completedSets)

    /**
     * How long the current rest has run, in seconds, counting up — or null while working or during
     * the lead-in. This is the number the screen shows: a stopwatch of the recovery, not a countdown
     * from a preset the user never asked for.
     */
    val restElapsedSeconds: Int?
        get() = if (isWorking || countdownUntilMs != null) null else (segmentElapsedMs / 1000).toInt()

    /**
     * What the *next* work block will run, or null once every planned set is resolved. While working
     * that's whatever follows this set; while resting (or warming up before set 1) it's the block the
     * controller will actually start — see [WorkoutPlan.upcoming].
     */
    val upNext: NextUp?
        get() {
            if (!isWorking) return plan.upcoming(currentSlotId, completedSets, deferred)
            // While working, count the set in progress as done — otherwise a warm-up move, or a split's
            // set 1 of 3, answered "what's next" with itself.
            val slot = currentSlotId ?: return plan.nextUp(null, completedSets, deferred)
            return plan.nextUp(slot, completedSets + (slot to ((completedSets[slot] ?: 0) + 1)), deferred - slot)
        }

    /** The movement the next work block will run — the current one again, or a new exercise. */
    val upNextExercise: PlannedExercise? get() = plan.slot(upNext?.slotId)

    /**
     * The set a "Skip" or "Do it later" acts on: the one being worked, or — while resting — the one
     * coming up. Skip used to act only on a set already started, which also banked its pre-filled reps.
     */
    val focus: NextUp?
        get() = if (isWorking) currentSlotId?.let { NextUp(it, setIndex) } else upNext

    val focusExercise: PlannedExercise? get() = plan.slot(focus?.slotId)

    /**
     * The movement the hero video should show: what you're working now, or — while resting or during
     * the lead-in — the one you're about to do, so the demo is of what's coming rather than what's done.
     */
    val heroExercise: PlannedExercise? get() = if (isWorking) currentExercise else (upNextExercise ?: currentExercise)

    /** True when the next work block moves on to a different exercise rather than another set. */
    val nextIsNewExercise: Boolean
        get() = upNext?.let { it.slotId != currentSlotId } ?: false

    /**
     * Every planned set is resolved. The signal the workout has actually finished — without it the
     * "start next set" control looped back onto the last exercise and the session never ended.
     */
    val allSetsDone: Boolean get() = !plan.isEmpty && upNext == null

    /** Whether banking the set in progress would complete the plan — nothing left after it. */
    val bankingEndsWorkout: Boolean
        get() {
            if (!isWorking) return false
            val slot = currentSlotId ?: return false
            val after = completedSets + (slot to ((completedSets[slot] ?: 0) + 1))
            return plan.nextUp(slot, after, deferred - slot) == null
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

    // ---- Phases: warm-up, workout, cool-down ------------------------------------------------------

    /** The phase of the block being worked — or, while resting, of the last one. */
    val currentPhase: SlotPhase get() = plan.phaseOf(currentSlotId)

    /** The phase the next work block belongs to, or null when nothing is left. */
    val upNextPhase: SlotPhase? get() = upNext?.let { plan.phaseOf(it.slotId) }

    /** Whether the block being worked is a warm-up or cool-down move rather than a working set. */
    val inRoutine: Boolean get() = isWorking && currentPhase != SlotPhase.MAIN

    /**
     * A rest that sits between two phases — the warm-up finished and the workout next, or the workout
     * finished and the cool-down next — plus the opening, when the session begins with a warm-up. The
     * live screen shows these as a hand-off card instead of an ordinary rest.
     */
    val phaseHandOff: PhaseHandOff?
        get() {
            if (isWorking || countdownUntilMs != null) return null
            val next = upNextPhase ?: return null
            val nothingYet = completedSetCount == 0
            return when {
                nothingYet && next == SlotPhase.WARM_UP -> PhaseHandOff(from = null, to = SlotPhase.WARM_UP)
                !nothingYet && next != currentPhase -> PhaseHandOff(from = currentPhase, to = next)
                else -> null
            }
        }

    /** "Cool-down · 2 / 4" — where the focus sits within its routine, for the top bar. */
    fun phasePosition(phase: SlotPhase): Pair<Int, Int> {
        val slots = plan.slotsIn(phase)
        val at = slots.indexOfFirst { it.slotId == (focus?.slotId ?: currentSlotId) }
        return (if (at < 0) slots.size else at + 1) to slots.size
    }

    /** The label over the progress dashes: round, routine position, or a finished workout. */
    val topLabel: String
        get() {
            val handOff = phaseHandOff
            val phase = when {
                handOff != null -> handOff.to
                isWorking -> currentPhase
                else -> upNextPhase ?: currentPhase
            }
            return when {
                allSetsDone -> "Workout complete"
                handOff?.from == SlotPhase.MAIN -> "Workout complete"
                phase != SlotPhase.MAIN -> phasePosition(phase).let { (i, n) -> "${phase.label} · $i / $n" }
                plan.isCircuit -> "Round $currentRound / ${plan.rounds}"
                else -> ""
            }
        }

    /** Skips recorded against [slotId]. */
    fun skipsFor(slotId: String): List<SetSkip> = skips.filter { it.slotId == slotId }

    /** Slots dropped from today with "Remove from today". */
    val removedSlots: Set<String>
        get() = skips.filter { it.reason == SkipReason.REMOVED }.mapTo(HashSet()) { it.slotId }
}

/** A rest between two phases of the session — see [LiveSession.phaseHandOff]. */
data class PhaseHandOff(val from: SlotPhase?, val to: SlotPhase)
