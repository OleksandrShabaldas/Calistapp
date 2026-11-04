package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SetSkip
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseSummaryTest {

    private fun stretch(id: String) = PlannedExercise(
        slotId = id, exerciseId = id, name = id, targetSets = 1, measure = ExerciseMeasure.SECONDS,
        targetSeconds = 30, runsOnce = true, phase = SlotPhase.COOL_DOWN, routineName = "Upper-body cooldown",
    )

    @Test
    fun `a cool-down summarises its holds, skips and how far heart rate settled`() {
        val session = WorkoutSession(
            id = "s",
            exerciseType = ExerciseType.CALISTHENICS,
            startMs = 0,
            plan = WorkoutPlan("p", exercises = listOf(PlannedExercise("a", "a", "A"), stretch("s1"), stretch("s2"))),
            segments = listOf(
                Segment(SegmentType.ACTIVE, 0, 10_000, slotId = "a", reps = 10),
                Segment(SegmentType.ACTIVE, 20_000, 50_000, slotId = "s1", reps = 30),
            ),
            setLogs = listOf(
                SetLog("a", "a", "A", 1, reps = 10, startMs = 0, endMs = 10_000),
                SetLog("s1", "s1", "s1", 1, reps = 30, startMs = 20_000, endMs = 50_000),
            ),
            skips = listOf(SetSkip("s2", 1, 51_000)),
            samples = listOf(HeartRateSample(5_000, 150), HeartRateSample(20_000, 130), HeartRateSample(50_000, 100)),
        )
        val cool = session.phaseSummary(SlotPhase.COOL_DOWN)!!
        assertEquals("Upper-body cooldown", cool.routineName)
        assertEquals(2, cool.moves)
        assertEquals(1, cool.done)
        assertEquals(1, cool.skipped)
        assertEquals(30_000L, cool.heldMs)
        assertEquals(130, cool.startHr)
        assertEquals(100, cool.endHr)
        assertNull(session.phaseSummary(SlotPhase.WARM_UP))
    }
}
