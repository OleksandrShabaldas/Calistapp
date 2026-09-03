package com.calistapp.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.calistapp.app.R

/**
 * Two athletic voices.
 *
 * The onyx/orange home screen is drawn in **Space Grotesk** (a geometric display sans) for headings
 * and every big number, and **Archivo** (a grotesque text sans) for body and labels. Both ship as
 * variable fonts, so a weight is requested through [FontVariation] rather than by bundling one file
 * per weight (minSdk 26 is where variable-font support starts). The old Playfair display serif is
 * fully retired — headings are Space Grotesk with tight tracking so big type still feels designed.
 */

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    resId = R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk — page titles, hero headings and the big numerals. */
val Display = FontFamily(
    grotesk(400),
    grotesk(500),
    grotesk(600),
    grotesk(700),
)

/** Archivo — everything you actually read or scan (body, labels, list rows). */
val Body = FontFamily(
    archivo(400),
    archivo(500),
    archivo(600),
    archivo(700),
)

/**
 * For large numerals — heart rate, calories, reps. Space Grotesk with tight tracking keeps big
 * figures from drifting apart, and its even weighting stops the layout jittering as a live value
 * counts up.
 */
val NumericLarge = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp,
    lineHeight = 58.sp,
    letterSpacing = (-1.5).sp,
)

val NumericMedium = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.8).sp,
)

/**
 * The live-workout screen's headline voice — the current exercise's name and the big phase labels
 * ("GET READY", "PAUSED"). Space Grotesk, so it reads as athletic rather than editorial.
 */
val TitleSans = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.3).sp,
)

val CalistTypography = Typography(
    // ---- Space Grotesk: page titles and hero headings (heavy weight + tight tracking) ----
    displayLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.0).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp,
    ),
    // Section headings are Space Grotesk too, so cards feel authored rather than generated.
    titleLarge = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    ),

    // ---- Archivo: everything you actually read or scan ----
    titleMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Body, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp,
    ),
)
