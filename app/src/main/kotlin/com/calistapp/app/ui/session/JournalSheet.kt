package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calistapp.app.session.LiveSession
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.exercises.ExerciseImage
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxFillStrong
import com.calistapp.app.ui.theme.OnyxRaised
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.formatKg

/** Which cell of the journal is being edited. */
private sealed interface JournalEdit {
    val slotId: String
    val setIndex: Int

    data class Reps(override val slotId: String, override val setIndex: Int, val current: Int) : JournalEdit
    data class Weight(override val slotId: String, override val setIndex: Int, val current: Int) : JournalEdit
    data class Effort(override val slotId: String, override val setIndex: Int, val scale: EffortScale?, val value: Int?) : JournalEdit
    data class Note(override val slotId: String, override val setIndex: Int, val current: String) : JournalEdit
}

/**
 * The compact journal: a slim row per exercise, expanding to a dense per-set grid (result / weight /
 * effort) plus notes. Every figure taps into the shared numpad or effort input; edits go straight to
 * the live session's set logs. Slides up from the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSheet(
    live: LiveSession,
    imageUrlsFor: (String) -> List<String>,
    onSetReps: (String, Int, Int) -> Unit,
    onSetWeight: (String, Int, Double) -> Unit,
    onSetEffort: (String, Int, EffortScale, Int) -> Unit,
    onSetNote: (String, Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<JournalEdit?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OnyxRaised,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Journal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Chalk, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(Capsule).border(1.dp, OnyxBorder, Capsule).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.Check, null, tint = Flame, modifier = Modifier.size(15.dp))
                    Text("Done", style = MaterialTheme.typography.labelLarge, color = Flame)
                }
            }

            live.plan.exercises.forEach { slot ->
                JournalExerciseRow(
                    slot = slot,
                    total = live.plan.targetSetsFor(slot.slotId),
                    sets = live.setLogs.filter { it.slotId == slot.slotId }.sortedBy { it.setIndex },
                    imageUrls = imageUrlsFor(slot.exerciseId),
                    onEdit = { editing = it },
                )
            }
        }
    }

    when (val e = editing) {
        is JournalEdit.Reps -> NumberPadSheet(
            title = "Result",
            initial = e.current,
            onConfirm = { onSetReps(e.slotId, e.setIndex, it); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Weight -> NumberPadSheet(
            title = "Added weight",
            initial = e.current,
            unit = "kg",
            onConfirm = { onSetWeight(e.slotId, e.setIndex, it.toDouble()); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Effort -> EffortInputSheet(
            initialScale = e.scale,
            initialValue = e.value,
            onConfirm = { s, v -> onSetEffort(e.slotId, e.setIndex, s, v); editing = null },
            onDismiss = { editing = null },
        )
        is JournalEdit.Note -> NoteDialog(
            initial = e.current,
            onConfirm = { onSetNote(e.slotId, e.setIndex, it); editing = null },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun JournalExerciseRow(
    slot: PlannedExercise,
    total: Int,
    sets: List<SetLog>,
    imageUrls: List<String>,
    onEdit: (JournalEdit) -> Unit,
) {
    var expanded by rememberSaveable(slot.slotId) { mutableStateOf(sets.isNotEmpty()) }
    val done = sets.size >= total && total > 0

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OnyxFillStrong.copy(alpha = 0.5f))
            .clickable { expanded = !expanded }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseImage(
                urls = imageUrls,
                contentDescription = slot.name,
                animate = false,
                phaseKey = slot.slotId,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(slot.name, style = MaterialTheme.typography.bodyLarge, color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(slot.targetLabel, style = MaterialTheme.typography.labelSmall, color = Ash)
            }
            if (done) Icon(Icons.Filled.Check, "Done", tint = Flame, modifier = Modifier.size(18.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null, tint = Ash, modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sets.isEmpty()) {
                    Text("No sets logged yet.", style = MaterialTheme.typography.labelMedium, color = Ash)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(20.dp))
                        GridLabel("RESULT", Modifier.weight(1f))
                        GridLabel("WEIGHT", Modifier.weight(1f))
                        GridLabel("EFFORT", Modifier.weight(1f))
                    }
                    sets.forEach { s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.setIndex}", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                            EditCell("${s.reps}", filled = true, Modifier.weight(1f)) {
                                onEdit(JournalEdit.Reps(s.slotId, s.setIndex, s.reps))
                            }
                            EditCell(if (s.weightKg > 0) "${formatKg(s.weightKg)}kg" else "—", filled = s.weightKg > 0, Modifier.weight(1f)) {
                                onEdit(JournalEdit.Weight(s.slotId, s.setIndex, s.weightKg.toInt()))
                            }
                            // Show the logged effort, or — until it's rated — the plan's target for
                            // this set as a dim "→ 8 RPE" hint, which also pre-fills the editor.
                            val planned = slot.sets().getOrNull(s.setIndex - 1)?.effort
                            val effortText = s.effortLabel ?: planned?.let { "→ ${it.label}" } ?: "—"
                            EditCell(effortText, filled = s.effortLabel != null, Modifier.weight(1f)) {
                                onEdit(
                                    JournalEdit.Effort(
                                        s.slotId,
                                        s.setIndex,
                                        s.effortScale ?: planned?.scale,
                                        s.effortValue?.toInt() ?: planned?.value?.toInt(),
                                    ),
                                )
                            }
                        }
                        if (s.note.isNotBlank()) {
                            Text(
                                "“${s.note}”",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ash,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEdit(JournalEdit.Note(s.slotId, s.setIndex, s.note)) }
                                    .padding(start = 26.dp),
                            )
                        }
                    }
                    val lastSet = sets.last()
                    if (lastSet.note.isBlank()) {
                        Text(
                            "+ Note",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ash,
                            modifier = Modifier
                                .clickable { onEdit(JournalEdit.Note(lastSet.slotId, lastSet.setIndex, lastSet.note)) }
                                .padding(start = 26.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Ash, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun EditCell(value: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (filled) Flame.copy(alpha = 0.45f) else OnyxBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, color = if (filled) Chalk else Ash)
    }
}

@Composable
private fun NoteDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("How did it feel?") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("Save", color = Flame) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
