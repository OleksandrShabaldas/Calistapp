package com.calistapp.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Sex { MALE, FEMALE }

/**
 * The physiological inputs that drive personalized energy-expenditure estimates.
 *
 * The more of these that are accurate (especially [vo2Max]), the closer the calorie
 * estimate gets to indirect-calorimetry ground truth. See CalorieEngine for how each
 * field is used.
 */
@Serializable
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val ageYears: Int = 30,
    val weightKg: Double = 75.0,
    val heightCm: Double = 178.0,
    /** Resting heart rate in bpm — used for reserve-based zones and rest-quality metrics. */
    val restingHr: Int = 60,
    /** Measured/known max HR. When null, estimated from age (Tanaka). */
    val maxHr: Int? = null,
    /** VO2max in ml/kg/min. Optional but meaningfully improves calorie accuracy when present. */
    val vo2Max: Double? = null,
) {
    /** Tanaka (2001): 208 - 0.7 * age. Falls back to this when [maxHr] is unknown. */
    val effectiveMaxHr: Int
        get() = maxHr ?: (208 - 0.7 * ageYears).toInt()

    val isValid: Boolean
        get() = ageYears in 5..120 && weightKg in 20.0..400.0 && heightCm in 80.0..260.0
}
