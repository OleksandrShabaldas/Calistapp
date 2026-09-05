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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.calistapp.app.ui.common.GlowIcon
import com.calistapp.app.ui.dashboard.CardBorder
import com.calistapp.app.ui.dashboard.CardSurface
import com.calistapp.app.ui.theme.Amber
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.core.model.HrRecovery
import com.calistapp.core.progress.PersonalRecord
import com.calistapp.core.progress.ProgressPoint
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
 * The story behind a personal best: what you hit, what it beat and when, and the shape of the climb.
 * [progression] is that movement's best-of-this-metric per session, oldest to newest, so the sparkline
 * ends on today's record.
 */
@Composable
fun PersonalBestOverlay(
    record: PersonalRecord,
    progression: List<ProgressPoint>,
    onOpenExercise: (() -> Unit)?,
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
            Text(
                "Up from ${record.previousLabel}$whenText.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ash,
            )
        }

        if (progression.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROGRESSION", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
                Sparkline(progression.map { it.value.toFloat() }, Modifier.fillMaxWidth().height(64.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${progression.size} sessions", style = MaterialTheme.typography.labelSmall, color = Ash)
                    Text("today", style = MaterialTheme.typography.labelSmall, color = Amber)
                }
            }
        }

        if (onOpenExercise != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable { onDismiss(); onOpenExercise() }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Full history & progress", style = MaterialTheme.typography.titleMedium, color = Chalk)
                Text("Open ›", style = MaterialTheme.typography.labelLarge, color = FlameHot, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun kindLabel(kind: RecordKind) = when (kind) {
    RecordKind.REPS -> "most reps in a set"
    RecordKind.WEIGHT -> "heaviest set"
    RecordKind.VOLUME -> "most volume"
}

/**
 * The full recovery picture: the headline drop and what it means, how it compares to the athlete's
 * recent sessions, and the drop measured after each individual set.
 */
@Composable
fun RecoveryOverlay(
    recovery: HrRecovery,
    recentMeanDrop: Int?,
    onDismiss: () -> Unit,
) {
    val accent = recoveryAccent(recovery.meanDropBpm)
    SummaryOverlay(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowIcon(Icons.Filled.FavoriteBorder, null, accent, size = 28.dp, glowRadius = 8.dp, glowAlpha = 0.5f)
            Column {
                Text("Heart-rate recovery", style = MaterialTheme.typography.headlineSmall, color = Chalk)
                Text("How fast your heart came back down", style = MaterialTheme.typography.bodySmall, color = Ash)
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${recovery.meanDropBpm}", style = MaterialTheme.typography.displaySmall, color = accent, fontWeight = FontWeight.Bold)
            Text("bpm / min average", style = MaterialTheme.typography.labelMedium, color = Ash, modifier = Modifier.padding(bottom = 6.dp))
        }

        Text(recoveryExplanation(recovery.meanDropBpm), style = MaterialTheme.typography.bodyMedium, color = Ash)

        if (recentMeanDrop != null) {
            val delta = recovery.meanDropBpm - recentMeanDrop
            val phrase = when {
                delta >= 3 -> "faster than"
                delta <= -3 -> "slower than"
                else -> "in line with"
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("VS YOUR RECENT AVERAGE", style = MaterialTheme.typography.labelSmall, color = Ash, fontWeight = FontWeight.Bold)
                Text(
                    "Today is $phrase your recent $recentMeanDrop bpm/min.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chalk,
                )
            }
        }

        if (recovery.drops.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AFTER EACH SET", style = MaterialTheme.typography.labelSmall, color = FlameHot, fontWeight = FontWeight.Bold)
                recovery.drops.forEachIndexed { i, drop ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(drop.afterExercise ?: "Rest ${i + 1}", style = MaterialTheme.typography.titleSmall, color = Chalk)
                            Text("${drop.peakBpm} → ${drop.endBpm} bpm", style = MaterialTheme.typography.labelMedium, color = Ash)
                        }
                        Text("−${drop.dropBpm}", style = MaterialTheme.typography.titleMedium, color = recoveryAccent(drop.dropBpm), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            "Measured from your peak heart rate at the end of a set to one minute into the rest that " +
                "followed, across rests long enough to count. Under about 12 bpm is the clinically " +
                "blunted threshold; trained people usually see 20–40.",
            style = MaterialTheme.typography.labelSmall,
            color = Ash,
        )
    }
}

private fun recoveryAccent(drop: Int): Color = when {
    drop >= 25 -> Flame // worth celebrating
    drop >= 12 -> Chalk // unremarkable — the same neutral the "Average" HR stat uses
    else -> Amber // worth watching
}

private fun recoveryExplanation(mean: Int): String = when {
    mean >= 25 -> "A strong drop in the first minute after your sets — that's a well-conditioned recovery response."
    mean >= 12 -> "A normal drop in the first minute after your sets."
    else -> "A slower drop than usual. One session doesn't mean much — fatigue, heat, caffeine and poor sleep all blunt it. Worth watching if it persists."
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
