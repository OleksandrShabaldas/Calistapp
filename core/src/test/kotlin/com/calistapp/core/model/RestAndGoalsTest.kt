package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestAndGoalsTest {

    private val slot = PlannedExercise(slotId = "a", exerciseId = "e", name = "Pull-Up")

    @Test
    fun `rest defaults to ninety seconds and reads as a clock`() {
        assertTrue(slot.isRestTimed)
        assertEquals("1:30", slot.restLabel)
    }

    @Test
    fun `zero rest means untimed, not instant`() {
        // The distinction the live screen depends on: nothing should count down or buzz.
        val untimed = slot.copy(restSeconds = 0)

        assertFalse(untimed.isRestTimed)
        assertNull(untimed.restLabel)
    }

    @Test
    fun `rest under a minute still pads its seconds`() {
        assertEquals("0:45", slot.copy(restSeconds = 45).restLabel)
        assertEquals("0:05", slot.copy(restSeconds = 5).restLabel)
        assertEquals("3:00", slot.copy(restSeconds = 180).restLabel)
    }

    @Test
    fun `a plan saved before rest existed still parses, with the default`() {
        // Older plans are stored JSON without the field; the default is what they get.
        assertEquals(90, PlannedExercise(slotId = "s", exerciseId = "e", name = "Dip").restSeconds)
    }

    @Test
    fun `goals default to something usable and validate their ranges`() {
        assertTrue(TrainingGoals().isValid)
        assertFalse(TrainingGoals(weeklyKcal = 0).isValid)
        assertFalse(TrainingGoals(weeklySessions = 0).isValid)
        assertFalse(TrainingGoals(weeklySessions = 20).isValid)
        assertTrue(TrainingGoals(weeklyKcal = 100, weeklySessions = 1).isValid)
    }

    @Test
    fun `a saved workout describes itself well enough to recognise`() {
        val plan = WorkoutPlan(
            id = "p",
            name = "Push day",
            exercises = listOf(slot, slot.copy(slotId = "b", name = "Dip")),
        )
        val saved = SavedWorkout("w", "Push day", plan, createdMs = 0, lastUsedMs = null)

        assertEquals("2 exercises · 6 sets", saved.summaryLabel)
    }

    @Test
    fun `a circuit says how many rounds`() {
        val plan = WorkoutPlan(
            id = "p",
            exercises = listOf(slot),
            style = WorkoutStyle.CIRCUIT,
            rounds = 5,
        )
        val saved = SavedWorkout("w", "Circuit", plan, createdMs = 0, lastUsedMs = null)

        assertEquals("1 exercise · 5 sets · 5 rounds", saved.summaryLabel)
    }
}
