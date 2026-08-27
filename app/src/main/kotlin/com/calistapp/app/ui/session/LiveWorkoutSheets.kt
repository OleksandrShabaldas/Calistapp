package com.calistapp.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.NumberPadSheet
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.core.model.EffortScale
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.formatKg

/**
 * Effort entry — the shared numpad with the three scales as tabs and the `?` explainer wired to the
 * selected scale's blurb. Returns the chosen scale and value together.
 */
@Composable
fun EffortInputSheet(
    initialScale: EffortScale?,
    initialValue: Int?,
    onConfirm: (EffortScale, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scales = EffortScale.entries
    var tab by remember { mutableStateOf(initialScale?.ordinal ?: 0) }
    NumberPadSheet(
        title = "Effort",
        initial = initialValue ?: 0,
        tabs = scales.map { it.label },
        selectedTab = tab,
        onSelectTab = { tab = it },
        help = scales[tab].blurb,
        onConfirm = { v -> onConfirm(scales[tab], v) },
        onDismiss = onDismiss,
    )
}

/**
 * The read-only "this exercise" detail, revealed by dragging the live sheet up: the target for the
 * set, how you did it last time, your best, and the movement's form cues. Everything here is context
 * to help the set you're about to do — nothing is editable (that's the Journal's job). Rendered inline
 * inside the sheet now, rather than as a separate modal, so the pull-up expands one card in place.
 */
@Composable
fun ThisExerciseContent(
    exercise: Exercise?,
    planned: PlannedExercise?,
    history: ExerciseHistoryStat?,
    nowMs: Long,
) {
    Text(
        planned?.displayName ?: exercise?.name ?: "Exercise",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Chalk,
    )
    if (planned != null) {
        Text("Target · ${planned.targetLabel}", style = MaterialTheme.typography.bodyMedium, color = Flame)
    }

    if (history != null) {
        Section("Last time") {
            Text(
                history.lastSets.joinToString("   ") { s ->
                    buildString {
                        append(s.reps)
                        if (s.weightKg > 0) append(" · ${formatKg(s.weightKg)}kg")
                        s.effortLabel?.let { append(" · $it") }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk,
            )
            Text(relativeDays(history.lastWhenMs, nowMs), style = MaterialTheme.typography.labelMedium, color = Ash)
        }
        Section("Best") {
            Text(
                buildString {
                    append("${history.bestReps} reps")
                    if (history.bestWeightKg > 0) append("  ·  +${formatKg(history.bestWeightKg)} kg")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Chalk,
            )
        }
    }

    exercise?.tips?.takeIf { it.isNotEmpty() }?.let { tips ->
        Section("Form cues") { tips.forEach { Bullet(it) } }
    }
    exercise?.commonMistakes?.takeIf { it.isNotEmpty() }?.let { mistakes ->
        Section("Common mistakes") { mistakes.forEach { Bullet(it) } }
    }
    exercise?.primaryMuscles?.takeIf { it.isNotEmpty() }?.let { muscles ->
        Section("Primary muscles") {
            Text(muscles.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Chalk)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Ash)
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium, color = Flame)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Chalk)
    }
}

/** "Today", "Yesterday", "4 days ago", "3 weeks ago" — a light relative time for the last-performed line. */
private fun relativeDays(thenMs: Long, nowMs: Long): String {
    val days = ((nowMs - thenMs) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        else -> "${days / 30} months ago"
    }
}
