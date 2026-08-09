package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TrainingProgressTest {

    private val zone = ZoneId.of("Europe/Bratislava")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** Monday 10 Aug 2026, mid-morning — "now" for every test here. */
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

    @Test
    fun `nothing recorded produces an empty but well-formed window`() {
        val progress = summarizeProgress(emptyList(), now, weekCount = 4, zone = zone)

        assertTrue(progress.isEmpty)
        assertEquals(4, progress.weeks.size)
        assertEquals(0, progress.streakWeeks)
        assertTrue(progress.exercises.isEmpty())
    }

    @Test
    fun `weeks you did not train are still in the series`() {
        // A chart that omits the quiet weeks reads as unbroken consistency.
        val progress = summarizeProgress(
            listOf(session("a", "2026-08-10T09:00:00")),
            now,
            weekCount = 4,
            zone = zone,
        )

        assertEquals(4, progress.weeks.size)
        assertEquals(listOf(false, false, false, true), progress.weeks.map { it.trained })
        assertEquals(at("2026-08-10T00:00:00"), progress.weeks.last().weekStartMs)
    }

    @Test
    fun `sessions land in the calendar week they happened in`() {
        val progress = summarizeProgress(
            listOf(
                session("a", "2026-08-10T09:00:00", kcal = 300),
                session("b", "2026-08-05T18:00:00", kcal = 200),
                session("c", "2026-08-03T07:00:00", kcal = 100),
            ),
            now,
            weekCount = 3,
            zone = zone,
        )

        val thisWeek = progress.weeks.last()
        val lastWeek = progress.weeks[progress.weeks.lastIndex - 1]
        assertEquals(300, thisWeek.kcal)
        assertEquals(1, thisWeek.sessions)
        // 3 Aug and 5 Aug are both in the week beginning Monday 3 Aug.
        assertEquals(300, lastWeek.kcal)
        assertEquals(2, lastWeek.sessions)
        assertEquals(600, progress.totalKcal)
    }

    @Test
    fun `a streak counts consecutive weeks back from now`() {
        val progress = summarizeProgress(
            listOf(
                session("a", "2026-08-10T09:00:00"),
                session("b", "2026-08-04T09:00:00"),
                session("c", "2026-07-29T09:00:00"),
            ),
            now,
            zone = zone,
        )

        assertEquals(3, progress.streakWeeks)
    }

    @Test
    fun `a quiet current week does not break the streak yet`() {
        // Monday morning, nothing done today. Last week and the one before were trained.
        val progress = summarizeProgress(
            listOf(
                session("b", "2026-08-06T09:00:00"),
                session("c", "2026-07-30T09:00:00"),
            ),
            now,
            zone = zone,
        )

        assertEquals(2, progress.streakWeeks)
    }

    @Test
    fun `missing a whole week ends the streak`() {
        val progress = summarizeProgress(
            listOf(
                session("a", "2026-08-10T09:00:00"),
                // nothing in the week of 3 Aug
                session("c", "2026-07-29T09:00:00"),
            ),
            now,
            zone = zone,
        )

        assertEquals(1, progress.streakWeeks)
    }

    @Test
    fun `per-exercise totals accumulate across sessions`() {
        val progress = summarizeProgress(
            listOf(
                session("a", "2026-08-10T09:00:00", sets = listOf("s1" to 10, "s1" to 8)),
                session("b", "2026-08-05T09:00:00", sets = listOf("s1" to 12, "s1" to 9)),
            ),
            now,
            zone = zone,
        )

        val pullUp = progress.exercises.single()
        assertEquals("Pull-Up", pullUp.exerciseName)
        assertEquals(2, pullUp.sessionCount)
        assertEquals(4, pullUp.totalSets)
        assertEquals(39, pullUp.totalReps)
        assertEquals(39, progress.totalReps)
    }

    @Test
    fun `the best set by reps is remembered with when it happened`() {
        val progress = summarizeProgress(
            listOf(
                session("a", "2026-08-10T09:00:00", sets = listOf("s1" to 10)),
                session("b", "2026-08-05T09:00:00", sets = listOf("s1" to 14, "s1" to 6)),
            ),
            now,
            zone = zone,
        )

        val best = progress.exercises.single().mostReps!!
        assertEquals(14, best.reps)
        assertEquals(at("2026-08-05T09:00:00"), best.atMs)
    }

    @Test
    fun `added load comes off the plan and drives the heaviest set`() {
        // The set log carries no weight — it lives on the plan's slot.
        val weighted = session(
            "a",
            "2026-08-10T09:00:00",
            slots = listOf(slot("s1", "Pull-Up", addedKg = 20.0)),
            sets = listOf("s1" to 5),
        )
        val bodyweight = session("b", "2026-08-05T09:00:00", sets = listOf("s1" to 12))

        val pullUp = summarizeProgress(listOf(weighted, bodyweight), now, zone = zone).exercises.single()

        assertEquals(20.0, pullUp.heaviest!!.addedWeightKg, 1e-9)
        assertEquals(5, pullUp.heaviest.reps)
        assertEquals(12, pullUp.mostReps!!.reps)
    }

    @Test
    fun `a movement never loaded has no heaviest set`() {
        val progress = summarizeProgress(listOf(session("a", "2026-08-10T09:00:00")), now, zone = zone)

        assertNull(progress.exercises.single().heaviest)
    }

    @Test
    fun `movements are ranked by how much you have actually done`() {
        val slots = listOf(slot("s1", "Pull-Up"), slot("s2", "Push-Up"))
        val progress = summarizeProgress(
            listOf(
                session(
                    "a",
                    "2026-08-10T09:00:00",
                    slots = slots,
                    sets = listOf("s1" to 8, "s2" to 30),
                ),
            ),
            now,
            zone = zone,
        )

        assertEquals(listOf("Push-Up", "Pull-Up"), progress.exercises.map { it.exerciseName })
    }

    @Test
    fun `sessions older than the window still count towards totals and records`() {
        // The chart is a window; the personal best is not.
        val progress = summarizeProgress(
            listOf(session("old", "2025-01-06T09:00:00", sets = listOf("s1" to 20))),
            now,
            weekCount = 4,
            zone = zone,
        )

        assertEquals(1, progress.totalSessions)
        assertEquals(20, progress.exercises.single().mostReps!!.reps)
        assertTrue("Out-of-window weeks stay empty", progress.weeks.none { it.trained })
    }
}
