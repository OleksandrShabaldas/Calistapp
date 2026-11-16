package com.calistapp.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Swipe down to dismiss — for popup cards and whole screens.
 *
 * Track: the card/screen follows the finger down 1:1 (upward only rubber-bands). A list inside hands
 * over through nested scrolling once it's at its top, and takes the drag back if you push up again.
 * Release: far enough (a quarter of its height) OR a downward flick dismisses — it leaves at the
 * finger's speed, then the dismiss runs; otherwise it springs back, fast. Can be caught mid-flight.
 * Linked values (a scrim, the screen's scale) read [DragDismissState.fraction].
 */

enum class DismissStyle {
    /** A popup card: slides down and fades a little. */
    Card,

    /** A whole screen: slides down, shrinks slightly and rounds its corners, like a card being put away. */
    Screen,
}

@Stable
class DragDismissState internal constructor(
    private val scope: CoroutineScope,
    private val flingPx: Float,
    private val reduceMotion: Boolean,
) {
    val offset = Animatable(0f)
    internal var heightPx by mutableFloatStateOf(1f)
    internal var onDismissed: (() -> Unit)? = null
    var isDragging by mutableStateOf(false)
        private set
    private var raw = 0f
    private var dismissing = false

    /** How far towards dismissed, 0..1 — for scrims and anything else linked to the drag. */
    val fraction: Float get() = (offset.value / heightPx).coerceIn(0f, 1f)

    fun dragStart() {
        if (dismissing) return
        isDragging = true
        raw = offset.value
        scope.launch { offset.stop() }
    }

    /** The finger moved [dy] px (down positive). */
    fun dragBy(dy: Float) {
        if (dismissing) return
        if (!isDragging) dragStart()
        raw += dy
        scope.launch { offset.snapTo(GestureMotion.elastic(raw, 0f, Float.MAX_VALUE)) }
    }

    fun dragEnd(velocity: Float) {
        if (!isDragging) return
        isDragging = false
        val dismiss = velocity > flingPx || (velocity > -flingPx && offset.value > heightPx * 0.25f)
        scope.launch {
            if (dismiss) {
                dismissing = true
                offset.animateTo(heightPx * 1.05f, GestureMotion.spring(0.3f, 0f), initialVelocity = velocity)
                onDismissed?.invoke()
            } else {
                offset.animateTo(0f, GestureMotion.snapBack(reduceMotion), initialVelocity = velocity)
            }
        }
    }

    internal val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y < 0 && isDragging && offset.value > 0f) {
                dragBy(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0) {
                dragBy(available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (isDragging) {
                dragEnd(available.y)
                return available
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberDragDismissState(onDismissed: () -> Unit): DragDismissState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduce = rememberReduceMotion()
    val current by rememberUpdatedState(onDismissed)
    return remember(scope, density, reduce) {
        DragDismissState(scope, GestureMotion.flingPx(density), reduce).also { it.onDismissed = { current() } }
    }
}

/** Drag + nested scroll + the motion. Put it first in the dismissible thing's modifier chain. */
fun Modifier.dragToDismiss(state: DragDismissState, style: DismissStyle = DismissStyle.Card): Modifier = composed {
    val drag = rememberDraggableState { state.dragBy(it) }
    this
        .nestedScroll(state.nestedScroll)
        .draggable(
            drag,
            Orientation.Vertical,
            startDragImmediately = state.offset.isRunning,
            onDragStarted = { state.dragStart() },
            onDragStopped = { v -> state.dragEnd(v) },
        )
        .dismissTransform(state, style)
}

/** Only the motion — for a surface whose drags are routed by hand (the live workout screen). */
fun Modifier.dismissTransform(state: DragDismissState, style: DismissStyle = DismissStyle.Card): Modifier = this
    .onSizeChanged { state.heightPx = it.height.toFloat().coerceAtLeast(1f) }
    .graphicsLayer {
        val f = state.fraction
        translationY = state.offset.value
        when (style) {
            DismissStyle.Card -> alpha = 1f - 0.35f * f
            DismissStyle.Screen -> {
                val s = 1f - 0.08f * f
                scaleX = s
                scaleY = s
                if (f > 0f) {
                    shape = RoundedCornerShape((32.dp.toPx() * (f * 5f).coerceAtMost(1f)))
                    clip = true
                }
            }
        }
    }
