package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calistapp.app.ui.common.HeartRateChart
import com.calistapp.app.ui.common.KeyValueRow
import com.calistapp.app.ui.common.PillChip
import com.calistapp.app.ui.common.SectionCard
import com.calistapp.app.ui.common.StatTile
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.common.formatDate
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Emerald
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HrRecovery
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val audit by viewModel.audit.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = {
                Text(
                    "The heart-rate recording, every set you logged, and the calorie breakdown go " +
                        "with it. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteSession(onBack) }) {
                    Text("Delete", color = Coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.exerciseType?.displayName ?: "Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = session
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val s = current.summary ?: SessionSummary.EMPTY

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(formatDate(current.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            current.exerciseName?.let { name ->
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            SectionCard {
                Text(
                    "${s.totalKcal.toInt()} kcal",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("Active burn", "${s.activeKcal.toInt()} kcal", accent = Emerald)
                    StatTile("Rest burn", "${s.restKcal.toInt()} kcal", accent = Sky)
                    StatTile("Work ratio", "${(s.activeRatio * 100).toInt()}%", accent = Amber)
                }
            }

            // Directly under the number it explains: the whole point is that the figure above isn't
            // asking to be taken on faith.
            val currentAudit = audit
            if (currentAudit != null) {
                CalorieBreakdownCard(audit = currentAudit, storedKcal = current.summary?.totalKcal)
            } else if (current.samples.isEmpty()) {
                // Nothing to explain, and a bare 0 with no reason reads as the app being broken.
                SectionCard(title = "No heart rate recorded") {
                    Text(
                        "This session has no heart-rate readings, so there was nothing for the " +
                            "calorie engine to integrate and it is filed at 0 kcal. The watch was " +
                            "either not connected or not streaming while it ran. Sets and reps below " +
                            "were still recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = "Duration") {
                KeyValueRow("Total", formatClock(s.totalDurationMs))
                KeyValueRow("Active", formatCompact(s.activeDurationMs))
                KeyValueRow("Rest", formatCompact(s.restDurationMs))
            }

            // Where the energy actually went — the payoff of tracking exercises and reps.
            if (s.perExercise.isNotEmpty()) {
                SectionCard(title = "By exercise") {
                    s.perExercise.forEach { row ->
                        KeyValueRow(
                            row.exerciseName,
                            "${row.kcal.toInt()} kcal · ${row.sets} × · ${row.reps} reps",
                        )
                    }
                    if (s.totalReps > 0) {
                        KeyValueRow("Total reps", "${s.totalReps}")
                    }
                }
            }

            if (!current.plan.isEmpty) {
                SectionCard(title = "Planned vs performed") {
                    current.plan.exercises.forEach { slot ->
                        val done = current.setLogs.filter { it.slotId == slot.slotId }
                        KeyValueRow(
                            slot.name,
                            "${done.size}/${slot.targetSets} sets · ${done.sumOf { it.reps }} reps " +
                                "(target ${slot.targetLabel})",
                        )
                    }
                }
            }

            SetsPerformedCard(
                segments = current.segments,
                onApply = viewModel::applySetEdits,
            )

            SectionCard(title = "Heart rate") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("Average", "${s.avgHr}")
                    StatTile("Active avg", "${s.avgActiveHr}", accent = Emerald)
                    StatTile("Peak", "${s.peakHr}", accent = Coral)
                    StatTile("Min", "${s.minHr}", accent = Sky)
                }
            }

            if (current.samples.size >= 2) {
                SectionCard(title = "Heart rate over time") {
                    HeartRateChart(
                        samples = current.samples,
                        segments = current.segments,
                        avgHr = s.avgHr,
                    )
                }
            }

            if (s.timeInZonesMs.isNotEmpty()) {
                SectionCard(title = "Time in HR zones") {
                    HrZoneBars(s)
                }
            }

            s.hrRecovery?.let { RecoveryCard(it) }

            RpeCard(rpe = current.rpe, onChange = viewModel::setRpe)

            NotesCard(saved = current.notes, onChange = viewModel::setNotes)

            AiCoachCard(
                insight = current.aiInsight,
                aiState = aiState,
                onGenerate = viewModel::generateInsight,
            )

            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete session", color = Coral)
            }
        }
    }
}

/**
 * The sets as performed, and a way to fix them.
 *
 * A rep count you got wrong, or a set you forgot to log at all, isn't just a cosmetic error — reps
 * feed the mechanical-work floor, so an unlogged set is silently costing that block its energy.
 * Edits are collected locally and applied in one go: each change would otherwise mean reloading the
 * session, rescoring it and writing it back, on every tap of a stepper.
 */
@Composable
private fun SetsPerformedCard(segments: List<Segment>, onApply: (Map<Long, Int>) -> Unit) {
    val performed = remember(segments) { segments.filter { it.type == SegmentType.ACTIVE } }
    if (performed.isEmpty()) return

    // Both keyed on the segments, and neither saveable: a save produces new segments and clears the
    // pending edits, and a rotation drops them together rather than leaving edit mode open over an
    // empty set of changes.
    var editing by remember(segments) { mutableStateOf(false) }
    val edits = remember(segments) { mutableStateMapOf<Long, Int>() }

    SectionCard(title = "Sets performed") {
        val counts = mutableMapOf<String, Int>()
        performed.forEach { segment ->
            val name = segment.exerciseName ?: "Work"
            val index = (counts[name] ?: 0) + 1
            counts[name] = index
            val reps = edits[segment.startMs] ?: segment.reps

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$name · set $index", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatCompact(segment.endMs?.minus(segment.startMs) ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editing) {
                    IconButton(
                        onClick = { edits[segment.startMs] = (reps - 1).coerceAtLeast(0) },
                        enabled = reps > 0,
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "One fewer rep")
                    }
                }
                Text(
                    if (reps > 0) "$reps reps" else "not logged",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (reps != segment.reps) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        reps != segment.reps -> Amber
                        reps > 0 -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (editing) {
                    IconButton(onClick = { edits[segment.startMs] = reps + 1 }) {
                        Icon(Icons.Filled.Add, contentDescription = "One more rep")
                    }
                }
            }
        }

        if (editing) {
            Text(
                "Saving rescores the session — reps feed the calorie estimate. It's recalculated " +
                    "with the body data in your profile as it is now.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { edits.clear(); editing = false }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onApply(edits.toMap()); editing = false },
                    enabled = edits.any { (start, reps) ->
                        performed.firstOrNull { it.startMs == start }?.reps != reps
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save changes")
                }
            }
        } else {
            TextButton(onClick = { editing = true }) { Text("Fix a rep count") }
        }
    }
}

