package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.progress.TimelineExercise

/**
 * One exercise, opened from its row on the summary. Shows the sets as performed — reps, time under
 * tension, and the rest taken after each — the energy it cost, the average heart rate while working
 * it, and lets a miscounted (or unlogged) set be corrected.
 *
 * The correction matters more than it looks: reps feed the mechanical-work floor, so a set left at
 * zero is silently costing that block its energy. Edits are gathered locally and applied in one go —
 * [onApplyEdits] rescores the whole session — rather than rewriting the session on every tap.
 */
@Composable
fun ExerciseDetailOverlay(
    exercise: TimelineExercise,
    segments: List<Segment>,
    samples: List<HeartRateSample>,
    delta: Int?,
    onApplyEdits: (Map<Long, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sets = remember(segments) { segments.sortedBy { it.startMs } }
    var editing by remember(segments) { mutableStateOf(false) }
    val edits = remember(segments) { mutableStateMapOf<Long, Int>() }

    val avgWorkHr = remember(segments, samples) {
        val inWork = samples.filter { s -> sets.any { s.timestampMs in it.startMs..(it.endMs ?: it.startMs) } }
        if (inWork.isEmpty()) null else inWork.map { it.bpm }.average().toInt()
    }

    SummaryOverlay(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, color = Chalk)
            Text(
                "${exercise.sets} ${if (exercise.sets == 1) "set" else "sets"} · ${exercise.reps} reps",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        // Headline stats.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${exercise.kcal.toInt()}", "kcal", Flame)
            Stat(formatCompact(exercise.spanMs), "on it", Chalk)
            Stat(formatCompact(exercise.activeMs), "working", Amber)
            if (avgWorkHr != null) Stat("$avgWorkHr", "avg hr", Coral)
        }

        if (delta != null && delta != 0) {
            val up = delta > 0
            Text(
                (if (up) "▲ " else "▼ ") + "${kotlin.math.abs(delta)} reps ${if (up) "more" else "fewer"} than last time",
                style = MaterialTheme.typography.bodyMedium,
                color = if (up) Amber else Coral,
            )
        }

        if (exercise.restBeforeMs > 0) {
            Text(
                "Rested ${formatCompact(exercise.restBeforeMs)} before starting.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }

        // Set-by-set.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SETS", style = MaterialTheme.typography.labelSmall, color = Flame, fontWeight = FontWeight.Bold)
            sets.forEachIndexed { i, seg ->
                val reps = edits[seg.startMs] ?: seg.reps
                val restAfter = sets.getOrNull(i + 1)?.let { it.startMs - (seg.endMs ?: seg.startMs) }?.takeIf { it > 0 }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Set ${i + 1}", style = MaterialTheme.typography.titleSmall, color = Chalk)
                        Text(
                            buildString {
                                append(formatCompact((seg.endMs ?: seg.startMs) - seg.startMs))
                                if (restAfter != null) append(" · ${formatCompact(restAfter)} rest")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Ash,
                        )
                    }
                    if (editing) {
                        IconButton(onClick = { edits[seg.startMs] = (reps - 1).coerceAtLeast(0) }, enabled = reps > 0) {
                            Icon(Icons.Filled.Remove, "One fewer rep", tint = Chalk)
                        }
                    }
                    Text(
                        if (reps > 0) "$reps reps" else "not logged",
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            reps != seg.reps -> Amber
                            reps > 0 -> Chalk
                            else -> Ash
                        },
                        fontWeight = if (reps != seg.reps) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (editing) {
                        IconButton(onClick = { edits[seg.startMs] = reps + 1 }) {
                            Icon(Icons.Filled.Add, "One more rep", tint = Chalk)
                        }
                    }
                }
            }
        }

        if (editing) {
            Text(
                "Saving rescores the session — reps feed the calorie estimate, and it's recomputed " +
                    "with your current body data.",
                style = MaterialTheme.typography.labelSmall,
                color = Ash,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { edits.clear(); editing = false }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApplyEdits(edits.toMap()); onDismiss() },
                    enabled = edits.any { (start, r) -> sets.firstOrNull { it.startMs == start }?.reps != r },
                    modifier = Modifier.weight(1f),
                ) { Text("Save changes") }
            }
        } else {
            TextButton(onClick = { editing = true }) { Text("Fix a rep count", color = FlameHot) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}
