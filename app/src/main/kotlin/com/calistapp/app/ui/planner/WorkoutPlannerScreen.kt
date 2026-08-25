package com.calistapp.app.ui.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.EditableStepper
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.common.WatchStatusStrip
import com.calistapp.app.ui.common.rememberReorderState
import com.calistapp.app.ui.exercises.ExerciseFilterSheet
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.SortMenu
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.OnyxFill
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.EffortTarget
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.PlannedSet
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.WorkoutStyle

/**
 * Build a workout up front: pick the exercises, set sets and reps, order them. What you build here
 * is what both the phone and the watch run, and what the calorie engine scores against.
 */
@Composable
fun WorkoutPlannerScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenSavedWorkout: (String) -> Unit,
    viewModel: WorkoutPlannerViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    // Saveable rather than remembered: opening an exercise's detail screen takes this whole
    // composable out of composition, and a plain remember would drop you back on the plan instead of
    // the picker you were browsing when you come back.
    var picking by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val saved by viewModel.savedWorkouts.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val planListState = rememberLazyListState()
    val reorder = rememberReorderState(planListState) { from, to -> viewModel.moveTo(from, to) }

    if (saving) {
        SaveWorkoutDialog(
            initialName = plan.name,
            onDismiss = { saving = false },
            onSave = { name -> viewModel.saveCurrentWorkout(name); saving = false },
        )
    }

    if (picking) {
        ExercisePicker(
            viewModel = viewModel,
            onOpenExercise = onOpenExercise,
            onDone = { picking = false },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text(
                "Build workout",
                style = MaterialTheme.typography.headlineMedium,
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Cancel") }
        }

        OutlinedTextField(
            value = plan.name,
            onValueChange = viewModel::rename,
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        StyleSelector(
            style = plan.style,
            rounds = plan.rounds,
            onStyle = viewModel::setStyle,
            onRounds = viewModel::setRounds,
        )

        if (plan.isEmpty) {
            // An empty plan is exactly when a saved workout is worth offering — it's the moment you'd
            // otherwise start rebuilding one from 834 exercises.
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No exercises yet", style = MaterialTheme.typography.titleMedium, color = Chalk)
                        Text(
                            "Add the movements you plan to do — the tracker uses them to score each set.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ash,
                        )
                    }
                }
                if (saved.isNotEmpty()) {
                    item {
                        Text(
                            "Your workouts",
                            style = MaterialTheme.typography.titleSmall,
                            color = Chalk,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(saved, key = { it.id }) { workout ->
                        SavedWorkoutRow(
                            workout = workout,
                            onOpen = { onOpenSavedWorkout(workout.id) },
                            onDelete = { viewModel.deleteWorkout(workout.id) },
                        )
                    }
                }
            }
        } else {
            Text(
                if (plan.isCircuit) {
                    "${plan.exercises.size} exercises · ${plan.rounds} rounds · ${plan.totalSets} sets"
                } else {
                    "${plan.exercises.size} exercises · ${plan.totalSets} sets"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Flame,
            )
            LazyColumn(
                Modifier.weight(1f),
                state = planListState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(plan.exercises, key = { _, it -> it.slotId }) { index, slot ->
                    val previous = plan.exercises.getOrNull(index - 1)
                    val dragging = reorder.draggingIndex == index
                    Box(
                        if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = reorder.draggedTranslationY }
                        } else {
                            Modifier
                        },
                    ) {
                        PlannedExerciseCard(
                            slot = slot,
                            target = viewModel.targetOf(slot),
                            showSets = !plan.isCircuit,
                            canSuperset = index > 0 && !plan.isCircuit,
                            inSuperset = slot.groupId != null && slot.groupId == previous?.groupId,
                            onToggleSuperset = { viewModel.toggleSupersetWithPrevious(slot.slotId) },
                            imageUrls = thumbnails[slot.exerciseId].orEmpty(),
                            onOpen = { onOpenExercise(slot.exerciseId) },
                            onTarget = { viewModel.setTarget(slot.slotId, it) },
                            onRest = { viewModel.setRest(slot.slotId, it) },
                            onToggleMeasure = { viewModel.toggleMeasure(slot.slotId) },
                            onToggleWeighted = { viewModel.toggleWeighted(slot.slotId) },
                            onWeight = { viewModel.setAddedWeight(slot.slotId, it) },
                            onSetReps = { i, v -> viewModel.setSetReps(slot.slotId, i, v) },
                            onSetWeight = { i, kg -> viewModel.setSetWeight(slot.slotId, i, kg) },
                            onSetEffort = { i, e -> viewModel.setSetEffort(slot.slotId, i, e) },
                            onToggleSetWarmup = { i -> viewModel.toggleSetWarmup(slot.slotId, i) },
                            onAddSet = { viewModel.addSet(slot.slotId) },
                            onRemoveSet = { i -> viewModel.removeSet(slot.slotId, i) },
                            isDragging = dragging,
                            dragHandleModifier = Modifier.pointerInput(slot.slotId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { reorder.onDragStart(index) },
                                    onDrag = { change, amount -> change.consume(); reorder.onDrag(amount.y) },
                                    onDragEnd = { reorder.onDragEnd() },
                                    onDragCancel = { reorder.onDragEnd() },
                                )
                            },
                            onRemove = { viewModel.remove(slot.slotId) },
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.weight(1f),
                shape = Capsule,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Add exercise")
            }
            if (!plan.isEmpty) {
                // Reflects whether the plan as it stands is already stored: a filled bookmark and
                // "Saved" once it is, back to "Save" the moment you change anything. Re-saving an
                // unchanged plan overwrites rather than piling up a duplicate.
                OutlinedButton(onClick = { saving = true }, shape = Capsule) {
                    Icon(
                        if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isSaved) Flame else Chalk,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(if (isSaved) "  Saved" else "  Save", color = if (isSaved) Flame else Chalk)
                }
            }
        }
        WatchStatusStrip(state = watchLink, onReconnect = viewModel::reconnectWatch)

        Button(
            onClick = onStarted,
            enabled = !plan.isEmpty,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = Capsule,
            colors = ButtonDefaults.buttonColors(containerColor = Flame),
        ) {
            Text("Continue", fontWeight = FontWeight.Bold)
        }
        Box(Modifier.height(8.dp))
    }
}

/** One reusable workout: tap to open its detail screen, or drop it. */
@Composable
private fun SavedWorkoutRow(workout: SavedWorkout, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${workout.name}\"?") },
            text = { Text("The saved workout goes; sessions you've already done with it stay.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }

    GlassCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(onClick = onOpen),
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    workout.summaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
            }
            TextButton(onClick = onOpen) { Text("Open", color = Flame) }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Delete ${workout.name}", tint = Coral, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SaveWorkoutDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "It'll be waiting on this screen next time, so you don't rebuild it from scratch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save", color = Flame)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * How the workout is worked through. A circuit replaces per-exercise set counts with a round count,
 * so the two controls are mutually exclusive rather than both being shown and one quietly ignored.
 */
@Composable
private fun StyleSelector(
    style: WorkoutStyle,
    rounds: Int,
    onStyle: (WorkoutStyle) -> Unit,
    onRounds: (Int) -> Unit,
) {
    GlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutStyle.entries.forEach { s ->
                PillChip(
                    label = s.displayName,
                    selected = style == s,
                    accent = if (s == WorkoutStyle.CIRCUIT) Violet else Flame,
                    onClick = { onStyle(s) },
                )
            }
        }
        AnimatedVisibility(visible = style == WorkoutStyle.CIRCUIT) {
            Column {
                Text(
                    "One set of every exercise, then round again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
                Stepper("Rounds", rounds, onRounds)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedExerciseCard(
    slot: PlannedExercise,
    target: Int,
    /** True for a split, where each set is its own editable row; false in a circuit (one definition). */
    showSets: Boolean,
    imageUrls: List<String>,
    onOpen: () -> Unit,
    onTarget: (Int) -> Unit,
    onRest: (Int) -> Unit,
    /** False for the first exercise and inside a circuit, where a superset means nothing. */
    canSuperset: Boolean,
    inSuperset: Boolean,
    onToggleSuperset: () -> Unit,
    onToggleMeasure: () -> Unit,
    onToggleWeighted: () -> Unit,
    onWeight: (Double) -> Unit,
    onSetReps: (Int, Int) -> Unit,
    onSetWeight: (Int, Double) -> Unit,
    onSetEffort: (Int, EffortTarget?) -> Unit,
    onToggleSetWarmup: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onRemove: () -> Unit,
) {
    // Collapsed by default: a plan is a list you scan, and eight cards of steppers is unreadable.
    // Tapping the row opens the controls for that one exercise. Saveable so the card stays as you
    // left it when the list is rebuilt — scrolled out of view, or returned to from the picker.
    var expanded by rememberSaveable { mutableStateOf(false) }

    GlassCard(
        accent = if (isDragging) Flame else if (slot.isWeighted) Amber else null,
        contentPadding = 12,
    ) {
        // The whole header toggles, not just the name — the chevron says which way it goes. A card
        // you can open but can't obviously close is the worse half of a disclosure control.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") {
                    expanded = !expanded
                },
        ) {
            // Tapping the thumbnail opens the movement's detail screen — that's where you go to
            // check form, which is a different intent from editing the set.
            ExerciseImage(
                urls = imageUrls,
                contentDescription = slot.name,
                phaseKey = slot.slotId,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpen),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    slot.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${slot.bodyPart.displayName} · ${slot.targetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Ash,
                modifier = Modifier.size(20.dp),
            )
            // Hold the handle to drag the exercise to a new position — the whole card follows your
            // finger and drops where you let go, in place of the old up/down arrows.
            Box(
                Modifier.size(36.dp).then(dragHandleModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "Hold and drag to reorder",
                    tint = Ash,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = Coral, modifier = Modifier.size(18.dp))
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showSets) {
                    // Split: an editable row per set — reps/seconds, added load and a target effort
                    // each set at a time, so a pyramid or a top-set-plus-back-offs is one card, not a
                    // compromise averaged into a single number.
                    PerSetEditor(
                        slot = slot,
                        onSetReps = onSetReps,
                        onSetWeight = onSetWeight,
                        onSetEffort = onSetEffort,
                        onToggleWarmup = onToggleSetWarmup,
                        onAddSet = onAddSet,
                        onRemoveSet = onRemoveSet,
                    )
                } else {
                    // Circuit: one definition, repeated each round (rounds live in the plan header).
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stepper(
                            label = if (slot.measure == ExerciseMeasure.SECONDS) "Seconds" else "Reps",
                            value = target,
                            onChange = onTarget,
                            step = if (slot.measure == ExerciseMeasure.SECONDS) 5 else 1,
                        )
                    }
                    PillChip(
                        label = "Added weight",
                        selected = slot.isWeighted,
                        accent = Amber,
                        leading = {
                            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        onClick = onToggleWeighted,
                    )
                    if (slot.isWeighted) {
                        Stepper(
                            label = "Kilograms",
                            value = slot.addedWeightKg.toInt(),
                            onChange = { onWeight(it.toDouble()) },
                            step = 5,
                        )
                    }
                }

                // Rest belongs to the movement: heavy pulling wants minutes, a finisher wants seconds.
                // Stepping to zero turns the timer off for this exercise.
                Stepper(
                    label = "Rest",
                    value = slot.restSeconds,
                    onChange = onRest,
                    step = 15,
                    format = { if (it <= 0) "off" else "${it / 60}:${(it % 60).toString().padStart(2, '0')}" },
                )

                // Superset is offered only where it means something — it needs a movement above it to
                // pair with, and a circuit already rotates everything.
                if (canSuperset) {
                    PillChip(
                        label = if (inSuperset) "Superset ✓" else "Superset with above",
                        selected = inSuperset,
                        accent = Violet,
                        onClick = onToggleSuperset,
                    )
                }

                TextButton(onClick = onToggleMeasure) {
                    Text(
                        if (slot.measure == ExerciseMeasure.SECONDS) {
                            "Counted as a hold — switch to reps"
                        } else {
                            "Counted in reps — switch to a timed hold"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * The per-set column for a split exercise: a header with "Add set", then one [SetRow] per set. Every
 * figure taps into the shared [NumberPadSheet] — reps/seconds and added load on the numeric pad,
 * target effort on the same pad wearing its RIR/RPE/%RM tabs — so entering a number here is identical
 * to entering one on the live screen.
 */
@Composable
private fun PerSetEditor(
    slot: PlannedExercise,
    onSetReps: (Int, Int) -> Unit,
    onSetWeight: (Int, Double) -> Unit,
    onSetEffort: (Int, EffortTarget?) -> Unit,
    onToggleWarmup: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
) {
    val sets = slot.sets()
    val secs = slot.measure == ExerciseMeasure.SECONDS
    var editing by remember { mutableStateOf<SetField?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sets", style = MaterialTheme.typography.labelLarge, color = Ash)
            TextButton(onClick = onAddSet) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("  Add set", style = MaterialTheme.typography.labelLarge)
            }
        }
        sets.forEachIndexed { i, set ->
            SetRow(
                number = i + 1,
                set = set,
                secs = secs,
                canRemove = sets.size > 1,
                onWarmup = { onToggleWarmup(i) },
                onReps = { editing = SetField.Reps(i, set.reps) },
                onWeight = { editing = SetField.Weight(i, set.weightKg) },
                onEffort = { editing = SetField.Effort(i, set.effort) },
                onRemove = { onRemoveSet(i) },
            )
        }
        // The warm-up hint, when any set is one.
        if (sets.any { it.isWarmup }) {
            Text(
                "Warm-up sets (W) count for calories but not for volume or records.",
                style = MaterialTheme.typography.labelSmall,
                color = Ash,
            )
        }
    }

    when (val e = editing) {
        is SetField.Reps -> NumberPadSheet(
            title = if (secs) "Seconds" else "Reps",
            initial = e.value,
            unit = if (secs) "sec" else "reps",
            onConfirm = { onSetReps(e.index, it); editing = null },
            onDismiss = { editing = null },
        )
        is SetField.Weight -> NumberPadSheet(
            title = "Added weight",
            initial = e.value.toInt(),
            unit = "kg",
            onConfirm = { onSetWeight(e.index, it.toDouble()); editing = null },
            onDismiss = { editing = null },
        )
        is SetField.Effort -> EffortPad(
            initial = e.effort,
            onConfirm = { onSetEffort(e.index, it); editing = null },
            onDismiss = { editing = null },
        )
        null -> {}
    }
}

/** One set's row: a warm-up/number badge, then tappable reps · weight · effort chips, then remove. */
@Composable
private fun SetRow(
    number: Int,
    set: PlannedSet,
    secs: Boolean,
    canRemove: Boolean,
    onWarmup: () -> Unit,
    onReps: () -> Unit,
    onWeight: () -> Unit,
    onEffort: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Tap the badge to flag the set as a warm-up (W) — the first rung of a ramp, scored for
        // calories but not for volume or records.
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (set.isWarmup) Amber.copy(alpha = 0.18f) else OnyxFill)
                .clickable(onClick = onWarmup),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (set.isWarmup) "W" else "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (set.isWarmup) Amber else Ash,
            )
        }
        SetChip("${set.reps}${if (secs) "s" else ""}", Modifier.weight(1f), onClick = onReps)
        SetChip(
            if (set.weightKg > 0) "+${set.weightKg.toInt()} kg" else "BW",
            Modifier.weight(1f),
            accent = if (set.weightKg > 0) Amber else null,
            onClick = onWeight,
        )
        SetChip(
            set.effort?.label ?: "Effort",
            Modifier.weight(1.2f),
            dim = set.effort == null,
            onClick = onEffort,
        )
        if (canRemove) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Remove set", tint = Coral, modifier = Modifier.size(15.dp))
            }
        } else {
            Box(Modifier.size(28.dp))
        }
    }
}

