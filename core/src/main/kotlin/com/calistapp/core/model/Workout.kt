package com.calistapp.core.model

import kotlinx.serialization.Serializable

/** How a set is counted — reps for dynamic movements, seconds for static holds. */
@Serializable
enum class ExerciseMeasure { REPS, SECONDS }

/**
 * The physical characteristics that let [com.calistapp.core.calorie.CalorieEngine] reason about an
 * exercise beyond what heart rate reveals.
 *
 * These are *baked into the plan* rather than looked up by id, for two reasons: the watch can then
 * compute without carrying the 800-entry exercise gallery, and a session stays interpretable years
 * later even if the gallery entry is edited or removed.
 *
 * Defaults describe a middling compound bodyweight movement, so an un-derived exercise degrades
 * gracefully instead of skewing the estimate.
 */
@Serializable
data class ExerciseMetabolics(
    /** Share of total skeletal muscle mass recruited, 0..1. Derived from primary/secondary muscles. */
    val muscleMassFraction: Double = 0.25,
    /** Share of bodyweight the movement actually moves (pull-up ≈ 0.95, push-up ≈ 0.64). */
    val loadFraction: Double = 0.50,
    /** Load beyond bodyweight in kg — weight vest, dumbbells, barbell. */
    val externalLoadKg: Double = 0.0,
    /** Vertical travel of the load per rep, in metres. */
    val romMetres: Double = 0.40,
    /** Static hold (plank, L-sit). Heart rate systematically under-represents these. */
    val isometric: Boolean = false,
    /** Multi-joint movement, as opposed to single-joint isolation. */
    val compound: Boolean = true,
) {
    companion object {
        val DEFAULT = ExerciseMetabolics()
    }
}

/**
 * One planned set, when a slot's sets aren't uniform.
 *
 * The old model gave a slot a single target (`targetSets × targetReps`, one added weight, N warm-ups)
 * — fine for straight sets, but it can't express a pyramid, a top-set-plus-back-offs, or a warm-up
 * ramp with rising load. [PlannedSet] carries per-set intent instead; a slot with a non-empty
 * [PlannedExercise.plannedSets] is driven set-by-set, and one without falls back to the uniform
 * fields (see [PlannedExercise.sets]). For a timed hold, [reps] carries the seconds, matching how the
 * rest of the model overloads "reps".
 */
@Serializable
data class PlannedSet(
    val reps: Int = 10,
    val weightKg: Double = 0.0,
    /** Target effort for this set, surfaced as a journal pre-fill. Null = no target set. */
    val effort: EffortTarget? = null,
    val note: String = "",
    val isWarmup: Boolean = false,
) {
    val isWeighted: Boolean get() = weightKg > 0.0
}

/**
 * A target effort for a planned set — the same three scales a completed set is rated on, so a plan
 * can say "top set at 8 RPE" and the journal opens pre-filled to it. Like logged effort, this never
 * enters the calorie estimate; it's intent, for history and the AI layer.
 */
@Serializable
data class EffortTarget(val scale: EffortScale, val value: Double) {
    /** "8 RPE", "2 RIR", "80 %RM". */
    val label: String
        get() {
            val n = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            return if (scale == EffortScale.PERCENT_RM) "$n %RM" else "$n ${scale.label}"
        }
}

/**
 * One exercise slotted into a workout plan.
 *
 * [slotId] is distinct from [exerciseId] because the same movement legitimately appears more than
 * once in a plan (e.g. push-ups at the start and again as a finisher), and each occurrence needs
 * its own sets, reps, and logged history.
 */
