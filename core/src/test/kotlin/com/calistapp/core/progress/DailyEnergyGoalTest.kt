package com.calistapp.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyEnergyGoalTest {

    @Test fun `per-step rate reuses FitPal's own figure`() {
        // 8000 steps → 320 kcal is 0.04 kcal/step (already trimmed by FitPal).
        assertEquals(0.04, DailyEnergyGoal.perStepRate(8000, 320.0)!!, 1e-9)
    }

    @Test fun `per-step rate is null without usable steps`() {
        assertNull(DailyEnergyGoal.perStepRate(0, 0.0))
        assertNull(DailyEnergyGoal.perStepRate(1000, 0.0))
    }

    @Test fun `fallback rate applies FitPal's formula and default trim`() {
        // 0.04 * (70/70) * (1 - .15) = 0.034
        assertEquals(0.034, DailyEnergyGoal.fallbackPerStepRate(70.0), 1e-9)
    }

    @Test fun `daily target converts a step goal into calories`() {
        assertEquals(320, DailyEnergyGoal.dailyTargetKcal(8000, 0.04))
        // Never rounds to a zero goal.
        assertEquals(1, DailyEnergyGoal.dailyTargetKcal(1, 0.0000001))
    }

    @Test fun `progress clamps to 0-1`() {
        assertEquals(0.5f, DailyEnergyGoal.progress(160.0, 320), 1e-6f)
        assertEquals(1f, DailyEnergyGoal.progress(999.0, 320), 1e-6f)
        assertEquals(0f, DailyEnergyGoal.progress(50.0, 0), 1e-6f)
    }

    private val today = LocalDate.of(2026, 9, 3)

    @Test fun `streak counts back from a today that is already met`() {
        val earned = mapOf(
            today to 400.0,                 // hit
            today.minusDays(1) to 350.0,    // hit
            today.minusDays(2) to 330.0,    // hit
            today.minusDays(3) to 100.0,    // miss — stops here
        )
        assertEquals(3, DailyEnergyGoal.currentStreak(earned, 320, today))
    }

    @Test fun `an unmet today does not break a streak earned through yesterday`() {
        val earned = mapOf(
            today to 90.0,                  // not yet hit — day still in progress
            today.minusDays(1) to 350.0,    // hit
            today.minusDays(2) to 500.0,    // hit
        )
        assertEquals(2, DailyEnergyGoal.currentStreak(earned, 320, today))
    }

    @Test fun `no streak when yesterday and today are both misses`() {
        val earned = mapOf(today to 10.0, today.minusDays(1) to 10.0)
        assertEquals(0, DailyEnergyGoal.currentStreak(earned, 320, today))
    }

    @Test fun `workout calories count toward the day`() {
        // 6k steps (~204 kcal) alone misses 320, but a 200 kcal workout tips it over.
        val stepsKcal = 6000 * 0.034
        assertFalse(DailyEnergyGoal.hit(stepsKcal, 320))
        assertTrue(DailyEnergyGoal.hit(stepsKcal + 200, 320))
    }

    @Test fun `a non-positive target yields no streak`() {
        assertEquals(0, DailyEnergyGoal.currentStreak(mapOf(today to 999.0), 0, today))
    }

    @Test fun `stats summarise the streak history`() {
        val d = LocalDate.of(2026, 9, 10)
        val earned = mapOf(
            d.minusDays(6) to 400.0, // 09-04 hit
            d.minusDays(5) to 100.0, // 09-05 miss
            d.minusDays(4) to 350.0, // 09-06 hit
            d.minusDays(3) to 500.0, // 09-07 hit (best)
            d.minusDays(2) to 90.0,  // 09-08 miss
            d.minusDays(1) to 400.0, // 09-09 hit
            d to 350.0,              // 09-10 hit (today)
        )
        val s = DailyEnergyGoal.stats(earned, 320, d)
        assertEquals(2, s.current)
        assertEquals(2, s.longest)
        assertEquals(1, s.longestMiss)
        assertEquals(d.minusDays(2), s.lastBrokenDay)   // the 09-08 miss right after a hit
        assertEquals(d.minusDays(3), s.bestDay)
        assertEquals(500, s.bestDayKcal)
        assertEquals(5, s.daysHitThisMonth)
        assertEquals(71, s.goalHitPercent)              // 5 of 7
        assertEquals(d.minusDays(6), s.firstDay)
        assertEquals(7, s.totalDaysTracked)
    }

    @Test fun `stats are empty before any burn`() {
        val s = DailyEnergyGoal.stats(emptyMap(), 320, today)
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertNull(s.firstDay)
    }
}
