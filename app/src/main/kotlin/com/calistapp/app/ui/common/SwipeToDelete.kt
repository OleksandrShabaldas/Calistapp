package com.calistapp.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.Coral
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxRaised
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Swipe a row left to delete it.
 *
 * The row follows the finger 1:1 (rightwards only rubber-bands); behind it a red well opens with a bin
 * that grows as you go and ticks when you pass the point of no return (40% of the width). Let go past
 * it — or flick left — and the row leaves at the finger's speed and [onDelete] runs; short of it, the
 * row springs back fast. Give the rows `Modifier.animateItem()` so the list closes the gap smoothly.
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val reduce = rememberReduceMotion()
    val flingPx = GestureMotion.flingPx(density)
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val delete by rememberUpdatedState(onDelete)
    val bin = rememberVectorPainter(Icons.Filled.DeleteOutline)
    val radius = with(density) { cornerRadius.toPx() }
    val iconPx = with(density) { 24.dp.toPx() }

    val drag = rememberDraggableState { d ->
        raw += d
        val shown = GestureMotion.elastic(raw, -width, 0f)
        scope.launch { offset.snapTo(shown) }
        val nowArmed = shown < -width * 0.4f
        if (nowArmed != armed) {
            armed = nowArmed
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            // The well, drawn only in the gap the row leaves, so a translucent card doesn't show red.
            .drawBehind {
                val gap = -offset.value
                if (gap <= 0f) return@drawBehind
                val p = (gap / (size.width * 0.4f)).coerceIn(0f, 1f)
                drawRoundRect(
                    Coral.copy(alpha = 0.16f + 0.5f * p),
                    topLeft = Offset(size.width - gap - radius, 0f),
                    size = Size(gap + radius, size.height),
                    cornerRadius = CornerRadius(radius),
                )
                val s = iconPx * (0.7f + 0.45f * p)
                val cx = size.width - (gap / 2f).coerceAtMost(iconPx * 1.6f)
                translate(left = cx - s / 2, top = size.height / 2 - s / 2) {
                    with(bin) { draw(Size(s, s), alpha = (gap / iconPx).coerceIn(0f, 1f), colorFilter = ColorFilter.tint(if (p >= 1f) Color.White else Chalk)) }
                }
            }
            .draggable(
                drag,
                Orientation.Horizontal,
                startDragImmediately = offset.isRunning,
                onDragStarted = { raw = offset.value; offset.stop() },
                onDragStopped = { v ->
                    val go = v < -flingPx || (v < flingPx && offset.value < -width * 0.4f)
                    armed = false
                    if (go) {
                        offset.animateTo(-width * 1.1f, GestureMotion.spring(0.26f, 0f), initialVelocity = v)
                        delete()
                    } else {
                        offset.animateTo(0f, GestureMotion.snapBack(reduce), initialVelocity = v)
                    }
                },
            )
            .graphicsLayer { translationX = offset.value },
    ) { content() }
}

/**
 * Deletions that wait out an undo window before they happen. Rows in [pending] are hidden at once;
 * [commit] runs for each when its window closes (or the screen goes away). Undo just forgets it — so
 * there's nothing to restore.
 */
@Stable
class PendingDeletes internal constructor(private val scope: kotlinx.coroutines.CoroutineScope) {
    val pending = mutableStateListOf<String>()
    var last by mutableStateOf<Pair<String, String>?>(null)
        private set
    internal var commit: (String) -> Unit = {}
    private val timers = mutableMapOf<String, Job>()
    var windowStart by mutableStateOf(0L)
        private set

    fun delete(id: String, label: String) {
        pending += id
        last = id to label
        windowStart = System.currentTimeMillis()
        timers[id]?.cancel()
        timers[id] = scope.launch {
            delay(UNDO_MS)
            finish(id)
        }
    }

    fun undo() {
        val id = last?.first ?: return
        timers.remove(id)?.cancel()
        pending.remove(id)
        last = null
    }

    /** Close the undo bar now (swiped away): commit what it was offering. */
    fun dismissBar() {
        last?.first?.let { finish(it) }
    }

    internal fun finish(id: String) {
        timers.remove(id)?.cancel()
        if (pending.remove(id)) commit(id)
        if (last?.first == id) last = null
    }

    internal fun flush() = pending.toList().forEach { finish(it) }

    companion object { const val UNDO_MS = 5_000L }
}

@Composable
fun rememberPendingDeletes(commit: (String) -> Unit): PendingDeletes {
    val scope = rememberCoroutineScope()
    val c by rememberUpdatedState(commit)
    val state = remember { PendingDeletes(scope) }
    state.commit = { c(it) }
    // Leaving the screen commits anything still waiting, so nothing is half-deleted.
    DisposableEffect(state) { onDispose { state.flush() } }
    return state
}

/**
 * "Deleted · Undo", rising from the bottom, with a bar that runs down the undo window (linearly — it's
 * time). Swipe it down to dismiss (that deletes now), in the direction it came from.
 */
@Composable
fun UndoBar(state: PendingDeletes, modifier: Modifier = Modifier) {
    val last = state.last
    AnimatedVisibility(
        visible = last != null,
        enter = fadeIn(tween(180)) + slideInVertically(GestureMotion.settle(0.36f)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        modifier = modifier,
    ) {
        val label = remember(last) { last?.second ?: "" }
        val timer = remember(last) { Animatable(1f) }
        LaunchedEffect(last) { timer.animateTo(0f, tween(PendingDeletes.UNDO_MS.toInt(), easing = LinearEasing)) }
        val dismiss = rememberDragDismissState(onDismissed = { state.dismissBar() })
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .dragToDismiss(dismiss)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(OnyxRaised)
                .border(1.dp, OnyxBorder, RoundedCornerShape(18.dp)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$label deleted", style = MaterialTheme.typography.bodyMedium, color = Chalk, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    color = FlameHot,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { state.undo() }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Ash.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(timer.value).height(2.dp).background(FlameHot.copy(alpha = 0.7f)))
            }
        }
    }
}