@Serializable
data class PlannedExercise(
    val slotId: String,
    val exerciseId: String,
    val name: String,
    val bodyPart: BodyPart = BodyPart.OTHER,
    val targetSets: Int = 3,
    /** Target reps per set when [measure] is REPS. */
    val targetReps: Int = 10,
    /** Target hold in seconds when [measure] is SECONDS. */
    val targetSeconds: Int = 45,
    val measure: ExerciseMeasure = ExerciseMeasure.REPS,
    /**
     * How long to rest after a set of this movement, in seconds.
     *
     * Per-exercise rather than per-workout because the right rest is a property of the movement and
     * the intent: heavy pull-ups want three minutes, a core finisher wants thirty seconds. Zero
     * means untimed — rest as long as you like, nothing will prompt you.
     */
    val restSeconds: Int = 90,
    /**
     * How many of this exercise's sets are warm-ups.
     *
     * The first [warmupSets] sets are treated as preparation: they cost energy like anything else —
     * heart rate is heart rate — but they don't count toward volume or personal bests, because a
     * record set with an empty bar isn't one.
     */
    val warmupSets: Int = 0,
    /**
     * Exercises sharing a group id are a superset: you rotate through the group, one set each,
     * before resting properly. Null means the movement stands alone.
     */
    val groupId: String? = null,
    val metabolics: ExerciseMetabolics = ExerciseMetabolics.DEFAULT,
    /**
     * Per-set targets, when this slot's sets aren't uniform. Empty means "uniform" — the slot is
     * driven by [targetSets]/[targetReps]/[targetSeconds]/[warmupSets]/[addedWeightKg] via [sets].
     * Additive with a default, so every plan and stored session written before per-set existed keeps
     * deserializing and behaving exactly as before.
     */
    val plannedSets: List<PlannedSet> = emptyList(),
    /**
     * A block that runs exactly once regardless of workout style — the warm-up and stretch routines
     * the setup screen threads onto the start and end of a plan. In a circuit every other slot is
     * worked once per round; these sit outside the rotation (leading ones before round 1, trailing
     * ones after the last round). Additive with a default, so stored plans read unchanged.
     */
    val runsOnce: Boolean = false,
    /**
     * Which part of the session this slot belongs to. The warm-up and cool-down routines are tagged so
     * every surface — journal, summary, totals, the coach — can treat them as their own phase instead
     * of as more exercises. Stored plans predate the tag; [WorkoutPlan.phaseOf] infers theirs.
     */
    val phase: SlotPhase = SlotPhase.MAIN,
    /** The routine a warm-up or cool-down slot came from ("Upper-body cooldown"), for phase headers. */
    val routineName: String? = null,
) {
    /**
     * Extra load carried beyond bodyweight — vest, belt, dumbbells. Any movement can be weighted,
     * which is why this is a property of the *slot* rather than a separate gallery entry: a
     * weighted pull-up is the same movement with more mass, not a different exercise.
     */
    val addedWeightKg: Double get() = metabolics.externalLoadKg

    val isWeighted: Boolean get() = addedWeightKg > 0.0

    /** "Pull-Up" → "Pull-Up +20 kg" when loaded, for display and for the session record. */
    val displayName: String
        get() = if (isWeighted) "$name +${formatKg(addedWeightKg)} kg" else name

    /**
     * The set-by-set plan. Returns [plannedSets] when the slot carries them, otherwise synthesizes a
     * uniform column from the legacy fields — the single place the two models meet, so everything
     * else (targets, warm-ups, the live counter, the watch) reads one shape and stays backward
     * compatible.
     */
    fun sets(): List<PlannedSet> {
        if (plannedSets.isNotEmpty()) return plannedSets
        val value = if (measure == ExerciseMeasure.SECONDS) targetSeconds else targetReps
        return List(targetSets.coerceAtLeast(0)) { i ->
            PlannedSet(reps = value, weightKg = addedWeightKg, isWarmup = i < warmupSets)
        }
    }

    val targetLabel: String
        get() {
            val s = sets()
            if (s.isEmpty()) return ""
            val unit = if (measure == ExerciseMeasure.SECONDS) "s" else ""
            val sameReps = s.all { it.reps == s.first().reps }
            val sameWeight = s.all { it.weightKg == s.first().weightKg }
            return when {
                sameReps -> {
                    val base = "${s.size} × ${s.first().reps}$unit"
                    val kg = s.first().weightKg
                    if (kg > 0 && sameWeight) "$base · +${formatKg(kg)} kg" else base
                }
                // A varying column reads best as the actual figures — "12/10/8".
                s.size <= 6 -> s.joinToString("/") { "${it.reps}" } + unit
                else -> "${s.size} sets"
            }
        }

    val isRestTimed: Boolean get() = restSeconds > 0

    /** Whether the [setIndex]-th set (1-based) of this exercise is a warm-up. */
    fun isWarmup(setIndex: Int): Boolean =
        sets().getOrNull(setIndex - 1)?.isWarmup ?: (setIndex <= warmupSets)

    /** "1:30" — the rest target, or null when this movement's rest is untimed. */
    val restLabel: String?
        get() = if (!isRestTimed) null else "${restSeconds / 60}:${(restSeconds % 60).toString().padStart(2, '0')}"
}

