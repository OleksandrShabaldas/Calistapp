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
    fun mifflinBmrKcalPerDay(profile: UserProfile): Double {
        val base = 10 * profile.weightKg + 6.25 * profile.heightCm - 5 * profile.ageYears
        return when (profile.sex) {
            Sex.MALE -> base + 5
            Sex.FEMALE -> base - 161
        }
    }

    /** Resting metabolic rate expressed per minute (kcal/min). */
    fun restingKcalPerMin(profile: UserProfile): Double = mifflinBmrKcalPerDay(profile) / 1440.0
}
