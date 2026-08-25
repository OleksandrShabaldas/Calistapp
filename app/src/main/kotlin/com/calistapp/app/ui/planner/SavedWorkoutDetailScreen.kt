package com.calistapp.app.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.GlassCard
import com.calistapp.app.ui.common.SectionHeading
import com.calistapp.app.ui.common.SessionRow
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.core.model.PlannedExercise

/**
 * A saved workout as its own screen: exercises, the history it's produced, and Start / Edit / Delete.
 */
@Composable
fun SavedWorkoutDetailScreen(
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: SavedWorkoutDetailViewModel = hiltViewModel(),
) {
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the saved workout. Sessions you've already run from it stay in your history.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(); onDeleted() }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    val w = workout
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = Ash,
                    modifier = Modifier.size(30.dp).clip(Capsule).clickable(onClick = onBack).padding(3.dp),
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        w?.name ?: "Workout",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (w != null) {
                        Text(w.summaryLabel, style = MaterialTheme.typography.bodyMedium, color = Ash)
                    }
                }
            }
        }

        if (w == null) {
            item {
                GlassCard { Text("This workout is no longer available.", color = Ash) }
            }
            return@LazyColumn
        }

        // Actions.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.loadIntoDraft(); viewModel.markUsed(); onStart() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = Capsule,
                    colors = ButtonDefaults.buttonColors(containerColor = Flame),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Onyx, modifier = Modifier.size(20.dp))
                    Text("  Start", fontWeight = FontWeight.Bold, color = Onyx)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loadIntoDraft(); onEdit() },
                        modifier = Modifier.weight(1f),
                        shape = Capsule,
                    ) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp))
                        Text("  Edit")
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                        shape = Capsule,
                    ) {
                        Icon(Icons.Filled.DeleteOutline, null, tint = Coral, modifier = Modifier.size(18.dp))
                        Text("  Delete", color = Coral)
                    }
                }
            }
        }

        item { SectionHeading("Exercises", count = w.plan.exercises.size) }
        items(w.plan.exercises, key = { it.slotId }) { slot ->
            ExerciseSummaryRow(
                slot = slot,
                imageUrls = thumbnails[slot.exerciseId].orEmpty(),
                onClick = { onOpenExercise(slot.exerciseId) },
            )
        }

        item { SectionHeading("History", count = history.size.takeIf { it > 0 }) }
        if (history.isEmpty()) {
            item {
                GlassCard {
                    Text("Not run yet", style = MaterialTheme.typography.titleMedium, color = Chalk)
                    Text(
                        "Start it, and every session you run from it lands here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ash,
                    )
                }
            }
        } else {
            items(history, key = { it.id }) { session ->
                SessionRow(session = session, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

@Composable
private fun ExerciseSummaryRow(
    slot: PlannedExercise,
    imageUrls: List<String>,
    onClick: () -> Unit,
) {
    GlassCard(contentPadding = 10, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseImage(
                urls = imageUrls,
                contentDescription = slot.name,
                animate = false,
                phaseKey = slot.slotId,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f)) {
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
        }
    }
}
