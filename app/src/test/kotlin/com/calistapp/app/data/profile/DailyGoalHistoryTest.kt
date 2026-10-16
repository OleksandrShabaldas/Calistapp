package com.calistapp.app.data.profile

import com.calistapp.core.model.TrainingGoals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyGoalHistoryTest {
    @Test fun `step equivalents and calorie goals agree across month boundaries`() {
        val day = LocalDate.of(2026, 9, 30)
        val history = DailyGoalHistory(
            goals = TrainingGoals(dailyStepGoal = 10_000, reduceGoalAfterActiveDay = true),
            rate = 0.034,
            earned = mapOf(day to 680.0, day.plusDays(1) to 510.0),
        )
        assertEquals(340, history.originalKcal)
        assertEquals(255, history.targetOn(day.plusDays(1)))
        assertEquals(7_500, history.stepsOn(day.plusDays(1)))
        assertEquals(255, history.targetOn(day.plusDays(2)))
        assertEquals(7_500, history.stepsOn(day.plusDays(2)))
        assertEquals(340, history.targetOn(day.plusDays(3)))
    }

    @Test fun `switching off restores the original goal for every day`() {
        val day = LocalDate.of(2026, 9, 30)
        val history = DailyGoalHistory(goals = TrainingGoals(dailyStepGoal = 10_000), rate = 0.04,
            earned = mapOf(day to 4_000.0))
        assertEquals(400, history.targetOn(day.plusDays(1)))
        assertEquals(10_000, history.stepsOn(day.plusDays(1)))
    }
}
