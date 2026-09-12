package com.calistapp.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.dashboard.CardBorder
import com.calistapp.app.ui.dashboard.CardSurface
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.HrRecovery
import com.calistapp.core.model.formatKg
import com.calistapp.core.progress.PersonalRecord
import com.calistapp.core.progress.RecordKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The scaffold every summary popup shares: a rounded card that scales and fades in over a dimmed
 * scrim, dismissed by tapping outside or the system back gesture. The card swallows its own taps so a
 * tap inside doesn't fall through to the scrim. Same motion as the dashboard's overlays, so popups
 * feel like one family across the app.
 */
@Composable
fun SummaryOverlay(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var appear by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appear = true }
        val scrim by animateColorAsState(
            if (appear) Color.Black.copy(alpha = 0.66f) else Color.Transparent,
            tween(200),
            label = "scrim",
        )
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = appear,
                enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
                exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.92f),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.94f)
                        .heightIn(max = maxHeight)
                        .padding(vertical = 24.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

private val PB_DATE = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/**
 * The story behind a personal best: what you hit, what it beat, and the movement's own history — its
 * all-time records, the shape of the climb, and the sessions it appeared in. Self-contained, so
 * there's nowhere else to go for the detail. [detail] carries that history; a first-time weighted PB
 * has no prior weight to chart, so the chart falls back to whatever metric actually has a trend.
 */
@Composable
fun PersonalBestOverlay(
    record: PersonalRecord,
    detail: PbDetail,
    onDismiss: () -> Unit,
) {
    SummaryOverlay(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowIcon(Icons.Filled.EmojiEvents, null, Amber, size = 30.dp, glowRadius = 8.dp, glowAlpha = 0.6f)
            Column {
                Text("New personal best", style = MaterialTheme.typography.headlineSmall, color = Chalk)
                Text(record.exerciseName, style = MaterialTheme.typography.bodyMedium, color = Ash)
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(record.label, style = MaterialTheme.typography.displaySmall, color = Amber, fontWeight = FontWeight.Bold)
            Text(kindLabel(record.kind), style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 6.dp))
        }

        if (record.previousLabel != null) {
            val whenText = record.previousAtMs?.let { " on ${PB_DATE.format(Date(it))}" } ?: ""
            Text("Up from ${record.previousLabel}$whenText.", style = MaterialTheme.typography.bodyMedium, color = Ash)
        }

        // All-time records for this movement.
        detail.progress?.let { p ->
            val tiles = buildList {
                p.heaviest?.let { add(Triple("Best weight", "+${formatKg(it.addedWeightKg)} kg", FlameHot)) }
                p.mostReps?.let { add(Triple("Best reps", "${it.reps}", Amber)) }
                p.maxVolume?.let { add(Triple("Best volume", "${formatKg(it.addedWeightKg * it.reps)} kg", Sky)) }
            }
            if (tiles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RECORDS", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.forEach { (label, value, accent) -> PbStat(label, value, accent, Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (detail.chart.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${detail.chartLabel.uppercase()} OVER TIME", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
                Sparkline(detail.chart.map { it.value.toFloat() }, Modifier.fillMaxWidth().height(72.dp))
            }
        }

        if (detail.history.isNotEmpty()) {
            Column {
                Text("HISTORY", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                val rows = detail.history.take(8)
                rows.forEachIndexed { i, e ->
                    val newest = i == 0
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(PB_DATE.format(Date(e.atMs)), style = MaterialTheme.typography.bodyMedium, color = if (newest) Amber else Ash)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (e.topWeightKg > 0.0) {
                                Text(
                                    "+${formatKg(e.topWeightKg)} kg",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FlameHot,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.background(FlameHot.copy(alpha = 0.12f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                "${e.sets} ${if (e.sets == 1) "set" else "sets"} · ${e.reps} reps",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (newest) Amber else Chalk,
                                fontWeight = if (newest) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                    if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
                }
                if (detail.history.size > rows.size) {
                    Text("+${detail.history.size - rows.size} earlier", style = MaterialTheme.typography.labelSmall, color = Ash, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PbStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

private fun kindLabel(kind: RecordKind) = when (kind) {
    RecordKind.REPS -> "most reps in a set"
    RecordKind.WEIGHT -> "heaviest set"
    RecordKind.VOLUME -> "most volume"
}

private enum class RecBand(val word: String, val color: Color) {
    BLUNTED("on the low side", Amber),
    TYPICAL("typical", Sky),
    STRONG("strong", Flame),
}

private fun recoveryBand(mean: Int): RecBand = when {
    mean >= 20 -> RecBand.STRONG
    mean >= 12 -> RecBand.TYPICAL
    else -> RecBand.BLUNTED
}

/**
 * The full recovery picture, laid out in sections rather than a pile of prose: the number and where
 * it sits on the normal range, what it is, what it means for you, the drop measured after each set
 * (which is how the number was found), and the method behind it.
 */
@Composable
fun RecoveryOverlay(
    recovery: HrRecovery,
    recentMeanDrop: Int?,
    onDismiss: () -> Unit,
) {
    val band = recoveryBand(recovery.meanDropBpm)
    SummaryOverlay(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowIcon(Icons.Filled.FavoriteBorder, null, band.color, size = 28.dp, glowRadius = 8.dp, glowAlpha = 0.5f)
            Column {
                Text("Heart-rate recovery", style = MaterialTheme.typography.headlineSmall, color = Chalk)
                Text("How fast your heart came back down", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${recovery.meanDropBpm}", style = MaterialTheme.typography.displaySmall, color = band.color, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.padding(bottom = 5.dp)) {
                Text("bpm / min", style = MaterialTheme.typography.labelMedium, color = Ash)
                Text(band.word, style = MaterialTheme.typography.labelLarge, color = band.color, fontWeight = FontWeight.SemiBold)
            }
        }

        RecoveryScale(recovery.meanDropBpm)

        RecSection("What this is") {
            Text(
                "How far your pulse falls in the minute after a hard effort. It tracks how quickly your " +
                    "nervous system switches from \"push\" back toward \"rest\" — one of the clearer " +
                    "day-to-day windows onto aerobic fitness and how recovered you are.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        RecSection("What it means for you") {
            Text(recoveryAdvice(recovery.meanDropBpm), style = MaterialTheme.typography.bodyMedium, color = Chalk)
            if (recentMeanDrop != null) {
                val delta = recovery.meanDropBpm - recentMeanDrop
                val phrase = when {
                    delta >= 3 -> "faster than"
                    delta <= -3 -> "slower than"
                    else -> "in line with"
                }
                Text(
                    "Today is $phrase your recent average of $recentMeanDrop bpm/min.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash,
                )
            }
        }

        if (recovery.drops.isNotEmpty()) {
            RecSection("After each set — how this number was found") {
                recovery.drops.forEachIndexed { i, drop ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(drop.afterExercise ?: "Rest ${i + 1}", style = MaterialTheme.typography.titleSmall, color = Chalk)
                            val window = if (drop.windowSeconds >= 60) "1 min" else "${drop.windowSeconds}s"
                            Text("${drop.peakBpm} → ${drop.endBpm} bpm over $window", style = MaterialTheme.typography.labelMedium, color = Ash)
                        }
                        // The raw drop over its window; coloured by the per-minute rate so a short rest's
                        // band matches the headline scale.
                        Text("−${drop.dropBpm}", style = MaterialTheme.typography.titleMedium, color = recoveryBand(drop.perMinuteDrop).color, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Averaged across the ${recovery.measuredRests} ${if (recovery.measuredRests == 1) "rest" else "rests"} long enough to measure = ${recovery.meanDropBpm} bpm/min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ash,
                )
            }
        }

        Text(
            "Measured from your peak heart rate at the end of a set to one minute into the rest that " +
                "followed, across rests long enough to count. Under about 12 bpm/min is the clinically " +
                "blunted range; trained people usually see 20–40.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

@Composable
private fun RecSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
        content()
    }
}

/**
 * Where the athlete's drop sits on the normal range — blunted / typical / strong — with a marker.
 * The band the value lands in is lit and glows (shaped to the band so the halo doesn't spill as a
 * circle), the other two stay dim, so the eye lands on where today actually sits.
 */
@Composable
private fun RecoveryScale(value: Int) {
    val frac = (value / 30f).coerceIn(0.02f, 0.98f)
    val weights = listOf(12f, 8f, 10f)
    val colors = listOf(Amber, Sky, Flame)
    val activeIndex = when {
        value >= 20 -> 2
        value >= 12 -> 1
        else -> 0
    }
    val before = weights.take(activeIndex).sum()
    val after = weights.drop(activeIndex + 1).sum()
    val bandShape = RoundedCornerShape(5.dp)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // The outer Box is deliberately un-clipped so the active band's glow isn't sliced at its edge.
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(999.dp))) {
                Box(Modifier.weight(12f).fillMaxHeight().background(Amber.copy(alpha = 0.26f)))
                Box(Modifier.weight(8f).fillMaxHeight().background(Sky.copy(alpha = 0.26f)))
                Box(Modifier.weight(10f).fillMaxHeight().background(Flame.copy(alpha = 0.26f)))
            }
            // The active band, at full colour with a shaped glow.
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                if (before > 0f) Spacer(Modifier.weight(before))
                GlowBox(
                    color = colors[activeIndex],
                    shape = bandShape,
                    glowRadius = 9.dp,
                    glowAlpha = 0.6f,
                    modifier = Modifier.weight(weights[activeIndex]).fillMaxHeight(),
                ) {
                    Box(Modifier.matchParentSize().clip(bandShape).background(colors[activeIndex]))
                }
                if (after > 0f) Spacer(Modifier.weight(after))
            }
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Spacer(Modifier.weight(frac))
                Box(Modifier.width(3.dp).fillMaxHeight().background(Chalk))
                Spacer(Modifier.weight(1f - frac))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("blunted", Modifier.weight(12f), style = MaterialTheme.typography.labelSmall, color = Amber)
            Text("typical", Modifier.weight(8f), style = MaterialTheme.typography.labelSmall, color = Sky, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("strong 20–40", Modifier.weight(10f), style = MaterialTheme.typography.labelSmall, color = Flame, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

private fun recoveryAdvice(mean: Int): String = when {
    mean >= 20 -> "A strong drop — well-conditioned autonomic recovery. Keep training and recovering the way you have been."
    mean >= 12 -> "A normal, healthy drop. Nothing to action here — this is what you want to see after hard sets."
    else -> "Low today. One session says little — heat, poor sleep, caffeine and accumulated fatigue all blunt it. If it stays here across several sessions, take it as a nudge to bank more sleep and easy days before pushing hard again."
}

/** A minimal rising sparkline: the values normalised to the box, last point marked. */
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(1e-3f)
        val stepX = size.width / (values.size - 1)
        fun x(i: Int) = i * stepX
        fun y(v: Float) = size.height - (v - minV) / range * size.height * 0.86f - size.height * 0.07f

        for (i in 0 until values.size - 1) {
            drawLine(
                color = FlameHot,
                start = Offset(x(i), y(values[i])),
                end = Offset(x(i + 1), y(values[i + 1])),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val lastX = x(values.size - 1)
        val lastY = y(values.last())
        drawCircle(Amber.copy(alpha = 0.4f), radius = 6.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(Amber, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
    }
}
