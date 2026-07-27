package com.calistapp.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val controller: SessionController,
    private val drafts: PlanDraftRepository,
    private val watchConnection: WatchConnectionMonitor,
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    val live: StateFlow<LiveSession?> = controller.live
    val plan: StateFlow<WorkoutPlan> = drafts.draft

    /** Link health comes from the monitor, which distinguishes paired / reachable / streaming. */
    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    fun reconnectWatch() { watchConnection.reconnect() }

    /**
     * Artwork per exercise id, so the live screen can show the movement you're on. Looked up here
     * rather than carried in the plan, which is serialised to the watch on every start.
     */
    val thumbnails: StateFlow<Map<String, List<String>>> = exerciseRepository.observeAll()
        .map { list -> list.associate { it.id to it.imageUrls } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val exerciseId: String? = savedStateHandle[Routes.ACTIVE_ARG]

    /** The exercise this screen was opened for (via the gallery), or null for a planned workout. */
    val plannedExercise: StateFlow<Exercise?> =
        (exerciseId?.let { exerciseRepository.observe(it) } ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Start whatever's queued up: the built plan, or — when launched straight from a gallery entry —
     * a one-exercise plan around that movement, so even the quick path gets exercise-aware scoring.
     */
    fun start(type: ExerciseType) {
        viewModelScope.launch {
            var current = plan.value
            if (current.isEmpty) {
                plannedExercise.value?.let { exercise ->
                    drafts.add(exercise)
                    current = drafts.draft.first()
                }
            }
            controller.start(type, current)
            drafts.clear()
        }
    }

    fun toggleSegment() = controller.toggleSegment()
    fun startWorkNow() = controller.startWorkNow()
    fun adjustReps(delta: Int) = controller.adjustReps(delta)
    fun selectSlot(slotId: String) = controller.selectSlot(slotId)
    fun advanceToNext() = controller.advanceToNext()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun discard() = controller.discard()

    fun finish(onDone: (String) -> Unit) {
        viewModelScope.launch { controller.stop()?.let(onDone) }
    }
}

/** Map an exercise's category onto the closest workout type for the session. */
fun exerciseTypeFor(exercise: Exercise): ExerciseType = when {
    exercise.category.equals("cardio", true) || exercise.category.equals("plyometrics", true) -> ExerciseType.HIIT
    exercise.isBodyweight -> ExerciseType.CALISTHENICS
    else -> ExerciseType.STRENGTH
}
