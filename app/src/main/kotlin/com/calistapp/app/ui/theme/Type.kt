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
 * Two voices, used for different jobs.
 *
 * **Playfair Display** (a high-contrast transitional serif) carries titles and section headings. It
 * is the single biggest reason the app stops looking like an unstyled Material template — stock
 * `Typography()` is default Roboto at default sizes, which reads as "no one chose this".
 *
 * **The system sans** carries body text, labels and — importantly — every number. Data wants a
 * neutral, evenly-weighted face; setting a heart rate in a display serif would be styling for its
 * own sake at the cost of legibility mid-set.
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

val CalistTypography = Typography(
    // ---- Display serif: page titles and hero headings ----
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 27.sp,
    ),
    // Section headings sit in the serif too, so cards feel authored rather than generated.
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 25.sp,
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