/** A tappable value cell inside a [SetRow] — opens the shared numeric pad for that figure. */
@Composable
private fun SetChip(
    text: String,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent?.copy(alpha = 0.14f) ?: OnyxFill)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (dim) AshFaint else (accent ?: Chalk),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The effort pad: the shared numeric keypad wearing its RIR/RPE/%RM tabs and the scale help blurb.
 * Confirming 0 clears the target — the clean way to remove one you set by mistake.
 */
@Composable
private fun EffortPad(
    initial: EffortTarget?,
    onConfirm: (EffortTarget?) -> Unit,
    onDismiss: () -> Unit,
) {
    var scaleIndex by remember { mutableStateOf(initial?.scale?.ordinal ?: EffortScale.RPE.ordinal) }
    val scale = EffortScale.entries[scaleIndex]
    NumberPadSheet(
        title = "Target effort",
        initial = initial?.value?.toInt() ?: 0,
        unit = scale.label,
        tabs = EffortScale.entries.map { it.label },
        selectedTab = scaleIndex,
        onSelectTab = { scaleIndex = it },
        help = scale.blurb,
        onConfirm = { v -> onConfirm(if (v <= 0) null else EffortTarget(EffortScale.entries[scaleIndex], v.toDouble())) },
        onDismiss = onDismiss,
    )
}