/** Trim the trailing ".0" so whole numbers read as "20 kg" rather than "20.0 kg". */
fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else ((kg * 10).toInt() / 10.0).toString()

/**
 * How the exercises are worked through.
 *
 * Both orders are common and they produce genuinely different sessions, so the plan has to know
 * which one is intended — the phone and the watch both advance the current exercise from it, and
 * they'd diverge immediately if they disagreed.
 */
@Serializable
enum class WorkoutStyle {
    /** All sets of one exercise, then all sets of the next. The classic strength split. */
    BY_EXERCISE,

    /** One set of every exercise, then round again. Circuit / full-body training. */
    CIRCUIT,
    ;

    val displayName: String
        get() = when (this) {
            BY_EXERCISE -> "Exercise by exercise"
            CIRCUIT -> "Circuit (rounds)"
        }
}

/** The part of a session a slot belongs to: the warm-up routine, the workout itself, or the cool-down. */
@Serializable
enum class SlotPhase(val label: String) {
    WARM_UP("Warm-up"),
    MAIN("Workout"),
    COOL_DOWN("Cool-down"),
}

/** The exercise to work next, and which set of it that is. */
data class NextUp(val slotId: String, val setIndex: Int)

/**
 * A workout built up-front: the ordered list of exercises the user intends to do. A session can run
 * without one (free workout), in which case active blocks simply carry no exercise context and the
 * engine falls back to pure heart-rate estimation.
 */
