package com.calistapp.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.common.HeartRateChart
import com.calistapp.app.ui.common.formatCompact
import com.calistapp.app.ui.common.hrZoneColor
import com.calistapp.app.ui.dashboard.CardBorder
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.progress.TimelineExercise

// ---------- Heart rate ----------

@Composable
internal fun HeartRateSection(
    summary: SessionSummary,
    samples: List<HeartRateSample>,
    segments: List<Segment>,
    maxHr: Int,
    showRest: Boolean,
    onToggleRest: () -> Unit,
) {
    FlatCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HrStat("${summary.peakHr}", "Peak", Coral)
            HrStat("${summary.avgHr}", "Average", Chalk)
            HrStat("${summary.avgActiveHr}", "Active", Flame)
            HrStat("${summary.minHr}", "Min", Sky)
        }
        if (samples.size >= 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("BPM over time")
                RestToggle(showRest, onToggleRest)
            }
            HeartRateChart(
                samples = samples,
                segments = segments,
                avgHr = summary.avgHr,
                maxHr = maxHr,
                showRest = showRest,
            )
        }
    }
}

@Composable
private fun HrStat(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun RestToggle(on: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .clip(shape)
            .then(if (on) Modifier.background(Flame.copy(alpha = 0.12f)) else Modifier)
            .border(1.dp, if (on) Flame.copy(alpha = 0.4f) else CardBorder, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(12.dp, 9.dp).clip(RoundedCornerShape(2.dp)).background(Sky.copy(alpha = if (on) 0.6f else 0.25f)))
        Text("Rest zones", style = MaterialTheme.typography.labelMedium, color = if (on) FlameHot else Ash)
    }
}

// ---------- Time in zones ----------

@Composable
internal fun ZonesCard(summary: SessionSummary) {
    val maxMs = summary.timeInZonesMs.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    FlatCard {
        Text("Time in zones", style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            HrZone.entries.forEach { zone ->
                val ms = summary.timeInZonesMs[zone] ?: 0L
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(zone.name.removePrefix("ZONE"), Modifier.width(14.dp), style = MaterialTheme.typography.labelMedium, color = Ash)
                    Text(zone.label, Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium, color = Ash)
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.05f)),
                    ) {
                        val frac = (ms.toFloat() / maxMs).coerceIn(0f, 1f)
                        if (frac > 0f) {
                            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(999.dp)).background(hrZoneColor(zone)))
                        }
                    }
                    Text(
                        if (ms > 0) formatCompact(ms) else "—",
                        Modifier.width(50.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Ash,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// ---------- Recovery (row → popup) ----------

@Composable
internal fun RecoveryRow(meanDropBpm: Int, onClick: () -> Unit) {
    FlatCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Heart-rate recovery", style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.SemiBold)
                Text("How fast your heart came back down", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
            Text("$meanDropBpm", style = MaterialTheme.typography.headlineSmall, color = FlameHot, fontWeight = FontWeight.Bold)
            Text(" bpm/min ›", style = MaterialTheme.typography.labelMedium, color = Ash)
        }
    }
}

// ---------- Exercises ----------

@Composable
internal fun ExercisesSection(
    timeline: List<TimelineExercise>,
    skipped: List<PlannedExercise>,
    deltas: Map<String, Int>,
    plan: WorkoutPlan,
    onRowClick: (TimelineExercise) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(com.calistapp.app.ui.dashboard.CardSurface)
            .border(1.dp, CardBorder, shape),
    ) {
        timeline.forEachIndexed { i, ex ->
            if (i > 0 && ex.restBeforeMs > 0) RestDivider(ex.restBeforeMs)
            else if (i > 0) HairlineDivider()
            ExerciseRow(
                index = i,
                ex = ex,
                weighted = (plan.slot(ex.key)?.addedWeightKg ?: 0.0) > 0.0,
                weightText = plan.slot(ex.key)?.addedWeightKg?.takeIf { it > 0.0 }?.let { "+${it.toInt()} kg" },
                delta = deltas[ex.name],
                onClick = { onRowClick(ex) },
            )
        }
        skipped.forEach { slot ->
            HairlineDivider()
            SkippedRow(slot)
        }
        HairlineDivider()
        val totalReps = timeline.sumOf { it.reps }
        val totalKcal = timeline.sumOf { it.kcal }.toInt()
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.02f)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Total", style = MaterialTheme.typography.labelLarge, color = Ash)
            Text("$totalReps reps · $totalKcal kcal", style = MaterialTheme.typography.titleSmall, color = Chalk, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRow(
    index: Int,
    ex: TimelineExercise,
    weighted: Boolean,
    weightText: String?,
    delta: Int?,
    onClick: () -> Unit,
) {
    val color = exerciseColor(index)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.titleMedium, color = Chalk, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "${ex.sets} ${if (ex.sets == 1) "set" else "sets"} · ${ex.reps} reps · ${formatCompact(ex.spanMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ash,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (weighted && weightText != null) Chip(weightText, FlameHot, Flame.copy(alpha = 0.12f))
                if (delta != null && delta != 0) {
                    val up = delta > 0
                    Chip(
                        (if (up) "▲ " else "▼ ") + kotlin.math.abs(delta),
                        if (up) Color(0xFFFF9A5C) else Coral,
                        (if (up) Amber else Coral).copy(alpha = 0.12f),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${ex.kcal.toInt()}", style = MaterialTheme.typography.titleMedium, color = FlameHot, fontWeight = FontWeight.Bold)
            Text("KCAL", style = MaterialTheme.typography.labelSmall, color = Ash)
        }
    }
}

@Composable
private fun SkippedRow(slot: PlannedExercise) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(SkippedColor))
        Column(Modifier.weight(1f)) {
            Text(slot.name, style = MaterialTheme.typography.titleMedium, color = SkippedText, fontWeight = FontWeight.SemiBold)
            Text("Skipped · 0 of ${slot.targetSets} sets", style = MaterialTheme.typography.labelMedium, color = Ash)
        }
        Text("—", style = MaterialTheme.typography.titleMedium, color = Ash)
    }
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(bg).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
}

/** The rest taken between two exercises, shown as a slim centred marker between their rows. */
@Composable
private fun RestDivider(restMs: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
        Text(
            "rested ${formatCompact(restMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = Sky.copy(alpha = 0.85f),
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.05f)))
    }
}

// ---------- Notes ----------

@Composable
internal fun NotesSection(saved: String, onChange: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    var hydrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) {
        if (!hydrated) { text = saved; hydrated = true }
    }
    FlatCard {
        Box {
            if (text.isEmpty()) {
                Text(
                    "How did it feel? Anything to change next time?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; onChange(it) },
                textStyle = LocalTextStyle.current.copy(color = Chalk, fontSize = MaterialTheme.typography.bodyMedium.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                cursorBrush = SolidColor(FlameHot),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("Saved as you type · read by your AI coach.", style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}
