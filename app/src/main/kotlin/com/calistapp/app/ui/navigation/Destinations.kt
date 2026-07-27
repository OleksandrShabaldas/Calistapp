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

    const val DETAIL = "detail/{sessionId}"
    fun detail(sessionId: String) = "detail/$sessionId"

    const val EXERCISE_DETAIL = "exerciseDetail/{exerciseId}"
    fun exerciseDetail(exerciseId: String) = "exerciseDetail/$exerciseId"

    const val EXERCISE_EDIT = "exerciseEdit?exerciseId={exerciseId}"
    const val EXERCISE_EDIT_ARG = "exerciseId"
    fun exerciseEdit(exerciseId: String? = null): String =
        if (exerciseId == null) "exerciseEdit" else "exerciseEdit?exerciseId=$exerciseId"

    val bottomBarRoutes = setOf(DASHBOARD, EXERCISES, HISTORY, PROFILE)
}
