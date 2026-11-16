package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A workout without usable heart rate is scored by what was done, never filed at zero — and only then.
 * The regression behind it: Nov 8, two readings in the first second, then nothing for eight minutes.
 */
class NoHeartRateEstimateTest {

    private val profile = UserProfile(sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0)
    private val engine = CalorieEngine()
    private val pullUp = ExerciseMetabolics(muscleMassFraction = 0.45, loadFraction = 0.95, romMetres = 0.55)

    /** Ten one-minute sets of pull-ups with two-minute rests: 30 minutes. */
    private val workout: List<Segment> = buildList {
        var t = 0L
        repeat(10) {
            add(Segment(SegmentType.ACTIVE, t, t + 60_000, reps = 8, metabolics = pullUp, exerciseName = "Pull-Up", slotId = "pull"))
            t += 60_000
            add(Segment(SegmentType.REST, t, t + 120_000))
            t += 120_000
        }
    }
    private val endMs = 30 * 60_000L

    @Test
    fun `no heart rate at all is estimated, not zero`() {
        val s = engine.compute(emptyList(), workout, profile, endMs)
        assertTrue(s.estimated)
        // 10 min of sets at 6 MET net of resting ≈ 5 × 3.5 × 75 / 200 = 6.6 kcal/min → 66 kcal, plus
        // 20 min of rest at 1.5 MET ≈ 0.66 kcal/min → 13 kcal. Same recruitment as the HR correction.
        assertEquals(79.0, s.totalKcal, 6.0)
        assertEquals(600_000L, s.activeDurationMs)
        assertEquals(80, s.totalReps)
        assertEquals("Pull-Up", s.perExercise.single().exerciseName)
    }

    @Test
    fun `two readings in the first second of a long workout are not enough heart rate`() {
        val samples = listOf(HeartRateSample(0, 101), HeartRateSample(1_000, 100))
        val s = engine.compute(samples, workout, profile, endMs)
        assertTrue(s.estimated)
        assertEquals(101, s.peakHr) // what did arrive still shows
    }

    @Test
    fun `a ring sampling every five minutes is too sparse to integrate`() {
        val samples = (0..6).map { HeartRateSample(it * 300_000L, 110) }
        assertTrue(engine.compute(samples, workout, profile, endMs).estimated)
    }

    @Test
    fun `a real stream with a dropout is still scored by heart rate`() {
        // 5-second readings for the whole workout except a four-minute hole in the middle.
        val samples = (0..360).map { HeartRateSample(it * 5_000L, 130) }
            .filterNot { it.timestampMs in 780_000L..1_020_000L }
        val s = engine.compute(samples, workout, profile, endMs)
        assertFalse(s.estimated)
    }

    @Test
    fun `a set never scores below the rep work it physically did`() {
        val heavy = ExerciseMetabolics(muscleMassFraction = 0.45, loadFraction = 0.95, externalLoadKg = 60.0, romMetres = 0.55)
        val oneSet = listOf(Segment(SegmentType.ACTIVE, 0, 10_000, reps = 30, metabolics = heavy, exerciseName = "Weighted Pull-Up"))
        val audit = engine.explain(emptyList(), oneSet, profile, 10_000)!!
        val block = audit.blocks.single()
        assertEquals(CalorieAudit.Basis.REP_WORK, block.basis)
        assertEquals(block.mechanical!!.kcal, block.kcal, 1e-9)
    }

    @Test
    fun `the audit says the session was estimated and how much heart rate it had`() {
        val audit = engine.explain(emptyList(), workout, profile, endMs)!!
        assertTrue(audit.estimated)
        assertEquals(0.0, audit.hrCoverage, 1e-9)
        assertTrue(audit.blocks.all { it.basis == CalorieAudit.Basis.ESTIMATE || it.basis == CalorieAudit.Basis.REP_WORK })
    }
}
