package com.calistapp.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseEnrichmentManager
import com.calistapp.app.data.exercise.ExercisePrefsRepository
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
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
import javax.inject.Inject

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    repository: ExerciseRepository,
    private val enrichmentManager: ExerciseEnrichmentManager,
    private val prefs: ExercisePrefsRepository,
) : ViewModel(), ExerciseFilterActions {

    private val _filters = MutableStateFlow(ExerciseFilters())
    val filters: StateFlow<ExerciseFilters> = _filters.asStateFlow()

    val enrichmentProgress = enrichmentManager.progress

    fun startEnrichAll() = enrichmentManager.start()
    fun stopEnrichAll() = enrichmentManager.stop()

    private val all: StateFlow<List<Exercise>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = all
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // Recomputed as the structural filters change, so the muscle/equipment chips only ever offer
    // what the chosen body part actually contains.
    val facets: StateFlow<FilterFacets> =
        combine(all, _filters) { list, f -> FilterFacets.of(list, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterFacets())

    val favourites: StateFlow<Set<String>> = prefs.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Search and filters as before, with starred movements lifted to the top.
     *
     * A stable sort, so within each group the chosen ordering is untouched — starring something
     * pulls it up the list without scrambling everything below it.
     */
    val exercises: StateFlow<List<Exercise>> =
        combine(all, _filters, prefs.favourites) { list, f, starred ->
            list.applyQuery(f).sortedByDescending { it.id in starred }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavourite(exerciseId: String) {
        viewModelScope.launch { prefs.toggleFavourite(exerciseId) }
    }

    // ---- Mutations -------------------------------------------------------------------------------

    fun setQuery(query: String) = _filters.update { it.copy(query = query) }

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

    /** Clear every filter but keep what was typed. */
    override fun clearFilters() = _filters.update { ExerciseFilters(query = it.query, sort = it.sort) }
}
