package com.calistapp.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.calistapp.app.R

/**
 * One athletic voice.
 *
 * The app now speaks in **bold sans** throughout — headings, titles, body, labels and every number —
 * matching the onyx/orange reference training app, which reads as athletic rather than editorial.
 * The old high-contrast **Playfair Display** serif is kept defined below (a few surfaces may still
 * opt into it deliberately) but is **no longer routed through `MaterialTheme.typography`**; headings
 * are heavy sans with tight tracking so big type still feels designed, not defaulted.
 *
 * Playfair ships here as a variable font, so weights are requested through [FontVariation] rather
 * than by bundling one file per weight (minSdk is 26, which is where variable font support starts).
 */

@OptIn(ExperimentalTextApi::class)
private fun playfair(weight: Int) = Font(
    resId = R.font.playfair_display,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun playfairItalic(weight: Int) = Font(
    resId = R.font.playfair_display_italic,
    weight = FontWeight(weight),
    style = FontStyle.Italic,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(
    playfair(400),
    playfair(500),
    playfair(600),
    playfair(700),
    playfair(800),
    playfair(900),
    playfairItalic(400),
    playfairItalic(600),
    playfairItalic(700),
)

private val Sans = FontFamily.Default

/**
 * For large numerals — heart rate, calories, reps. Tight tracking keeps big figures from drifting
 * apart, and tabular-ish weighting stops the layout jittering as a live value counts up.
 */
val NumericLarge = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp,
    lineHeight = 58.sp,
    letterSpacing = (-1.5).sp,
)

val NumericMedium = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.8).sp,
)

/**
 * The live-workout screen speaks in bold sans, not the display serif — the reference training app is
 * all-sans and it reads as athletic rather than editorial. Used for the current exercise's name and
 * the big phase labels ("GET READY", "PAUSED").
 */
val TitleSans = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp,
)

val CalistTypography = Typography(
    // ---- Bold sans: page titles and hero headings (heavy weight + tight tracking) ----
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Black,
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Black,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    // Section headings are heavy sans too, so cards feel authored rather than generated.
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),

    // ---- Sans: everything you actually read or scan ----
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp,
    ),
)
