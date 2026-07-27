package com.calistapp.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Calistapp is **dark-only by design**, not by omission.
 *
 * The whole visual system rests on a coloured ambient wash bleeding out of a near-black page — that
 * effect has no meaningful light-mode equivalent, and the previous half-specified light scheme
 * (three colours, everything else defaulted) was a large part of why the app looked unfinished.
 * One well-made theme beats two mediocre ones.
 */
private val CalistColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Ink,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = Cream,

    secondary = Sky,
    onSecondary = Ink,

    tertiary = Amber,
    onTertiary = Ink,

    background = Ink,
    onBackground = Cream,

    // Surfaces stay near-transparent in practice: cards use the glass tokens so the ambient
    // gradient reads through them. These are the fallbacks for anything opaque.
    surface = InkElevated,
    onSurface = Cream,
    surfaceVariant = InkElevated,
    onSurfaceVariant = CreamMuted,

    outline = GlassBorder,
    outlineVariant = GlassBorder,

    error = Coral,
    onError = Ink,

    scrim = Ink,
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
        CompositionLocalProvider(LocalContentColor provides Cream, content = content)
    }
}
