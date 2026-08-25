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
) {
    val isEmpty: Boolean get() = exercises.isEmpty()

    val isCircuit: Boolean get() = style == WorkoutStyle.CIRCUIT

    /**
     * Sets in the whole workout. In a circuit the round count drives this — each exercise is worked
     * once per round — whereas a split runs each exercise's own set count to completion.
     */
    val totalSets: Int
        get() = if (isCircuit) exercises.size * rounds.coerceAtLeast(1)
        else exercises.sumOf { it.sets().size }

    /** How many sets of [slotId] this plan calls for. */
    fun targetSetsFor(slotId: String?): Int {
        val slot = slot(slotId) ?: return 0
        // A circuit's set count is the round count (each slot is worked once per round); a split runs
        // the slot's own column, which is now its per-set list (uniform or not).
        return if (isCircuit) rounds.coerceAtLeast(1) else slot.sets().size
    }

    fun slot(slotId: String?): PlannedExercise? =
        slotId?.let { id -> exercises.firstOrNull { it.slotId == id } }

    /** The slot after [slotId] in plan order — wrapping in a circuit, since rounds cycle. */
    fun next(slotId: String?): PlannedExercise? {
        val i = exercises.indexOfFirst { it.slotId == slotId }
        if (i < 0) return exercises.firstOrNull()
        return when {
            i < exercises.lastIndex -> exercises[i + 1]
            isCircuit -> exercises.firstOrNull()
            else -> null
        }
    }

    /**
     * What to work next once the current set is banked, given how many sets of each slot are
     * already done. Returns null when the plan is finished.
     *
     * This lives on the model rather than in either session manager on purpose: the phone and the
     * watch both advance the current exercise independently, and if their rules disagreed by even
     * one set the two screens would show different exercises for the rest of the workout.
     */
    fun nextUp(currentSlotId: String?, completedSets: Map<String, Int>): NextUp? {
        if (exercises.isEmpty()) return null
        fun done(id: String) = completedSets[id] ?: 0
        fun remaining(e: PlannedExercise) = targetSetsFor(e.slotId) - done(e.slotId)

        if (isCircuit) {
            // Walk forward from the current position and take the first slot still owing a set, so
            // an exercise skipped mid-round is picked up on the next pass rather than lost.
            val start = exercises.indexOfFirst { it.slotId == currentSlotId }
            for (step in 1..exercises.size) {
                val e = exercises[(start + step).mod(exercises.size)]
                if (remaining(e) > 0) return NextUp(e.slotId, done(e.slotId) + 1)
            }
            return null
        }

        val current = slot(currentSlotId)

        // A superset is a circuit of two or three: rotate through the group, one set each, and only
        // move on once the whole group is done. Checked before the split rule, which would otherwise
        // finish all sets of the first movement and never rotate at all.
        val group = current?.groupId
        if (group != null) {
            val members = exercises.filter { it.groupId == group }
            val position = members.indexOfFirst { it.slotId == currentSlotId }
            for (step in 1..members.size) {
                val e = members[(position + step).mod(members.size)]
                if (remaining(e) > 0) return NextUp(e.slotId, done(e.slotId) + 1)
            }
            // Group exhausted — fall through and leave it behind.
        } else if (current != null && remaining(current) > 0) {
            // Split: finish the current exercise before moving on.
            return NextUp(current.slotId, done(current.slotId) + 1)
        }

        val start = exercises.indexOfFirst { it.slotId == currentSlotId }
        for (step in 1..exercises.size) {
            val e = exercises[(start + step).mod(exercises.size)]
            if (remaining(e) > 0) return NextUp(e.slotId, done(e.slotId) + 1)
        }
        return null
    }

    /** Round currently being worked, 1-based. Always 1 for a split. */
    fun roundOf(completedSets: Map<String, Int>): Int =
        if (!isCircuit) 1
        else (exercises.minOfOrNull { completedSets[it.slotId] ?: 0 } ?: 0) + 1

    companion object {
        val EMPTY = WorkoutPlan(id = "", name = "")
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

    /** "2 RIR", "8 RPE", "80 %RM", or null when unrated — for compact display in the journal. */
    val effortLabel: String?
        get() {
            val scale = effortScale ?: return null
            val v = effortValue ?: return null
            val n = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
            return if (scale == EffortScale.PERCENT_RM) "$n %RM" else "$n ${scale.label}"
        }
}
