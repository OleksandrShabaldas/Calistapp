package com.calistapp.core.weather

import kotlin.math.roundToInt

/** Current conditions at the athlete's location — only what the indoor/outdoor call needs. */
data class WeatherInput(
    val tempC: Double,
    /** Apparent ("feels like") temperature: air temperature with wind chill and humidity folded in. */
    val feelsLikeC: Double,
    val windKph: Double,
    /** Precipitation in the current hour, mm. */
    val precipMm: Double,
    /** WMO weather-interpretation code. */
    val weatherCode: Int,
    /** Precipitation over the previous ~3 hours, mm — bars and ground stay wet after rain stops. */
    val recentPrecipMm: Double? = null,
    val gustKph: Double? = null,
    val uvIndex: Double? = null,
    val usAqi: Int? = null,
    val isDay: Boolean? = null,
)

enum class Venue { INDOOR, OUTDOOR }

/** One thing that shaped the call. Ordered roughly by how much it matters. */
enum class AdviceReason {
    STORM, RAIN, SNOW, POOR_AIR, GALE, COLD, HOT,
    WET_BARS, CHILLY, HEAT, WINDY, FOG, HAZY, HIGH_UV, DARK,
    PLEASANT,
}

/**
 * The indoor/outdoor verdict for a calisthenics session.
 *
 * @property score how good it is to train outside, 0..100 — the ring around the dashboard gauge.
 * @property reasons what drove it, most important first; never empty.
 */
data class OutdoorAdvice(
    val venue: Venue,
    val score: Int,
    val reasons: List<AdviceReason>,
    /** Put layers on: outdoors, but on the cool side. */
    val layers: Boolean = false,
    /** UV is high enough to need sunscreen. */
    val sunscreen: Boolean = false,
) {
    /** "Indoor", "Outdoor", "Outdoor · SPF" or "Outdoor · layers" — the label the gauge and the AI use. */
    val label: String
        get() = when {
            venue == Venue.INDOOR -> "Indoor"
            layers -> "Outdoor · layers"
            sunscreen -> "Outdoor · SPF"
            else -> "Outdoor"
        }
}

/**
 * Decides whether to train outside, for **calisthenics** specifically — which is fussier about weather
 * than running is. A session is mostly standing still between sets, so you cool down fast; cold metal
 * bars sap grip and wet ones are slippery; explosive moves on cold muscles are how strains happen. So
 * the comfortable band sits warmer than a runner's: around 8 °C, which the previous rules (and the AI,
 * told only to avoid "temperature extremes") called a fine day outside, now reads as an indoor day.
 *
 * Feels-like temperature sets a base score, the rest of the weather takes points off, and a few hard
 * blockers (rain, storms, bad air, a gale, cold below ~10 °C feels-like, heat above ~32 °C) force
 * indoors whatever the score. Outside needs a score of at least [OUTDOOR_MIN_SCORE].
 */
object OutdoorAdvisor {

    const val OUTDOOR_MIN_SCORE = 50

    /** Feels-like °C → base score. Peaks across 22–25 °C; 50 (the outdoor line) sits at 13 °C and 31.8 °C. */
    private val comfortCurve = listOf(
        -5.0 to 0.0, 5.0 to 10.0, 10.0 to 35.0, 13.0 to 50.0, 16.0 to 72.0, 19.0 to 90.0,
        22.0 to 100.0, 25.0 to 100.0, 28.0 to 82.0, 31.0 to 55.0, 34.0 to 30.0, 38.0 to 0.0,
    )

    private val stormCodes = setOf(95, 96, 99)
    private val rainCodes = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
    private val snowCodes = setOf(71, 73, 75, 77, 85, 86)
    private val fogCodes = setOf(45, 48)

