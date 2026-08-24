package com.calistapp.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.SessionPrefs
import com.calistapp.app.data.session.SessionPrefsRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.app.ui.navigation.Routes
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.progress.PerformedSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val sessionPrefs: SessionPrefsRepository,
    sessionRepository: SessionRepository,
    savedStateHandle: SavedStateHandle,
    exerciseRepository: ExerciseRepository,
) : ViewModel() {

    val live: StateFlow<LiveSession?> = controller.live
    val plan: StateFlow<WorkoutPlan> = drafts.draft

    /** Link health comes from the monitor, which distinguishes paired / reachable / streaming. */
    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    fun reconnectWatch() { watchConnection.reconnect() }

    /** The live-workout toggles (sound, vibration, autoplay, hands-free), from the pause screen. */
    val prefs: StateFlow<SessionPrefs> =
        sessionPrefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionPrefs())

    /** Every exercise by id, so the live screen can resolve the current movement's media and cues. */
    private val exercisesById: StateFlow<Map<String, Exercise>> = exerciseRepository.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The full [Exercise] the hero should show — its videos, angles and coaching cues. */
    val heroExercise: StateFlow<Exercise?> = combine(controller.live, exercisesById) { s, byId ->
        s?.heroExercise?.exerciseId?.let { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cross-session history for the hero exercise — last time performed, and personal bests. */
    val heroHistory: StateFlow<ExerciseHistoryStat?> = combine(
        controller.live.map { it?.heroExercise?.exerciseId }.distinctUntilChanged(),
        sessionRepository.observePerformed(),
    ) { id, performed -> historyFor(id, performed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Artwork per exercise id — kept for any caller that only needs the thumbnail frames. */
    val thumbnails: StateFlow<Map<String, List<String>>> = exercisesById
        .map { m -> m.mapValues { it.value.imageUrls } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val exerciseId: String? = savedStateHandle[Routes.ACTIVE_ARG]

    /** Whether this screen was opened for a specific gallery exercise. Known immediately. */
    val hasRequestedExercise: Boolean = exerciseId != null

    /** The exercise this screen was opened for (via the gallery), or null for a planned workout. */
    val plannedExercise: StateFlow<Exercise?> =
        (exerciseId?.let { exerciseRepository.observe(it) } ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Start whatever's queued up: the built plan, or — when launched from a gallery entry — a
     * one-exercise plan around that movement, so even the quick path gets exercise-aware scoring.
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
            if (current.isEmpty) return@launch
            controller.start(type, current)
            drafts.clear()
        }
    }

    fun toggleSegment() = controller.toggleSegment()
    fun startWorkNow() = controller.startWorkNow()
    fun adjustReps(delta: Int) = controller.adjustReps(delta)
    fun setReps(reps: Int) = controller.adjustReps(reps - (live.value?.currentReps ?: 0))
    fun setAddedWeight(kg: Double) = controller.setAddedWeight(kg)
    fun selectSlot(slotId: String) = controller.selectSlot(slotId)
    fun advanceToNext() = controller.advanceToNext()
    fun restartCurrentSet() = controller.restartCurrentSet()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun discard() = controller.discard()

    // Journal edits — annotate banked sets.
    fun setSetEffort(slotId: String, setIndex: Int, scale: EffortScale?, value: Double?) =
        controller.setSetEffort(slotId, setIndex, scale, value)

    fun setSetNote(slotId: String, setIndex: Int, note: String) = controller.setSetNote(slotId, setIndex, note)
    fun setSetReps(slotId: String, setIndex: Int, reps: Int) = controller.setSetReps(slotId, setIndex, reps)
    fun setSetWeight(slotId: String, setIndex: Int, kg: Double) = controller.setSetWeight(slotId, setIndex, kg)

    // Pause-screen toggles.
    fun setSound(on: Boolean) = viewModelScope.launch { sessionPrefs.setSound(on) }
    fun setVibration(on: Boolean) = viewModelScope.launch { sessionPrefs.setVibration(on) }
    fun setAutoplay(on: Boolean) = viewModelScope.launch { sessionPrefs.setAutoplay(on) }
    fun setHandsFree(on: Boolean) = viewModelScope.launch { sessionPrefs.setHandsFree(on) }

    fun finish(onDone: (String) -> Unit) {
        viewModelScope.launch { controller.stop()?.let(onDone) }
    }
}

/** Last-time and best figures for one exercise, pulled from finished sessions. */
data class ExerciseHistoryStat(
    val lastSets: List<SetLog>,
    val lastWhenMs: Long,
    val bestReps: Int,
    val bestWeightKg: Double,
)

private fun historyFor(exerciseId: String?, performed: List<PerformedSession>): ExerciseHistoryStat? {
    if (exerciseId == null) return null
    val withEx = performed.mapNotNull { s ->
        val sets = s.setLogs.filter { it.exerciseId == exerciseId }
        if (sets.isEmpty()) null else s.startMs to sets
    }
    if (withEx.isEmpty()) return null
    val latest = withEx.maxByOrNull { it.first }!!
    val allSets = withEx.flatMap { it.second }
    return ExerciseHistoryStat(
        lastSets = latest.second,
        lastWhenMs = latest.first,
        bestReps = allSets.maxOfOrNull { it.reps } ?: 0,
        bestWeightKg = allSets.maxOfOrNull { it.weightKg } ?: 0.0,
    )
}

/** Map an exercise's category onto the closest workout type for the session. */
fun exerciseTypeFor(exercise: Exercise): ExerciseType = when {
    exercise.category.equals("cardio", true) || exercise.category.equals("plyometrics", true) -> ExerciseType.HIIT
    exercise.isBodyweight -> ExerciseType.CALISTHENICS
    else -> ExerciseType.STRENGTH
}
