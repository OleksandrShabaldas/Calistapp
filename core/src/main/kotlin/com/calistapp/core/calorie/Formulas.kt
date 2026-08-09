package com.calistapp.core.calorie

import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile

/**
 * Peer-reviewed energy-expenditure formulas used by the [CalorieEngine].
 *
 * We deliberately avoid the "average the whole workout's HR and multiply by a constant"
 * shortcut most trackers use. Instead we evaluate energy expenditure at the instantaneous
 * heart rate and integrate over time (see [CalorieEngine]).
 */
object Formulas {

    private const val KJ_PER_KCAL = 4.184

    /**
     * Which of the four Keytel regressions a profile selects, and the equation it evaluates.
     *
     * The text lives next to the implementation so the two can't drift: [keytelKcalPerMin] is the
     * only place these coefficients appear, and this is the only place they're written out for
     * display.
     */
    enum class KeytelVariant(val label: String, val equation: String) {
        MALE_VO2(
            "Male, VO₂max known",
            "kJ/min = −59.3954 + 0.634×HR + 0.404×VO₂max + 0.394×weight + 0.271×age",
        ),
        FEMALE_VO2(
            "Female, VO₂max known",
            "kJ/min = −59.3954 + 0.450×HR + 0.380×VO₂max + 0.103×weight + 0.274×age",
        ),
        MALE_BASIC(
            "Male",
            "kJ/min = −55.0969 + 0.6309×HR + 0.1988×weight + 0.2017×age",
        ),
        FEMALE_BASIC(
            "Female",
            "kJ/min = −20.4022 + 0.4472×HR − 0.1263×weight + 0.0740×age",
        ),
    }

    /** The regression [keytelKcalPerMin] will use for this profile. */
    fun keytelVariant(profile: UserProfile): KeytelVariant = when {
        profile.vo2Max != null && profile.sex == Sex.MALE -> KeytelVariant.MALE_VO2
        profile.vo2Max != null -> KeytelVariant.FEMALE_VO2
        profile.sex == Sex.MALE -> KeytelVariant.MALE_BASIC
        else -> KeytelVariant.FEMALE_BASIC
    }

    /**
     * Keytel et al. (2005) heart-rate → energy-expenditure regression.
     * Returns kcal per minute at the given instantaneous heart rate.
     *
     * When [UserProfile.vo2Max] is known we use the fitness-adjusted variant, which
     * accounts for cardiovascular efficiency and is materially more accurate; otherwise
     * we fall back to the age/weight/sex-only variant.
     *
     * Validated for HR roughly in the 90–150 bpm range during steady exercise; the
     * engine applies a resting-metabolic floor for low-HR / rest periods.
     */
    fun keytelKcalPerMin(bpm: Int, profile: UserProfile): Double {
        val hr = bpm.toDouble()
        val w = profile.weightKg
        val a = profile.ageYears.toDouble()
        val vo2 = profile.vo2Max

        val kJPerMin = if (vo2 != null) {
            when (profile.sex) {
                // Fitness-adjusted Keytel (VO2max included)
                Sex.MALE -> -59.3954 + 0.634 * hr + 0.404 * vo2 + 0.394 * w + 0.271 * a
                Sex.FEMALE -> -59.3954 + 0.450 * hr + 0.380 * vo2 + 0.103 * w + 0.274 * a
            }
        } else {
            when (profile.sex) {
                Sex.MALE -> -55.0969 + 0.6309 * hr + 0.1988 * w + 0.2017 * a
                Sex.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * w + 0.074 * a
            }
        }
        return kJPerMin / KJ_PER_KCAL
    }

    /**
     * Energy expenditure **above** what the body would have spent at rest anyway, in kcal/min.
     *
     * [keytelKcalPerMin] returns *gross* expenditure — the body's total metabolic rate at that heart
     * rate, resting metabolism included. Reporting that as "calories burned" credits the workout with
     * energy you'd have spent lying on the sofa, which is the single largest source of overcounting in
     * HR-based trackers: at a typical resting rate of ~1.3 kcal/min it inflates an hour's session by
     * roughly 80 kcal before any other error.
     *
     * Net expenditure is what "active calories" means on every serious tracker, and it's the figure
     * that's meaningful for energy balance. Floored at zero — a heart rate at or below resting
     * represents no exercise energy, not negative energy.
     */
    fun netKcalPerMin(grossKcalPerMin: Double, restingKcalPerMin: Double): Double =
        (grossKcalPerMin - restingKcalPerMin).coerceAtLeast(0.0)

    /**
     * Mifflin–St Jeor basal metabolic rate in kcal/day. Used to derive a per-minute
     * resting floor so calorie counts never fall below true resting metabolism.
     */
    fun mifflinBmrKcalPerDay(profile: UserProfile): Double = resting(profile).kcalPerDay

    /** Resting metabolic rate expressed per minute (kcal/min). */
    fun restingKcalPerMin(profile: UserProfile): Double = resting(profile).kcalPerMin

    /** Every term of the Mifflin–St Jeor substitution, so the resting figure can be checked by hand. */
    data class Resting(
        val weightTerm: Double,
        val heightTerm: Double,
        val ageTerm: Double,
        val sexOffset: Double,
        val kcalPerDay: Double,
    ) {
        val kcalPerMin: Double get() = kcalPerDay / 1440.0
    }

    /** [mifflinBmrKcalPerDay] with its terms exposed. */
    fun resting(profile: UserProfile): Resting {
        val weightTerm = 10 * profile.weightKg
        val heightTerm = 6.25 * profile.heightCm
        val ageTerm = -5.0 * profile.ageYears
        val sexOffset = when (profile.sex) {
            Sex.MALE -> 5.0
            Sex.FEMALE -> -161.0
        }
        return Resting(
            weightTerm = weightTerm,
            heightTerm = heightTerm,
            ageTerm = ageTerm,
            sexOffset = sexOffset,
            kcalPerDay = weightTerm + heightTerm + ageTerm + sexOffset,
        )
    }
}
