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
 * Resuming an interrupted workout replays its recorded history back into a fresh accumulator. The
 * test that matters is that replaying gets to the same place as never having been interrupted —
 * otherwise a recovered session silently reports different calories from one that ran straight
 * through, which is worse than an obvious failure.
 */
class RebuildAccumulatorTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0,
    )

    private val pullUp = ExerciseMetabolics(
        muscleMassFraction = 0.30, loadFraction = 0.95, romMetres = 0.45, isometric = false,
    )

    /** Feed an accumulator the way a live session would, and hand back what it recorded. */
    private fun runLive(
        script: List<Segment>,
        samples: List<HeartRateSample>,
        finalReps: Int,
    ): LiveCalorieAccumulator {
        val live = LiveCalorieAccumulator(profile)
        val first = script.first()
        live.begin(first.startMs, first.type)
        live.setCurrentExercise(first.slotId, first.exerciseName, first.metabolics)

        var next = 0
        script.forEachIndexed { i, seg ->
            if (i == 0) return@forEachIndexed
            while (next < samples.size && samples[next].timestampMs < seg.startMs) {
                live.addSample(samples[next++])
            }
            live.setCurrentReps(script[i - 1].reps)
            live.startSegment(seg.type, seg.startMs, seg.slotId, seg.exerciseName, seg.metabolics)
        }
        while (next < samples.size) live.addSample(samples[next++])
        live.setCurrentReps(finalReps)
        return live
    }

    private fun samples(bpm: Int, fromMs: Long, toMs: Long) =
        generateSequence(fromMs) { it + 5_000 }.takeWhile { it <= toMs }
            .map { HeartRateSample(it, bpm) }
            .toList()

    private val script = listOf(
        Segment(SegmentType.REST, 0, 20_000, "s1", "Pull-Up", 0, pullUp),
        Segment(SegmentType.ACTIVE, 20_000, 65_000, "s1", "Pull-Up", 9, pullUp),
        Segment(SegmentType.REST, 65_000, 155_000, "s1", "Pull-Up", 0, pullUp),
        Segment(SegmentType.ACTIVE, 155_000, 200_000, "s1", "Pull-Up", 8, pullUp),
        // Still open — this is where the process died.
        Segment(SegmentType.REST, 200_000, null, "s1", "Pull-Up", 0, pullUp),
    )
    private val stream = samples(95, 0, 20_000) + samples(150, 25_000, 65_000) +
        samples(115, 70_000, 155_000) + samples(148, 160_000, 200_000) +
        samples(120, 205_000, 260_000)

    @Test
    fun `a rebuilt accumulator matches one that was never interrupted`() {
        val uninterrupted = runLive(script, stream, finalReps = 0).snapshot(260_000)
        val rebuilt = rebuildAccumulator(profile, script, stream).snapshot(260_000)

        assertEquals(uninterrupted, rebuilt)
    }

    @Test
    fun `reps banked before the interruption survive it`() {
        val rebuilt = rebuildAccumulator(profile, script, stream).snapshot(260_000)

        // Both completed sets, and nothing invented.
        assertEquals(17, rebuilt.totalReps)
        assertEquals(2, rebuilt.perExercise.single().sets)
        assertEquals(17, rebuilt.perExercise.single().reps)
    }

    @Test
    fun `reps logged into the open block are carried across`() {
        // The block in progress had 4 reps when the app died; they come back as the current count,
        // not as a completed set.
        val open = rebuildAccumulator(
            profile = profile,
            segments = script.dropLast(1) + Segment(SegmentType.ACTIVE, 200_000, null, "s1", "Pull-Up", 4, pullUp),
            samples = stream,
            currentReps = 4,
        )
        val without = rebuildAccumulator(
            profile = profile,
            segments = script.dropLast(1) + Segment(SegmentType.ACTIVE, 200_000, null, "s1", "Pull-Up", 0, pullUp),
            samples = stream,
            currentReps = 0,
        )

        assertTrue(
            "Carrying the in-flight reps must not lose energy",
            open.snapshot(260_000).totalKcal >= without.snapshot(260_000).totalKcal,
        )
        // Two banked sets plus the one in progress, which is how the live screen counts it.
        assertEquals(3, open.snapshot(260_000).perExercise.single().sets)
        assertEquals(21, open.snapshot(260_000).totalReps)
    }

    @Test
    fun `out of order samples are replayed in time order`() {
        val shuffled = stream.shuffled(kotlin.random.Random(7))

        assertEquals(
            rebuildAccumulator(profile, script, stream).snapshot(260_000),
            rebuildAccumulator(profile, script, shuffled).snapshot(260_000),
        )
    }

    @Test
    fun `a session interrupted before its first segment still replays`() {
        val early = samples(90, 0, 15_000)
        val rebuilt = rebuildAccumulator(profile, emptyList(), early)

        assertEquals(90, rebuilt.snapshot(15_000).avgHr)
    }

    @Test
    fun `nothing recorded rebuilds to an empty session`() {
        val rebuilt = rebuildAccumulator(profile, emptyList(), emptyList())

        assertEquals(0.0, rebuilt.snapshot(0).totalKcal, 1e-9)
    }
}
