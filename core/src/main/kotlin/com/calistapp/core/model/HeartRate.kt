package com.calistapp.core.model

import kotlinx.serialization.Serializable

/** Sensor-reported confidence for a heart-rate reading (Health Services exposes accuracy). */
@Serializable
enum class HrConfidence { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * A single heart-rate reading. These arrive in real time from the watch (~1 Hz) and are the
 * raw material the [com.calistapp.core.calorie.CalorieEngine] integrates over time.
 */
@Serializable
data class HeartRateSample(
    val timestampMs: Long,
    val bpm: Int,
    val confidence: HrConfidence = HrConfidence.UNKNOWN,
)

/** Five-zone model expressed as a fraction of max HR. */
@Serializable
enum class HrZone(val lowerFractionOfMax: Double, val label: String) {
    ZONE1(0.50, "Very light"),
    ZONE2(0.60, "Light"),
    ZONE3(0.70, "Moderate"),
    ZONE4(0.80, "Hard"),
    ZONE5(0.90, "Maximum");

    companion object {
        fun forHr(bpm: Int, maxHr: Int): HrZone {
            val f = bpm.toDouble() / maxHr
            return when {
                f >= ZONE5.lowerFractionOfMax -> ZONE5
                f >= ZONE4.lowerFractionOfMax -> ZONE4
                f >= ZONE3.lowerFractionOfMax -> ZONE3
                f >= ZONE2.lowerFractionOfMax -> ZONE2
                else -> ZONE1
            }
        }
    }
}
