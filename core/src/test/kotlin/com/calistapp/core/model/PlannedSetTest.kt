package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-set model, and — most importantly — that a uniform slot still behaves exactly as before. */
class PlannedSetTest {

    private fun slot(
        sets: Int = 3,
        reps: Int = 10,
        warmups: Int = 0,
        kg: Double = 0.0,
        measure: ExerciseMeasure = ExerciseMeasure.REPS,
        seconds: Int = 45,
        planned: List<PlannedSet> = emptyList(),
    ) = PlannedExercise(
        slotId = "s",
        exerciseId = "e",
        name = "Pull-Up",
        targetSets = sets,
        targetReps = reps,
        warmupSets = warmups,
        measure = measure,
        targetSeconds = seconds,
        metabolics = ExerciseMetabolics(externalLoadKg = kg),
        plannedSets = planned,
    )

    @Test
    fun `a uniform slot synthesizes one set per target, carrying reps weight and warm-ups`() {
        val s = slot(sets = 4, reps = 8, warmups = 1, kg = 20.0).sets()
        assertEquals(4, s.size)
        assertTrue(s.all { it.reps == 8 && it.weightKg == 20.0 })
        assertTrue(s[0].isWarmup)
        assertFalse(s[1].isWarmup)
    }

    @Test
    fun `a hold synthesizes its seconds into the set value`() {
        val s = slot(sets = 2, measure = ExerciseMeasure.SECONDS, seconds = 30).sets()
        assertTrue(s.all { it.reps == 30 })
    }

    @Test
    fun `explicit per-set list takes over from the uniform fields`() {
        val slot = slot(
            planned = listOf(
                PlannedSet(reps = 12, weightKg = 0.0, isWarmup = true),
                PlannedSet(reps = 8, weightKg = 20.0),
                PlannedSet(reps = 6, weightKg = 25.0),
            ),
        )
        val s = slot.sets()
        assertEquals(3, s.size)
        assertEquals(6, s[2].reps)
        assertEquals(25.0, s[2].weightKg, 0.0)
        assertTrue(slot.isWarmup(1))
        assertFalse(slot.isWarmup(2))
    }

    @Test
    fun `uniform target label is unchanged by the new derivation`() {
        assertEquals("3 × 10", slot(sets = 3, reps = 10).targetLabel)
        assertEquals("3 × 10 · +20 kg", slot(sets = 3, reps = 10, kg = 20.0).targetLabel)
        assertEquals("2 × 45s", slot(sets = 2, measure = ExerciseMeasure.SECONDS, seconds = 45).targetLabel)
    }

    @Test
    fun `a varying column reads as its figures`() {
        val label = slot(
            planned = listOf(PlannedSet(reps = 12), PlannedSet(reps = 10), PlannedSet(reps = 8)),
        ).targetLabel
        assertEquals("12/10/8", label)
    }

    @Test
    fun `plan set counts follow the per-set column for a split`() {
        val plan = WorkoutPlan(
            id = "p",
            exercises = listOf(
                slot(planned = listOf(PlannedSet(reps = 5), PlannedSet(reps = 5), PlannedSet(reps = 5), PlannedSet(reps = 5))),
            ),
            style = WorkoutStyle.BY_EXERCISE,
        )
        assertEquals(4, plan.targetSetsFor("s"))
        assertEquals(4, plan.totalSets)
    }
}
