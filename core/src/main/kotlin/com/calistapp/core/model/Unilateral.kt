package com.calistapp.core.model

/**
 * Whether a movement is worked one side at a time — a Bulgarian split squat, a lunge, a one-arm row,
 * a pistol. For these the app logs reps as the TOTAL across both sides (20 per leg is logged as 40),
 * which is what a set counter naturally produces and what the calorie engine wants (each rep is one
 * side's work). Anything that reads those numbers back — the AI coach above all — must be told, or it
 * takes "40 reps" as 40 per leg and coaches against a set that never happened.
 *
 * There's no unilateral flag in the exercise data (free-exercise-db doesn't carry one), so this reads
 * the name. It's deliberately conservative: a miss only means the coach gets no per-side hint, while a
 * false positive would halve a real bilateral number, so only unambiguous single-side cues count.
 */
fun isUnilateral(name: String): Boolean {
    val n = name.lowercase()
    return UNILATERAL_CUES.any { it in n }
}

private val UNILATERAL_CUES = listOf(
    "single-leg", "single leg", "one-leg", "one leg",
    "single-arm", "single arm", "one-arm", "one arm", "one-handed", "one handed",
    "unilateral", "alternating", "alternate",
    "split squat", "bulgarian", "lunge", "step-up", "step up",
    "pistol", "shrimp squat", "cossack", "archer",
)
