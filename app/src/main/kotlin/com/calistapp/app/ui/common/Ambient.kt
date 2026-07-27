package com.calistapp.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.calistapp.app.ui.theme.Ink

/**
 * The soft coloured glow that sits behind every screen, bleeding from the top edge and falling away
 * to near-black.
 *
 * This is the backbone of the whole look: cards are kept translucent precisely so this wash reads
 * through them, which is what stops a dark UI feeling like flat grey boxes on black. Each screen
 * carries its own hue so moving between tabs feels like moving somewhere, and during a workout the
 * tint is driven by whether you're working or resting — an ambient cue you can read from across the
 * room without focusing on any number.
 */
/**
 * Lets a screen take over the app-wide ambient hue.
 *
 * Hoisting the tint rather than nesting a second [AmbientScreen] matters for two reasons: a nested
 * one sits inside the nav host's status-bar inset and so leaves a visible band of the old colour
 * along the top edge, and drawing two full-screen gradients every frame is wasted work.
 */
val LocalAmbientTint = compositionLocalOf<MutableState<Color?>> {
    error("LocalAmbientTint requires an AmbientHost")
}

/** Apply [tint] app-wide for as long as this composable is in the tree. */
@Composable
fun AmbientOverride(tint: Color) {
    val holder = LocalAmbientTint.current
    DisposableEffect(tint) {
        holder.value = tint
        onDispose { holder.value = null }
    }
}

/** Root wrapper: owns the override slot and paints the wash. */
@Composable
fun AmbientHost(
    routeTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val override = remember { mutableStateOf<Color?>(null) }
    CompositionLocalProvider(LocalAmbientTint provides override) {
        AmbientScreen(tint = override.value ?: routeTint, modifier = modifier, content = content)
    }
}

@Composable
fun AmbientScreen(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Crossfade rather than cut, so a work↔rest switch feels like the room changing colour.
    val animated by animateColorAsState(tint, animationSpec = tween(600), label = "ambient")

    Box(
        modifier
            .fillMaxSize()
            .background(Ink)
            .drawBehind {
                // Main glow, offset above the top edge so only its lower falloff is visible.
                // Kept deliberately restrained — this should read as light spilling into the
                // frame, not as a coloured panel behind the content.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animated.copy(alpha = 0.20f),
                            animated.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.62f, -size.height * 0.06f),
                        radius = size.height * 0.58f,
                    ),
                )
                // Cooler counter-glow on the opposite side; keeps the wash from looking like a
                // single flat vignette.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animated.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.02f, size.height * 0.26f),
                        radius = size.height * 0.34f,
                    ),
                )
                // Settle to black well before the bottom so content there stays high-contrast.
                drawRect(
                    brush = Brush.verticalGradient(
                        0.24f to Color.Transparent,
                        0.72f to Ink.copy(alpha = 0.82f),
                        1f to Ink,
                    ),
                )
            },
        content = content,
    )
}
