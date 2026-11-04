package com.calistapp.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calistapp.app.session.LiveSession
import com.calistapp.app.session.PhaseHandOff
import com.calistapp.app.session.WorkoutChanges
import com.calistapp.app.ui.common.GlowBox
import com.calistapp.app.ui.common.formatClock
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.Display
import com.calistapp.app.ui.theme.Flame
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.Onyx
import com.calistapp.app.ui.theme.Sky
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.repsIn

/*
 * The live screen's phase-aware pieces: the skip sheet (skip / do it later / remove), the hand-off
 * between warm-up, workout and cool-down, the guided routine move, and the end-of-session "keep
 * today's changes?" question. The cool-down wears the app's "settled" blue — the colour the summary
 * already uses for recovered heart rate — so it reads as its own phase at a glance.
 */

/** The accent a phase is drawn in: the cool-down's settled blue, otherwise the app's orange. */
internal fun phaseAccent(phase: SlotPhase?): Color = if (phase == SlotPhase.COOL_DOWN) Sky else Flame

private val SheetSurface = Color(0xFF18171B)
private val OptionSurface = Color(0xFF1F1E23)

// ---- Skip ------------------------------------------------------------------------------------------

/**
 * The three answers to "not this one", replacing a skip that silently left the exercise owed. Slides
 * up over the live screen; the default (first) answer is lit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipChoicesSheet(
    exerciseName: String,
    isCircuit: Boolean,
    multiRound: Boolean,
    onSkip: () -> Unit,
    onLater: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Skip $exerciseName?",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.3).sp),
                color = Chalk,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                "Nothing is logged for it either way.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
            SkipOption(
                icon = Icons.Filled.SkipNext,
                title = "Skip this set",
                body = when {
                    isCircuit && multiRound -> "Marked skipped and the circuit moves on. It's back next round."
                    isCircuit -> "Marked skipped and the circuit moves on — it won't come back to it."
                    else -> "Marked skipped; the workout carries on from the next set."
                },
                primary = true,
                onClick = onSkip,
            )
            SkipOption(
                icon = Icons.Filled.Schedule,
                title = "Do it later",
                body = if (isCircuit) "Moves to the end of this round, before the cool-down."
                else "Moves after the other exercises, before the cool-down.",
                onClick = onLater,
            )
            SkipOption(
                icon = Icons.Filled.Remove,
                title = "Remove from today",
                body = if (multiRound) "Drops it from this session, every round. Your saved workout is unchanged."
                else "Drops it from this session. Your saved workout is unchanged.",
                tint = Coral,
                onClick = onRemove,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Keep going", color = Ash, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SkipOption(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    tint: Color = Chalk,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A small press-in, so the choice registers before the sheet slides away.
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "press")
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (primary) {
                    Modifier
                        .background(Brush.verticalGradient(listOf(FlameGlow.copy(alpha = 0.16f), FlameHot.copy(alpha = 0.07f))), shape)
                        .border(1.dp, FlameHot.copy(alpha = 0.38f), shape)
                } else {
                    Modifier.background(OptionSurface, shape)
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tile = RoundedCornerShape(11.dp)
        if (primary) {
            GlowBox(FlameHot, tile, glowRadius = 8.dp, glowAlpha = 0.5f, modifier = Modifier.size(34.dp)) {
                Box(
                    Modifier.size(34.dp).background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), tile),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Onyx, modifier = Modifier.size(18.dp)) }
            }
        } else {
            Box(
                Modifier.size(34.dp).background(tint.copy(alpha = if (tint == Chalk) 0.06f else 0.12f), tile),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.5.sp), color = Chalk)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Ash)
        }
    }
}

// ---- Phase hand-off ------------------------------------------------------------------------------

/**
 * The rest between two phases: the warm-up waiting at the start, the warm-up done with the workout
 * next, or the workout done with the cool-down next. Says what just ended and what the next phase is,
 * rather than an ordinary "resting" stopwatch that looked like the workout hadn't moved on.
 */
@Composable
fun PhaseHandOffContent(handOff: PhaseHandOff, live: LiveSession) {
    val nextSlots = live.plan.slotsIn(handOff.to)
    val routine = nextSlots.firstOrNull()?.routineName
    Column(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (handOff.from) {
            null -> PhaseBadge("Warm-up first", Flame, done = false)
            SlotPhase.MAIN -> PhaseBadge("Workout done", FlameHot, done = true)
            else -> PhaseBadge("${handOff.from.label} done", FlameHot, done = true)
        }
        if (handOff.from == SlotPhase.MAIN) {
            val main = live.plan.slotsIn(SlotPhase.MAIN)
            val done = main.count { slot -> live.setLogs.any { it.slotId == slot.slotId } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HandOffStat("$done/${main.size}", "Exercises", Modifier.weight(1f))
                HandOffStat("${live.plan.repsIn(live.setLogs)}", "Reps", Modifier.weight(1f))
                HandOffStat(formatClock(live.elapsedMs), "Time", Modifier.weight(1f))
            }
        }
        if (handOff.to == SlotPhase.MAIN) {
            NextPhaseCard(
                phase = handOff.to,
                eyebrow = "Workout next",
                title = live.upNextExercise?.name ?: "First exercise",
                detail = live.upNextExercise?.let(live.plan::targetLabelFor).orEmpty(),
            )
        } else {
            val seconds = nextSlots.sumOf { it.targetSeconds }
            NextPhaseCard(
                phase = handOff.to,
                eyebrow = "${handOff.to.label} next",
                title = routine ?: handOff.to.label,
                detail = "${nextSlots.size} ${if (handOff.to == SlotPhase.COOL_DOWN) "stretches" else "moves"} · " +
                    "${(seconds + 59) / 60} min · guided, heart rate keeps recording",
            )
        }
    }
}

