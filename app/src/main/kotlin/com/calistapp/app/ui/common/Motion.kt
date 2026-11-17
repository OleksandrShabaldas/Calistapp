package com.calistapp.app.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Strong ease-out for things entering: fast start, gentle landing. cubic-bezier(0.23, 1, 0.32, 1). */
val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Strong ease-in-out for things moving on screen. cubic-bezier(0.77, 0, 0.175, 1). */
val EaseInOutStrong = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/**
 * A screen section rising into place the first time it appears.
 *
 * The delay shrinks as the index grows (45, 85, 120, 150 … ms) and stops at the sixth slot, so a long
 * list reads as one wave rather than a queue, and nothing far down the list waits seconds to appear.
 * [key] lets a caller replay it (e.g. a new issue); [seen] skips it after the first visit.
 * Reduced motion keeps the fade and drops the rise.
 */
@Composable
fun Entrance(index: Int, key: Any = Unit, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    var shown by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (!shown) {
            val i = index.coerceIn(0, 6)
            delay((0 until i).sumOf { 45L - it * 5L })
            shown = true
        }
    }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(if (reduce) 200 else 420, easing = EaseOutStrong), label = "entrance")
    Box(
        Modifier.graphicsLayer {
            alpha = p
            if (!reduce) translationY = (1f - p) * 24.dp.toPx()
        },
    ) { content() }
}

/**
 * Press feedback for anything tappable: scales to 0.97 the instant the finger lands and springs back
 * on release. Pass the same [interaction] to the clickable so the two stay in step.
 */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, tween(if (isPressed) 110 else 160, easing = EaseOutStrong), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

/** Convenience: an interaction source remembered at the call site. */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
