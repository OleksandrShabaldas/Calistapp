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

class CalorieEngineTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0,
    )
    private val engine = CalorieEngine()

    /** Realistic ~5-second sampling at a constant bpm (like a real HR stream). */
    private fun steadySamples(bpm: Int, minutes: Int = 10): List<HeartRateSample> {
        val intervals = minutes * 12 // 12 five-second samples per minute
        return (0..intervals).map { HeartRateSample(timestampMs = it * 5_000L, bpm = bpm) }
    }

    @Test
    fun `empty samples produce empty summary`() {
        val s = engine.compute(emptyList(), emptyList(), profile)
        assertEquals(0.0, s.totalKcal, 0.0001)
    }

    @Test
    fun `10 minutes active at 140bpm matches keytel integration`() {
        val samples = steadySamples(140)
        val seg = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))
        val s = engine.compute(samples, seg, profile)

        // Keytel male @140bpm ≈ 12.95 kcal/min gross. Reported energy is net of resting
        // metabolism (≈1.19 kcal/min here) and scaled by the field calibration:
        //   (12.95 − 1.19) × 0.90 ≈ 10.58 kcal/min → ~105.8 kcal over 10 min.
        assertEquals(105.8, s.totalKcal, 1.5)
        assertEquals(105.8, s.activeKcal, 1.5)
        assertEquals(0.0, s.restKcal, 0.0001)
        assertEquals(600_000L, s.activeDurationMs)
        assertEquals(140, s.avgHr)
        assertEquals(140, s.avgActiveHr)
    }

    @Test
    fun `reported energy excludes the resting metabolism you would burn anyway`() {
        val samples = steadySamples(140)
        val seg = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))

        val net = engine.compute(samples, seg, profile).totalKcal
        val gross = CalorieEngine(CalorieEngine.Config(netOfResting = false, hrCalibration = 1.0))
            .compute(samples, seg, profile).totalKcal

        // Ten minutes of resting metabolism ≈ 11.9 kcal, and the calibration removes a further 10%.
        assertTrue("Net ($net) must be below gross ($gross)", net < gross)
        assertEquals(gross - Formulas.restingKcalPerMin(profile) * 10.0, net / 0.90, 1.0)
    }

    @Test
    fun `sitting still burns essentially no exercise calories`() {
        // Resting HR for this profile. Gross expenditure is nonzero — a body at rest still burns
        // fuel — but none of it is attributable to the workout.
        val samples = steadySamples(60)
        val s = engine.compute(samples, listOf(Segment(SegmentType.REST, 0, 600_000)), profile)
        assertEquals(0.0, s.totalKcal, 0.5)
    }

    @Test
    fun `same HR costs far less when tagged as rest than as active`() {
        val samples = steadySamples(140)
        val active = engine.compute(samples, listOf(Segment(SegmentType.ACTIVE, 0, 600_000)), profile)
        val rest = engine.compute(samples, listOf(Segment(SegmentType.REST, 0, 600_000)), profile)

        assertTrue(
            "Rest calories (${rest.totalKcal}) should be well below active (${active.totalKcal})",
            rest.totalKcal < active.totalKcal * 0.5,
        )
        assertEquals(600_000L, rest.restDurationMs)
    }

    @Test
    fun `higher heart rate burns more calories`() {
        val seg = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))
        val low = engine.compute(steadySamples(110), seg, profile).totalKcal
        val high = engine.compute(steadySamples(160), seg, profile).totalKcal
        assertTrue("HR 160 ($high) should exceed HR 110 ($low)", high > low)
    }

    @Test
    fun `mixed active-rest session splits calories by segment`() {
        // 5 min active @150, then 5 min rest @100, sampled every 5s.
        val samples = (0..120).map {
            val ms = it * 5_000L
            val bpm = if (ms < 300_000L) 150 else 100
            HeartRateSample(ms, bpm)
        }
        val segs = listOf(
            Segment(SegmentType.ACTIVE, 0, 300_000),
            Segment(SegmentType.REST, 300_000, 600_000),
        )
        val s = engine.compute(samples, segs, profile)
        assertTrue(s.activeKcal > 0.0)
        assertTrue(s.restKcal > 0.0)
        assertTrue("Active portion should dominate", s.activeKcal > s.restKcal)
        assertEquals(s.activeKcal + s.restKcal, s.totalKcal, 0.0001)
        assertEquals(300_000L, s.activeDurationMs)
        assertEquals(300_000L, s.restDurationMs)
    }

    @Test
    fun `vo2max profile changes the estimate`() {
        val seg = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))
        val base = engine.compute(steadySamples(140), seg, profile).totalKcal
        val fit = engine.compute(
            steadySamples(140), seg, profile.copy(vo2Max = 50.0),
        ).totalKcal
        assertTrue("VO2max path should produce a distinct estimate", base != fit)
    }

    @Test
    fun `sensor dropout gap is capped and does not explode calories`() {
        // Two samples 10 minutes apart — a big gap. Should credit only maxIntervalMs (30s).
        val samples = listOf(
            HeartRateSample(0, 150),
            HeartRateSample(600_000, 150),
        )
        val seg = listOf(Segment(SegmentType.ACTIVE, 0, 600_000))
        val s = engine.compute(samples, seg, profile)
        // ~30s @ ~14 kcal/min ≈ 7 kcal, nowhere near 10 min worth (~140 kcal).
        assertTrue("Gap should be capped, got ${s.totalKcal}", s.totalKcal < 15.0)
    }

    // ---- Exercise-aware refinements ----------------------------------------------------------

    private val pullUp = ExerciseMetabolics(
        muscleMassFraction = 0.34, loadFraction = 0.95, romMetres = 0.45, compound = true,
    )
    private val curl = ExerciseMetabolics(
        muscleMassFraction = 0.03, loadFraction = 0.12, romMetres = 0.35, compound = false,
    )

    @Test
    fun `segments without exercise context are unchanged`() {
        // Backwards compatibility: a plain session must score exactly as before.
        val samples = steadySamples(140)
        val plain = engine.compute(samples, listOf(Segment(SegmentType.ACTIVE, 0, 600_000)), profile)
        assertEquals(105.8, plain.totalKcal, 1.5)
        assertTrue(plain.perExercise.isEmpty())
    }

    @Test
    fun `recruitment correction favours the higher muscle-mass movement at equal heart rate`() {
        val samples = steadySamples(140)
        fun burn(m: ExerciseMetabolics) = engine.compute(
            samples,
            listOf(Segment(SegmentType.ACTIVE, 0, 600_000, exerciseName = "x", metabolics = m)),
            profile,
        ).totalKcal

        val big = burn(pullUp)
        val small = burn(curl)
        assertTrue("Pull-up ($big) should out-score curl ($small) at the same HR", big > small)
    }

    @Test
    fun `exercise correction stays within the documented bounds`() {
        val samples = steadySamples(140)
        val baseline = engine.compute(
            samples, listOf(Segment(SegmentType.ACTIVE, 0, 600_000)), profile,
        ).totalKcal

        // An absurd profile must still not move the estimate beyond the clamp.
        val extreme = ExerciseMetabolics(
            muscleMassFraction = 1.0, loadFraction = 0.0, romMetres = 0.0, isometric = true,
        )
        val corrected = engine.compute(
            samples,
            listOf(Segment(SegmentType.ACTIVE, 0, 600_000, exerciseName = "x", metabolics = extreme)),
            profile,
        ).totalKcal

        val ratio = corrected / baseline
        assertTrue(
            "Correction ratio $ratio must stay within bounds",
            ratio in ExerciseIntensity.MIN_CORRECTION..ExerciseIntensity.MAX_CORRECTION,
        )
    }

    @Test
    fun `mechanical work from reps floors the estimate when heart rate drops out`() {
        // Sensor reads an implausibly low 60 bpm through a real set of 20 pull-ups.
        val samples = (0..12).map { HeartRateSample(it * 5_000L, 60) }
        val seg = listOf(
            Segment(
                SegmentType.ACTIVE, 0, 60_000,
                exerciseName = "Pull-up", reps = 20, metabolics = pullUp,
            ),
        )
        val s = engine.compute(samples, seg, profile)

        // 20 reps x ~0.45 kcal ≈ 9 kcal of mechanical work the HR signal completely missed.
        assertTrue("Expected the mechanical floor to dominate, got ${s.totalKcal}", s.totalKcal > 8.0)
    }

    @Test
    fun `per-rep mechanical cost matches published figures`() {
        // Calibration guard: these are the numbers the model's credibility rests on.
        assertEquals(0.45, ExerciseIntensity.mechanicalKcal(pullUp, 1, profile), 0.05)

        val pushUp = ExerciseMetabolics(loadFraction = 0.64, romMetres = 0.35)
        assertEquals(0.24, ExerciseIntensity.mechanicalKcal(pushUp, 1, profile), 0.05)

        val squat = ExerciseMetabolics(loadFraction = 0.75, romMetres = 0.40)
        assertEquals(0.32, ExerciseIntensity.mechanicalKcal(squat, 1, profile), 0.05)
    }

    @Test
    fun `isometric holds contribute no mechanical work`() {
        val plank = ExerciseMetabolics(loadFraction = 0.6, romMetres = 0.0, isometric = true)
        assertEquals(0.0, ExerciseIntensity.mechanicalKcal(plank, 30, profile), 0.0001)
    }

    @Test
    fun `session reports a per-exercise breakdown`() {
        val samples = (0..120).map { HeartRateSample(it * 5_000L, 140) }
        val segs = listOf(
            Segment(
                SegmentType.ACTIVE, 0, 200_000,
                slotId = "s1", exerciseName = "Pull-up", reps = 10, metabolics = pullUp,
            ),
            Segment(SegmentType.REST, 200_000, 300_000),
            Segment(
                SegmentType.ACTIVE, 300_000, 600_000,
                slotId = "s2", exerciseName = "Squat", reps = 25, metabolics = curl,
            ),
        )
        val s = engine.compute(samples, segs, profile)

        assertEquals(2, s.perExercise.size)
        assertEquals(35, s.totalReps)
        // Sorted by cost, and — since every active moment here is tagged — the parts must add
        // back up to the active total.
        assertTrue(s.perExercise[0].kcal >= s.perExercise[1].kcal)
        assertEquals(s.activeKcal, s.perExercise.sumOf { it.kcal }, 0.5)
    }

    @Test
    fun `untagged active time counts toward calories but not toward any exercise`() {
        // Work done before the first exercise is selected still burns energy; it just can't be
        // blamed on a movement. The breakdown is therefore allowed to under-sum the active total.
        val samples = (0..120).map { HeartRateSample(it * 5_000L, 140) }
        val segs = listOf(
            Segment(
                SegmentType.ACTIVE, 0, 300_000,
                slotId = "s1", exerciseName = "Pull-up", reps = 10, metabolics = pullUp,
            ),
        )
        val s = engine.compute(samples, segs, profile)

        assertEquals(1, s.perExercise.size)
        assertTrue(
            "Untagged tail should still be counted as active energy",
            s.activeKcal > s.perExercise.sumOf { it.kcal },
        )
    }

    @Test
    fun `repeated sets of one exercise aggregate into a single breakdown row`() {
        val samples = (0..120).map { HeartRateSample(it * 5_000L, 140) }
        val segs = listOf(
            Segment(SegmentType.ACTIVE, 0, 100_000, slotId = "s1", exerciseName = "Pull-up", reps = 8, metabolics = pullUp),
            Segment(SegmentType.REST, 100_000, 200_000),
            Segment(SegmentType.ACTIVE, 200_000, 300_000, slotId = "s1", exerciseName = "Pull-up", reps = 6, metabolics = pullUp),
        )
        val s = engine.compute(samples, segs, profile)

        assertEquals(1, s.perExercise.size)
        assertEquals(2, s.perExercise[0].sets)
        assertEquals(14, s.perExercise[0].reps)
    }
}
