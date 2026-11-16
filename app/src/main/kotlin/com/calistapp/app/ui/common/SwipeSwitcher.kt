package com.calistapp.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe left/right between sibling views (tabs) of different heights — where a pager can't go (inside
 * a lazy list item). The content follows the finger 1:1, rubber-bands at the first and last view, and on
 * release a far-enough drag (30%) OR a flick carries it off at the finger's speed while the next view
 * slides in from the side it lies on. Changing [index] any other way (tapping a tab) slides in from the
 * matching side too, so the direction always says where you are.
 */
@Composable
fun SwipeSwitcher(
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduce = rememberReduceMotion()
    val flingPx = GestureMotion.flingPx(density)
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableIntStateOf(index) }
    val current by rememberUpdatedState(index)
    val change by rememberUpdatedState(onIndexChange)

    // A new view: enter from the side it lies on (+1 = from the right, it's further along).
    LaunchedEffect(index) {
        val before = shown
        shown = index
        if (before == index) return@LaunchedEffect
        val dir = if (index > before) 1 else -1
        if (abs(offset.value) < width * 0.5f) offset.snapTo(dir * width * 0.3f) else offset.snapTo(dir * width * 0.3f)
        offset.animateTo(0f, GestureMotion.release(reduce, bounce = 0.1f, durationS = 0.42f))
    }

    val drag = rememberDraggableState { d ->
        raw += d
        val min = if (current < count - 1) -width else 0f
        val max = if (current > 0) width else 0f
        scope.launch { offset.snapTo(GestureMotion.elastic(raw, min, max)) }
    }
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                drag,
                Orientation.Horizontal,
                startDragImmediately = offset.isRunning,
                onDragStarted = { raw = offset.value; offset.stop() },
                onDragStopped = { v ->
                    val next = when {
                        (v < -flingPx || offset.value < -width * 0.3f) && v < flingPx && current < count - 1 -> current + 1
                        (v > flingPx || offset.value > width * 0.3f) && v > -flingPx && current > 0 -> current - 1
                        else -> current
                    }
                    if (next == current) {
                        offset.animateTo(0f, GestureMotion.snapBack(reduce), initialVelocity = v)
                    } else {
                        // Off at the finger's speed, then the swap (the LaunchedEffect slides the next in).
                        offset.animateTo(if (next > current) -width else width, GestureMotion.spring(0.26f, 0f), initialVelocity = v)
                        change(next)
                    }
                },
            )
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - 0.6f * (abs(offset.value) / width).coerceIn(0f, 1f)
            },
    ) {
        content(index)
    }
}
