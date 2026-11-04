package com.calistapp.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SessionTimingTest {
    @Test fun `old opening rest is warmup and gaps stay paused`() {
        val segments = listOf(
            Segment(SegmentType.REST, 0, 60_000),
            Segment(SegmentType.REST, 90_000, 150_000),
            Segment(SegmentType.ACTIVE, 150_000, 180_000),
            Segment(SegmentType.REST, 180_000, 210_000),
        )
        val original = SessionSummary.EMPTY.copy(totalKcal = 42.0, activeKcal = 30.0, restKcal = 12.0)
        val summary = original.withSessionTiming(segments.reversed(), 210_000)
        assertEquals(120_000L, summary.warmupDurationMs)
        assertEquals(30_000L, summary.restDurationMs)
        assertEquals(30_000L, summary.activeDurationMs)
        assertEquals(180_000L, summary.totalDurationMs)
        assertEquals(original.totalKcal, summary.totalKcal, 0.0)
        assertEquals(summary, summary.withSessionTiming(segments, 210_000))
        assertEquals(summary, Json.decodeFromString<SessionSummary>(Json.encodeToString(SessionSummary.serializer(), summary)))
    }

    @Test fun `warmup only and no sensor session still has duration`() {
        val summary = SessionSummary.EMPTY.withSessionTiming(listOf(Segment(SegmentType.REST, 0)), 60_000)
        assertEquals(60_000L, summary.warmupDurationMs)
        assertEquals(0L, summary.restDurationMs)
    }

    @Test fun `session starting with work has no opening warmup`() {
        val summary = SessionSummary.EMPTY.withSessionTiming(listOf(
            Segment(SegmentType.ACTIVE, 0, 30_000), Segment(SegmentType.REST, 30_000, 90_000),
        ), 90_000)
        assertEquals(0L, summary.warmupDurationMs)
        assertEquals(60_000L, summary.restDurationMs)
    }

    private fun main(id: String) = PlannedExercise(slotId = id, exerciseId = id, name = id)
    private fun hold(id: String, phase: SlotPhase) = PlannedExercise(
        slotId = id, exerciseId = id, name = id, measure = ExerciseMeasure.SECONDS, targetSeconds = 30,
        runsOnce = true, phase = phase,
    )

    private val phased = WorkoutPlan(
        id = "p",
        exercises = listOf(hold("w1", SlotPhase.WARM_UP), main("m1"), main("m2"), hold("c1", SlotPhase.COOL_DOWN)),
    )

    private fun active(slot: String, from: Long, to: Long) = Segment(SegmentType.ACTIVE, from, to, slotId = slot)
    private fun rest(from: Long, to: Long) = Segment(SegmentType.REST, from, to)

    /** The 1.3.0 regression: guided warm-up holds read as work — "Warmup 00:17 · Work 01:29" for 18 s of sets. */
    @Test fun `guided warm-up and cool-down are their own time, never work`() {
        val segments = listOf(
            rest(0, 10_000),                    // opening preparation
            active("w1", 10_000, 40_000),       // warm-up hold
            rest(40_000, 60_000),               // hand-off into the workout
            active("m1", 60_000, 90_000),
            rest(90_000, 150_000),              // between-set rest
            active("m2", 150_000, 180_000),
            rest(180_000, 200_000),             // hand-off into the cool-down
            active("c1", 200_000, 240_000),     // stretch
            rest(240_000, 250_000),
        )
        val s = SessionSummary.EMPTY.withSessionTiming(segments, 250_000, phased)
        assertEquals(60_000L, s.warmupDurationMs)
        assertEquals(60_000L, s.activeDurationMs)
        assertEquals(60_000L, s.restDurationMs)
        assertEquals(70_000L, s.coolDownDurationMs)
        assertEquals(250_000L, s.totalDurationMs)
        assertEquals(0.24, s.activeRatio, 0.001)
        // Stored and re-read summaries are re-timed; doing it twice changes nothing.
        assertEquals(s, s.withSessionTiming(segments.reversed(), 250_000, phased))
    }

    @Test fun `a skipped cool-down leaves the last rest as rest`() {
        val segments = listOf(active("m1", 0, 30_000), rest(30_000, 90_000), active("m2", 90_000, 120_000), rest(120_000, 150_000))
        val s = SessionSummary.EMPTY.withSessionTiming(segments, 150_000, phased)
        assertEquals(0L, s.coolDownDurationMs)
        assertEquals(90_000L, s.restDurationMs)
    }

    @Test fun `working energy leaves out the warm-up and cool-down holds`() {
        val s = SessionSummary.EMPTY.copy(
            totalKcal = 100.0, activeKcal = 80.0, restKcal = 20.0,
            perExercise = listOf(
                ExerciseBreakdown(slotId = "m1", exerciseName = "m1", kcal = 70.0, reps = 10, sets = 1, activeDurationMs = 30_000),
                ExerciseBreakdown(slotId = "w1", exerciseName = "w1", kcal = 6.0, reps = 0, sets = 1, activeDurationMs = 30_000),
                ExerciseBreakdown(slotId = "c1", exerciseName = "c1", kcal = 4.0, reps = 0, sets = 1, activeDurationMs = 40_000),
            ),
        )
        assertEquals(70.0, s.workingKcal(phased), 0.0001)
        assertEquals(80.0, s.workingKcal(WorkoutPlan.EMPTY), 0.0001)
    }
}