    fun advise(w: WeatherInput): OutdoorAdvice {
        val feels = w.feelsLikeC
        val blockers = mutableListOf<AdviceReason>()
        val notes = mutableListOf<AdviceReason>()
        var score = interpolate(feels)

        when {
            w.weatherCode in stormCodes -> { blockers += AdviceReason.STORM; score = 0.0 }
            w.weatherCode in snowCodes -> { blockers += AdviceReason.SNOW; score = minOf(score, 10.0) }
            w.weatherCode in rainCodes || w.precipMm > 0.2 -> { blockers += AdviceReason.RAIN; score = minOf(score, 15.0) }
        }
        if ((w.usAqi ?: 0) > 100) { blockers += AdviceReason.POOR_AIR; score = minOf(score, 20.0) }
        if (w.windKph >= 40 || (w.gustKph ?: 0.0) >= 60) { blockers += AdviceReason.GALE; score = minOf(score, 20.0) }
        if (feels < 10) blockers += AdviceReason.COLD
        if (feels >= 32) blockers += AdviceReason.HOT

        // Soft factors: points off, and a note if it matters.
        if (AdviceReason.RAIN !in blockers && (w.recentPrecipMm ?: 0.0) >= 0.5) {
            score -= 30; notes += AdviceReason.WET_BARS
        }
        val wind = maxOf(w.windKph, (w.gustKph ?: 0.0) * 0.6)
        if (wind > 15 && AdviceReason.GALE !in blockers) {
            score -= (wind - 15) * 1.2; if (wind >= 25) notes += AdviceReason.WINDY
        }
        if (w.weatherCode in fogCodes) { score -= 10; notes += AdviceReason.FOG }
        w.usAqi?.let { if (it in 51..100) { score -= (it - 50) * 0.3; if (it > 75) notes += AdviceReason.HAZY } }
        val uv = w.uvIndex ?: 0.0
        if (uv >= 9) score -= 10
        if (w.isDay == false) { score -= 10; notes += AdviceReason.DARK }

        val scoreInt = score.roundToInt().coerceIn(0, 100)
        val outdoor = blockers.isEmpty() && scoreInt >= OUTDOOR_MIN_SCORE
        if (!outdoor && blockers.isEmpty()) {
            // Below the line without a single hard blocker: say which side of comfortable it fell on.
            blockers += when {
                feels < 19 -> AdviceReason.CHILLY
                feels > 25 -> AdviceReason.HEAT
                else -> notes.firstOrNull() ?: AdviceReason.CHILLY
            }
        }
        if (outdoor && feels < 16) notes.add(0, AdviceReason.CHILLY)
        if (outdoor && feels >= 27) notes.add(0, AdviceReason.HEAT)
        if (outdoor && notes.isEmpty()) notes += AdviceReason.PLEASANT
        if (outdoor && uv >= 6) notes += AdviceReason.HIGH_UV
        val reasons = (blockers + notes).distinct().ifEmpty { listOf(AdviceReason.PLEASANT) }
        return OutdoorAdvice(
            venue = if (outdoor) Venue.OUTDOOR else Venue.INDOOR,
            score = scoreInt,
            reasons = reasons,
            layers = outdoor && feels < 16,
            sunscreen = outdoor && uv >= 6,
        )
    }

    private fun interpolate(feels: Double): Double {
        if (feels <= comfortCurve.first().first) return comfortCurve.first().second
        if (feels >= comfortCurve.last().first) return comfortCurve.last().second
        val i = comfortCurve.indexOfFirst { it.first >= feels }
        val (x0, y0) = comfortCurve[i - 1]
        val (x1, y1) = comfortCurve[i]
        return y0 + (feels - x0) / (x1 - x0) * (y1 - y0)
    }

    /** A plain-English line per reason, for the fallback explanation and the AI prompt. */
    fun describe(reason: AdviceReason, w: WeatherInput): String {
        val feels = w.feelsLikeC.roundToInt()
        return when (reason) {
            AdviceReason.STORM -> "There's a thunderstorm — no bars in lightning."
            AdviceReason.RAIN -> "It's raining, so bars are slippery and you'd be soaked between sets."
            AdviceReason.SNOW -> "It's snowing — icy bars and cold, stiff muscles."
            AdviceReason.POOR_AIR -> "Air quality is poor (AQI ${w.usAqi}), which adds respiratory load."
            AdviceReason.GALE -> "It's very windy (${w.windKph.roundToInt()} km/h), enough to swing rings and chill you fast."
            AdviceReason.COLD -> "It feels like $feels °C. You stand still between sets, so you'd cool down fast, cold bars sap grip and explosive moves on cold muscles risk a strain."
            AdviceReason.HOT -> "It feels like $feels °C — too hot to train hard outside safely."
            AdviceReason.WET_BARS -> "It rained recently, so bars and the ground are likely still wet — wipe them down first."
            AdviceReason.CHILLY -> "It feels like $feels °C, on the cool side for standing around between sets — layer up and warm up longer."
            AdviceReason.HEAT -> "It feels like $feels °C — drink plenty and look for shade."
            AdviceReason.WINDY -> "There's a stiff breeze (${w.windKph.roundToInt()} km/h) that will cool you between sets."
            AdviceReason.FOG -> "It's foggy and damp."
            AdviceReason.HAZY -> "Air quality is middling (AQI ${w.usAqi})."
            AdviceReason.HIGH_UV -> "UV is high (${"%.0f".format(w.uvIndex ?: 0.0)}), so wear sunscreen."
            AdviceReason.DARK -> "It's dark out — pick a well-lit spot."
            AdviceReason.PLEASANT -> "Mild, dry and calm — a good window to train outside."
        }
    }
}
