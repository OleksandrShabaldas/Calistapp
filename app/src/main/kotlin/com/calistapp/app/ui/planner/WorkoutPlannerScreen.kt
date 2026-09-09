package com.calistapp.app.ui.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.DecimalPadSheet
import com.calistapp.app.ui.common.EditableNumber
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.common.WatchStatusStrip
import com.calistapp.app.ui.common.glow
import com.calistapp.app.ui.common.rememberReorderState
import com.calistapp.app.ui.exercises.ExerciseFilterSheet
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.SortMenu
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.CardFlat
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.AshFaint
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Eyebrow
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.FlameSoft
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFill
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.app.ui.theme.PageDim
import com.calistapp.app.ui.theme.PageInk
import com.calistapp.app.ui.theme.PageWarm
import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.EffortTarget
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.PlannedSet
import com.calistapp.core.model.SavedWorkout
import com.calistapp.core.model.WorkoutStyle
import com.calistapp.core.model.formatKg

/**
 * Build a workout up front: pick the exercises, set sets and reps, order them. What you build here
 * is what both the phone and the watch run, and what the calorie engine scores against.
 */
@Composable
fun WorkoutPlannerScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenExerciseFromPicker: (String) -> Unit,
    onOpenSavedWorkout: (String) -> Unit,
    viewModel: WorkoutPlannerViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    // Saveable rather than remembered: opening an exercise's detail screen takes this whole
    // composable out of composition, and a plain remember would drop you back on the plan instead of
    // the picker you were browsing when you come back.
    var picking by rememberSaveable { mutableStateOf(false) }
    val saved by viewModel.savedWorkouts.collectAsStateWithLifecycle()
    val watchLink by viewModel.watchLink.collectAsStateWithLifecycle()
    val planListState = rememberLazyListState()
    val reorder = rememberReorderState(planListState) { from, to -> viewModel.moveTo(from, to) }

    if (picking) {
        ExercisePicker(
            viewModel = viewModel,
            onOpenExercise = onOpenExerciseFromPicker,
            onDone = { picking = false },
        )
        return
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(PageWarm, PageDim, PageInk)))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text(
                "Build workout",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.4).sp),
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Cancel", color = Flame, style = MaterialTheme.typography.labelLarge) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("NAME", style = Eyebrow, color = Ash)
            ProtoField(
                value = plan.name,
                onValueChange = viewModel::rename,
                placeholder = "Untitled workout",
            )
        }

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
                    "${plan.exercises.size} exercises · ${plan.rounds} ${if (plan.rounds == 1) "round" else "rounds"} · ${plan.totalSets} sets"
                } else {
                    "${plan.exercises.size} exercises · ${plan.totalSets} sets"
                },
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.2.sp),
                color = Flame,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
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

        // Dashed "add" slot — full width now that Save is the one primary action below it.
        Box(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                .dashedCapsuleBorder(Flame.copy(alpha = 0.55f))
                .clickable { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Filled.Add, null, tint = Flame, modifier = Modifier.size(18.dp))
                Text("Add exercise", color = Flame, style = MaterialTheme.typography.labelLarge)
            }
        }
        WatchStatusStrip(state = watchLink, onReconnect = viewModel::reconnectWatch)

        // Save stores the plan (named from the field above) and hands you to its detail screen, where
        // History and Start live — the prototype's single gradient bottom action.
        val canSave = !plan.isEmpty
        GlowBox(
            color = FlameHot,
            shape = Capsule,
            glowRadius = if (canSave) 16.dp else 0.dp,
            glowAlpha = if (canSave) 0.45f else 0f,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(Capsule)
                    .background(
                        if (canSave) Brush.horizontalGradient(listOf(FlameHot, FlameGlow))
                        else Brush.horizontalGradient(listOf(OnyxFillStrong, OnyxFillStrong)),
                    )
                    .clickable(enabled = canSave) {
                        viewModel.saveCurrentWorkout(plan.name)?.let { onOpenSavedWorkout(it) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Filled.Bookmark, null, tint = if (canSave) Onyx else AshFaint, modifier = Modifier.size(19.dp))
                    Text(
                        "Save workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) Onyx else AshFaint,
                    )
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        // Two-up segmented switch — the active mode fills with the orange wash + border per the
        // prototype; the inactive one is a hairline outline.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutStyle.entries.forEach { s ->
                val active = style == s
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (active) Flame.copy(alpha = 0.12f) else Color.Transparent)
                        .border(1.dp, if (active) Flame.copy(alpha = 0.5f) else OnyxBorder, RoundedCornerShape(13.dp))
                        .clickable { onStyle(s) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.displayName, style = MaterialTheme.typography.labelLarge, color = if (active) Flame else Ash)
                }
            }
        }
        AnimatedVisibility(visible = style == WorkoutStyle.CIRCUIT) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardFlat)
                    .border(1.dp, OnyxBorder, RoundedCornerShape(14.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ROUNDS", style = Eyebrow, color = Ash)
                InfoDot("In a circuit you do one set of every exercise in order, then repeat the whole list. Rounds is how many times you go through it.")
                Box(Modifier.weight(1f))
                ProtoStepper(value = rounds, onChange = onRounds, min = 1)
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

    val muscle = muscleColor(slot.bodyPart)
    FlatCard(
        // The card you're editing lifts to the orange accent (border + faint wash), the same active
        // cue dragging uses; collapsed cards stay flat so the plan reads as a clean scannable list.
        accent = if (isDragging || expanded) Flame else null,
    ) {
        // Tap the header to open/close; press and hold it to drag the card to a new position — the
        // whole card is the grip, there's no separate handle. The chevron says which way a tap goes.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
                .then(dragHandleModifier),
        ) {
            // Collapsed: a small glowing muscle-coloured dot. Expanded: it morphs into the movement's
            // thumbnail, which then also opens the exercise's detail on tap (to check form).
            Crossfade(targetState = expanded, label = "planned-thumb") { isExpanded ->
                if (isExpanded) {
                    ExerciseImage(
                        urls = imageUrls,
                        contentDescription = slot.name,
                        phaseKey = slot.slotId,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
                    )
                } else {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).glow(muscle, spread = 6.dp, alpha = 0.7f).clip(Capsule).background(muscle))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    slot.displayName,
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.2).sp),
                    color = Chalk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val unit = if (slot.measure == ExerciseMeasure.SECONDS) "s" else " reps"
                    MiniChip("$target$unit", Ash, tinted = false)
                    if (slot.isWeighted) MiniChip("+${formatKg(slot.addedWeightKg)} kg", Flame, tinted = true)
                }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Ash,
                modifier = Modifier.size(20.dp),
            )
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (slot.measure == ExerciseMeasure.SECONDS) "SECONDS" else "REPS",
                            style = Eyebrow,
                            color = Ash,
                            modifier = Modifier.weight(1f),
                        )
                        ProtoStepper(
                            value = target,
                            onChange = onTarget,
                            step = if (slot.measure == ExerciseMeasure.SECONDS) 5 else 1,
                        )
                    }
                    PillChip(
                        label = "Added weight",
                        selected = slot.isWeighted,
                        accent = Flame,
                        leading = {
                            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        onClick = onToggleWeighted,
                    )
                    if (slot.isWeighted) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("KILOGRAMS", style = Eyebrow, color = Ash, modifier = Modifier.weight(1f))
                            ProtoStepper(
                                value = slot.addedWeightKg.toInt(),
                                onChange = { onWeight(it.toDouble()) },
                                step = 5,
                            )
                        }
                    }
                }

                // Superset is offered only where it means something — it needs a movement above it to
                // pair with, and a circuit already rotates everything.
                if (canSuperset) {
                    PillChip(
                        label = if (inSuperset) "Superset ✓" else "Superset with above",
                        selected = inSuperset,
                        accent = Flame,
                        onClick = onToggleSuperset,
                    )
                }

                // Measure toggle on the left; Remove lives here now that the header is a clean row.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleMeasure, modifier = Modifier.weight(1f)) {
                        Text(
                            if (slot.measure == ExerciseMeasure.SECONDS) {
                                "Counted as a hold — switch to reps"
                            } else {
                                "Counted in reps — switch to a timed hold"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, null, tint = Coral, modifier = Modifier.size(15.dp))
                        Text("  Remove", color = Coral, style = MaterialTheme.typography.labelSmall)
                    }
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
        is SetField.Weight -> DecimalPadSheet(
            title = "Added weight",
            initial = e.value,
            onConfirm = { onSetWeight(e.index, it); editing = null },
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
    var favOpen by rememberSaveable { mutableStateOf(true) }
    val resultsState = rememberLazyListState()

    // A new search should show its best matches, which are at the top — the list used to keep the old
    // scroll offset, landing you in the middle of results you hadn't seen the start of.
    LaunchedEffect(filters.query) { resultsState.scrollToItem(0) }

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
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(PageWarm, PageDim, PageInk)))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
            Text(
                "Add exercise",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.6).sp),
                color = Chalk,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text("Done · ${plan.exercises.size}", color = Flame, style = MaterialTheme.typography.labelLarge) }
        }

        ProtoField(
            value = filters.query,
            onValueChange = viewModel::search,
            placeholder = "Search name, muscle, tag…",
            height = 50.dp,
            radius = 15.dp,
            imeAction = ImeAction.Search,
            leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = AshFaint, modifier = Modifier.size(18.dp)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SortMenu(current = filters.sort, onSelect = viewModel::setSort)
            PillChip(
                label = if (filters.activeCount > 0) "Filters · ${filters.activeCount}" else "Filters",
                selected = filters.activeCount > 0,
                accent = Flame,
                onClick = { showFilters = true },
            )
            if (filters.activeCount > 0) {
                PillChip(label = "Clear", accent = Coral, onClick = viewModel::clearFilters)
            }
        }

        // Favourites float to their own group at the top so the movements you reach for most aren't
        // hunted out of 800 — the rest follow under "All exercises". Both honour the live search.
        val favResults = results.filter { it.id in favourites }
        val otherResults = results.filterNot { it.id in favourites }

        LazyColumn(
            Modifier.weight(1f),
            state = resultsState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            if (favResults.isNotEmpty()) {
                item(key = "fav-header") { FavouritesHeader(favResults.size, favOpen) { favOpen = !favOpen } }
                if (favOpen) {
                    items(favResults, key = { "fav-${it.id}" }) { exercise ->
                        PickerRow(
                            exercise = exercise,
                            timesAdded = plan.exercises.count { it.exerciseId == exercise.id },
                            isFavourite = true,
                            onToggleFavourite = { viewModel.toggleFavourite(exercise.id) },
                            onAdd = { viewModel.add(exercise) },
                            onOpen = { onOpenExercise(exercise.id) },
                        )
                    }
                }
                item(key = "all-header") { PickerSectionLabel("All exercises") }
            }
            items(otherResults, key = { it.id }) { exercise ->
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
    val muscle = muscleColor(exercise.bodyPart)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(CardFlat).border(1.dp, OnyxBorder, RoundedCornerShape(17.dp))
            // The row opens the exercise; only the "+" adds it. Tapping a name to read about a
            // movement is the more common intent, and silently adding it instead is a surprise.
            .clickable(onClick = onOpen)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ExerciseImage(
            urls = exercise.imageUrls,
            contentDescription = exercise.name,
            phaseKey = exercise.id,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                exercise.name,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = (-0.2).sp),
                color = Chalk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip(exercise.bodyPart.displayName, muscle, tinted = true)
                MiniChip(exercise.difficulty.displayName, Ash, tinted = false)
            }
        }
        IconButton(onClick = onToggleFavourite, modifier = Modifier.size(34.dp)) {
            Icon(
                if (isFavourite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isFavourite) {
                    "Remove ${exercise.name} from favourites"
                } else {
                    "Add ${exercise.name} to favourites"
                },
                tint = if (isFavourite) Flame else Ash,
                modifier = Modifier.size(17.dp),
            )
        }
        // In-plan exercises show a filled orange ✓ tile; the rest a hollow orange "+" — the prototype's
        // add control. (The "×N / in plan" text is gone; the tile carries the state.)
        val added = timesAdded > 0
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                .then(
                    if (added) Modifier.background(Brush.horizontalGradient(listOf(FlameHot, FlameGlow)))
                    else Modifier.border(1.dp, Flame.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                )
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (added) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = if (added) "In plan — add another ${exercise.name}" else "Add ${exercise.name}",
                tint = if (added) Onyx else Flame,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The collapsible "Favourites" group header at the top of the picker. */
@Composable
private fun FavouritesHeader(count: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Filled.Bookmark, null, tint = Flame, modifier = Modifier.size(15.dp))
        Text("FAVOURITES", style = Eyebrow, color = Flame)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Ash)
        Box(Modifier.weight(1f))
        Icon(
            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (open) "Collapse favourites" else "Expand favourites",
            tint = Ash,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(text.uppercase(), style = Eyebrow, color = Ash, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/**
 * Per-muscle accent for the planned-exercise dot, from the redesign's palette. Coarse on purpose —
 * the gallery's body-part vocabulary is coarse — so it just colours the dot, not any load-bearing data.
 */
private fun muscleColor(bodyPart: BodyPart): Color = when (bodyPart) {
    BodyPart.BACK -> Color(0xFFFF6A1A)
    BodyPart.CHEST -> Color(0xFFFF8A3D)
    BodyPart.GLUTES -> Color(0xFFFFAB5C)
    BodyPart.CORE -> Color(0xFFC96A4A)
    BodyPart.LEGS -> Color(0xFFE0902F)
    else -> Color(0xFFFF8A3D)
}

/** A muscle/level tag under a picker row — tinted for the muscle, neutral for the level. */
@Composable
private fun MiniChip(text: String, color: Color, tinted: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (tinted) color else Ash,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (tinted) color.copy(alpha = 0.14f) else OnyxFill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/** A dashed capsule outline — the "add exercise" affordance's border, since border() can't dash. */
private fun Modifier.dashedCapsuleBorder(color: Color): Modifier = drawBehind {
    val r = size.height / 2f
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f), 0f)),
    )
}

