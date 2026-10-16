package com.calistapp.core.progress

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GoalReductionTest {
    private val day = LocalDate.of(2026, 1, 31)

    @Test fun `normal and consecutive reduced days follow the original thresholds`() {
        val targets = DailyEnergyGoal.reducedTargets(mapOf(
            day to 20_000.0, day.plusDays(1) to 15_000.0, day.plusDays(2) to 10_000.0,
        ), 10_000.0, true)
        assertEquals(7_500.0, targets[day.plusDays(1)]!!, 0.0)
        assertEquals(7_500.0, targets[day.plusDays(2)]!!, 0.0)
        assertNull(targets[day.plusDays(3)])
    }

    @Test fun `minimum is twenty percent and unused reduction does not carry forward`() {
        val targets = DailyEnergyGoal.reducedTargets(mapOf(day to 100_000.0, day.plusDays(1) to 2_000.0), 10_000.0, true)
        assertEquals(2_000.0, targets[day.plusDays(1)]!!, 0.0)
        assertNull(targets[day.plusDays(2)])
        assertEquals(21, DailyEnergyGoal.roundedTarget(20.2, 101.0))
    }

    @Test fun `threshold is strict and disabled means no reductions`() {
        assertTrue(DailyEnergyGoal.reducedTargets(mapOf(day to 15_000.0), 10_000.0, true).isEmpty())
        assertTrue(DailyEnergyGoal.reducedTargets(mapOf(day to 25_000.0), 10_000.0, false).isEmpty())
        assertTrue(DailyEnergyGoal.reducedTargets(emptyMap(), 10_000.0, true).isEmpty())
    }

    @Test fun `gap resets reduced day status and input order does not matter`() {
        val targets = DailyEnergyGoal.reducedTargets(linkedMapOf(day.plusDays(2) to 15_000.0, day to 20_000.0), 10_000.0, true)
        assertNull(targets[day.plusDays(3)])
        assertEquals(7_500.0, targets[day.plusDays(1)]!!, 0.0)
    }

    @Test fun `walking and workout calories are equivalent and streak uses each days goal`() {
        val earned = mapOf(day to (600.0 + 200.0), day.plusDays(1) to 300.0)
        val targets = DailyEnergyGoal.reducedTargets(earned, 400.0, true)
        fun target(date: LocalDate) = DailyEnergyGoal.roundedTarget(targets[date] ?: 400.0, 400.0)
        assertEquals(300, target(day.plusDays(1)))
        val stats = DailyEnergyGoal.stats(earned, 400, day.plusDays(1), ::target)
        assertEquals(2, stats.current)
        assertEquals(2, stats.longest)
        assertEquals(100, stats.goalHitPercent)
        assertEquals(1, stats.daysHitThisMonth)
    }

    @Test fun `reaching reduced goal does not itself earn another reduction`() {
        val targets = DailyEnergyGoal.reducedTargets(mapOf(day to 20_000.0, day.plusDays(1) to 9_000.0), 10_000.0, true)
        assertNull(targets[day.plusDays(2)])
    }
}
