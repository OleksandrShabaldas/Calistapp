package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live accumulator exists purely for speed, so the property that matters is that it agrees
 * with [CalorieEngine] — the authoritative scorer. These tests pin that equivalence.
 */
class LiveCalorieAccumulatorTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0,
    )
    private val pullUp = ExerciseMetabolics(
        muscleMassFraction = 0.34, loadFraction = 0.95, romMetres = 0.45,
    )

    @Test
    fun `incremental total matches the batch engine for a steady session`() {
        val samples = (0..120).map { HeartRateSample(it * 5_000L, 140) }
        val endMs = 600_000L

        val acc = LiveCalorieAccumulator(profile)
        acc.begin(0, SegmentType.ACTIVE)
        samples.forEach { acc.addSample(it) }
        acc.advanceTo(endMs)
        val live = acc.snapshot(endMs)

        val batch = CalorieEngine().compute(
            samples, listOf(Segment(SegmentType.ACTIVE, 0, endMs)), profile, endMs = endMs,
        )

        assertEquals(batch.totalKcal, live.totalKcal, 0.5)
        assertEquals(batch.activeDurationMs, live.activeDurationMs)
    }

    @Test
    fun `incremental total matches the batch engine across active-rest toggles`() {
        val samples = (0..120).map {
            val ms = it * 5_000L
            HeartRateSample(ms, if (ms < 300_000L) 150 else 100)
        }
        val endMs = 600_000L

        val acc = LiveCalorieAccumulator(profile)
        acc.begin(0, SegmentType.ACTIVE)
        samples.forEach {
            if (it.timestampMs == 300_000L) acc.startSegment(SegmentType.REST, 300_000L)
            acc.addSample(it)
        }
        acc.advanceTo(endMs)
        val live = acc.snapshot(endMs)

        val batch = CalorieEngine().compute(
            samples,
            listOf(
                Segment(SegmentType.ACTIVE, 0, 300_000),
                Segment(SegmentType.REST, 300_000, endMs),
            ),
            profile,
            endMs = endMs,
        )

        assertEquals(batch.activeKcal, live.activeKcal, 0.5)
        assertEquals(batch.restKcal, live.restKcal, 0.5)
        assertEquals(batch.totalKcal, live.totalKcal, 0.5)
    }

    @Test
    fun `exercise context and reps survive into the live breakdown`() {
        val acc = LiveCalorieAccumulator(profile)
        acc.begin(0, SegmentType.ACTIVE)
        acc.setCurrentExercise("s1", "Pull-up", pullUp)
        (0..12).forEach { acc.addSample(HeartRateSample(it * 5_000L, 140)) }
        acc.setCurrentReps(10)

        val snap = acc.snapshot(60_000)
        assertEquals(1, snap.perExercise.size)
        assertEquals("Pull-up", snap.perExercise[0].exerciseName)
        assertEquals(10, snap.perExercise[0].reps)
        assertEquals(10, snap.totalReps)
    }

    @Test
    fun `closed blocks aggregate per exercise across sets`() {
        val acc = LiveCalorieAccumulator(profile)
        acc.begin(0, SegmentType.ACTIVE)
        acc.setCurrentExercise("s1", "Pull-up", pullUp)
        (0..12).forEach { acc.addSample(HeartRateSample(it * 5_000L, 140)) }
        acc.setCurrentReps(8)

        acc.startSegment(SegmentType.REST, 60_000)
        (13..24).forEach { acc.addSample(HeartRateSample(it * 5_000L, 110)) }

        acc.startSegment(SegmentType.ACTIVE, 120_000, "s1", "Pull-up", pullUp)
        (25..36).forEach { acc.addSample(HeartRateSample(it * 5_000L, 145)) }
        acc.setCurrentReps(6)

        val snap = acc.snapshot(180_000)
        assertEquals(1, snap.perExercise.size)
        assertEquals(2, snap.perExercise[0].sets)
        assertEquals(14, snap.perExercise[0].reps)
        assertTrue(snap.restKcal > 0.0)
    }

    @Test
    fun `mechanical floor applies live when heart rate reads implausibly low`() {
        val acc = LiveCalorieAccumulator(profile)
        acc.begin(0, SegmentType.ACTIVE)
        acc.setCurrentExercise("s1", "Pull-up", pullUp)
        (0..12).forEach { acc.addSample(HeartRateSample(it * 5_000L, 60)) }
        acc.setCurrentReps(20)

        // 20 pull-ups is ~9 kcal of mechanical work regardless of what the sensor claims.
        assertTrue(acc.snapshot(60_000).totalKcal > 8.0)
    }
}
