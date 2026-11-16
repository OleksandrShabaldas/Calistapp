package com.calistapp.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calistapp.app.ui.theme.Capsule
import com.calistapp.app.ui.theme.OnyxBorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A destructive action you confirm by holding, instead of a "Are you sure?" dialog.
 *
 * Per the gesture rules the two phases are asymmetric: while held, a fill sweeps across **linearly**
 * (it shows time passing, and time passes linearly — any curve would lie about how long is left); let
 * go early and it snaps back **fast** with an ease-out. Reaching the end gives a firm tick and runs
 * [onConfirm]. A quick tap just says how it works. Screen readers get a plain click action.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    color: Color,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdHint: String = "Hold to ${label.lowercase()}",
    holdMs: Int = 1200,
) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val confirm by rememberUpdatedState(onConfirm)
    var hint by remember { mutableStateOf(false) }
    var hintJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier
            .clip(Capsule)
            .border(1.dp, OnyxBorder, Capsule)
            .drawBehind {
                drawRect(color.copy(alpha = 0.10f))
                drawRect(color.copy(alpha = 0.32f), size = Size(size.width * fill.value, size.height))
            }
            .semantics { onClick(label) { confirm(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val run = scope.launch {
                        // Linear, from wherever it is now, over the time left.
                        val left = ((1f - fill.value) * holdMs).toInt().coerceAtLeast(1)
                        fill.animateTo(1f, tween(left, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirm()
                    }
                    val released = tryAwaitRelease()
                    if (fill.value < 1f) {
                        run.cancel()
                        scope.launch { fill.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
                        if (released && fill.value < 0.15f) {
                            // A tap: explain, briefly.
                            hint = true
                            hintJob?.cancel()
                            hintJob = scope.launch { delay(1600); hint = false }
                        }
                    }
                })
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = hint,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "holdHint",
        ) { h ->
            Text(if (h) holdHint else label, style = MaterialTheme.typography.titleSmall, color = color)
        }
    }
}