@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String = "",
    val exercises: List<PlannedExercise> = emptyList(),
    val style: WorkoutStyle = WorkoutStyle.BY_EXERCISE,
    /** Times the whole list is repeated in [WorkoutStyle.CIRCUIT]. Ignored otherwise. */
    val rounds: Int = 1,
    /**
     * The saved workout a session's plan was started from, or null for a scratch plan. A session edits
     * its own copy of the plan; this is what lets it offer to write those edits back at the end.
     */
    val sourceWorkoutId: String? = null,
) {
    val isEmpty: Boolean get() = exercises.isEmpty()

    /**
     * The phase [slot] belongs to. Plans written before slots carried a phase still had run-once routine
     * blocks: one ahead of the first working slot was the warm-up, one after it the stretch.
     */
    fun phaseOf(slot: PlannedExercise): SlotPhase {
        if (slot.phase != SlotPhase.MAIN || !slot.runsOnce) return slot.phase
        val firstMain = exercises.indexOfFirst { !it.runsOnce }
        val at = exercises.indexOfFirst { it.slotId == slot.slotId }
        return if (firstMain < 0 || at < firstMain) SlotPhase.WARM_UP else SlotPhase.COOL_DOWN
    }

    fun phaseOf(slotId: String?): SlotPhase = slot(slotId)?.let(::phaseOf) ?: SlotPhase.MAIN

    /** The slots of one phase, in plan order. */
    fun slotsIn(phase: SlotPhase): List<PlannedExercise> = exercises.filter { phaseOf(it) == phase }

    /**
     * Whether a slot's logged count is a rep count. A timed hold logs seconds in the same field, and the
     * warm-up and cool-down are routines, not training — neither belongs in a reps total or a volume.
     */
    fun countsReps(slotId: String?): Boolean {
        val slot = slot(slotId) ?: return true
        return slot.measure == ExerciseMeasure.REPS && phaseOf(slot) == SlotPhase.MAIN
    }

    val isCircuit: Boolean get() = style == WorkoutStyle.CIRCUIT

    /**
     * Sets in the whole workout. In a circuit the round count drives this — each exercise is worked
     * once per round — whereas a split runs each exercise's own set count to completion.
     */
    val totalSets: Int
        get() = if (isCircuit) {
            rotation.size * rounds.coerceAtLeast(1) + exercises.filter { it.runsOnce }.sumOf { it.sets().size }
        } else {
            exercises.sumOf { it.sets().size }
        }

    /** The slots a circuit cycles through each round — everything but the [PlannedExercise.runsOnce] blocks. */
    private val rotation: List<PlannedExercise> get() = exercises.filter { !it.runsOnce }

    /**
     * A rough time estimate for the whole workout, in seconds — a set's working time plus its rest,
     * summed across the plan. Deliberately coarse (reps are scored at a nominal tempo, holds at their
     * own duration, and the trailing rest is left in as buffer): it exists only to show a "~N min"
     * badge on the workout, never to drive scoring, which stays on real elapsed time and heart rate.
     */
    val estimatedSeconds: Int
        get() {
            if (exercises.isEmpty()) return 0
            fun work(slot: PlannedExercise, set: PlannedSet): Int =
                if (slot.measure == ExerciseMeasure.SECONDS) set.reps else set.reps * SECONDS_PER_REP
            return if (isCircuit) {
                rounds.coerceAtLeast(1) * rotation.sumOf { slot ->
                    val set = slot.sets().firstOrNull() ?: PlannedSet()
                    work(slot, set) + slot.restSeconds
                } + exercises.filter { it.runsOnce }.sumOf { slot ->
                    slot.sets().sumOf { set -> work(slot, set) + slot.restSeconds }
                }
            } else {
                exercises.sumOf { slot -> slot.sets().sumOf { set -> work(slot, set) + slot.restSeconds } }
            }
        }

    /** The estimate as whole minutes, at least 1 for any non-empty plan — for the "~38 min" line. */
    val estimatedMinutes: Int
        get() = if (isEmpty) 0 else ((estimatedSeconds + 59) / 60).coerceAtLeast(1)

    /** How many sets of [slotId] this plan calls for. */
    fun targetSetsFor(slotId: String?): Int {
        val slot = slot(slotId) ?: return 0
        // A circuit's set count is the round count (each slot is worked once per round); a split runs
        // the slot's own column, which is now its per-set list (uniform or not).
        return if (isCircuit && !slot.runsOnce) rounds.coerceAtLeast(1) else slot.sets().size
    }

    /**
     * The slot's target as this plan will actually run it — what every screen should print. For a
     * split that's the slot's own [PlannedExercise.targetLabel]. For a circuit it is NOT: a circuit
     * slot keeps its (ignored) default of 3 sets, while it's really worked once per round, so a
     * 1-round circuit read "3 × 7" everywhere. Here the set count is the round count.
     */
    fun targetLabelFor(slot: PlannedExercise): String {
        if (!isCircuit || slot.runsOnce) return slot.targetLabel
        val sets = slot.sets()
        if (sets.isEmpty()) return slot.targetLabel
        val perRound = List(rounds.coerceAtLeast(1)) { sets.getOrElse(it) { sets.last() } }
        return slot.copy(plannedSets = perRound).targetLabel
    }

    fun slot(slotId: String?): PlannedExercise? =
        slotId?.let { id -> exercises.firstOrNull { it.slotId == id } }

    /** The slot after [slotId] in plan order — wrapping in a circuit, since rounds cycle. */
    fun next(slotId: String?): PlannedExercise? {
        val i = exercises.indexOfFirst { it.slotId == slotId }
        if (i < 0) return exercises.firstOrNull()
        return when {
            i < exercises.lastIndex -> exercises[i + 1]
            isCircuit -> rotation.firstOrNull()
            else -> null
        }
    }

    /**
     * What the next work block will run, seen from a rest (or the opening warm-up) at [currentSlotId].
     *
     * [nextUp] answers "what follows the set just banked", so it always steps *past* the current slot
     * in a circuit or superset. Before the first set there is no banked set to step past: the opening
     * block works the selected slot itself. Asking [nextUp] there skipped exercise 1 — the live screen
     * announced exercise 2 as "up next" while the controller (correctly) started exercise 1. This is
     * the one rule both devices' controllers and the live screen ask, so they can't drift again.
     */
    fun upcoming(
        currentSlotId: String?,
        completedSets: Map<String, Int>,
        deferred: Set<String> = emptySet(),
    ): NextUp? {
        val current = slot(currentSlotId)
        if (current != null && completedSets.values.sum() == 0 && current.slotId !in deferred) return NextUp(current.slotId, 1)
        return nextUp(currentSlotId, completedSets, deferred)
    }

    /**
     * What to work next once the current set is banked, given how many sets of each slot are already
     * resolved. Returns null when the plan is finished. It lives on the model rather than in either
     * session manager on purpose: the phone and the watch both ask it, and if their rules disagreed by
     * even one set the two screens would show different exercises for the rest of the workout.
     *
     * [completedSets] counts every *resolved* set — performed or skipped — so a skipped set is never
     * owed again (a skip used to leave the slot owing, and the circuit looped back to it after the last
     * exercise instead of finishing). [deferred] holds the slots put off with "do it later": they're
     * still owed, but everything else in the same round (or, in a split, the rest of the workout) comes
     * first.
     */
    fun nextUp(
        currentSlotId: String?,
        completedSets: Map<String, Int>,
        deferred: Set<String> = emptySet(),
    ): NextUp? {
        if (exercises.isEmpty()) return null
        fun done(id: String) = completedSets[id] ?: 0
        fun remaining(e: PlannedExercise) = targetSetsFor(e.slotId) - done(e.slotId)
        fun next(e: PlannedExercise) = NextUp(e.slotId, done(e.slotId) + 1)

        /** First slot owing a set walking forward from [currentSlotId] (wrapping), deferred ones last. */
        fun walk(list: List<PlannedExercise>, accept: (PlannedExercise) -> Boolean = { true }): NextUp? {
            if (list.isEmpty()) return null
            val start = list.indexOfFirst { it.slotId == currentSlotId }
            val order = (1..list.size).map { list[(start + it).mod(list.size)] }.filter { remaining(it) > 0 && accept(it) }
            return (order.firstOrNull { it.slotId !in deferred } ?: order.firstOrNull())?.let(::next)
        }

        if (isCircuit) {
            // Run-once blocks bracket the rounds: any leading warm-up first, in order, and any
            // trailing stretch only once every round is done — never folded into the rotation.
            val lead = exercises.takeWhile { it.runsOnce }
            val trail = exercises.drop(lead.size).filter { it.runsOnce }
            walkInOrder(lead, ::remaining, deferred)?.let { return next(it) }
            // The round in progress is the lowest one any rotation slot still owes. Finish it — a set
            // put off with "do it later" included — before any slot starts the next round.
            val owing = rotation.filter { remaining(it) > 0 }
            if (owing.isNotEmpty()) {
                val lagging = owing.minOf { done(it.slotId) }
                val inRound = rotation.filter { remaining(it) > 0 && done(it.slotId) == lagging }
                val at = rotation.indexOfFirst { it.slotId == currentSlotId }
                // Mid-round (the current slot is already a round ahead of the laggards) carry on from it;
                // otherwise a new round is starting — from a warm-up, or right after a deferred set —
                // and it starts at the top, not wherever the last set happened to be.
                val ordered = if (at >= 0 && done(rotation[at].slotId) > lagging) {
                    inRound.filter { rotation.indexOf(it) > at } + inRound.filter { rotation.indexOf(it) <= at }
                } else {
                    inRound
                }
                (ordered.firstOrNull { it.slotId !in deferred } ?: ordered.first()).let { return next(it) }
            }
            walkInOrder(trail, ::remaining, deferred)?.let { return next(it) }
            return null
        }

        val current = slot(currentSlotId)

        // A superset is a circuit of two or three: rotate through the group, one set each, and only
        // move on once the whole group is done. Checked before the split rule, which would otherwise
        // finish all sets of the first movement and never rotate at all.
        val group = current?.groupId
        if (group != null) {
            walk(exercises.filter { it.groupId == group })?.let { return it }
            // Group exhausted — fall through and leave it behind.
        } else if (current != null && remaining(current) > 0 && current.slotId !in deferred) {
            // Split: finish the current exercise before moving on.
            return next(current)
        }

        // Phase by phase: the warm-up in order, then the workout walking on from where you are, then
        // the cool-down only once nothing else is owed — so a deferred exercise still precedes the
        // stretch, and a deferred warm-up move still precedes the first working set.
        walkInOrder(slotsIn(SlotPhase.WARM_UP), ::remaining, deferred)?.let { return next(it) }
        walk(slotsIn(SlotPhase.MAIN))?.let { return it }
        return walkInOrder(slotsIn(SlotPhase.COOL_DOWN), ::remaining, deferred)?.let(::next)
    }

    /** First slot of [list] in order still owing a set, putting [deferred] ones after the others. */
    private fun walkInOrder(
        list: List<PlannedExercise>,
        remaining: (PlannedExercise) -> Int,
        deferred: Set<String>,
    ): PlannedExercise? {
        val owing = list.filter { remaining(it) > 0 }
        return owing.firstOrNull { it.slotId !in deferred } ?: owing.firstOrNull()
    }

    /** Round currently being worked, 1-based. Always 1 for a split. */
    fun roundOf(completedSets: Map<String, Int>): Int =
        if (!isCircuit) 1
        else ((rotation.minOfOrNull { completedSets[it.slotId] ?: 0 } ?: 0) + 1).coerceAtMost(rounds.coerceAtLeast(1))

    companion object {
        val EMPTY = WorkoutPlan(id = "", name = "")

        /** Nominal seconds per rep, used only by [estimatedSeconds] — not by any scoring path. */
        private const val SECONDS_PER_REP = 3
    }
}

