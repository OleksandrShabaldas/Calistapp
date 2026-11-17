package com.calistapp.core.insights

import com.calistapp.core.model.Exercise

/**
 * Sorting a set into push / pull / core / legs, and naming the muscles it trained.
 *
 * The catalog (free-exercise-db plus authored entries) carries `primaryMuscles` and `force`, which is
 * enough for nearly everything. Custom exercises have neither, so the exercise name is the fallback:
 * crude, but a "Nordic curl" with no catalog entry should still count as legs.
 */
internal object MovementGroups {
    private val LEG_MUSCLES = setOf("quadriceps", "hamstrings", "calves", "glutes", "adductors", "abductors")
    private val CORE_MUSCLES = setOf("abdominals", "lower back", "obliques")
    private val PULL_MUSCLES = setOf("lats", "middle back", "biceps", "traps", "forearms")
    private val PUSH_MUSCLES = setOf("chest", "shoulders", "triceps")

    /** Muscles worth calling out when they go quiet, with the name shown on screen. */
    val TRACKED: Map<String, String> = linkedMapOf(
        "hamstrings" to "Hamstrings", "calves" to "Calves", "glutes" to "Glutes", "quadriceps" to "Quads",
        "chest" to "Chest", "lats" to "Lats", "shoulders" to "Shoulders", "triceps" to "Triceps",
        "biceps" to "Biceps", "abdominals" to "Abs",
    )

    fun groupOf(exercise: Exercise?, name: String): MovementGroup? {
        if (exercise != null) {
            val prim = exercise.primaryMuscles.map { it.lowercase() }
            when {
                prim.any { it in LEG_MUSCLES } -> return MovementGroup.LEGS
                prim.any { it in CORE_MUSCLES } -> return MovementGroup.CORE
            }
            when (exercise.force?.lowercase()) {
                "push" -> return MovementGroup.PUSH
                "pull" -> return MovementGroup.PULL
            }
            when {
                prim.any { it in PULL_MUSCLES } -> return MovementGroup.PULL
                prim.any { it in PUSH_MUSCLES } -> return MovementGroup.PUSH
            }
        }
        return groupOfName(name)
    }

    /** Primary and secondary muscles: what counts as "trained at all" when judging what went quiet. */
    fun trainedMusclesOf(exercise: Exercise?, name: String): List<String> =
        musclesOf(exercise, name) + exercise?.secondaryMuscles?.map { it.lowercase() }.orEmpty()

    fun musclesOf(exercise: Exercise?, name: String): List<String> {
        val prim = exercise?.primaryMuscles?.map { it.lowercase() }.orEmpty()
        if (prim.isNotEmpty()) return prim
        return when (groupOfName(name)) {
            MovementGroup.LEGS -> when {
                name.contains("calf", true) -> listOf("calves")
                name.contains("nordic", true) || name.contains("hamstring", true) -> listOf("hamstrings")
                name.contains("glute", true) || name.contains("bridge", true) -> listOf("glutes")
                else -> listOf("quadriceps", "glutes")
            }
            MovementGroup.PULL -> listOf("lats", "biceps")
            MovementGroup.PUSH -> if (name.contains("dip", true)) listOf("triceps", "chest") else listOf("chest", "triceps")
            MovementGroup.CORE -> listOf("abdominals")
            null -> emptyList()
        }
    }

    private fun groupOfName(raw: String): MovementGroup? {
        val n = raw.lowercase()
        return when {
            listOf("squat", "lunge", "pistol", "calf", "nordic", "step-up", "step up", "bridge", "deadlift", "hamstring", "glute", "jump").any { it in n } -> MovementGroup.LEGS
            listOf("plank", "l-sit", "l sit", "crunch", "leg raise", "hollow", "sit-up", "situp", "dragon flag", "ab ").any { it in n } -> MovementGroup.CORE
            listOf("pull", "chin", "row", "lever", "muscle-up", "muscle up", "curl").any { it in n } -> MovementGroup.PULL
            listOf("push", "dip", "press", "handstand", "planche", "pike").any { it in n } -> MovementGroup.PUSH
            else -> null
        }
    }
}
