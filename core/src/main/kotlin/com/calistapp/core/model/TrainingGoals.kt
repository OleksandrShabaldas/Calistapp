package com.calistapp.core.model

/**
 * What the week is being measured against.
 *
 * Separate from [UserProfile] because these aren't facts about a body — they're a choice about
 * training, and they change for reasons the physiology doesn't. The dashboard's progress ring was
 * previously scaled by a constant compiled into the screen, which made it a decoration rather than
 * a target.
 */
data class TrainingGoals(
    val weeklyKcal: Int = DEFAULT_WEEKLY_KCAL,
    val weeklySessions: Int = DEFAULT_WEEKLY_SESSIONS,
    /**
     * The daily *step* target. The dashboard turns it into a calorie goal (via the FitPal per-step
     * rate) and credits workout calories against it, so a training day needs fewer steps — see
     * `DailyEnergyGoal`. The user picks steps because that's the number they already think in.
     */
    val dailyStepGoal: Int = DEFAULT_DAILY_STEP_GOAL,
) {
    val isValid: Boolean
        get() = weeklyKcal in WEEKLY_KCAL_RANGE &&
            weeklySessions in WEEKLY_SESSIONS_RANGE &&
            dailyStepGoal in DAILY_STEP_GOAL_RANGE

    companion object {
        /** Roughly four moderate sessions — a starting point, not a prescription. */
        const val DEFAULT_WEEKLY_KCAL = 3500
        const val DEFAULT_WEEKLY_SESSIONS = 4
        /** A common "active" daily target — a starting point, not a prescription. */
        const val DEFAULT_DAILY_STEP_GOAL = 8000

        val WEEKLY_KCAL_RANGE = 100..20_000
        val WEEKLY_SESSIONS_RANGE = 1..14
        val DAILY_STEP_GOAL_RANGE = 1_000..50_000
    }
}
