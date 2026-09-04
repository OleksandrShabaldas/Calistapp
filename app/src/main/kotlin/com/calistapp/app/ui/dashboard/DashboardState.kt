package com.calistapp.app.ui.dashboard

import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.progress.StreakStats
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

/** One square in the streak heatmap: a day, its burn, and how it compares to the goal. */
data class HeatCell(
    val date: LocalDate,
    val kcal: Int,
    /** earned ÷ target — ≥1 hit the goal, >1 over it (up to 2 = full red), <1 under (fades out). */
    val fraction: Float,
)

/** Everything the streak popup renders. */
data class StreakData(
    val stats: StreakStats = StreakStats(0, 0, 0, null, 0, null, 0, 0, 0, emptyMap(), null, 0),
    /** firstDay → today, one per day, for the scrollable heatmap. */
    val cells: List<HeatCell> = emptyList(),
    val targetKcal: Int = 0,
)

/** One bar in the steps popup's 30-day chart. */
data class DayStep(val date: LocalDate, val steps: Int)

/** Everything the steps popup renders. */
data class StepsInsights(
    val last30: List<DayStep> = emptyList(),
    val today: Int = 0,
    val goal: Int = TrainingGoals.DEFAULT_DAILY_STEP_GOAL,
    val avg7d: Int = 0,
    val bestDaySteps: Int = 0,
    val bestDay: LocalDate? = null,
    /** Consecutive days that hit the raw step goal, counting back from today. */
    val stepGoalStreak: Int = 0,
    val distanceKm: Double = 0.0,
    val activeKcal: Int = 0,
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
