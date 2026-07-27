package com.calistapp.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Calistapp's palette.
 *
 * Emerald is the signature — the app is deliberately green-led, and every accent below is tuned to
 * sit against near-black rather than against a mid-grey Material surface. Screens are washed with a
 * soft ambient tint (see `AmbientScreen`), so surfaces are kept translucent and nearly colourless;
 * the colour in the UI comes from the wash showing through the glass, not from painted panels.
 */

// ---- Accents ---------------------------------------------------------------------------------

/** Primary. Work, progress, "good". */
val Emerald = Color(0xFF3DDC97)
val EmeraldDeep = Color(0xFF0F7D5B)

/** Secondary. Rest, recovery, neutral data. */
val Sky = Color(0xFF4FB8F5)

/** Effort, heart rate, destructive. */
val Coral = Color(0xFFFF6B6B)

/** Highlight and warning. */
val Amber = Color(0xFFF5C242)

/** Tertiary categorical accent — the exercise library. */
val Violet = Color(0xFF9B8CFF)

// ---- Ink surfaces ----------------------------------------------------------------------------

/** Page base. Near-black so the ambient wash has room to read. */
val Ink = Color(0xFF07090D)

/** Solid surface for the rare opaque element (menus, dialogs). */
val InkElevated = Color(0xFF10141B)

/** Warm off-white for display type — pure white reads cold and cheap against these darks. */
val Cream = Color(0xFFF6F2E9)
val CreamMuted = Color(0xFFA9A49A)
val CreamFaint = Color(0xFF6F6B64)

// ---- Glass -----------------------------------------------------------------------------------

/** Card fill: barely-there white so the ambient gradient shows through. */
val GlassFill = Color(0x0DFFFFFF)

/** Slightly stronger fill for nested/pressed surfaces. */
val GlassFillStrong = Color(0x14FFFFFF)

/** Hairline border that gives glass its edge. */
val GlassBorder = Color(0x1FFFFFFF)
