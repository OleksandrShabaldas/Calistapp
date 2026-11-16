package com.calistapp.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.session.PlanDraftRepository
import com.calistapp.app.data.session.RoutineRepository
import com.calistapp.app.data.session.SessionPrefs
import com.calistapp.app.data.session.SessionPrefsRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.Routine
import com.calistapp.core.model.RoutineKind
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The pre-flight screen between building a workout and running it: an optional warm-up, an optional
 * stretch, the live-session toggles, and a session-wide rest default. Starting from here threads the
 * chosen routines onto the plan as an opening and a closing block and hands it to the controller.
 */
@HiltViewModel
class SessionSetupViewModel @Inject constructor(
    private val drafts: PlanDraftRepository,
    private val routines: RoutineRepository,
    private val prefsRepo: SessionPrefsRepository,
    private val controller: SessionController,
    private val watchConnection: WatchConnectionMonitor,
    devicePrefs: com.calistapp.app.data.device.DevicePrefsRepository,
) : ViewModel() {

    /** A Bluetooth heart-rate band chosen in Settings → Devices — it connects when the workout starts. */
    val band: StateFlow<com.calistapp.app.data.device.SavedBand?> = devicePrefs.prefs
        .map { it.band }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    val plan: StateFlow<WorkoutPlan> = drafts.draft

    val warmUps: List<Routine> = routines.byKind(RoutineKind.WARM_UP)
    val stretches: List<Routine> = routines.byKind(RoutineKind.STRETCH)

    private val _warmUpId = MutableStateFlow<String?>(null)
    val warmUpId: StateFlow<String?> = _warmUpId.asStateFlow()

    private val _stretchId = MutableStateFlow<String?>(null)
    val stretchId: StateFlow<String?> = _stretchId.asStateFlow()

    val prefs: StateFlow<SessionPrefs> =
        prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionPrefs())

    val watchLink: StateFlow<WatchLinkState> = watchConnection.state

    fun reconnectWatch() = watchConnection.reconnect()

    /** Tap a chosen routine again to clear it — a warm-up is optional. */
    fun selectWarmUp(id: String) = _warmUpId.update { if (it == id) null else id }
    fun selectStretch(id: String) = _stretchId.update { if (it == id) null else id }

    fun setSound(on: Boolean) = viewModelScope.launch { prefsRepo.setSound(on) }
    fun setVibration(on: Boolean) = viewModelScope.launch { prefsRepo.setVibration(on) }
    fun setAutoplay(on: Boolean) = viewModelScope.launch { prefsRepo.setAutoplay(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { prefsRepo.setKeepScreenOn(on) }

    /**
     * Build the final plan and start. Warm-up runs first, then the working plan, then the stretch —
     * the routine blocks are single-set timed holds that score for calories but not volume or records.
     * They're marked run-once, so a circuit runs the warm-up before round 1 and the stretch after the
     * last round instead of rotating them every round. (Routines used to be withheld from circuits
     * entirely, which hid the pickers for every circuit workout — the feature looked missing.)
     */
    fun start() {
        val base = drafts.draft.value
        if (base.isEmpty) return

        val warm = _warmUpId.value?.let(routines::byId)?.toBlocks(SlotPhase.WARM_UP).orEmpty()
        val stretch = _stretchId.value?.let(routines::byId)?.toBlocks(SlotPhase.COOL_DOWN).orEmpty()

        // The saved workout it came from rides along, so finishing can offer to keep today's edits.
        val finalPlan = base.copy(exercises = warm + base.exercises + stretch, sourceWorkoutId = drafts.originId.value)
        controller.start(typeFor(base), finalPlan)
        drafts.clear()
    }

    private fun typeFor(plan: WorkoutPlan): ExerciseType =
        if (plan.exercises.any { it.isWeighted }) ExerciseType.STRENGTH else ExerciseType.CALISTHENICS

    /**
     * A routine's timed items become single-set blocks that flow one into the next, tagged with their
     * phase and routine so the journal, the summary and the coach treat them as a warm-up or cool-down
     * rather than as more exercises.
     */
    private fun Routine.toBlocks(phase: SlotPhase): List<PlannedExercise> = items.map { item ->
        PlannedExercise(
            slotId = UUID.randomUUID().toString(),
            exerciseId = item.exerciseId,
            name = item.name,
            measure = ExerciseMeasure.SECONDS,
            targetSets = 1,
            targetSeconds = item.seconds,
            warmupSets = 1,
            restSeconds = 0,
            metabolics = ROUTINE_METABOLICS,
            runsOnce = true,
            phase = phase,
            routineName = name,
        )
    }

    private companion object {
        /** Gentle profile — a mobility drill or a stretch, not a working set. HR still scores it. */
        val ROUTINE_METABOLICS = ExerciseMetabolics(
            muscleMassFraction = 0.12,
            loadFraction = 0.30,
            romMetres = 0.20,
            isometric = true,
            compound = false,
        )
    }
}