/**
 * How a set's effort was rated. All three are standard training scales; the user picks whichever
 * they think in. Effort is recorded for history and the AI layer only — it deliberately does **not**
 * enter the calorie estimate, which stays anchored on heart rate and mechanical work.
 */
@Serializable
enum class EffortScale(val label: String, val blurb: String) {
    /** Reps in reserve: how many more you had left. 0 = to failure, 3–4 = comfortable. */
    RIR("RIR", "Reps in reserve — how many more reps you could have done. 0 means you went to failure; 3–4 means it felt comfortable."),

    /** Rate of perceived exertion, Borg CR10-style, 1–10. */
    RPE("RPE", "Rate of perceived exertion, 1–10. 10 is an all-out set with nothing left; 7–8 is a hard working set with a rep or two in the tank."),

    /** Percent of one-rep max — the load relative to your best single. */
    PERCENT_RM("%RM", "Percent of your one-rep max — the load as a share of the most you can lift once. 80% is heavy; 50% is light/technique work.");
}

/**
 * A completed set — the record of what was actually performed, as opposed to what was planned.
 * Reps are the one signal heart rate cannot see, so these feed the mechanical-work term of the
 * calorie estimate as well as the history/AI views.
 */
@Serializable
data class SetLog(
    val slotId: String,
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val reps: Int = 0,
    val seconds: Int = 0,
    val startMs: Long,
    val endMs: Long,
    /** Load carried on this set, in kg beyond bodyweight. Copied from the plan slot at bank time. */
    val weightKg: Double = 0.0,
    /** Which effort scale [effortValue] is on, or null when effort wasn't rated for this set. */
    val effortScale: EffortScale? = null,
    /** The effort rating, meaning [effortScale]. Null when unrated. */
    val effortValue: Double? = null,
    /** A free-text note the user added to this set. */
    val note: String = "",
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)

    /** "40s" for a timed hold, "12" for reps — the logged figure in its own unit. */
    fun resultLabel(measure: ExerciseMeasure): String = if (measure == ExerciseMeasure.SECONDS) "${reps}s" else "$reps"

    /** "2 RIR", "8 RPE", "80 %RM", or null when unrated — for compact display in the journal. */
    val effortLabel: String?
        get() {
            val scale = effortScale ?: return null
            val v = effortValue ?: return null
            val n = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
            return if (scale == EffortScale.PERCENT_RM) "$n %RM" else "$n ${scale.label}"
        }
}