/**
 * How hard it felt, 1–10.
 *
 * The one signal no sensor produces. Heart rate reports what your circulation did; this reports what
 * it cost, and the two diverge exactly where the estimate is weakest — heat, poor sleep, heavy grip
 * work. Tapping the selected value again clears it, so a mis-tap isn't permanent.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RpeCard(rpe: Int?, onChange: (Int?) -> Unit) {
    SectionCard(title = "How hard did it feel?") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { value ->
                PillChip(
                    label = "$value",
                    selected = rpe == value,
                    accent = rpeAccent(value),
                    onClick = { onChange(if (rpe == value) null else value) },
                )
            }
        }
        Text(
            rpe?.let { "$it — ${rpeLabel(it)}" }
                ?: "Unrated. 1 is barely anything, 10 is all you had.",
            style = MaterialTheme.typography.bodySmall,
            color = if (rpe == null) MaterialTheme.colorScheme.onSurfaceVariant else rpeAccent(rpe),
        )
    }
}

private fun rpeLabel(rpe: Int): String = when (rpe) {
    1, 2 -> "very easy"
    3, 4 -> "easy"
    5, 6 -> "moderate"
    7, 8 -> "hard"
    9 -> "very hard"
    else -> "maximal"
}

private fun rpeAccent(rpe: Int): Color = when {
    rpe <= 4 -> Sky
    rpe <= 6 -> Emerald
    rpe <= 8 -> Amber
    else -> Coral
}

/**
 * Free-text note on the session — how it felt, what hurt, what to change next time.
 *
 * Seeded once from the stored value and owned locally after that: re-seeding on every emission of
 * the session flow would overwrite the sentence being typed with the one already saved.
 */
@Composable
private fun NotesCard(saved: String, onChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    var hydrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) {
        if (!hydrated) {
            text = saved
            hydrated = true
        }
    }

    SectionCard(title = "Notes") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("How did it feel? Anything to change next time?") },
        )
        Text(
            "Saved as you type, and read by the AI coach when it reviews this session.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How fast the heart came back down after sets.
 *
 * Free evidence from data already collected: the app knows the exact instant each set ended, which
 * is the measurement other trackers have to guess at. Bands follow the usual reading — under ~12 bpm
 * in the first minute is the clinically blunted threshold, 20+ is unremarkable for trained people.
 */
@Composable
private fun RecoveryCard(recovery: HrRecovery) {
    val accent = when {
        recovery.meanDropBpm >= 25 -> Emerald
        recovery.meanDropBpm >= 12 -> Sky
        else -> Amber
    }

    SectionCard(title = "Heart-rate recovery") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("Average drop", "${recovery.meanDropBpm} bpm", accent = accent)
            StatTile("Best", "${recovery.bestDropBpm} bpm", accent = Emerald)
            StatTile("Rests measured", "${recovery.measuredRests}")
        }
        Text(
            when {
                recovery.meanDropBpm >= 25 ->
                    "A strong drop in the first minute after your sets — that's a well-conditioned " +
                        "recovery response."
                recovery.meanDropBpm >= 12 ->
                    "A normal drop in the first minute after your sets."
                else ->
                    "A slower drop than usual. One session doesn't mean much — fatigue, heat, " +
                        "caffeine and poor sleep all blunt it. Worth watching if it persists."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Measured from your peak heart rate at the end of a set to one minute into the rest " +
                "that followed, across rests long enough to count.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HrZoneBars(s: SessionSummary) {
    val zoneColors = mapOf(
        HrZone.ZONE1 to Sky, HrZone.ZONE2 to Emerald, HrZone.ZONE3 to Amber,
        HrZone.ZONE4 to Color(0xFFF97316), HrZone.ZONE5 to Coral,
    )
    val maxMs = s.timeInZonesMs.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HrZone.entries.forEach { zone ->
            val ms = s.timeInZonesMs[zone] ?: 0L
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zone.name.removePrefix("ZONE"), modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (ms.toFloat() / maxMs).coerceIn(0f, 1f))
                            .height(16.dp)
                            .background(zoneColors[zone] ?: Emerald, RoundedCornerShape(8.dp)),
                    )
                }
                Text(formatCompact(ms), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AiCoachCard(
    insight: String?,
    aiState: AiUiState,
    onGenerate: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("AI Coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        when (aiState) {
            is AiUiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Text("Analyzing your session…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is AiUiState.Error -> Text(
                aiState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )

            AiUiState.Idle -> Unit
        }

        if (insight != null) {
            Text(insight, style = MaterialTheme.typography.bodyMedium)
        } else if (aiState !is AiUiState.Loading) {
            Text(
                "Get personalized feedback and recommendations based on this session's heart-rate and effort data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (aiState !is AiUiState.Loading) {
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                Text(if (insight == null) "Analyze with AI" else "Regenerate analysis")
            }
        }
    }
}
