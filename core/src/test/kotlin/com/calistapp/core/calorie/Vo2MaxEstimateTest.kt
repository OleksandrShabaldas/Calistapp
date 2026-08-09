package com.calistapp.core.calorie

import com.calistapp.core.model.Sex
import com.calistapp.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Vo2MaxEstimateTest {

    private val profile = UserProfile(
        sex = Sex.MALE, ageYears = 30, weightKg = 75.0, restingHr = 55, maxHr = 190,
    )

    @Test
    fun `the Uth ratio is applied as published`() {
        // 15.3 × 190 / 55 ≈ 52.9
        assertEquals(52.9, Vo2MaxEstimate.forProfile(profile)!!, 0.05)
    }

    @Test
    fun `a lower resting heart rate implies a higher estimate`() {
        val fitter = Vo2MaxEstimate.forProfile(profile.copy(restingHr = 45))!!

        assertTrue(fitter > Vo2MaxEstimate.forProfile(profile)!!)
    }

    @Test
    fun `an estimated max heart rate is not good enough to estimate from`() {
        // Without a measured max the formula degenerates into age and resting HR, which is a much
        // weaker claim than the study supports — so it declines rather than guessing.
        assertNull(Vo2MaxEstimate.forProfile(profile.copy(maxHr = null)))
        assertNotNull(Vo2MaxEstimate.blockedReason(profile.copy(maxHr = null)))
    }

    @Test
    fun `a max at or below resting yields nothing and says why`() {
        val broken = profile.copy(restingHr = 190, maxHr = 180)

        assertNull(Vo2MaxEstimate.forProfile(broken))
        assertNotNull(Vo2MaxEstimate.blockedReason(broken))
    }

    @Test
    fun `an implausible result is refused rather than displayed`() {
        // A resting HR of 25 with a max of 200 gives ~122, which no human has.
        val implausible = profile.copy(restingHr = 25, maxHr = 200)

        assertNull(Vo2MaxEstimate.forProfile(implausible))
        assertNotNull(Vo2MaxEstimate.blockedReason(implausible))
    }

    @Test
    fun `a usable profile has nothing to explain`() {
        assertNull(Vo2MaxEstimate.blockedReason(profile))
    }

    @Test
    fun `the result is rounded, not truncated, to one decimal`() {
        // 15.3 × 190 / 55 = 52.854…, which must present as 52.9 rather than 52.8.
        val estimate = Vo2MaxEstimate.forProfile(profile)!!

        assertEquals(estimate, kotlin.math.round(estimate * 10) / 10.0, 1e-9)
        assertEquals(52.9, estimate, 1e-9)
    }
}
