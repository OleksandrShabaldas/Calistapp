package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsProgressTest {

    private fun plan(weightKg: Double = 0.0) = WorkoutPlan(
        id = "p",
        exercises = listOf(
            PlannedExercise(
                slotId = "s1", exerciseId = "a", name = "A",
                metabolics = ExerciseMetabolics(externalLoadKg = weightKg),
            ),
        ),
    )

    private fun log(reps: Int, at: Long) =
        SetLog(slotId = "s1", exerciseId = "a", exerciseName = "A", setIndex = 1, reps = reps, startMs = at, endMs = at + 1)

    private fun session(id: String, startMs: Long, reps: Int, weightKg: Double = 0.0) = PerformedSession(
        id = id, startMs = startMs, kcal = 0, activeDurationMs = 0,
        setLogs = listOf(log(reps, startMs)), plan = plan(weightKg),
    )

    @Test
    fun `a reps record carries the previous best and when it was set`() {
        val prior = session("old", startMs = 111, reps = 15)
        val current = session("cur", startMs = 222, reps = 20)

        val records = personalRecords(listOf(current, prior), "cur")
        assertEquals(1, records.size)
        val r = records.single()
        assertEquals(RecordKind.REPS, r.kind)
        assertEquals("20 reps", r.label)
        assertEquals("a", r.exerciseKey)
        assertEquals("15 reps", r.previousLabel)
        assertEquals(111L, r.previousAtMs)
    }

    @Test
    fun `a movement's first appearance is not a record`() {
        val first = session("cur", startMs = 100, reps = 12)
        assertTrue(personalRecords(listOf(first), "cur").isEmpty())
    }

    @Test
    fun `progression is the per-session best of the metric, oldest to newest`() {
        val a = session("old", startMs = 100, reps = 15)
        val b = session("cur", startMs = 200, reps = 20)

        val points = bestProgression(listOf(b, a), "a", RecordKind.REPS)
        assertEquals(listOf(15.0, 20.0), points.map { it.value })
        assertEquals(listOf(100L, 200L), points.map { it.atMs })
    }

    @Test
    fun `progression skips sessions that did not include the movement`() {
        val withIt = session("s1", startMs = 100, reps = 15)
        val without = PerformedSession(
            id = "s2", startMs = 200, kcal = 0, activeDurationMs = 0,
            setLogs = listOf(SetLog(slotId = "sX", exerciseId = "x", exerciseName = "X", setIndex = 1, reps = 9, startMs = 200, endMs = 201)),
            plan = WorkoutPlan(id = "p2", exercises = listOf(PlannedExercise(slotId = "sX", exerciseId = "x", name = "X"))),
        )
        val points = bestProgression(listOf(withIt, without), "a", RecordKind.REPS)
        assertEquals(1, points.size)
        assertNull(points.firstOrNull { it.atMs == 200L })
    }
}
