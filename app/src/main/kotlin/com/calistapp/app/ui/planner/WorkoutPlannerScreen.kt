package com.calistapp.app.ui.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.exercises.ExerciseFilterSheet
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.exercises.SortMenu
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Cream
import com.calistapp.app.ui.theme.CreamMuted
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.Violet
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
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
    viewModel: WorkoutPlannerViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    // Saveable rather than remembered: opening an exercise's detail screen takes this whole
    // composable out of composition, and a plain remember would drop you back on the plan instead of
    // the picker you were browsing when you come back.
    var picking by rememberSaveable { mutableStateOf(false) }

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
                color = Cream,
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No exercises yet", style = MaterialTheme.typography.titleMedium, color = Cream)
                    Text(
                        "Add the movements you plan to do — the tracker uses them to score each set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CreamMuted,
                    )
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
                color = Emerald,
            )
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plan.exercises, key = { it.slotId }) { slot ->
                    PlannedExerciseCard(
                        slot = slot,
                        target = viewModel.targetOf(slot),
                        showSets = !plan.isCircuit,
                        imageUrls = thumbnails[slot.exerciseId].orEmpty(),
                        onOpen = { onOpenExercise(slot.exerciseId) },
                        onSets = { viewModel.setSets(slot.slotId, it) },
                        onTarget = { viewModel.setTarget(slot.slotId, it) },
                        onToggleMeasure = { viewModel.toggleMeasure(slot.slotId) },
                        onToggleWeighted = { viewModel.toggleWeighted(slot.slotId) },
                        onWeight = { viewModel.setAddedWeight(slot.slotId, it) },
                        onMove = { viewModel.move(slot.slotId, it) },
                        onRemove = { viewModel.remove(slot.slotId) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
            shape = Capsule,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Add exercise")
        }
        Button(
            onClick = { viewModel.startWorkout(); onStarted() },
            enabled = !plan.isEmpty,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = Capsule,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
        ) {
            Text("Start workout", fontWeight = FontWeight.Bold)
        }
        Box(Modifier.height(8.dp))
    }
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
                    accent = if (s == WorkoutStyle.CIRCUIT) Violet else Emerald,
                    onClick = { onStyle(s) },
                )
            }
        }
        AnimatedVisibility(visible = style == WorkoutStyle.CIRCUIT) {
            Column {
                Text(
                    "One set of every exercise, then round again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted,
                )
                Stepper("Rounds", rounds, onRounds)
            }
        }
    }
}

@Composable
private fun PlannedExerciseCard(
    slot: PlannedExercise,
    target: Int,
    showSets: Boolean,
    imageUrls: List<String>,
    onOpen: () -> Unit,
    onSets: (Int) -> Unit,
    onTarget: (Int) -> Unit,
    onToggleMeasure: () -> Unit,
    onToggleWeighted: () -> Unit,
    onWeight: (Double) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    // Collapsed by default: a plan is a list you scan, and eight cards of steppers is unreadable.
    // Tapping the row opens the controls for that one exercise. Saveable so the card stays as you
    // left it when the list is rebuilt — scrolled out of view, or returned to from the picker.
    var expanded by rememberSaveable { mutableStateOf(false) }

    GlassCard(accent = if (slot.isWeighted) Amber else null, contentPadding = 12) {
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
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${slot.bodyPart.displayName} · ${slot.targetLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = CreamMuted,
                modifier = Modifier.size(20.dp),
            )
            IconButton(onClick = { onMove(-1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMove(1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = Coral, modifier = Modifier.size(18.dp))
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (showSets) Stepper("Sets", slot.targetSets, onSets)
                    Stepper(
                        label = if (slot.measure == ExerciseMeasure.SECONDS) "Seconds" else "Reps",
                        value = target,
                        onChange = onTarget,
                        step = if (slot.measure == ExerciseMeasure.SECONDS) 5 else 1,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip(
                        label = "Added weight",
                        selected = slot.isWeighted,
                        accent = Amber,
                        leading = {
                            Icon(
                                Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        onClick = onToggleWeighted,
                    )
                }
                if (slot.isWeighted) {
                    Stepper(
                        label = "Kilograms",
                        value = slot.addedWeightKg.toInt(),
                        onChange = { onWeight(it.toDouble()) },
                        step = 5,
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

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, step: Int = 1) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - step) }) {
                Icon(Icons.Filled.Remove, "Decrease $label")
            }
            Text("$value", style = MaterialTheme.typography.titleMedium, color = Cream, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onChange(value + step) }) {
                Icon(Icons.Filled.Add, "Increase $label")
            }
        }
    }
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
                color = Cream,
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
                    // Adding closes the picker: you came here to add one thing, and being returned
                    // to the plan confirms it landed. Reopening is one tap.
                    onAdd = { viewModel.add(exercise); onDone() },
                    onOpen = { onOpenExercise(exercise.id) },
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing matches that. Try a shorter search, or loosen the filters.",
                        color = CreamMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(exercise: Exercise, onAdd: () -> Unit, onOpen: () -> Unit) {
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
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.bodyPart.displayName} · ${exercise.difficulty.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add ${exercise.name}", tint = Emerald)
            }
        }
    }
}