/** Why a planned set was resolved without being performed. */
@Serializable
enum class SkipReason {
    /** "Skip this set" — this one set, this round. */
    SKIPPED,

    /** "Remove from today" — every set the exercise still owed in this session. */
    REMOVED,
}

/**
 * A planned set the athlete chose not to do. Recorded rather than inferred: the session's next-up
 * rule counts it as resolved (so the workout moves on and ends), while the journal, the summary and the
 * coach can still tell a skipped set from a performed one. It carries no reps and no time — whatever
 * heart rate did meanwhile is scored as recovery, not as a set.
 */
@Serializable
data class SetSkip(
    val slotId: String,
    val setIndex: Int,
    val atMs: Long,
    val reason: SkipReason = SkipReason.SKIPPED,
)

/** Sets resolved per slot — performed (from the banked count) plus skipped — the next-up rule's input. */
fun resolvedSets(performed: Map<String, Int>, skips: List<SetSkip>): Map<String, Int> {
    val out = performed.toMutableMap()
    skips.forEach { out[it.slotId] = (out[it.slotId] ?: 0) + 1 }
    return out
}

/** Reps across [logs], counting only rep-based workout sets — never hold seconds or routine moves. */
fun WorkoutPlan.repsIn(logs: List<SetLog>): Int = logs.filter { countsReps(it.slotId) }.sumOf { it.reps }
