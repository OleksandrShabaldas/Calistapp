package com.calistapp.core.calorie

import com.calistapp.core.model.UserProfile

/**
 * VO₂max estimated from the ratio of maximum to resting heart rate.
 *
 * Uth, Sørensen, Overgaard & Pedersen (2004): `VO₂max ≈ 15.3 × HRmax / HRrest`. It needs nothing the
 * profile doesn't already hold, which is what makes it worth offering — [Formulas.keytelKcalPerMin]
 * switches to its fitness-adjusted variant the moment a VO₂max exists, so filling this in measurably
 * improves every calorie figure the app produces afterwards.
 *
 * It is an estimate of an estimate, and the app says so: the original study reports roughly ±10–15%
 * against measured values, and it degrades when either input is guessed rather than known. A real
 * lab or field test beats it and should replace it.
 */
object Vo2MaxEstimate {

    private const val COEFFICIENT = 15.3

    /** Plausible span for the result. Outside it, the inputs are wrong rather than the person. */
    private val CREDIBLE = 15.0..90.0

    /**
     * The estimate, or null when the profile can't support one.
     *
     * Requires a *measured* max heart rate: with an age-estimated max the formula collapses to a
     * function of age and resting HR, which is a materially weaker claim than the study supports.
     */
    fun forProfile(profile: UserProfile): Double? {
        val maxHr = profile.maxHr ?: return null
        if (profile.restingHr <= 0 || maxHr <= profile.restingHr) return null

        val estimate = COEFFICIENT * maxHr / profile.restingHr
        return estimate.takeIf { it in CREDIBLE }?.let { kotlin.math.round(it * 10) / 10.0 }
    }

    /** Why the estimate isn't available, for a screen that has to explain itself. */
    fun blockedReason(profile: UserProfile): String? = when {
        profile.maxHr == null ->
            "Add your measured max heart rate and this can be estimated from it."
        profile.restingHr <= 0 ->
            "Add your resting heart rate and this can be estimated from it."
        profile.maxHr <= profile.restingHr ->
            "Your max heart rate needs to be above your resting one."
        forProfile(profile) == null ->
            "Those two heart rates give an implausible result — worth double-checking them."
        else -> null
    }
}