@Composable
private fun PhaseBadge(text: String, color: Color, done: Boolean) {
    val shape = Capsule
    if (done) {
        GlowBox(color, shape, glowRadius = 12.dp, glowAlpha = 0.55f) {
            Row(
                Modifier
                    .background(Brush.verticalGradient(listOf(FlameGlow, FlameHot)), shape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Check, null, tint = Onyx, modifier = Modifier.size(15.dp))
                Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp), fontWeight = FontWeight.Bold, color = Onyx)
            }
        }
    } else {
        Box(Modifier.clip(shape).background(color.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp), fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun HandOffStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Chalk.copy(alpha = 0.04f))
            .border(1.dp, Chalk.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp), color = Chalk)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = Ash)
    }
}

@Composable
private fun NextPhaseCard(phase: SlotPhase, eyebrow: String, title: String, detail: String) {
    val accent = phaseAccent(phase)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.07f), shape)
            .border(1.dp, accent.copy(alpha = 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), fontWeight = FontWeight.SemiBold, color = accent)
        Text(title, style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp), color = Chalk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Ash)
    }
}

// ---- Guided routine move -------------------------------------------------------------------------

/**
 * A warm-up or cool-down move in progress: the hold counting down in a ring, what's next, and the
 * guided-mode switch. In guided mode the next move starts by itself when the ring empties.
 */
@Composable
fun RoutineMoveContent(live: LiveSession, autoAdvance: Boolean, onAutoAdvance: (Boolean) -> Unit) {
    val phase = live.currentPhase
    val accent = phaseAccent(phase)
    val slot = live.currentExercise ?: return
    val target = (live.currentSet?.reps ?: slot.targetSeconds).coerceAtLeast(1)
    val elapsed = (live.segmentElapsedMs / 1000).toInt()
    val remaining = target - elapsed
    val (index, total) = live.phasePosition(phase)
    val next = live.upNext?.takeIf { live.plan.phaseOf(it.slotId) == phase }?.let { live.plan.slot(it.slotId) }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.clip(Capsule).background(accent.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(
                "${if (phase == SlotPhase.COOL_DOWN) "Stretch" else "Move"} $index of $total".uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        val progress by animateFloatAsState((elapsed.toFloat() / target).coerceIn(0f, 1f), tween(900), label = "hold")
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                // The hold's progress glows along its own arc — layered strokes shaped like the ring,
                // not a disc of light behind it.
                for (i in 5 downTo 1) {
                    val t = i / 5f
                    drawArc(
                        accent.copy(alpha = 0.12f * (1f - t * 0.7f)), -90f, 360f * progress, false, Offset(inset, inset), arc,
                        style = Stroke(stroke + 12.dp.toPx() * t, cap = StrokeCap.Round),
                    )
                }
                drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // The seconds tick down in place (a quick fade-through), so they don't smear into each other.
            AnimatedContent(
                targetState = remaining,
                transitionSpec = { fadeIn(tween(90, delayMillis = 60)) togetherWith fadeOut(tween(60)) },
                label = "hold-seconds",
            ) { r ->
                Text(
                    if (r >= 0) formatClock(r * 1000L) else "+${formatClock(-r * 1000L)}",
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 38.sp, letterSpacing = (-1).sp),
                    color = Chalk,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                next?.let { "Next · ${it.name}" } ?: "Last one",
                style = MaterialTheme.typography.labelLarge,
                color = Ash,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Auto-advance", style = MaterialTheme.typography.labelLarge, color = Chalk.copy(alpha = 0.8f))
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = autoAdvance,
                onCheckedChange = onAutoAdvance,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Chalk,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Ash,
                    uncheckedTrackColor = Chalk.copy(alpha = 0.08f),
                ),
            )
        }
    }
}

// ---- Keep today's changes? -----------------------------------------------------------------------

/**
 * Asked once, at the end, and only when today's session changed the saved workout it came from: keep
 * the edits for next time, or leave the workout as it was.
 */
@Composable
fun SaveChangesDialog(changes: WorkoutChanges, onSave: () -> Unit, onJustToday: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetSurface,
        title = {
            Text(
                "Keep today's changes?",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = Chalk,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Save them to ${changes.workoutName} for next time?", style = MaterialTheme.typography.bodyMedium, color = Ash)
                Spacer(Modifier.height(2.dp))
                changes.lines.take(8).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(Capsule).background(FlameHot))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = Chalk)
                    }
                }
                if (changes.lines.size > 8) {
                    Text("+${changes.lines.size - 8} more", style = MaterialTheme.typography.labelMedium, color = Ash)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save to workout", color = FlameHot, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onJustToday) { Text("Just today", color = Ash) } },
    )
}

/** The phase-coloured primary-button colour for the hand-off into [phase]. */
internal fun primaryFor(phase: SlotPhase?): Color = if (phase == SlotPhase.COOL_DOWN) Sky else Flame

/** "Hold 30s" — the hero label over a routine move. */
internal fun holdLabel(seconds: Int): String = "Hold ${seconds}s"