/**
 * The redesign's filled input — an onyx box with a hairline border and an optional leading icon,
 * with a mono eyebrow supplied above it by the caller. A [BasicTextField] rather than an
 * `OutlinedTextField` so the height and fill match the prototype exactly.
 */
@Composable
private fun ProtoField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    radius: androidx.compose.ui.unit.Dp = 14.dp,
    imeAction: ImeAction = ImeAction.Done,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(radius))
            .background(OnyxFill).border(1.dp, OnyxBorder, RoundedCornerShape(radius))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading?.invoke()
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = AshFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Chalk),
                cursorBrush = SolidColor(Flame),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The prototype's −/value/+ stepper: a neutral minus and an orange-bordered plus flanking a value you
 * can still tap to type (via the shared [EditableNumber]), so the type-to-enter shortcut survives the
 * restyle.
 */
@Composable
private fun ProtoStepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    min: Int = 0,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StepBox("−", accent = false) { onChange((value - step).coerceAtLeast(min)) }
        EditableNumber(
            value = value,
            onChange = { onChange(it.coerceAtLeast(min)) },
            display = value.toString(),
            color = Chalk,
            textStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            modifier = Modifier.widthIn(min = 26.dp),
        )
        StepBox("+", accent = true) { onChange(value + step) }
    }
}

@Composable
private fun StepBox(symbol: String, accent: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
            .border(1.dp, if (accent) Flame.copy(alpha = 0.5f) else OnyxBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (accent) Flame else Chalk)
    }
}

/**
 * The redesign's flat card — a solid opaque [CardFlat] surface with a hairline border, replacing the
 * translucent/sheened GlassCard on these screens so cards read as crisp lifted surfaces (not glass) on
 * the near-black page. [accent] (a Build card that's open/dragging) lifts it to orange.
 */
@Composable
private fun FlatCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(CardFlat)
            .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
            .border(1.dp, accent?.copy(alpha = 0.5f) ?: OnyxBorder, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A tiny "?" that reveals a small tooltip popup right next to it — not a full-screen dialog. */
@Composable
private fun InfoDot(text: String) {
    var show by rememberSaveable { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(18.dp).clip(Capsule).border(1.dp, Ash.copy(alpha = 0.5f), Capsule).clickable { show = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
        if (show) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = IntOffset(0, 8),
                onDismissRequest = { show = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(10.dp))
                        .background(OnyxRaised).border(1.dp, OnyxBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Chalk)
                }
            }
        }
    }
}
