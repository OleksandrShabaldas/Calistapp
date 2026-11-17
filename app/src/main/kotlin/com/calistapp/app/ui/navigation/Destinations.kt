package com.calistapp.app.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val EXERCISES = "exercises"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    const val ACTIVE = "active?exerciseId={exerciseId}"
    const val ACTIVE_ARG = "exerciseId"
    fun active(exerciseId: String? = null): String =
        if (exerciseId == null) "active" else "active?exerciseId=$exerciseId"

    /** Build the workout — pick exercises, sets and reps — before starting a session. */
    const val PLANNER = "planner"

    /** Pre-flight: warm-up, stretch, session settings and timers, then start. */
    const val SETUP = "setup"

    /** Mid-workout: the exercise picker, adding to the running session rather than the plan draft. */
    const val LIVE_ADD = "liveAdd"

    /** Plan the week — assign saved workouts to weekdays and reschedule this week. */
    const val SCHEDULE = "schedule"

    /** A saved workout on its own screen — exercises, history, start, edit. */
    const val SAVED_WORKOUT = "savedWorkout/{id}"
    const val SAVED_WORKOUT_ARG = "id"
    fun savedWorkout(id: String) = "savedWorkout/$id"

    const val DETAIL = "detail/{sessionId}"
    fun detail(sessionId: String) = "detail/$sessionId"

    const val EXERCISE_DETAIL = "exerciseDetail/{exerciseId}?origin={origin}"
    const val EXERCISE_DETAIL_ARG = "exerciseId"
    const val EXERCISE_DETAIL_ORIGIN = "origin"

    /** [origin] the screen was opened from — [ORIGIN_PICKER] flips its CTA to "Add to workout". */
    const val ORIGIN_PICKER = "picker"

    fun exerciseDetail(exerciseId: String, origin: String? = null): String =
        if (origin == null) "exerciseDetail/$exerciseId" else "exerciseDetail/$exerciseId?origin=$origin"

    const val EXERCISE_EDIT = "exerciseEdit?exerciseId={exerciseId}"
    const val EXERCISE_EDIT_ARG = "exerciseId"
    fun exerciseEdit(exerciseId: String? = null): String =
        if (exerciseId == null) "exerciseEdit" else "exerciseEdit?exerciseId=$exerciseId"

    /** One Insights question on its own page, grown out of its card on the History tab. */
    const val INSIGHT = "insight/{question}"
    const val INSIGHT_ARG = "question"
    fun insight(question: String) = "insight/$question"

    /** The medal case. [focus] is an award key ("pull-ups#1") to open lifted out, from Insights. */
    const val MEDALS = "medals?focus={focus}"
    const val MEDALS_ARG = "focus"
    fun medals(focus: String? = null) = if (focus == null) "medals" else "medals?focus=${android.net.Uri.encode(focus)}"

    val bottomBarRoutes = setOf(DASHBOARD, EXERCISES, HISTORY, PROFILE)
}
