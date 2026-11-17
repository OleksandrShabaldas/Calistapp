package com.calistapp.core.medals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedalCatalogTest {

    private val all = MedalCatalog.all

    @Test
    fun `forty tiers in twenty-six lines`() {
        assertEquals(26, all.size)
        assertEquals(40, all.sumOf { it.tiers.size })
    }

    @Test
    fun `kinds split as designed`() {
        val tiersByKind = all.groupBy { it.kind }.mapValues { (_, lines) -> lines.sumOf { it.tiers.size } }
        assertEquals(
            mapOf(
                MedalKind.SKILLS to 5, MedalKind.STRENGTH to 12, MedalKind.SHOWING_UP to 10,
                MedalKind.HEART to 4, MedalKind.BALANCE to 4, MedalKind.LIFE to 5,
            ),
            tiersByKind,
        )
    }

    @Test
    fun `ids are unique and stable kebab-case`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertTrue(it.id, it.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*"))) }
    }

    @Test
    fun `onyx only crowns the week streak and the sessions ladder`() {
        val onyx = all.flatMap { def -> def.tiers.filter { it.metal == MedalMetal.ONYX }.map { def.id to it.label } }
        assertEquals(listOf("week-streak" to "52", "sessions" to "500"), onyx)
    }

    @Test
    fun `every one-off is copper and every ladder rises copper silver gold`() {
        all.filter { it.tiers.size == 1 }.forEach { assertEquals(it.id, MedalMetal.COPPER, it.tiers.single().metal) }
        all.filter { it.tiers.size > 1 }.forEach { def ->
            assertEquals(def.id, listOf(MedalMetal.COPPER, MedalMetal.SILVER, MedalMetal.GOLD), def.tiers.take(3).map { it.metal })
            assertTrue(def.id, def.tiers.zipWithNext().all { (a, b) -> a.threshold < b.threshold })
        }
    }

    @Test
    fun `exactly two hidden medals, both in life`() {
        val hidden = all.filter { it.hidden }
        assertEquals(listOf("twice-in-a-day", "century"), hidden.map { it.id })
        assertTrue(hidden.all { it.kind == MedalKind.LIFE })
    }

    @Test
    fun `emblems match the drawn medal set`() {
        val emblems = all.filterNot { it.hidden }.associate { it.id to it.emblem }
        assertEquals("e-pullup", emblems["first-pull-up"])
        assertEquals("e-kettle", emblems["weighted-pull-up"])
        assertEquals("e-calcheck", emblems["perfect-month"])
        assertEquals("e-heartpulse", emblems["recovery-30"])
        assertEquals("e-compass", emblems["explorer"])
        assertEquals("e-moon", emblems["night-owl"])
    }

    @Test
    fun `progress phrases read like the design`() {
        assertEquals("13 of 15", MedalUnit.REPS.ofLabel(13.0, 15.0))
        assertEquals("2 reps to go", MedalUnit.REPS.toGo(2.0))
        assertEquals("1 rep to go", MedalUnit.REPS.toGo(1.0))
        assertEquals("+5 of +10 kg", MedalUnit.KG.ofLabel(5.0, 10.0))
        assertEquals("2.5 kg to go", MedalUnit.KG.toGo(2.5))
        assertEquals("8 of 10 s", MedalUnit.SECONDS.ofLabel(8.0, 10.0))
        assertEquals("12 of 26", MedalUnit.STREAK_WEEKS.ofLabel(12.0, 26.0))
        assertEquals("14 weeks to go", MedalUnit.STREAK_WEEKS.toGo(14.0))
        assertEquals("38 to go", MedalUnit.COUNT.toGo(38.0))
        assertEquals("7 sessions to go", MedalUnit.SESSIONS.toGo(7.0))
        assertEquals("1 leg to go", MedalUnit.LEGS.toGo(1.0))
        // Hours: floored in the label, ceilinged in the to-go, so the two add up.
        assertEquals("4 of 10 h", MedalUnit.HOURS.ofLabel(4.4, 10.0))
        assertEquals("6 h to go", MedalUnit.HOURS.toGo(5.6))
        assertEquals("25 min to go", MedalUnit.HOURS.toGo(25 / 60.0))
        assertFalse(MedalUnit.EVENT.measurable)
    }
}
