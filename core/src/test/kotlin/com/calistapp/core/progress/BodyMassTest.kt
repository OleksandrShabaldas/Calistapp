package com.calistapp.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMassTest {

    private val day = 24L * 60 * 60 * 1000
    private fun entry(dayIndex: Int, kg: Double) = WeightEntry(dayIndex * day, kg)

    private fun trend(entries: List<WeightEntry>, trainingKcal: Int = 0) =
        BodyMass.trend(entries) { _, _ -> trainingKcal }

    @Test
    fun `a single reading is not a trend`() {
        assertNull(trend(listOf(entry(0, 80.0))))
        assertNull(trend(emptyList()))
    }

    @Test
    fun `two readings a few days apart are not a trend either`() {
        assertNull(trend(listOf(entry(0, 80.0), entry(6, 79.0))))
    }

    @Test
    fun `a fortnight is enough`() {
        assertTrue(trend(listOf(entry(0, 80.0), entry(14, 79.0))) != null)
    }

    @Test
    fun `mass lost is reported as a deficit`() {
        val result = trend(listOf(entry(0, 82.0), entry(56, 79.0)))!!

        assertEquals(-3.0, result.changeKg, 1e-9)
        assertEquals(BodyMassTrend.Direction.DOWN, result.direction)
        // 3 kg × 7700 = 23,100 kcal, negative because it left.
        assertEquals(-23_100, result.impliedEnergyKcal)
        assertEquals(-412, result.impliedDailyKcal)
    }

    @Test
    fun `mass gained is reported as a surplus`() {
        val result = trend(listOf(entry(0, 78.0), entry(28, 80.0)))!!

        assertEquals(BodyMassTrend.Direction.UP, result.direction)
        assertTrue(result.impliedEnergyKcal > 0)
    }

    @Test
    fun `a change too small to mean anything reads as steady`() {
        val result = trend(listOf(entry(0, 80.0), entry(28, 80.3)))!!

        assertEquals(BodyMassTrend.Direction.STEADY, result.direction)
        assertFalse(result.isMeaningful)
    }

    @Test
    fun `training logged in the window is reported alongside, not subtracted from`() {
        // The card puts these two numbers side by side and explicitly declines to reconcile them.
        val result = trend(listOf(entry(0, 82.0), entry(28, 80.0)), trainingKcal = 9_000)!!

        assertEquals(9_000, result.loggedTrainingKcal)
        assertEquals(-15_400, result.impliedEnergyKcal)
    }

    @Test
    fun `readings out of order still bracket the span correctly`() {
        val result = trend(listOf(entry(28, 79.0), entry(0, 82.0), entry(14, 80.5)))!!

        assertEquals(82.0, result.first.weightKg, 1e-9)
        assertEquals(79.0, result.latest.weightKg, 1e-9)
        assertEquals(28, result.spanDays)
        assertEquals(3, result.entries.size)
    }
}
