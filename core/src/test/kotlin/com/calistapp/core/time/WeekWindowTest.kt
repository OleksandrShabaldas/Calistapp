package com.calistapp.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class WeekWindowTest {

    private val zone = ZoneId.of("Europe/Bratislava")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun weekOf(text: String) = startOfWeekMs(at(text), zone)

    @Test
    fun `the week starts at midnight on Monday`() {
        assertEquals(at("2026-08-03T00:00:00"), weekOf("2026-08-05T14:30:00"))
    }

    @Test
    fun `Monday itself belongs to the week it starts`() {
        assertEquals(at("2026-08-03T00:00:00"), weekOf("2026-08-03T00:00:00"))
        assertEquals(at("2026-08-03T00:00:00"), weekOf("2026-08-03T23:59:59"))
    }

    @Test
    fun `Sunday still belongs to the week that began the Monday before`() {
        assertEquals(at("2026-08-03T00:00:00"), weekOf("2026-08-09T23:59:59"))
    }

    @Test
    fun `crossing midnight into Monday moves to the new week`() {
        val sundayNight = weekOf("2026-08-09T23:59:59")
        val mondayMorning = weekOf("2026-08-10T00:00:01")

        // The bug this guards: a window computed once and never recalculated kept totalling the
        // previous week for anyone who left the app open.
        assertTrue("Monday must open a new week", mondayMorning > sundayNight)
        assertEquals(at("2026-08-10T00:00:00"), mondayMorning)
    }

    @Test
    fun `a week is seven days long across a daylight-saving change`() {
        // Central Europe puts the clocks back on the last Sunday of October, making that week 169
        // hours. Anchoring on local midnight rather than subtracting a fixed number of milliseconds
        // is what keeps the boundary on Monday regardless.
        val before = weekOf("2026-10-24T12:00:00")
        val after = weekOf("2026-10-26T12:00:00")

        assertEquals(at("2026-10-19T00:00:00"), before)
        assertEquals(at("2026-10-26T00:00:00"), after)
    }

    @Test
    fun `the first day of the week is configurable`() {
        assertEquals(
            at("2026-08-09T00:00:00"),
            startOfWeekMs(at("2026-08-12T09:00:00"), zone, DayOfWeek.SUNDAY),
        )
    }
}
