package com.calistapp.core.progress

import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLoadTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, restingHr = 60, maxHr = 190,
    )

    private fun hour(avgHr: Int) = TrainingLoad.trimp(avgHr, 3_600_000, profile)

    @Test
    fun `harder work earns disproportionately more load than easier work`() {
        // The exponential is the whole point: an hour hard must beat two hours easy.
        val hourHard = hour(170)
        val twoHoursEasy = TrainingLoad.trimp(110, 7_200_000, profile)

        assertTrue("$hourHard should exceed $twoHoursEasy", hourHard > twoHoursEasy)
    }

    @Test
    fun `load scales linearly with time at a fixed intensity`() {
        assertEquals(hour(150) * 2, TrainingLoad.trimp(150, 7_200_000, profile), 1e-9)
    }

    @Test
    fun `heart rate at or below resting is no load at all`() {
        assertEquals(0.0, hour(60), 1e-9)
        assertEquals(0.0, hour(45), 1e-9)
    }

    @Test
    fun `a zero-length session is no load`() {
        assertEquals(0.0, TrainingLoad.trimp(170, 0, profile), 1e-9)
    }

    @Test
    fun `an impossible heart-rate reserve yields nothing rather than nonsense`() {
        val broken = profile.copy(restingHr = 190, maxHr = 190)

        assertEquals(0.0, TrainingLoad.trimp(180, 3_600_000, broken), 1e-9)
    }

    @Test
    fun `heart rate above max is clamped, not extrapolated`() {
        assertEquals(hour(190), hour(230), 1e-9)
    }

    @Test
    fun `women use their own constants`() {
        val female = profile.copy(sex = Sex.FEMALE)
        val hers = TrainingLoad.trimp(150, 3_600_000, female)

        assertTrue(hers > 0.0)
        assertTrue("The two fits must not be identical", hers != hour(150))
    }

    @Test
    fun `ramp needs a month of history before it says anything`() {
        assertNull(TrainingLoad.ramp(List(27) { 50.0 }))
        assertTrue(TrainingLoad.ramp(List(28) { 50.0 }) != null)
    }

    @Test
    fun `a steady month sits at a ratio of one`() {
        val ramp = TrainingLoad.ramp(List(28) { 50.0 })!!

        assertEquals(1.0, ramp.ratio, 1e-9)
        assertEquals(TrainingLoad.Band.STEADY, ramp.band)
    }

    @Test
    fun `a big week against a quiet month reads as a sharp jump`() {
        // 7 hard days on top of 21 nearly-empty ones.
        val loads = List(7) { 100.0 } + List(21) { 5.0 }
        val ramp = TrainingLoad.ramp(loads)!!

        assertTrue(ramp.ratio > 1.5)
        assertEquals(TrainingLoad.Band.SHARP_JUMP, ramp.band)
    }

    @Test
    fun `a rest week after a heavy month reads as easing off`() {
        val loads = List(7) { 0.0 } + List(21) { 80.0 }
        val ramp = TrainingLoad.ramp(loads)!!

        assertEquals(0.0, ramp.acuteLoad, 1e-9)
        assertEquals(TrainingLoad.Band.EASING_OFF, ramp.band)
    }

    @Test
    fun `a month of nothing has no ratio to report`() {
        assertNull(TrainingLoad.ramp(List(28) { 0.0 }))
    }

    @Test
    fun `band boundaries fall where they are documented`() {
        fun bandAt(ratio: Double) =
            TrainingLoad.Ramp(acuteLoad = ratio, chronicLoad = 1.0, ratio = ratio).band

        assertEquals(TrainingLoad.Band.EASING_OFF, bandAt(0.79))
        assertEquals(TrainingLoad.Band.STEADY, bandAt(0.8))
        assertEquals(TrainingLoad.Band.STEADY, bandAt(1.3))
        assertEquals(TrainingLoad.Band.RAMPING, bandAt(1.31))
        assertEquals(TrainingLoad.Band.RAMPING, bandAt(1.5))
        assertEquals(TrainingLoad.Band.SHARP_JUMP, bandAt(1.51))
    }
}
