package com.calistapp.core.analysis

import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateRecoveryTest {

    /** A ramp of readings every 5s from [fromMs] to [toMs], interpolating [startBpm] → [endBpm]. */
    private fun ramp(fromMs: Long, toMs: Long, startBpm: Int, endBpm: Int): List<HeartRateSample> {
        val steps = ((toMs - fromMs) / 5_000).toInt().coerceAtLeast(1)
        return (0..steps).map { i ->
            val bpm = startBpm + (endBpm - startBpm) * i / steps
            HeartRateSample(fromMs + i * 5_000L, bpm)
        }
    }

    @Test
    fun `the drop is measured from the peak of work to one minute into rest`() {
        // Work climbing to 170, then rest falling to 130 by the one-minute mark.
        val samples = ramp(0, 60_000, 120, 170) + ramp(65_000, 180_000, 168, 110)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000),
            Segment(SegmentType.REST, 60_000, 180_000),
        )

        val recovery = HeartRateRecovery.analyze(samples, segments)!!

        assertEquals(1, recovery.measuredRests)
        // Peak in the 30s before the transition is 170; at +60s the ramp is around 138.
        assertEquals(32.0, recovery.meanDropBpm.toDouble(), 5.0)
    }

    @Test
    fun `several rests are averaged and the best is kept`() {
        val samples = ramp(0, 40_000, 120, 170) +
            ramp(45_000, 160_000, 168, 120) +      // rest 1: big drop
            ramp(165_000, 200_000, 130, 172) +
            ramp(205_000, 320_000, 170, 150)       // rest 2: small drop
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 40_000),
            Segment(SegmentType.REST, 40_000, 160_000),
            Segment(SegmentType.ACTIVE, 160_000, 200_000),
            Segment(SegmentType.REST, 200_000, 320_000),
        )

        val recovery = HeartRateRecovery.analyze(samples, segments)!!

        assertEquals(2, recovery.measuredRests)
        assert(recovery.bestDropBpm >= recovery.meanDropBpm)
    }

    @Test
    fun `a rest shorter than the window is not measured`() {
        // 40 seconds of rest can't produce a 60-second recovery figure.
        val samples = ramp(0, 60_000, 120, 170) + ramp(62_000, 100_000, 168, 150)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000),
            Segment(SegmentType.REST, 60_000, 100_000),
        )

        assertNull(HeartRateRecovery.analyze(samples, segments))
    }

    @Test
    fun `the opening rest before any work is not a recovery`() {
        val samples = ramp(0, 180_000, 70, 75)
        val segments = listOf(Segment(SegmentType.REST, 0, 180_000))

        assertNull(HeartRateRecovery.analyze(samples, segments))
    }

    @Test
    fun `heart rate that rises through the rest is discarded`() {
        // Kept moving, or the sensor drifted. Either way it is not a recovery measurement.
        val samples = ramp(0, 60_000, 120, 140) + ramp(65_000, 180_000, 140, 165)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000),
            Segment(SegmentType.REST, 60_000, 180_000),
        )

        assertNull(HeartRateRecovery.analyze(samples, segments))
    }

    @Test
    fun `a gap across the one-minute mark is not guessed at`() {
        // Work, then the sensor drops out for the whole window.
        val samples = ramp(0, 60_000, 120, 170) +
            listOf(HeartRateSample(200_000, 110))
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000),
            Segment(SegmentType.REST, 60_000, 240_000),
        )

        assertNull(HeartRateRecovery.analyze(samples, segments))
    }

    @Test
    fun `nothing recorded produces nothing`() {
        assertNull(HeartRateRecovery.analyze(emptyList(), emptyList()))
        assertNull(HeartRateRecovery.analyze(listOf(HeartRateSample(0, 120)), emptyList()))
    }
}
