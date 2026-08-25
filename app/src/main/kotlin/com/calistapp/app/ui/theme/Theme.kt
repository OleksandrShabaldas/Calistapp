package com.calistapp.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Calistapp is **dark-only by design**, not by omission.
 *
 * The whole visual system rests on a warm orange glow bleeding out of a near-black page — that effect
 * has no meaningful light-mode equivalent, and a half-specified light scheme is a large part of what
 * made the app once look unfinished. One well-made theme beats two mediocre ones.
 */
private val CalistColors = darkColorScheme(
    primary = Flame,
    onPrimary = Onyx,
    primaryContainer = FlameDeep,
    onPrimaryContainer = Chalk,

    secondary = Sky,
    onSecondary = Onyx,

    tertiary = Amber,
    onTertiary = Onyx,

    background = Onyx,
    onBackground = Chalk,

    // Surfaces stay near-flat in practice: cards use the onyx fill tokens over the ambient glow.
    // These are the fallbacks for anything opaque (menus, dialogs).
    surface = OnyxRaised,
    onSurface = Chalk,
    surfaceVariant = OnyxRaised,
    onSurfaceVariant = Ash,

    outline = OnyxBorder,
    outlineVariant = OnyxBorder,

    error = Coral,
    onError = Onyx,

    scrim = Onyx,
)

@Composable
fun CalistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalistColors,
        typography = CalistTypography,
        shapes = CalistShapes,
    ) {
        // The app's root is a plain Box (the ambient wash), not a Surface, so nothing would
        // otherwise supply LocalContentColor — and Material3 defaults it to black, which renders
        // unstyled text invisible on this background. Provide it once here so every screen inherits.
        CompositionLocalProvider(LocalContentColor provides Chalk, content = content)
    }
}
