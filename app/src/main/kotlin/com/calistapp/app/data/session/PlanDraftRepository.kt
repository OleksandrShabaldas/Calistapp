package com.calistapp.app.data.session

import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.WorkoutPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The workout currently being built.
 *
 * Held in memory rather than persisted: a draft only needs to survive navigation between the
 * planner, the gallery and the active screen. The plan itself *is* persisted once a session runs —
 * [com.calistapp.core.model.WorkoutSession.plan] carries it into history. Reusable saved workouts
 * are the natural next step and would slot in behind this same interface.
 */
@Singleton
class PlanDraftRepository @Inject constructor() {

    private val _draft = MutableStateFlow(WorkoutPlan(id = UUID.randomUUID().toString()))
    val draft: StateFlow<WorkoutPlan> = _draft.asStateFlow()

    /**
     * Append a gallery exercise. Its physical profile is derived once, here, and baked into the
     * slot — so the watch can score it without carrying the gallery, and the record stays readable
     * even if the gallery entry later changes.
     */
    fun add(exercise: Exercise) {
        val metabolics = ExerciseIntensity.deriveMetabolics(exercise)
        val slot = PlannedExercise(
            slotId = UUID.randomUUID().toString(),
            exerciseId = exercise.id,
            name = exercise.name,
            bodyPart = exercise.bodyPart,
            measure = if (metabolics.isometric) ExerciseMeasure.SECONDS else ExerciseMeasure.REPS,
            metabolics = metabolics,
        )
        _draft.update { it.copy(exercises = it.exercises + slot) }
    }

    fun remove(slotId: String) =
        _draft.update { it.copy(exercises = it.exercises.filterNot { e -> e.slotId == slotId }) }

    fun update(slotId: String, transform: (PlannedExercise) -> PlannedExercise) =
        _draft.update { plan ->
            plan.copy(exercises = plan.exercises.map { if (it.slotId == slotId) transform(it) else it })
        }

    /** Change the plan itself rather than one of its slots — name, style, round count. */
    fun updatePlan(transform: (WorkoutPlan) -> WorkoutPlan) = _draft.update(transform)

    fun move(slotId: String, delta: Int) = _draft.update { plan ->
        val list = plan.exercises.toMutableList()
        val from = list.indexOfFirst { it.slotId == slotId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update plan
        list.add(to, list.removeAt(from))
        plan.copy(exercises = list)
    }

    fun rename(name: String) = _draft.update { it.copy(name = name) }

    /** Start a fresh draft — called once a workout has been handed to the session controller. */
    fun clear() {
        _draft.value = WorkoutPlan(id = UUID.randomUUID().toString())
    }
}
