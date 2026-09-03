package com.calistapp.app.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft radial glow drawn *behind* the element and extending [spread] beyond its bounds.
 *
 * Deliberately a radial gradient rather than `Modifier.blur` (RenderEffect), so it works on every API
 * level the app supports (minSdk 26) instead of silently doing nothing below 31. Draw it before any
 * `clip`/`background` in the chain so the halo isn't clipped away. This is the orange haze the home
 * screen wears on its live elements — the Start button, the nav, logged days, the steps icon.
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
