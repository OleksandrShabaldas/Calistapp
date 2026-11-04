package com.calistapp.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.SessionController
import com.calistapp.app.ui.planner.ExercisePicker
import com.calistapp.app.ui.planner.WorkoutPlannerViewModel
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.SlotPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Adds and removes exercises in the running session — the picker's target during a workout. */
@HiltViewModel
class LiveEditViewModel @Inject constructor(
    private val controller: SessionController,
) : ViewModel() {

    val live: StateFlow<LiveSession?> = controller.live

    /**
     * The picker's ✓ toggle, aimed at today's session: an exercise that's in it untouched comes back
     * out, anything else is added after the last working exercise. Exercises with sets done stay.
     */
    fun toggle(exercise: Exercise) {
        val cur = live.value ?: return
        val untouched = cur.plan.slotsIn(SlotPhase.MAIN).lastOrNull { slot ->
            slot.exerciseId == exercise.id &&
                cur.setLogs.none { it.slotId == slot.slotId } &&
                cur.skipsFor(slot.slotId).isEmpty()
        }
        if (untouched != null) controller.removeExercise(untouched.slotId) else controller.addExercise(exercise)
    }
}

/**
 * The exercise picker opened from the journal's edit mode: the same search, filters and favourites as
 * building a workout, but every pick lands in today's session rather than the plan draft.
 */
@Composable
fun LiveAddExerciseScreen(
    onDone: () -> Unit,
    onOpenExercise: (String) -> Unit,
    search: WorkoutPlannerViewModel = hiltViewModel(),
    edit: LiveEditViewModel = hiltViewModel(),
) {
    val live by edit.live.collectAsStateWithLifecycle()
    val main = live?.plan?.slotsIn(SlotPhase.MAIN).orEmpty()
    ExercisePicker(
        viewModel = search,
        onOpenExercise = onOpenExercise,
        onDone = onDone,
        timesAdded = { e -> main.count { it.exerciseId == e.id } },
        onToggle = edit::toggle,
        addedCount = main.size,
    )
}
