package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.TimeBucket
import com.calistapp.core.model.UserProfile

/**
 * Calories for a workout with no usable heart rate — no device connected, or what a device recorded
 * covers too little of the session to integrate honestly: a ring that samples every few minutes, a
 * watch that dropped out after two readings.
 *
 * Heart rate stays the backbone of every figure the app produces; this exists only so a workout
 * without it isn't filed at zero. It is deliberately simple and conservative, it never mixes with a
 * real heart-rate stream (a session either has usable heart rate or is estimated whole — gaps in a
 * real stream are still never guessed across), and every session it scores is marked
 * [com.calistapp.core.model.SessionSummary.estimated] so the screens say so.
 *
 * Each block is costed from the Compendium of Physical Activities (2024) metabolic equivalents, net of
 * resting metabolism like the heart-rate path: (MET − 1) × 3.5 ml O₂/kg/min ≈ (MET − 1) × kg × 3.5 / 200
 * kcal/min. A working set is scaled by how much muscle the movement recruits (the same term the
 * heart-rate path's exercise correction uses), and never scores below the rep work it physically did.
 */
object NoHeartRateEstimate {
    /** A working set of a rep-based movement — the Compendium's vigorous resistance training (6.0). */
    const val MET_SET = 6.0

    /** A static hold (plank, L-sit, hang) — the Compendium's moderate calisthenics, rounded up (4.0). */
    const val MET_HOLD = 4.0

    /** A guided warm-up or cool-down move — the Compendium's stretching, mild (2.3). */
    const val MET_ROUTINE = 2.3

    /** Recovery between sets — standing, breathing it off (1.5). */
    const val MET_REST = 1.5

    /**
     * A session whose heart rate covers less than this share of it is scored by this estimate instead.
     * At half or more the stream is real data and the heart-rate path scores it (its gaps uncredited,
     * as always); below half there isn't enough of it to stand for the workout.
     */
    const val MIN_HR_COVERAGE = 0.5

    fun metFor(type: SegmentType, bucket: TimeBucket, metabolics: ExerciseMetabolics?): Double = when {
        type == SegmentType.REST -> MET_REST
        bucket == TimeBucket.WARM_UP || bucket == TimeBucket.COOL_DOWN -> MET_ROUTINE
        metabolics?.isometric == true -> MET_HOLD
        else -> MET_SET
    }

    /** Energy per minute at [met], net of resting metabolism unless [netOfResting] is false. */
    fun kcalPerMin(met: Double, profile: UserProfile, netOfResting: Boolean = true): Double =
        (if (netOfResting) met - 1.0 else met).coerceAtLeast(0.0) * 3.5 * profile.weightKg / 200.0
}
