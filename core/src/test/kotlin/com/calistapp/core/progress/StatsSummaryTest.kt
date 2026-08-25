package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsSummaryTest {

    private val zone = ZoneId.of("Europe/Bratislava")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** Monday 10 Aug 2026 — start of week is 10 Aug, start of month is 1 Aug. */
    private val now = at("2026-08-10T10:00:00")

    private fun slot(id: String, name: String, addedKg: Double = 0.0) = PlannedExercise(
        slotId = id,
        exerciseId = name.lowercase(),
        name = name,
        metabolics = ExerciseMetabolics(externalLoadKg = addedKg),
    )

    private fun session(
        id: String,
        atText: String,
        kcal: Int = 100,
        activeMs: Long = 600_000,
        slots: List<PlannedExercise> = listOf(slot("s1", "Pull-Up")),
        sets: List<Pair<String, Int>> = listOf("s1" to 10),
    ): PerformedSession {
        val start = at(atText)
        return PerformedSession(
            id = id,
            startMs = start,
            kcal = kcal,
            activeDurationMs = activeMs,
            plan = WorkoutPlan(id = "plan-$id", exercises = slots),
            setLogs = sets.mapIndexed { i, (slotId, reps) ->
                val name = slots.first { it.slotId == slotId }.name
                SetLog(
                    slotId = slotId,
                    exerciseId = name.lowercase(),
                    exerciseName = name,
                    setIndex = i + 1,
                    reps = reps,
                    startMs = start + i * 60_000L,
                    endMs = start + i * 60_000L + 40_000,
                )
            },
        )
    }

    private val history = listOf(
        session("thisWeek", "2026-08-10T09:00:00"),
        session("thisMonth", "2026-08-05T18:00:00"), // this month, previous week
        session("lastMonth", "2026-07-20T07:00:00"),
    )

    @Test
    fun `week scopes to the current calendar week`() {
        val s = statsSummary(history, StatsPeriod.WEEK, now, zone)
        assertEquals(1, s.workouts)
    }

    @Test
    fun `month includes earlier weeks of the same month but not last month`() {
        val s = statsSummary(history, StatsPeriod.MONTH, now, zone)
        assertEquals(2, s.workouts)
    }

    @Test
    fun `all time counts everything`() {
        val s = statsSummary(history, StatsPeriod.ALL, now, zone)
        assertEquals(3, s.workouts)
    }

    @Test
    fun `an empty period is well-formed`() {
        val s = statsSummary(emptyList(), StatsPeriod.WEEK, now, zone)
        assertTrue(s.isEmpty)
        assertEquals(0, s.workouts)
        assertEquals(0, s.exercises)
        assertEquals(0L, s.avgActiveMs)
    }

    @Test
    fun `volume is added weight times reps, reps and distinct movements summed`() {
        val sessions = listOf(
            session(
                "w",
                "2026-08-10T09:00:00",
                slots = listOf(slot("s1", "Weighted Pull-Up", addedKg = 20.0), slot("s2", "Dip")),
                sets = listOf("s1" to 5, "s2" to 12),
            ),
        )
        val s = statsSummary(sessions, StatsPeriod.WEEK, now, zone)

        assertEquals(17, s.totalReps)            // 5 + 12
        assertEquals(100.0, s.totalVolumeKg, 0.0) // 20kg × 5, the dip adds nothing
        assertEquals(2, s.exercises)              // two distinct movements
    }

    @Test
    fun `average active time is total over workouts`() {
        val sessions = listOf(
            session("a", "2026-08-10T09:00:00", activeMs = 600_000),
            session("b", "2026-08-10T12:00:00", activeMs = 400_000),
        )
        val s = statsSummary(sessions, StatsPeriod.WEEK, now, zone)

        assertEquals(1_000_000, s.totalActiveMs)
        assertEquals(500_000, s.avgActiveMs)
    }
}
