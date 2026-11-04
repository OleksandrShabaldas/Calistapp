package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateStreamTest {

    private fun hr(t: Long, bpm: Int = 100) = HeartRateSample(t, bpm)

    @Test
    fun `a late batch is slotted in by measurement time, not arrival`() {
        val list = mutableListOf(hr(1_000), hr(2_000), hr(9_000))
        val added = list.mergeReadings(listOf(hr(4_000), hr(3_000), hr(5_000)))
        assertEquals(3, added)
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 9_000L), list.map { it.timestampMs })
    }

    @Test
    fun `a resent reading is not counted twice`() {
        val list = mutableListOf(hr(1_000), hr(2_000))
        assertEquals(1, list.mergeReadings(listOf(hr(2_000), hr(3_000), hr(1_000))))
        assertEquals(3, list.size)
    }

    @Test
    fun `link health reports the last minute and the longest gap`() {
        val samples = listOf(hr(0), hr(1_000), hr(11_000), hr(70_000), hr(71_000))
        val health = linkHealthOf(samples, nowMs = 72_000, backfilled = 2)
        assertEquals(2, health.readingsLastMinute)
        assertEquals(59_000L, health.longestGapMs)
        assertEquals(1_000L, health.newestAgeMs)
        assertEquals(2, health.backfilled)
    }
}
