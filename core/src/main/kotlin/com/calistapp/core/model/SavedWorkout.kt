package com.calistapp.core.model

/** A named [WorkoutPlan] kept for reuse, with enough history to sort sensibly. */
data class SavedWorkout(
    val id: String,
    val name: String,
    val plan: WorkoutPlan,
    val createdMs: Long,
    /** Null until it's been run. */
    val lastUsedMs: Long?,
) {
    /** "5 exercises · 15 sets" — enough to recognise it without opening it. */
    val summaryLabel: String
        get() = buildString {
            append("${plan.exercises.size} ${if (plan.exercises.size == 1) "exercise" else "exercises"}")
            append(" · ${plan.totalSets} sets")
            if (plan.isCircuit) append(" · ${plan.rounds} rounds")
        }
}
