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

    val isValid: Boolean get() = invalidFields().isEmpty()

    /**
     * Which values are outside a range a human body occupies.
     *
     * Returned per field rather than as one boolean because the screen has to say *what* is wrong.
     * These feed the Keytel and Mifflin–St Jeor equations directly, so a fat-fingered weight doesn't
     * produce a slightly-off calorie count — it produces a confidently wrong one, and the app's whole
     * claim is that it doesn't do that. Bounds are deliberately generous: the job is to catch typos
     * and empty fields, not to police who may use the app.
     */
    fun invalidFields(): Set<ProfileField> = buildSet {
        if (ageYears !in Limits.AGE) add(ProfileField.AGE)
        if (weightKg !in Limits.WEIGHT_KG) add(ProfileField.WEIGHT)
        if (heightCm !in Limits.HEIGHT_CM) add(ProfileField.HEIGHT)
        if (restingHr !in Limits.RESTING_HR) add(ProfileField.RESTING_HR)
        maxHr?.let { if (it !in Limits.MAX_HR) add(ProfileField.MAX_HR) }
        vo2Max?.let { if (it !in Limits.VO2_MAX) add(ProfileField.VO2_MAX) }
        // A max below resting isn't a typo in one field or the other — it's the pair that can't be.
        val max = maxHr
        if (max != null && max <= restingHr) {
            add(ProfileField.MAX_HR)
            add(ProfileField.RESTING_HR)
        }
    }

    /** Plausible ranges for the physiological inputs. */
    object Limits {
        val AGE = 5..120
        val WEIGHT_KG = 20.0..400.0
        val HEIGHT_CM = 80.0..260.0
        val RESTING_HR = 25..120
        val MAX_HR = 100..230
        val VO2_MAX = 10.0..95.0
    }
}

/** A field of [UserProfile] that can be filled in wrongly. */
enum class ProfileField { AGE, WEIGHT, HEIGHT, RESTING_HR, MAX_HR, VO2_MAX }
