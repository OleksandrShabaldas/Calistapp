package com.calistapp.app.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A soft radial glow drawn *behind* the element and extending [spread] beyond its bounds. For genuinely
 * round things (dots, the round nav button) a radial halo already matches the shape. Works on every API
 * level (unlike [blur]); draw it before any `clip`/`background` so the halo isn't clipped away.
 */
fun Modifier.glow(
    color: Color,
    spread: Dp = 20.dp,
    alpha: Float = 0.55f,
    centerYFraction: Float = 0.5f,
): Modifier = drawBehind {
    val radius = size.maxDimension / 2f + spread.toPx()
    val center = Offset(size.width / 2f, size.height * centerYFraction)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * An icon with a glow that takes the icon's own shape — a blurred, tinted copy of the glyph sitting
 * behind it — rather than a generic circle, so it reads as the icon glowing rather than a disc. The
 * blur needs API 31+; below that it renders the plain icon (the halo is a nicety, not load-bearing).
 */
@Composable
fun GlowIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    glowColor: Color = tint,
    glowRadius: Dp = 6.dp,
    glowAlpha: Float = 0.55f,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (canBlur) {
            Icon(
                imageVector,
                contentDescription = null,
                modifier = Modifier.size(size).blur(glowRadius, BlurredEdgeTreatment.Unbounded),
                tint = glowColor.copy(alpha = glowAlpha),
            )
        }
        Icon(imageVector, contentDescription, Modifier.size(size), tint = tint)
    }
}

/**
 * A glow that takes [content]'s [shape] — a blurred, tinted copy of the shape behind the content. Used
 * for the shapes a circular halo would misfit (the today bar, a pill button). Falls back to the radial
 * [glow] below API 31.
 */
@Composable
fun GlowBox(
    color: Color,
    shape: Shape,
    glowRadius: Dp,
    glowAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.then(if (!canBlur) Modifier.glow(color, spread = glowRadius, alpha = glowAlpha * 0.7f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (canBlur) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                    .background(color.copy(alpha = glowAlpha), shape),
            )
        }
        content()
    }
}
