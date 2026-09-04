package com.calistapp.app.ui.dashboard

import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.TrainingGoals
import java.time.LocalDate

/** The greeting header + streak pill. */
data class HeaderState(
    val name: String = "",
    /** "Good morning" / "Good afternoon" / "Good evening". */
    val greeting: String = "Welcome",
    /** "TUESDAY · SEP 2". */
    val dateLabel: String = "",
    val streak: Int = 0,
)

/** One column in the weekly bar chart. */
data class DayCell(
    val date: LocalDate,
    /** "M".."S". */
    val letter: String,
    val kcal: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    /** A workout was recorded this day → the orange dot. */
    val trained: Boolean,
    /** A workout is scheduled this day → the grey dot (when not [trained]). */
    val planned: Boolean,
    /** Sunday, drawn red in the strip. */
    val isSunday: Boolean = false,
)

data class WeekState(
    val days: List<DayCell> = emptyList(),
    val totalKcal: Int = 0,
    /** The week total, split for the tap-the-number breakdown popup. */
    val stepKcal: Int = 0,
    val workoutKcal: Int = 0,
    /** "This week" for the current week, otherwise a date range like "1–7 Sep". */
    val title: String = "This week",
    val isCurrentWeek: Boolean = true,
) {
    /** For scaling bar heights; never zero so an empty week doesn't divide by nothing. */
    val maxKcal: Int get() = (days.maxOfOrNull { it.kcal } ?: 0).coerceAtLeast(1)
}

/** Today's steps and the daily energy-goal ring. */
data class StepsState(
    val steps: Int = 0,
    val stepGoal: Int = TrainingGoals.DEFAULT_DAILY_STEP_GOAL,
    val earnedKcal: Int = 0,
    val targetKcal: Int = 0,
    val progress: Float = 0f,
    val goalMet: Boolean = false,
)

/** A past day the user tapped to inspect — its steps, energy and the workouts logged that day. */
data class DayView(
    val date: LocalDate,
    /** "Wednesday, Sep 2". */
    val dateLabel: String,
    val steps: Int,
    val stepGoal: Int,
    val earnedKcal: Int,
    val targetKcal: Int,
    val progress: Float,
    val sessions: List<SessionOverview>,
)

/** The Next Up card — the next planned workout, or a saved one to fall back on. */
data class NextUpState(
    val savedWorkoutId: String,
    val name: String,
    /** "7 exercises · 21 sets". */
    val meta: String,
    /** One demo video per exercise, in plan order, cycled in the card. Empty → fall back to images. */
    val videoUrls: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    /** "Today" / "Tomorrow" / "Thursday" / "Saved". */
    val whenLabel: String = "",
    val scheduled: Boolean = false,
)
