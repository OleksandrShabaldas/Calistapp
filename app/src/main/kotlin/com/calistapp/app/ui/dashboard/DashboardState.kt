package com.calistapp.app.ui.dashboard

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
)

data class WeekState(
    val days: List<DayCell> = emptyList(),
    val totalKcal: Int = 0,
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

/** The Next Up card — the next planned workout, or a saved one to fall back on. */
data class NextUpState(
    val savedWorkoutId: String,
    val name: String,
    /** "7 exercises · 21 sets". */
    val meta: String,
    val imageUrls: List<String> = emptyList(),
    /** "Today" / "Tomorrow" / "Thursday" / "Saved". */
    val whenLabel: String = "",
    val scheduled: Boolean = false,
)
