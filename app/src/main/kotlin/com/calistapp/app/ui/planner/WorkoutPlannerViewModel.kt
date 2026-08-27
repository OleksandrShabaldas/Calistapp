package com.calistapp.app.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.ui.exercises.ExerciseFilterActions
import com.calistapp.app.ui.exercises.ExerciseFilters
import com.calistapp.app.ui.exercises.ExerciseSort
import com.calistapp.app.ui.exercises.FilterFacets
import com.calistapp.app.ui.exercises.applyQuery
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.EffortTarget
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.PlannedSet
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

    // Recomputed as the structural filters change, so the muscle/equipment chips only ever offer
    // what the chosen body part actually contains.
    val facets: StateFlow<FilterFacets> =
        combine(all, _filters) { list, f -> FilterFacets.of(list, f) }
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

    /**
     * Whether the draft is backed by a saved workout — drives the Save button's "Saved" state. Once
     * a draft is backed (loaded from the list, or saved once), edits stream back automatically, so
     * this stays lit through editing rather than flipping to "Save" and implying a fresh copy.
     */
    val isSaved: StateFlow<Boolean> =
        drafts.originId.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * First-time Save for a scratch draft: store it, and back the draft with it so every later edit
     * auto-syncs (no re-tapping Save, no duplicate). Re-saving an already-backed draft (e.g. to
     * rename) overwrites the same row via its origin id rather than minting a new one.
     */
    fun saveCurrentWorkout(name: String) {
        val plan = drafts.draft.value
        if (plan.isEmpty) return
        val trimmed = name.trim().ifBlank { "Workout" }
        // Re-save the same row (origin, or a name/plan match) keeping its history; otherwise a new one.
        val existingId = drafts.originId.value
            ?: savedWorkouts.value.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) || samePlan(it.plan, plan)
            }?.id
        drafts.rename(trimmed)
        if (existingId != null) {
            drafts.markSavedAs(existingId)
            viewModelScope.launch { savedWorkouts0.syncPlan(existingId, plan.copy(name = trimmed)) }
        } else {
            val id = UUID.randomUUID().toString()
            drafts.markSavedAs(id)
            viewModelScope.launch { savedWorkouts0.save(trimmed, plan.copy(name = trimmed), id) }
        }
    }

    /** Two plans are "the same saved workout" when they match apart from the plan id. */
    private fun samePlan(a: WorkoutPlan, b: WorkoutPlan): Boolean = a.copy(id = "") == b.copy(id = "")

    /** Load a saved workout into the draft (backed by it, so edits sync), and mark it used. */
    fun loadWorkout(saved: SavedWorkout) {
        drafts.replaceWith(saved.plan, originSavedId = saved.id)
        viewModelScope.launch { savedWorkouts0.markUsed(saved.id) }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch { savedWorkouts0.delete(id) }
    }

    fun add(exercise: Exercise) {
        drafts.add(exercise)
        // Adding a movement to a plan counts as "using" it — surface it in the gallery's Recent.
        viewModelScope.launch { prefs.markRecent(exercise.id) }
    }

    fun remove(slotId: String) = drafts.remove(slotId)
    fun move(slotId: String, delta: Int) = drafts.move(slotId, delta)

    /** Drag-to-reorder: move the exercise at [from] to position [to]. */
    fun moveTo(from: Int, to: Int) = drafts.moveIndex(from, to)
    fun rename(name: String) = drafts.rename(name)

    fun setStyle(style: WorkoutStyle) = drafts.updatePlan { plan ->
        // A per-set column is a split concept — a circuit repeats one definition per round. Switching
        // to a circuit collapses any per-set column back to a uniform slot (keeping the set count and
        // the first set's load/reps), so the round-by-round engine never reads a pyramid.
        val exercises = if (style != WorkoutStyle.CIRCUIT) {
            plan.exercises
        } else {
            plan.exercises.map { slot ->
                val sets = slot.plannedSets
                if (sets.isEmpty()) return@map slot
                val first = sets.first()
                slot.copy(
                    plannedSets = emptyList(),
                    targetSets = sets.size,
                    targetReps = if (slot.measure == ExerciseMeasure.REPS) first.reps else slot.targetReps,
                    targetSeconds = if (slot.measure == ExerciseMeasure.SECONDS) first.reps else slot.targetSeconds,
                    warmupSets = sets.count { it.isWarmup },
                    metabolics = slot.metabolics.copy(externalLoadKg = first.weightKg),
                )
            }
        }
        plan.copy(style = style, exercises = exercises)
    }

    fun setRounds(rounds: Int) = drafts.updatePlan { it.copy(rounds = rounds.coerceIn(1, 30)) }

    /** The circuit path's single reps/seconds definition (a split edits per set — see [setSetReps]). */
    fun setTarget(slotId: String, value: Int) = drafts.update(slotId) {
        when (it.measure) {
            ExerciseMeasure.REPS -> it.copy(targetReps = value.coerceIn(1, 200))
            ExerciseMeasure.SECONDS -> it.copy(targetSeconds = value.coerceIn(5, 600))
        }
    }

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

    // ---- Per-set editing --------------------------------------------------------------------------
    //
    // Every edit routes through the slot's materialized set list: `it.sets()` returns the explicit
    // per-set column when one exists, or synthesizes a uniform one from the legacy fields, so the very
    // first per-set edit "expands" a uniform slot into an editable column without a separate mode flag.

    private fun editSets(slotId: String, transform: (List<PlannedSet>) -> List<PlannedSet>) =
        drafts.update(slotId) { slot ->
            val next = transform(slot.sets())
            // Keep the slot's nominal load (what `displayName`, `isWeighted` and the session-type
            // guess read) tracking the heaviest set, so per-set weights don't leave those stale.
            val nominal = next.maxOfOrNull { it.weightKg } ?: 0.0
            slot.copy(
                plannedSets = next,
                metabolics = slot.metabolics.copy(externalLoadKg = nominal),
            )
        }

    /** Set the target value (reps, or seconds for a hold) of one set. */
    fun setSetReps(slotId: String, index: Int, value: Int) = editSets(slotId) { sets ->
        sets.mapIndexed { i, s -> if (i == index) s.copy(reps = value.coerceIn(1, 600)) else s }
    }

    fun setSetWeight(slotId: String, index: Int, kg: Double) = editSets(slotId) { sets ->
        sets.mapIndexed { i, s -> if (i == index) s.copy(weightKg = kg.coerceIn(0.0, 300.0)) else s }
    }

    fun setSetEffort(slotId: String, index: Int, effort: EffortTarget?) = editSets(slotId) { sets ->
        sets.mapIndexed { i, s -> if (i == index) s.copy(effort = effort) else s }
    }

    fun setSetNote(slotId: String, index: Int, note: String) = editSets(slotId) { sets ->
        sets.mapIndexed { i, s -> if (i == index) s.copy(note = note.take(240)) else s }
    }

    fun toggleSetWarmup(slotId: String, index: Int) = editSets(slotId) { sets ->
        sets.mapIndexed { i, s -> if (i == index) s.copy(isWarmup = !s.isWarmup) else s }
    }

    /** Append a set, copying the last one's load so a straight-set add needs no further taps. */
    fun addSet(slotId: String) = editSets(slotId) { sets ->
        val template = sets.lastOrNull() ?: PlannedSet()
        sets + template.copy(isWarmup = false, effort = null, note = "")
    }

    fun removeSet(slotId: String, index: Int) = editSets(slotId) { sets ->
        if (sets.size <= 1) sets else sets.filterIndexed { i, _ -> i != index }
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

    fun targetOf(slot: PlannedExercise): Int =
        if (slot.measure == ExerciseMeasure.SECONDS) slot.targetSeconds else slot.targetReps

    private companion object {
        /** A plate or a light vest — the usual starting point when you first add load. */
        const val DEFAULT_ADDED_KG = 10.0
    }
}
