package com.calistapp.core.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdoorAdviceTest {

    private fun w(
        temp: Double,
        feels: Double = temp,
        wind: Double = 5.0,
        precip: Double = 0.0,
        code: Int = 3,
        recent: Double? = 0.0,
        uv: Double? = 2.0,
        aqi: Int? = 30,
        day: Boolean? = true,
    ) = WeatherInput(temp, feels, wind, precip, code, recent, null, uv, aqi, day)

    @Test
    fun eightDegreesOvercastIsAnIndoorDay() {
        val a = OutdoorAdvisor.advise(w(8.0, feels = 6.5))
        assertEquals(Venue.INDOOR, a.venue)
        assertEquals(AdviceReason.COLD, a.reasons.first())
        assertTrue(a.score < OutdoorAdvisor.OUTDOOR_MIN_SCORE)
    }

    @Test
    fun aDryTwelveIsStillTooCoolToStandAroundIn() {
        val a = OutdoorAdvisor.advise(w(12.0))
        assertEquals(Venue.INDOOR, a.venue)
        assertEquals(AdviceReason.CHILLY, a.reasons.first())
    }

    @Test
    fun fourteenDryAndCalmIsOutdoorsWithLayers() {
        val a = OutdoorAdvisor.advise(w(14.0))
        assertEquals(Venue.OUTDOOR, a.venue)
        assertEquals("Outdoor · layers", a.label)
    }

    @Test
    fun mildDryDayIsPleasantOutdoors() {
        val a = OutdoorAdvisor.advise(w(21.0, code = 1))
        assertEquals(Venue.OUTDOOR, a.venue)
        assertEquals("Outdoor", a.label)
        assertEquals(AdviceReason.PLEASANT, a.reasons.first())
        assertTrue(a.score >= 90)
    }

    @Test
    fun highUvAsksForSunscreen() {
        val a = OutdoorAdvisor.advise(w(23.0, code = 0, uv = 7.5))
        assertEquals("Outdoor · SPF", a.label)
        assertTrue(AdviceReason.HIGH_UV in a.reasons)
    }

    @Test
    fun rainForcesIndoorsEvenWhenWarm() {
        val a = OutdoorAdvisor.advise(w(22.0, precip = 1.2, code = 61))
        assertEquals(Venue.INDOOR, a.venue)
        assertEquals(AdviceReason.RAIN, a.reasons.first())
    }

    @Test
    fun recentRainTakesPointsForWetBars() {
        val dry = OutdoorAdvisor.advise(w(18.0, recent = 0.0))
        val wet = OutdoorAdvisor.advise(w(18.0, recent = 2.0))
        assertTrue(wet.score <= dry.score - 30)
        assertTrue(AdviceReason.WET_BARS in wet.reasons)
    }

    @Test
    fun windChillIsJudgedByFeelsLike() {
        // 15 °C air but 9 °C feels-like in the wind: indoors.
        val a = OutdoorAdvisor.advise(w(15.0, feels = 9.0, wind = 28.0))
        assertEquals(Venue.INDOOR, a.venue)
        assertEquals(AdviceReason.COLD, a.reasons.first())
    }

    @Test
    fun heatAndBadAirForceIndoors() {
        assertEquals(AdviceReason.HOT, OutdoorAdvisor.advise(w(34.0, feels = 36.0, code = 0)).reasons.first())
        assertEquals(AdviceReason.POOR_AIR, OutdoorAdvisor.advise(w(20.0, aqi = 140)).reasons.first())
    }

    @Test
    fun warmButBearableIsOutdoorsWithAHeatNote() {
        val a = OutdoorAdvisor.advise(w(29.0, code = 0))
        assertEquals(Venue.OUTDOOR, a.venue)
        assertEquals(AdviceReason.HEAT, a.reasons.first())
    }

    @Test
    fun scoreIsAlwaysInRange() {
        for (t in -20..45) {
            val a = OutdoorAdvisor.advise(w(t.toDouble(), wind = 60.0, aqi = 300, code = 99))
            assertTrue(a.score in 0..100)
            assertTrue(a.reasons.isNotEmpty())
        }
    }
}