/** Which figure of which set the numeric pad is currently editing. */
private sealed interface SetField {
    val index: Int
    data class Reps(override val index: Int, val value: Int) : SetField
    data class Weight(override val index: Int, val value: Double) : SetField
    data class Effort(override val index: Int, val effort: EffortTarget?) : SetField
}

/**
 * Every planner number is a stepper you can also type into — tap the value to enter it directly
 * rather than holding ＋ thirty times to get from 10 to 40. Delegates to the shared [EditableStepper].
 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) = EditableStepper(label = label, value = value, onChange = onChange, step = step, format = format)

/**
 * The picker. Same relevance search, filters and sort as the gallery — this is the screen where not
 * knowing the dataset's exact spelling actually costs you something.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisePicker(
    viewModel: WorkoutPlannerViewModel,
    onOpenExercise: (String) -> Unit,
    onDone: () -> Unit,
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val facets by viewModel.facets.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    // The picker is a step *inside* the planner, not a destination of its own, so back closes it and
    // returns to the plan rather than popping the planner off the stack entirely.
    BackHandler(onBack = onDone)

    if (showFilters) {
        ExerciseFilterSheet(
            filters = filters,
            facets = facets,
            actions = viewModel,
            onDismiss = { showFilters = false },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text(
                "Add exercise",
                style = MaterialTheme.typography.headlineSmall,
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text("Done (${plan.exercises.size})") }
        }

        OutlinedTextField(
            value = filters.query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search name, muscle, tag…") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SortMenu(current = filters.sort, onSelect = viewModel::setSort)
            PillChip(
                label = if (filters.activeCount > 0) "Filters · ${filters.activeCount}" else "Filters",
                selected = filters.activeCount > 0,
                accent = Violet,
                onClick = { showFilters = true },
            )
            if (filters.activeCount > 0) {
                PillChip(label = "Clear", accent = Coral, onClick = viewModel::clearFilters)
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(results, key = { it.id }) { exercise ->
                PickerRow(
                    exercise = exercise,
                    // How many times it's already in the plan. This is what closing the picker after
                    // every add used to communicate — a workout is usually six or eight movements,
                    // and confirming each one by throwing you out of the list cost six or eight
                    // round trips to save a moment's doubt.
                    timesAdded = plan.exercises.count { it.exerciseId == exercise.id },
                    isFavourite = exercise.id in favourites,
                    onToggleFavourite = { viewModel.toggleFavourite(exercise.id) },
                    onAdd = { viewModel.add(exercise) },
                    onOpen = { onOpenExercise(exercise.id) },
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing matches that. Try a shorter search, or loosen the filters.",
                        color = Ash,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    exercise: Exercise,
    timesAdded: Int,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        // The row opens the exercise; only the "+" adds it. Tapping a name to read about a movement
        // is the more common intent, and silently adding it instead is a surprise.
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(
                urls = exercise.imageUrls,
                contentDescription = exercise.name,
                phaseKey = exercise.id,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.bodyPart.displayName} · ${exercise.difficulty.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavourite, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavourite) {
                        "Remove ${exercise.name} from favourites"
                    } else {
                        "Add ${exercise.name} to favourites"
                    },
                    tint = if (isFavourite) Amber else Ash,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (timesAdded > 0) {
                Text(
                    if (timesAdded == 1) "in plan" else "×$timesAdded",
                    style = MaterialTheme.typography.labelSmall,
                    color = Flame,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (timesAdded > 0) {
                        "Add another ${exercise.name} — $timesAdded already in the plan"
                    } else {
                        "Add ${exercise.name}"
                    },
                    tint = Flame,
                )
            }
        }
    }
}
