package com.calistapp.core.progress

import kotlin.math.abs
import kotlin.math.roundToInt

/** A bodyweight reading. History of these is what turns one number into a trend. */
data class WeightEntry(val atMs: Long, val weightKg: Double)

/**
 * Bodyweight over a span, alongside what training was logged across the same span.
 *
 * ## What this is not
 *
 * It is tempting to treat this as a check on the calorie engine — weigh yourself, compare the mass
 * you lost against the calories the app claims you burned, and read off the error. That doesn't
 * work, and the app should not imply it does. Mass change is intake *minus* total expenditure, and
 * total expenditure is mostly resting metabolism and daily movement, neither of which this app
 * measures. With intake unknown, a discrepancy cannot be attributed to any one term.
 *
 * What it honestly gives you is arithmetic: the energy your mass change implies, and the training
 * energy you logged in the same window, side by side. That is genuinely useful context for someone
 * deciding whether to eat more or train more. It is not a validation, and [BodyMassTrend] is worded
 * so nothing downstream can present it as one.
 */
object BodyMass {

    /**
     * Energy per kilogram of body mass, mixed tissue. The classic 7700 kcal/kg — a reasonable
     * average for gradual change, and wrong for rapid change, most of which is water.
     */
    const val KCAL_PER_KG = 7700.0

    /** Below this, a change is water and measurement noise rather than a trend. */
    const val MEANINGFUL_KG = 0.5

    /** Fewer than this many days between the ends and there's no trend to speak of. */
    const val MIN_SPAN_DAYS = 14

    fun trend(entries: List<WeightEntry>, trainingKcalInSpan: (fromMs: Long, toMs: Long) -> Int): BodyMassTrend? {
        if (entries.size < 2) return null
        val ordered = entries.sortedBy { it.atMs }
        val first = ordered.first()
        val latest = ordered.last()

        val spanDays = ((latest.atMs - first.atMs) / DAY_MS).toInt()
        if (spanDays < MIN_SPAN_DAYS) return null

        val changeKg = latest.weightKg - first.weightKg
        return BodyMassTrend(
            first = first,
            latest = latest,
            changeKg = changeKg,
            spanDays = spanDays,
            impliedEnergyKcal = (changeKg * KCAL_PER_KG).roundToInt(),
            loggedTrainingKcal = trainingKcalInSpan(first.atMs, latest.atMs),
            entries = ordered,
        )
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}

data class BodyMassTrend(
    val first: WeightEntry,
    val latest: WeightEntry,
    /** Negative when mass was lost. */
    val changeKg: Double,
    val spanDays: Int,
    /** Energy the change implies at [BodyMass.KCAL_PER_KG]. Negative means a net deficit. */
    val impliedEnergyKcal: Int,
    /** Training energy this app recorded over the same window. */
    val loggedTrainingKcal: Int,
    val entries: List<WeightEntry>,
) {
    val isMeaningful: Boolean get() = abs(changeKg) >= BodyMass.MEANINGFUL_KG

    val direction: Direction
        get() = when {
            !isMeaningful -> Direction.STEADY
            changeKg < 0 -> Direction.DOWN
            else -> Direction.UP
        }

    /** Average kcal per day the mass change implies — the figure people actually reason with. */
    val impliedDailyKcal: Int get() = if (spanDays <= 0) 0 else impliedEnergyKcal / spanDays

    enum class Direction { DOWN, STEADY, UP }
}
