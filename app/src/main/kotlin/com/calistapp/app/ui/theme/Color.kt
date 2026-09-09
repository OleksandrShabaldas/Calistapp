package com.calistapp.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Calistapp's palette — **onyx and orange**.
 *
 * The app is led by a hot-orange accent ([Flame]) on a neutral near-black ([Onyx]). It reads as
 * athletic rather than editorial, and every accent below is tuned to sit against warm near-black.
 * (The emerald/"glass" era's token names — `Emerald`, `Cream`, `Ink`, `Glass*` — were retired in the
 * final sweep; everything now names the onyx/orange tokens directly.)
 */

// ---- Onyx / orange (the real palette) --------------------------------------------------------

/** Page base — neutral near-black, giving the orange room to read. */
val Onyx = Color(0xFF0B0B0C)

/** Slightly raised near-black, for docks, sheets and opaque surfaces. */
val OnyxRaised = Color(0xFF141416)

/** Primary accent: work, progress, primary actions, calories. */
val Flame = Color(0xFFEE6C2B)
val FlameDeep = Color(0xFFC0521C)
val FlameSoft = Color(0x26EE6C2B)

/**
 * The hotter *display* orange pair from the home-screen prototype — a touch brighter and more
 * saturated than [Flame]. Used only for the dashboard's hero elements (the weekly bars, the Start
 * button, the goal/gauge rings and the streak pill), where a vivid vertical [FlameGlow] → [FlameHot]
 * gradient reads as energy. [Flame] stays the app-wide accent everywhere else.
 */
val FlameHot = Color(0xFFFF6A1A)
val FlameGlow = Color(0xFFFF8A3D)

/** Near-white text — neutral and athletic. */
val Chalk = Color(0xFFF4F4F5)
val Ash = Color(0xFF8A8A8E)
val AshFaint = Color(0xFF5C5C60)

/** Flat card fill + border for the onyx skin — less translucent than the old glass tokens. */
val OnyxFill = Color(0x0DFFFFFF)
val OnyxFillStrong = Color(0x14FFFFFF)
val OnyxBorder = Color(0x1AFFFFFF)

/**
 * The exercise-screens redesign's **solid, opaque** card fill — the prototype's exact flat `#131215`.
 * Use this — not the translucent [OnyxFill]/[OnyxFillStrong] or the sheened `GlassCard` — for the flat
 * cards on Workout Detail / Build / Add exercise / Exercise Detail, so they read as crisp lifted
 * surfaces rather than glass. Pair with [OnyxBorder].
 */
val CardFlat = Color(0xFF131215)

/**
 * The prototype's warm page field for those same four screens — a warm charcoal ([PageWarm]) fading
 * through [PageDim] to a near-black [PageInk], so the background reads as ashy warm charcoal rather
 * than the app's cold pitch [Onyx]. Paint as a vertical gradient of the three (prototype's own values).
 */
val PageWarm = Color(0xFF1C140D)
val PageDim = Color(0xFF0D0C0E)
val PageInk = Color(0xFF0A0A0B)

// ---- Semantic accents ------------------------------------------------------------------------

/** Effort, heart rate, destructive. Kept red, nudged to sit on warm onyx. */
val Coral = Color(0xFFFF6B6B)

/** Positive cue — coaching tips, "goes well" notes. The one green allowed on the warm palette. */
val Mint = Color(0xFF3FBF7F)

/** Highlight and warning. */
val Amber = Color(0xFFF5C242)

/**
 * Rest / recovery / neutral data. A cool counterpoint to the orange — still useful as a "not-work"
 * signal on a warm palette. (Explicit categorical uses of [Sky]/[Violet] get a per-screen judgement
 * in the final sweep; for now they're retuned to coexist with orange-on-onyx.)
 */
val Sky = Color(0xFF57B7E6)

/** Tertiary categorical accent — the exercise library. */
val Violet = Color(0xFF9B8CFF)

// ---- Stat-card gradients ---------------------------------------------------------------------

/**
 * Colour pairs for the statistics metric grid, where colour carries meaning per-tile. Each is a
 * (top-left → bottom-right) gradient tuned to glow on onyx without going neon. Consumed by
 * `GradientStatCard`.
 */
data class StatGradient(val start: Color, val end: Color, val accent: Color)

val StatRed = StatGradient(Color(0xFF7A241C), Color(0xFF3A1512), Color(0xFFFF7A6B))
val StatAmber = StatGradient(Color(0xFF7A5410), Color(0xFF3A2A0C), Color(0xFFF5C242))
val StatOlive = StatGradient(Color(0xFF4E5A18), Color(0xFF232A0E), Color(0xFFC6D65A))
val StatGreen = StatGradient(Color(0xFF175A3C), Color(0xFF0E2A20), Color(0xFF4FD8A0))
val StatTeal = StatGradient(Color(0xFF14565A), Color(0xFF0C2A2C), Color(0xFF52D6D0))
val StatBlue = StatGradient(Color(0xFF1C4A7A), Color(0xFF12233A), Color(0xFF6FB6F5))

/** The grid's default cycle, in reading order. */
val StatGradients = listOf(StatRed, StatAmber, StatOlive, StatGreen, StatTeal, StatBlue)
