package com.calistapp.core.progress

import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import kotlin.math.exp

/**
 * How much a session actually asked of you, and whether the last week is out of step with the last
 * month.
 *
 * The app already knows heart rate, duration and your resting/max — which is precisely the input
 * Banister's TRIMP was defined on. It is the standard way to put a number on "how hard was that",
 * comparable between a long easy session and a short brutal one in a way calories are not: 300 kcal
 * of steady work and 300 kcal of intervals cost the same energy and very different recovery.
 */
object TrainingLoad {

    /**
     * Banister TRIMP for one session.
     *
     * `duration × ΔHR × k·e^(b·ΔHR)`, where ΔHR is heart-rate reserve used. The exponential is what
     * makes hard minutes count for more than easy ones; the sex-specific constants come from the
     * original fit to blood-lactate response.
     *
     * Returns 0 when heart rate never rose above resting, or when the profile can't give a usable
     * reserve — a nonsense denominator should produce nothing, not a large number.
     */
    fun trimp(avgHr: Int, durationMs: Long, profile: UserProfile): Double {
        val reserve = profile.effectiveMaxHr - profile.restingHr
        if (reserve <= 0 || durationMs <= 0) return 0.0

        val fraction = ((avgHr - profile.restingHr).toDouble() / reserve).coerceIn(0.0, 1.0)
        if (fraction <= 0.0) return 0.0

        val (k, b) = when (profile.sex) {
            Sex.MALE -> 0.64 to 1.92
            Sex.FEMALE -> 0.86 to 1.67
        }
        val minutes = durationMs / 60_000.0
        return minutes * fraction * k * exp(b * fraction)
    }

    /**
     * Acute load against chronic load — the last week measured against the four-week average week.
     *
     * Deliberately reported as a ratio and a direction, not as a risk score. The acute:chronic
     * literature is genuinely contested and this app has no business telling anyone they are about
     * to get injured; what it can honestly say is whether this week looks like the weeks before it.
     *
     * Null until there is a month of history to average — a ratio computed against two weeks of data
     * is noise with a decimal point.
     */
    fun ramp(loadsByDayDescending: List<Double>): Ramp? {
        if (loadsByDayDescending.size < CHRONIC_DAYS) return null

        val acute = loadsByDayDescending.take(ACUTE_DAYS).sum()
        val chronic = loadsByDayDescending.take(CHRONIC_DAYS).sum() / (CHRONIC_DAYS / ACUTE_DAYS)
        if (chronic <= 0.0) return null

        return Ramp(acuteLoad = acute, chronicLoad = chronic, ratio = acute / chronic)
    }

    data class Ramp(
        /** Total load over the last seven days. */
        val acuteLoad: Double,
        /** What an average week looked like over the last twenty-eight. */
        val chronicLoad: Double,
        val ratio: Double,
    ) {
        val band: Band
            get() = when {
                ratio < 0.8 -> Band.EASING_OFF
                ratio <= 1.3 -> Band.STEADY
                ratio <= 1.5 -> Band.RAMPING
                else -> Band.SHARP_JUMP
            }
    }

    /** Plain descriptions of the ratio. Not a diagnosis, and worded so as not to read like one. */
    enum class Band(val label: String, val detail: String) {
        EASING_OFF(
            "Easing off",
            "This week is lighter than your recent average. Fine if it's deliberate — a rest week is training too.",
        ),
        STEADY(
            "Steady",
            "This week looks like the weeks before it.",
        ),
        RAMPING(
            "Ramping up",
            "A bigger week than usual. Worth noticing, not worth worrying about on its own.",
        ),
        SHARP_JUMP(
            "Sharp jump",
            "Well above your recent average. Big single-week jumps are the ones people tend to regret.",
        ),
    }

    const val ACUTE_DAYS = 7
    const val CHRONIC_DAYS = 28
}
