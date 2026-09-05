package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseBreakdown
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimelineTest {

    private fun active(start: Long, end: Long, slot: String, name: String, reps: Int) =
        Segment(SegmentType.ACTIVE, start, end, slotId = slot, exerciseName = name, reps = reps)

    private fun rest(start: Long, end: Long) = Segment(SegmentType.REST, start, end)

    // A → A → B, straight sets, 45s work / 90s rest.
    private val segments = listOf(
        active(0, 45_000, "s1", "A", 10),
        rest(45_000, 135_000),
        active(135_000, 180_000, "s1", "A", 12),
        rest(180_000, 270_000),
        active(270_000, 315_000, "s2", "B", 8),
    )
    private val breakdown = listOf(
        ExerciseBreakdown(slotId = "s1", exerciseName = "A", kcal = 20.0, reps = 22, sets = 2, activeDurationMs = 90_000),
        ExerciseBreakdown(slotId = "s2", exerciseName = "B", kcal = 8.0, reps = 8, sets = 1, activeDurationMs = 45_000),
    )

    @Test
    fun `groups sets by exercise in performed order`() {
        val timeline = sessionTimeline(segments, breakdown)
        assertEquals(listOf("A", "B"), timeline.map { it.name })
        assertEquals(2, timeline[0].sets)
        assertEquals(22, timeline[0].reps)
        assertEquals(1, timeline[1].sets)
    }

    @Test
    fun `span covers first set start to last set end, active time sums the sets`() {
        val a = sessionTimeline(segments, breakdown).first()
        assertEquals(180_000, a.spanMs) // 0..180000, including the rest between its two sets
        assertEquals(90_000, a.activeMs) // two 45s sets
    }

    @Test
    fun `rest before an exercise is the gap from the previous exercise, zero for the first`() {
        val timeline = sessionTimeline(segments, breakdown)
        assertEquals(0, timeline[0].restBeforeMs)
        assertEquals(90_000, timeline[1].restBeforeMs) // A's last set ends 180000, B starts 270000
    }

    @Test
    fun `joins the engine's per-exercise energy by key`() {
        val timeline = sessionTimeline(segments, breakdown)
        assertEquals(20.0, timeline[0].kcal, 0.0001)
        assertEquals(8.0, timeline[1].kcal, 0.0001)
    }

    @Test
    fun `a session with no active work has an empty timeline`() {
        assertTrue(sessionTimeline(listOf(rest(0, 1000)), emptyList()).isEmpty())
    }

    // ---- deltas ----

    private fun plan() = WorkoutPlan(
        id = "p",
        exercises = listOf(
            PlannedExercise(slotId = "s1", exerciseId = "a", name = "A"),
            PlannedExercise(slotId = "s2", exerciseId = "b", name = "B"),
            PlannedExercise(slotId = "s3", exerciseId = "c", name = "C"),
        ),
    )

    private fun log(slot: String, id: String, name: String, ix: Int, reps: Int) =
        SetLog(slotId = slot, exerciseId = id, exerciseName = name, setIndex = ix, reps = reps, startMs = 0, endMs = 1)

    @Test
    fun `delta is this session's reps minus the most recent earlier session's`() {
        val prior = PerformedSession(
            id = "old", startMs = 1_000, kcal = 0, activeDurationMs = 0,
            setLogs = listOf(log("s1", "a", "A", 1, 15), log("s2", "b", "B", 1, 8)),
            plan = plan(),
        )
        val current = PerformedSession(
            id = "cur", startMs = 2_000, kcal = 0, activeDurationMs = 0,
            setLogs = listOf(
                log("s1", "a", "A", 1, 10), log("s1", "a", "A", 2, 10), // 20 total
                log("s2", "b", "B", 1, 8),
                log("s3", "c", "C", 1, 5), // first appearance
            ),
            plan = plan(),
        )
        val deltas = lastSessionDeltas("cur", listOf(current, prior))
        assertEquals(5, deltas["A"]) // 20 vs 15
        assertEquals(0, deltas["B"]) // 8 vs 8
        assertNull(deltas["C"]) // nothing to compare against
    }
}
