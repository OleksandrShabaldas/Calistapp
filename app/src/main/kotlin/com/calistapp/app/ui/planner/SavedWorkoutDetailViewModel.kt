package com.calistapp.app.ui.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.SavedWorkoutRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.SessionOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A saved workout on its own screen: what's in it, what it's produced, and the ways to act on it.
 *
 * Reached by tapping a saved workout rather than loading it straight into the planner — the list was
 * a dead end that could only "Load", and a programme you run twice a week deserves to show its
 * history and start in one tap.
 */
@HiltViewModel
class SavedWorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savedWorkouts: SavedWorkoutRepository,
    private val drafts: PlanDraftRepository,
    sessionRepository: SessionRepository,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val id: String = savedStateHandle[Routes.SAVED_WORKOUT_ARG] ?: ""

    val workout: StateFlow<SavedWorkout?> =
        savedWorkouts.saved
            .map { list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Thumbnails for the read-only exercise list, keyed by exercise id. */
    val thumbnails: StateFlow<Map<String, List<String>>> =
        exerciseRepository.observeAll()
            .map { list -> list.associate { it.id to it.imageUrls } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The sessions run from this workout.
     *
     * Matched by plan name, not structural equality: the setup screen threads warm-up and stretch
     * blocks onto the plan and can override its rest, so a performed session is rarely identical to
     * the saved one — but it carries the same name through. Names are unique (a re-save overwrites
     * the same name), so this doesn't over-match in practice.
     */
    val history: StateFlow<List<SessionOverview>> =
        combine(
            sessionRepository.observeSessions(),
            sessionRepository.observePerformed(),
            workout,
        ) { overviews, performed, w ->
            if (w == null || w.name.isBlank()) {
                emptyList()
            } else {
                val ids = performed
                    .filter { it.plan.name.equals(w.name, ignoreCase = true) }
                    .mapTo(mutableSetOf()) { it.id }
                overviews.filter { it.id in ids }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Copy this workout into the draft — the shared step behind both Start and Edit. Threads the
     * workout's id through as the draft origin, so an Edit that follows auto-syncs back to this row.
     */
    fun loadIntoDraft() {
        workout.value?.let { drafts.replaceWith(it.plan, originSavedId = it.id) }
    }

    /** Remember it was used, when starting a session from it. */
    fun markUsed() {
        viewModelScope.launch { savedWorkouts.markUsed(id) }
    }

    fun delete() {
        viewModelScope.launch { savedWorkouts.delete(id) }
    }
}
