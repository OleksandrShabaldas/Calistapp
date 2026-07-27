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
    val metabolics: ExerciseMetabolics = ExerciseMetabolics.DEFAULT,
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

    val targetLabel: String
        get() {
            val base = when (measure) {
                ExerciseMeasure.REPS -> "$targetSets × $targetReps"
                ExerciseMeasure.SECONDS -> "$targetSets × ${targetSeconds}s"
            }
            return if (isWeighted) "$base · +${formatKg(addedWeightKg)} kg" else base
        }
}

/** Trim the trailing ".0" so whole numbers read as "20 kg" rather than "20.0 kg". */
internal fun formatKg(kg: Double): String =
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
        else exercises.sumOf { it.targetSets }

    /** How many sets of [slotId] this plan calls for. */
    fun targetSetsFor(slotId: String?): Int {
        val slot = slot(slotId) ?: return 0
        return if (isCircuit) rounds.coerceAtLeast(1) else slot.targetSets
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

        // Split: finish the current exercise before moving on.
        val current = slot(currentSlotId)
        if (current != null && remaining(current) > 0) {
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
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
