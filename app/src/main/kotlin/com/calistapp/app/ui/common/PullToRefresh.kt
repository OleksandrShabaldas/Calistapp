package com.calistapp.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.FlameHot
import com.calistapp.app.ui.theme.OnyxRaised
import kotlinx.coroutines.launch

/**
 * Pull down past the top of a scrolling screen to refresh it.
 *
 * Built to the gesture rules: the content and the indicator follow the finger (with pull resistance,
 * then a rubber band past the trigger point), the arc fills as you approach the trigger and ticks when
 * you cross it, and letting go either springs to the "refreshing" rest (seeded with the release speed)
 * or snaps back fast. Pushing back up while pulled takes the pull back first, before the list scrolls.
 * Material's version on this toolkit settles with fixed tweens and a stock look, hence this one.
 */
@Composable
fun PullToRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { 84.dp.toPx() }
    val holdPx = with(density) { 64.dp.toPx() }
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pull = remember { Animatable(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val isRefreshing by rememberUpdatedState(refreshing)
    val refresh by rememberUpdatedState(onRefresh)

    fun shown(r: Float): Float = GestureMotion.elastic(r * 0.55f, 0f, triggerPx, 0.25f)
    fun track(r: Float) {
        raw = r.coerceAtLeast(0f)
        val s = shown(raw)
        scope.launch { pull.snapTo(s) }
        val nowArmed = s >= triggerPx
        if (nowArmed != armed) {
            armed = nowArmed
            if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Done refreshing: tuck the indicator away (no bounce — nothing was thrown).
    LaunchedEffect(refreshing) {
        if (!refreshing && pull.value > 0f && raw == 0f) pull.animateTo(0f, GestureMotion.settle(0.32f))
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0 && raw > 0f) {
                    val before = raw
                    track(raw + available.y)
                    return Offset(0f, -(before - raw))
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0 && !isRefreshing) {
                    if (raw == 0f) scope.launch { pull.stop() }
                    track(raw + available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (raw <= 0f) return Velocity.Zero
                val fire = pull.value >= triggerPx
                raw = 0f
                armed = false
                if (fire && !isRefreshing) {
                    refresh()
                    pull.animateTo(holdPx, GestureMotion.release(reduce, bounce = 0.12f, durationS = 0.4f), initialVelocity = available.y * 0.5f)
                } else {
                    pull.animateTo(if (isRefreshing) holdPx else 0f, GestureMotion.snapBack(reduce), initialVelocity = available.y * 0.5f)
                }
                return available
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pull.value }) { content() }
        RefreshIndicator(
            progress = { (pull.value / triggerPx).coerceIn(0f, 1.4f) },
            offsetPx = { pull.value },
            refreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun RefreshIndicator(progress: () -> Float, offsetPx: () -> Float, refreshing: Boolean, modifier: Modifier) {
    val spin by rememberInfiniteTransition(label = "refreshSpin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Box(
        modifier
            .statusBarsPadding()
            .padding(top = 4.dp)
            .graphicsLayer {
                val p = progress()
                translationY = offsetPx() - 44.dp.toPx()
                alpha = (p * 1.6f).coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * p.coerceAtMost(1f)
                scaleX = s
                scaleY = s
            }
            // The one live thing at that moment, so it glows (round, so the radial halo fits).
            .glow(FlameHot, spread = 10.dp, alpha = 0.35f)
            .size(38.dp)
            .clip(CircleShape)
            .background(OnyxRaised),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val p = progress().coerceAtMost(1f)
            val stroke = 2.6.dp.toPx()
            rotate(if (refreshing) spin else p * 220f) {
                drawArc(
                    FlameHot,
                    startAngle = -90f,
                    sweepAngle = if (refreshing) 270f else 300f * p,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
