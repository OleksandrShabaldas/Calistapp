package com.calistapp.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.calistapp.app.ui.theme.Ash
import com.calistapp.app.ui.theme.Chalk
import com.calistapp.app.ui.theme.FlameGlow
import com.calistapp.app.ui.theme.OnyxBorder
import com.calistapp.app.ui.theme.OnyxRaised

/**
 * The app's one way of explaining something: a small "?" in a circle next to what it explains, which
 * opens a bubble that grows out of the dot. Tap outside (or Back) and it shrinks back into it.
 *
 * Replaces the paragraphs of explainer text that used to sit permanently under titles and toggles all
 * over the app — the explanation is one tap away instead of always in the way. (It also replaces two
 * private copies of an unanimated "?" tooltip on the exercise-detail and build screens.)
 */
@Composable
fun HelpDot(text: String, modifier: Modifier = Modifier, contentDescription: String = "What's this?") {
    // `open` is what the user asked for; `shown` keeps the bubble's window up until it has shrunk away.
    var open by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (open) FlameGlow else Ash, tween(180), label = "helpTint")
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val position = remember(gapPx, marginPx) { BubblePosition(gapPx, marginPx) }

    Box(modifier) {
        Box(
            Modifier
                .size(20.dp)
                .border(1.dp, tint.copy(alpha = 0.65f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = contentDescription,
                ) {
                    open = !open
                    if (open) shown = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        if (shown) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val grow = remember { Animatable(0f) }
                LaunchedEffect(open) {
                    if (open) {
                        grow.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium))
                    } else {
                        grow.animateTo(0f, tween(140))
                        shown = false
                    }
                }
                Box(
                    Modifier
                        .graphicsLayer {
                            val g = grow.value
                            alpha = g.coerceIn(0f, 1f)
                            scaleX = 0.5f + 0.5f * g
                            scaleY = 0.5f + 0.5f * g
                            transformOrigin = position.origin
                        }
                        .widthIn(max = 268.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .background(OnyxRaised, RoundedCornerShape(12.dp))
                        .border(1.dp, OnyxBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodySmall, color = Chalk)
                }
            }
        }
    }
}

/**
 * Below the dot, left-aligned with it, pushed back inside the window's margins — or above it when
 * there's no room below. Remembers where the dot sits relative to the bubble ([origin]), so the bubble
 * grows out of (and shrinks back into) the dot itself. A plain field, not Compose state: it's set during
 * layout and only read at draw time, which always comes after.
 */
private class BubblePosition(private val gapPx: Int, private val marginPx: Int) : PopupPositionProvider {
    var origin = TransformOrigin(0f, 0f)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left - marginPx / 2)
            .coerceAtMost(windowSize.width - marginPx - popupContentSize.width)
            .coerceAtLeast(marginPx)
        val below = anchorBounds.bottom + gapPx
        val fitsBelow = below + popupContentSize.height <= windowSize.height - marginPx
        val y = if (fitsBelow) below else (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(marginPx)
        val pivotX = if (popupContentSize.width > 0) {
            ((anchorBounds.center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        } else {
            0f
        }
        origin = TransformOrigin(pivotX, if (fitsBelow) 0f else 1f)
        return IntOffset(x, y)
    }
}
