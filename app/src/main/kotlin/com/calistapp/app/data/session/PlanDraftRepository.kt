package com.calistapp.app.data.session

import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.calorie.ExerciseIntensity
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.WorkoutPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The workout currently being built.
 *
 * Held in memory rather than persisted: a draft only needs to survive navigation between the
 * planner, the gallery and the active screen. The plan itself *is* persisted once a session runs —
 * [com.calistapp.core.model.WorkoutSession.plan] carries it into history.
 *
 * When the draft was opened from a saved workout (see [originId]), edits are **streamed back** to
 * that workout automatically — editing a programme you tapped in the list shouldn't feel like it
 * forks a new copy and then nag you to "save" it again.
 */
@Singleton
class PlanDraftRepository @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val savedWorkouts: SavedWorkoutRepository,
) {

    private val _draft = MutableStateFlow(WorkoutPlan(id = UUID.randomUUID().toString()))
    val draft: StateFlow<WorkoutPlan> = _draft.asStateFlow()

    /** The saved workout this draft is backed by, or null for a scratch draft. */
    private val _originId = MutableStateFlow<String?>(null)
    val originId: StateFlow<String?> = _originId.asStateFlow()

    @OptIn(FlowPreview::class)
    private val autoSync = scope.launch {
        // Persist edits back to the backing workout, debounced so a run of stepper taps is one write.
        combine(_draft, _originId) { plan, origin -> origin?.takeIf { !plan.isEmpty }?.let { it to plan } }
            .distinctUntilChanged()
            .debounce(500)
            .collect { pair -> pair?.let { (origin, plan) -> savedWorkouts.syncPlan(origin, plan) } }
    }

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

    /** Reorder by absolute position — what the drag-to-reorder handle drives. */
    fun moveIndex(from: Int, to: Int) = _draft.update { plan ->
        val list = plan.exercises.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return@update plan
        list.add(to, list.removeAt(from))
        plan.copy(exercises = list)
    }

    fun rename(name: String) = _draft.update { it.copy(name = name) }

    /**
     * Replace the draft wholesale — loading a saved workout. Pass [originSavedId] when the draft is
     * backed by a stored workout, so subsequent edits sync straight back to it. The plan itself gets a
     * fresh id (it's a working copy); the origin is what ties it to the saved row.
     */
    fun replaceWith(plan: WorkoutPlan, originSavedId: String? = null) {
        _originId.value = originSavedId
        _draft.value = plan.copy(id = UUID.randomUUID().toString())
    }

    /** Mark the current draft as backed by saved workout [id] — set right after a first Save. */
    fun markSavedAs(id: String) {
        _originId.value = id
    }

    /** Start a fresh, unbacked draft — called once a workout has been handed to the session controller. */
    fun clear() {
        _originId.value = null
        _draft.value = WorkoutPlan(id = UUID.randomUUID().toString())
    }
}
