package com.calistapp.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.SessionController
import com.calistapp.app.ui.exercises.ExerciseFilterActions
import com.calistapp.app.ui.exercises.ExerciseFilters
import com.calistapp.app.ui.exercises.ExerciseSort
import com.calistapp.app.ui.exercises.FilterFacets
import com.calistapp.app.ui.exercises.applyQuery
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WorkoutPlannerViewModel @Inject constructor(
    private val drafts: PlanDraftRepository,
    private val controller: SessionController,
    private val savedWorkouts0: SavedWorkoutRepository,
    private val watchConnection: WatchConnectionMonitor,
    private val prefs: ExercisePrefsRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel(), ExerciseFilterActions {

    val plan: StateFlow<WorkoutPlan> = drafts.draft

    /** The planner starts sessions now, so it has to be able to warn about a silent watch. */
    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    fun reconnectWatch() = watchConnection.reconnect()

    private val _filters = MutableStateFlow(ExerciseFilters())
    val filters: StateFlow<ExerciseFilters> = _filters.asStateFlow()

    private val all: StateFlow<List<Exercise>> = exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val facets: StateFlow<FilterFacets> = all
        .map(FilterFacets::of)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterFacets())

    /**
     * Artwork per exercise id, for the plan's thumbnails.
     *
     * Looked up here rather than copied into [PlannedExercise], because the plan is serialised and
     * sent to the watch on every start — carrying a dozen image URLs it will never render would
     * bloat the payload for nothing.
     */
    val thumbnails: StateFlow<Map<String, List<String>>> = all
        .map { list -> list.associate { it.id to it.imageUrls } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The picker's results — the same relevance search, filters and sorting the gallery uses.
     * Uncapped, because a result limit silently hid matches that a filter combination had already
     * narrowed to a handful.
     */
    val favourites: StateFlow<Set<String>> = prefs.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavourite(exerciseId: String) {
        viewModelScope.launch { prefs.toggleFavourite(exerciseId) }
    }

    val searchResults: StateFlow<List<Exercise>> =
        combine(all, _filters, prefs.favourites) { list, f, starred ->
            list.applyQuery(f).sortedByDescending { it.id in starred }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Search and filters ----------------------------------------------------------------------

    fun search(query: String) = _filters.update { it.copy(query = query) }

    override fun setSort(sort: ExerciseSort) = _filters.update { it.copy(sort = sort) }

    override fun toggleBodyPart(bodyPart: BodyPart) =
        _filters.update { with(it) { copy(bodyParts = bodyParts.toggled(bodyPart)) } }

    override fun toggleDifficulty(difficulty: Difficulty) =
        _filters.update { with(it) { copy(difficulties = difficulties.toggled(difficulty)) } }

    override fun togglePrimaryMuscle(muscle: String) =
        _filters.update { with(it) { copy(primaryMuscles = primaryMuscles.toggled(muscle)) } }

    override fun toggleSecondaryMuscle(muscle: String) =
        _filters.update { with(it) { copy(secondaryMuscles = secondaryMuscles.toggled(muscle)) } }

    override fun toggleEquipment(item: String) =
        _filters.update { with(it) { copy(equipment = equipment.toggled(item)) } }

    override fun toggleAvoidArea(area: String) =
        _filters.update { with(it) { copy(avoidAreas = avoidAreas.toggled(area)) } }

    override fun toggleCalisthenics() =
        _filters.update { it.copy(calisthenicsOnly = !it.calisthenicsOnly) }

    override fun clearFilters() = _filters.update { ExerciseFilters(query = it.query, sort = it.sort) }

    // ---- Plan structure ---------------------------------------------------------------------------

    /** Workouts kept for reuse, most recently used first. */
    val savedWorkouts: StateFlow<List<SavedWorkout>> = savedWorkouts0.saved
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveCurrentWorkout(name: String) {
        val plan = drafts.draft.value
        if (plan.isEmpty) return
        viewModelScope.launch { savedWorkouts0.save(name, plan) }
    }

    /** Load a saved workout into the draft, and remember that it was used. */
    fun loadWorkout(saved: SavedWorkout) {
        drafts.replaceWith(saved.plan)
        viewModelScope.launch { savedWorkouts0.markUsed(saved.id) }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch { savedWorkouts0.delete(id) }
    }

    fun add(exercise: Exercise) = drafts.add(exercise)
    fun remove(slotId: String) = drafts.remove(slotId)
    fun move(slotId: String, delta: Int) = drafts.move(slotId, delta)
    fun rename(name: String) = drafts.rename(name)

    fun setStyle(style: WorkoutStyle) = drafts.updatePlan { it.copy(style = style) }

    fun setRounds(rounds: Int) = drafts.updatePlan { it.copy(rounds = rounds.coerceIn(1, 30)) }

    fun setSets(slotId: String, sets: Int) =
        drafts.update(slotId) { it.copy(targetSets = sets.coerceIn(1, 20)) }

    fun setTarget(slotId: String, value: Int) = drafts.update(slotId) {
        when (it.measure) {
            ExerciseMeasure.REPS -> it.copy(targetReps = value.coerceIn(1, 200))
            ExerciseMeasure.SECONDS -> it.copy(targetSeconds = value.coerceIn(5, 600))
        }
    }

    /** Rest after a set of this movement. Zero turns the timer off for it. */
    fun setRest(slotId: String, seconds: Int) =
        drafts.update(slotId) { it.copy(restSeconds = seconds.coerceIn(0, 600)) }

    /** How many of this exercise's sets are warm-ups. They score calories but not volume. */
    fun setWarmupSets(slotId: String, sets: Int) =
        drafts.update(slotId) { it.copy(warmupSets = sets.coerceIn(0, it.targetSets)) }

    /**
     * Pair this exercise with the one above it into a superset, or split it back out.
     *
     * Grouping with the neighbour rather than offering free-form group management: a superset is
     * almost always adjacent movements, and a plan screen that asks you to name groups is a worse
     * trade than one that assumes the obvious.
     */
    fun toggleSupersetWithPrevious(slotId: String) {
        val plan = drafts.draft.value
        val index = plan.exercises.indexOfFirst { it.slotId == slotId }
        if (index <= 0) return
        val previous = plan.exercises[index - 1]
        val current = plan.exercises[index]

        if (current.groupId != null && current.groupId == previous.groupId) {
            drafts.update(slotId) { it.copy(groupId = null) }
            // A group of one isn't a superset; release the partner too.
            val stillGrouped = plan.exercises.count { it.groupId == current.groupId } - 1
            if (stillGrouped <= 1) {
                plan.exercises.filter { it.groupId == current.groupId && it.slotId != slotId }
                    .forEach { orphan -> drafts.update(orphan.slotId) { it.copy(groupId = null) } }
            }
            return
        }

        val group = previous.groupId ?: UUID.randomUUID().toString()
        if (previous.groupId == null) drafts.update(previous.slotId) { it.copy(groupId = group) }
        drafts.update(slotId) { it.copy(groupId = group) }
    }

    fun toggleMeasure(slotId: String) = drafts.update(slotId) {
        it.copy(
            measure = if (it.measure == ExerciseMeasure.REPS) ExerciseMeasure.SECONDS else ExerciseMeasure.REPS,
            metabolics = it.metabolics.copy(isometric = it.measure == ExerciseMeasure.REPS),
        )
    }

    /**
     * Added load in kg. Any movement can be weighted, so this is a property of the slot rather than
     * a separate gallery entry — the dataset has no "Weighted Pull-Up" and shouldn't need one.
     * The value feeds straight into the mechanical-work term of the calorie estimate.
     */
    fun setAddedWeight(slotId: String, kg: Double) = drafts.update(slotId) {
        it.copy(metabolics = it.metabolics.copy(externalLoadKg = kg.coerceIn(0.0, 300.0)))
    }

    /** Turn added load on or off, restoring a sensible default rather than starting at zero. */
    fun toggleWeighted(slotId: String) = drafts.update(slotId) {
        val kg = if (it.isWeighted) 0.0 else DEFAULT_ADDED_KG
        it.copy(metabolics = it.metabolics.copy(externalLoadKg = kg))
    }

    /** Hand the built plan to the session controller, which also pushes it to the watch. */
    fun startWorkout() {
        val current = plan.value
        if (current.isEmpty) return
        controller.start(typeFor(current), current)
        drafts.clear()
    }

    /** Pick the closest session type from what the plan is mostly made of. */
    private fun typeFor(plan: WorkoutPlan): ExerciseType =
        if (plan.exercises.any { it.isWeighted }) ExerciseType.STRENGTH else ExerciseType.CALISTHENICS

    fun targetOf(slot: PlannedExercise): Int =
        if (slot.measure == ExerciseMeasure.SECONDS) slot.targetSeconds else slot.targetReps

    private companion object {
        /** A plate or a light vest — the usual starting point when you first add load. */
        const val DEFAULT_ADDED_KG = 10.0
    }
}
