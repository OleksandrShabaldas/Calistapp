package com.calistapp.core.calorie

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The audit's whole purpose is to be checkable, so these tests check it: every figure it shows must
 * reconcile with the number the engine actually reported, and with the arithmetic it claims to have
 * done. An audit that quietly disagreed with the summary would be worse than no audit at all.
 */
class CalorieAuditTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0,
    )
    private val engine = CalorieEngine()

    private fun steadySamples(bpm: Int, minutes: Int = 10, fromMs: Long = 0L): List<HeartRateSample> =
        (0..minutes * 12).map { HeartRateSample(timestampMs = fromMs + it * 5_000L, bpm = bpm) }

    private val pullUp = ExerciseMetabolics(
        muscleMassFraction = 0.30, loadFraction = 0.95, romMetres = 0.45, isometric = false,
    )

    @Test
    fun `audit reports the same summary the engine computes`() {
        val samples = steadySamples(140)
        val segments = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))

        val summary = engine.compute(samples, segments, profile)
        val audit = engine.explain(samples, segments, profile)

        assertNotNull(audit)
        assertEquals(summary, audit!!.summary)
    }

    @Test
    fun `blocks add up to the reported total`() {
        val samples = steadySamples(150, minutes = 12)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000, exerciseName = "Pull-Up", reps = 8, metabolics = pullUp),
            Segment(SegmentType.REST, 60_000, 180_000),
            Segment(SegmentType.ACTIVE, 180_000, 240_000, exerciseName = "Pull-Up", reps = 7, metabolics = pullUp),
            Segment(SegmentType.REST, 240_000, 360_000),
            Segment(SegmentType.ACTIVE, 360_000, 420_000, exerciseName = "Pull-Up", reps = 6, metabolics = pullUp),
        )

        val audit = engine.explain(samples, segments, profile)!!

        // What the user is shown line by line has to equal the headline figure.
        assertEquals(audit.summary.totalKcal, audit.blockKcalTotal, 1e-9)
        assertEquals(3, audit.workBlocks.size)
        assertEquals(2, audit.restBlocks.size)
    }

    @Test
    fun `each set is numbered in the order it was performed`() {
        val samples = steadySamples(150, minutes = 8)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000, slotId = "a", exerciseName = "Pull-Up", reps = 8),
            Segment(SegmentType.REST, 60_000, 120_000),
            Segment(SegmentType.ACTIVE, 120_000, 180_000, slotId = "a", exerciseName = "Pull-Up", reps = 7),
            Segment(SegmentType.ACTIVE, 180_000, 240_000, slotId = "b", exerciseName = "Dip", reps = 10),
        )

        val work = engine.explain(samples, segments, profile)!!.workBlocks

        assertEquals(listOf(1, 2, 1), work.map { it.setIndex })
        assertEquals(listOf("Pull-Up", "Pull-Up", "Dip"), work.map { it.exerciseName })
    }

    @Test
    fun `correction terms multiply out to the factor that was applied`() {
        val samples = steadySamples(150, minutes = 2)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 40_000, exerciseName = "Pull-Up", reps = 8, metabolics = pullUp),
        )

        val block = engine.explain(samples, segments, profile)!!.workBlocks.single()
        val c = block.correction!!

        assertEquals(c.recruitment * c.hrLag * c.isometricUplift, c.product, 1e-12)
        assertEquals(c.product.coerceIn(ExerciseIntensity.MIN_CORRECTION, ExerciseIntensity.MAX_CORRECTION), c.factor, 1e-12)
        assertEquals(block.heartRate.kcal * c.factor, block.correctedKcal, 1e-9)
    }

    @Test
    fun `mechanical work reconciles per rep and shows the mass it moved`() {
        val samples = steadySamples(150, minutes = 2)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 40_000, exerciseName = "Pull-Up", reps = 9, metabolics = pullUp),
        )

        val work = engine.explain(samples, segments, profile)!!.workBlocks.single().mechanical!!

        assertEquals(0.95 * 75.0, work.movedKg, 1e-9)
        assertEquals(work.kcalPerRep * 9, work.kcal, 1e-9)
        // Sanity against the published figure quoted in ExerciseIntensity: ≈0.45 kcal per pull-up.
        assertEquals(0.45, work.kcalPerRep, 0.05)
    }

    @Test
    fun `a set the sensor missed is counted on rep work, and the audit says so`() {
        // Heart rate flatlines at resting through a set of pull-ups — the classic grip-heavy dropout.
        val samples = steadySamples(60, minutes = 2)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 60_000, exerciseName = "Pull-Up", reps = 10, metabolics = pullUp),
        )

        val block = engine.explain(samples, segments, profile)!!.workBlocks.single()

        assertEquals(CalorieAudit.Basis.REP_WORK, block.basis)
        assertEquals(block.mechanical!!.kcal, block.kcal, 1e-9)
        assertTrue("Rep work must beat the HR term here", block.mechanical.kcal > block.correctedKcal)
    }

    @Test
    fun `a normally sensed set is counted on heart rate`() {
        val samples = steadySamples(155, minutes = 2)
        val segments = listOf(
            Segment(SegmentType.ACTIVE, 0, 90_000, exerciseName = "Pull-Up", reps = 6, metabolics = pullUp),
        )

        val block = engine.explain(samples, segments, profile)!!.workBlocks.single()

        assertEquals(CalorieAudit.Basis.HEART_RATE, block.basis)
        assertEquals(block.correctedKcal, block.kcal, 1e-9)
    }

    @Test
    fun `free work carries no exercise correction to explain`() {
        val samples = steadySamples(140, minutes = 5)
        val segments = listOf(Segment(SegmentType.ACTIVE, 0, 300_000))

        val block = engine.explain(samples, segments, profile)!!.workBlocks.single()

        assertNull(block.correction)
        assertNull(block.mechanical)
        assertEquals(block.heartRate.kcal, block.kcal, 1e-9)
    }

    @Test
    fun `the block rate reproduces the block total`() {
        val samples = steadySamples(145, minutes = 5)
        val segments = listOf(Segment(SegmentType.ACTIVE, 0, 300_000))

        val hr = engine.explain(samples, segments, profile)!!.workBlocks.single().heartRate

        // The screen shows "X kcal/min over Y minutes"; that has to come back to the block figure.
        val minutes = hr.coveredDurationMs / 60_000.0
        assertEquals(hr.kcal, hr.effectiveKcalPerMin * minutes, 1e-9)
        // And for a steady block it should sit close to evaluating the formula at the average HR.
        val expected = (hr.keytelAtAvgKcalPerMin - hr.restingKcalPerMin) * hr.calibration
        assertEquals(expected, hr.effectiveKcalPerMin, 0.2)
    }

    @Test
    fun `sensor dropouts are reported as time that was not credited`() {
        // Two minutes of samples, then a five-minute hole, then two more.
        val samples = steadySamples(140, minutes = 2) +
            steadySamples(140, minutes = 2, fromMs = 420_000L)
        val segments = listOf(Segment(SegmentType.ACTIVE, 0, 540_000))

        val sampling = engine.explain(samples, segments, profile)!!.sampling

        assertEquals(1, sampling.gapSliceCount)
        // The hole runs 120s → 420s; only the 30s cap is credited, so 270s goes uncounted.
        assertEquals(270_000L, sampling.uncreditedGapMs)
    }

    @Test
    fun `rest blocks record when the ceiling held heart rate back`() {
        // Elevated HR during recovery — exactly what the rest ceiling exists to bound.
        val samples = steadySamples(150, minutes = 4)
        val segments = listOf(Segment(SegmentType.REST, 0, 240_000))

        val block = engine.explain(samples, segments, profile)!!.restBlocks.single()

        assertEquals(CalorieAudit.Basis.REST, block.basis)
        assertTrue("Ceiling should have bound most slices", block.heartRate.ceilingSlices > 0)
        assertEquals(block.heartRate.sliceCount, block.heartRate.ceilingSlices)
    }

    @Test
    fun `the inputs behind the resting figure are all shown`() {
        val audit = engine.explain(steadySamples(140), listOf(Segment(SegmentType.ACTIVE, 0, 600_000)), profile)!!
        val r = audit.resting

        assertEquals(750.0, r.weightTerm, 1e-9)
        assertEquals(1112.5, r.heightTerm, 1e-9)
        assertEquals(-150.0, r.ageTerm, 1e-9)
        assertEquals(5.0, r.sexOffset, 1e-9)
        assertEquals(r.weightTerm + r.heightTerm + r.ageTerm + r.sexOffset, r.kcalPerDay, 1e-9)
        assertEquals(Formulas.restingKcalPerMin(profile), r.kcalPerMin, 1e-12)
        assertSame(Formulas.KeytelVariant.MALE_BASIC, audit.keytel)
    }

    @Test
    fun `knowing your VO2max switches the regression, and the audit names it`() {
        val fit = profile.copy(vo2Max = 48.0)
        val audit = engine.explain(steadySamples(140), listOf(Segment(SegmentType.ACTIVE, 0, 600_000)), fit)!!

        assertSame(Formulas.KeytelVariant.MALE_VO2, audit.keytel)
    }

    @Test
    fun `nothing to explain when there are no samples`() {
        assertNull(engine.explain(emptyList(), emptyList(), profile))
    }
}
