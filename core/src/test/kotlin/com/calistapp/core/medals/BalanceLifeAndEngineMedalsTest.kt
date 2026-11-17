package com.calistapp.core.medals

import com.calistapp.core.medals.MedalTestKit.SetSpec
import com.calistapp.core.medals.MedalTestKit.evaluate
import com.calistapp.core.medals.MedalTestKit.exercise
import com.calistapp.core.medals.MedalTestKit.of
import com.calistapp.core.medals.MedalTestKit.session
import com.calistapp.core.medals.MedalTestKit.set
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class BalanceLifeAndEngineMedalsTest {

    private val gallery = listOf(
        exercise("pushup", "Push-Up", listOf("chest"), "push"),
        exercise("pullup", "Pull-Up", listOf("lats"), "pull"),
        exercise("pike", "Pike Push-Up", listOf("shoulders"), "push"),
        exercise("dip", "Dips", listOf("triceps"), "push"),
        exercise("plank", "Plank", listOf("abdominals"), "static"),
        exercise("squat", "Bodyweight Squat", listOf("quadriceps"), "push"),
    )

    private fun move(id: String, reps: Int = 10) = set(gallery.first { it.id == id }.name, reps = reps, id = id)
    private fun times(n: Int, spec: SetSpec): Array<SetSpec> = Array(n) { spec }

    // ---- Balance --------------------------------------------------------------------------------

    @Test
    fun `full-body week needs all six groups inside one calendar week`() {
        // Week of Monday 7 Sep 2026.
        val sessions = listOf(
            session("a", "2026-09-08T18:00", move("pushup"), move("pullup"), move("pike"), move("dip")),
            session("b", "2026-09-10T18:00", set("Plank", seconds = 60, id = "plank")),
        )
        val five = evaluate(sessions, gallery).of("full-body-week")
        assertTrue(five.awards.isEmpty())
        assertEquals("5 of 6 groups", five.progress!!.label)
        assertEquals("1 group to go", five.progress!!.toGo)

        val nextWeek = evaluate(sessions + session("c", "2026-09-14T18:00", move("squat")), gallery).of("full-body-week")
        assertTrue(nextWeek.awards.isEmpty())

        val award = evaluate(sessions + session("c", "2026-09-12T10:00", move("squat")), gallery).of("full-body-week").awards.single()
        assertEquals("c", award.sessionId)
        assertEquals("ALL 6 GROUPS IN 3 SESSIONS", award.engraving)
    }

    @Test
    fun `even split holds push and pull sets within 1_2 over four weeks`() {
        // Tuesdays of four consecutive weeks: 8, 15, 22 and 29 September.
        fun weeks(push: List<Int>, pull: List<Int>) = (0 until 4).map { w ->
            session(
                "w$w", LocalDate.of(2026, 9, 8).plusWeeks(w.toLong()).toString() + "T18:00",
                *times(push[w], move("pushup")), *times(pull[w], move("pullup")),
            )
        }

        val even = weeks(listOf(6, 6, 6, 6), listOf(5, 5, 5, 5)) // 24 : 20 = 1.2
        assertTrue(evaluate(even.take(3), gallery).of("even-split").awards.isEmpty()) // 18 : 15, too few
        val award = evaluate(even, gallery).of("even-split").awards.single()
        assertEquals("w3", award.sessionId)
        assertEquals("24 PUSH · 20 PULL SETS", award.engraving)

        val uneven = evaluate(weeks(listOf(7, 6, 6, 6), listOf(5, 5, 5, 5)), gallery).of("even-split") // 25 : 20
        assertTrue(uneven.awards.isEmpty())
        assertEquals("20 of 21 pull sets", uneven.progress!!.label)
        assertEquals("1 pull set to go", uneven.progress!!.toGo)

        val thin = evaluate(weeks(listOf(5, 5, 5, 4), listOf(5, 5, 5, 4)), gallery).of("even-split") // 19 : 19
        assertTrue(thin.awards.isEmpty())
        assertEquals("19 of 20 pull sets", thin.progress!!.label)
    }

    @Test
    fun `legs every week for eight weeks running`() {
        val legDays = (0 until 8).map { w ->
            session("l$w", LocalDate.of(2026, 9, 8).plusWeeks(w.toLong()).toString() + "T18:00", move("squat"))
        }
        val upperOnly = session("u", "2026-09-10T18:00", move("pullup"))

        val seven = evaluate(legDays.take(7) + upperOnly, gallery).of("legs-every-week")
        assertTrue(seven.awards.isEmpty())
        assertEquals("7 of 8 weeks", seven.progress!!.label)
        assertEquals("1 week to go", seven.progress!!.toGo)

        val award = evaluate(legDays + upperOnly, gallery).of("legs-every-week").awards.single()
        assertEquals("l7", award.sessionId)
        assertEquals("LEGS 8 WEEKS RUNNING", award.engraving)
    }

    @Test
    fun `explorer counts distinct working exercises, not warm-up routine moves`() {
        val fortyNine = (1..49).map { set("Move $it", reps = 5) }.toTypedArray()
        val first = session("a", "2026-05-01T18:00", *fortyNine, set("Arm circles", seconds = 30, phase = SlotPhase.WARM_UP))
        val line = evaluate(listOf(first)).of("explorer")
        assertTrue(line.awards.isEmpty())
        assertEquals("49 of 50", line.progress!!.label)

        val award = evaluate(listOf(first, session("b", "2026-05-03T18:00", set("Move 1", reps = 5), set("Move 50", reps = 5))))
            .of("explorer").awards.single()
        assertEquals("b", award.sessionId)
        assertEquals("50 DIFFERENT EXERCISES", award.engraving)
    }

    // ---- Life -----------------------------------------------------------------------------------

    @Test
    fun `cold-day session is outdoors and feels under 8 degrees`() {
        fun day(id: String, feels: Double, outdoor: Boolean?) =
            session(id, "2026-11-0${id.last()}T10:00", weather = WeatherSnapshot(tempC = feels + 2, feelsLikeC = feels), outdoor = outdoor)

        val none = evaluate(listOf(day("s1", 8.0, true), day("s2", 2.0, false), day("s3", -5.0, null))).of("cold-day")
        assertTrue(none.awards.isEmpty())

        val award = evaluate(listOf(day("s1", 8.0, true), day("s4", 7.96, true))).of("cold-day").awards.single()
        assertEquals("s4", award.sessionId)
        assertEquals("OUTSIDE, FEELS LIKE 7.9°", award.engraving)

        val freezing = evaluate(listOf(day("s5", -3.25, true))).of("cold-day").awards.single()
        assertEquals("OUTSIDE, FEELS LIKE −3.2°", freezing.engraving)
    }

    @Test
    fun `early bird is 4 to 7 am and night owl is 9 pm to 4 am, local time`() {
        val edges = listOf("03:59", "04:00", "06:59", "07:00", "20:59", "21:00", "23:30", "01:30")
            .mapIndexed { i, t -> session("e$i", "2026-02-${(10 + i)}T$t") }
        val report = evaluate(edges)
        assertEquals("2 of 10", report.of("early-bird").progress!!.label) // 04:00, 06:59
        assertEquals("4 of 10", report.of("night-owl").progress!!.label) // 21:00, 23:30, 01:30, 03:59

        val mornings = (0 until 10).map { session("m$it", LocalDate.of(2026, 3, 1).plusDays(it.toLong()).toString() + "T06:15") }
        assertTrue(evaluate(mornings.take(9)).of("early-bird").awards.isEmpty())
        val award = evaluate(mornings).of("early-bird").awards.single()
        assertEquals("m9", award.sessionId)
        assertEquals("10 STARTS BEFORE 7 AM", award.engraving)

        val nights = (0 until 10).map { session("n$it", LocalDate.of(2026, 3, 1).plusDays(it.toLong()).toString() + "T22:00") }
        assertEquals("10 STARTS AFTER 9 PM", evaluate(nights).of("night-owl").awards.single().engraving)
    }

    @Test
    fun `the time zone decides what local time a session started at`() {
        // 07:30 in Bratislava in November is 06:30 UTC.
        val starts = (0 until 10).map { session("z$it", LocalDate.of(2026, 11, 2).plusDays(it.toLong()).toString() + "T07:30") }
        assertTrue(evaluate(starts).of("early-bird").awards.isEmpty())
        assertEquals(1, evaluate(starts, zone = ZoneOffset.UTC).of("early-bird").awards.size)
    }

    // ---- Hidden ---------------------------------------------------------------------------------

    @Test
    fun `hidden medals show nothing until earned`() {
        val restart = listOf(
            session("a", "2026-04-04T07:00", move("pushup"), totalMinutes = 45),
            session("b", "2026-04-04T08:15", move("pushup")), // 30 min after the first ended: a restart, not a second session
            session("tiny", "2026-04-04T20:00", totalMinutes = 3, activeMinutes = 0), // a saved mis-tap
            session("c", "2026-04-05T10:00", move("pushup", reps = 50), move("pushup", reps = 49), set("Plank", seconds = 120)),
        )
        val report = evaluate(restart)
        listOf("twice-in-a-day", "century").forEach { id ->
            val line = report.of(id)
            assertTrue(line.definition.hidden)
            assertTrue(line.awards.isEmpty())
            assertNull("$id leaks progress", line.progress)
        }
    }

    @Test
    fun `twice in a day and century once earned`() {
        val sessions = listOf(
            session("a", "2026-04-04T07:00", move("pushup"), totalMinutes = 45),
            session("b", "2026-04-04T18:00", move("pushup", reps = 60), move("squat", reps = 60)),
            session("c", "2026-04-05T10:00", move("pushup", reps = 50), move("pushup", reps = 50)),
        )
        val report = evaluate(sessions, gallery)
        val twice = report.of("twice-in-a-day")
        assertEquals("b", twice.awards.single().sessionId)
        assertEquals("2 SESSIONS IN ONE DAY", twice.awards.single().engraving)
        assertTrue(twice.progress!!.complete)

        // 60 + 60 of two different moves isn't a century; 50 + 50 of one is.
        val century = report.of("century")
        assertEquals("c", century.awards.single().sessionId)
        assertEquals("100 REPS IN ONE SESSION", century.awards.single().engraving)
    }

    // ---- The engine as a whole ------------------------------------------------------------------

    @Test
    fun `an empty history earns nothing and starts every visible line at zero`() {
        val report = evaluate(emptyList(), gallery)
        assertEquals(MedalCatalog.all.map { it.id }, report.lines.map { it.definition.id })
        assertTrue(report.awards.isEmpty())
        report.lines.forEach { line ->
            assertTrue(line.definition.id, line.awards.isEmpty())
            if (line.definition.hidden) {
                assertNull(line.progress)
            } else {
                val p = assertNotNullProgress(line)
                assertEquals(line.definition.id, 0.0, p.fraction, 0.0)
                assertEquals(line.definition.id, 0, p.nextTierIndex)
            }
        }
    }

    private fun assertNotNullProgress(line: MedalLine): MedalProgress {
        assertNotNull(line.definition.id, line.progress)
        return line.progress!!
    }

    private val mixedHistory = listOf(
        session("h1", "2026-01-05T06:30", move("pullup", 11), move("pushup", 30), avgHr = 140, recovery = 24, zoneMinutes = intArrayOf(5, 30, 10, 2, 0)),
        session("h2", "2026-01-12T18:00", move("pullup", 16), move("dip", 18), move("squat"), avgHr = 138, sourceWorkoutId = "w"),
        session("h3", "2026-01-12T18:00", move("pike"), set("Plank", seconds = 45, id = "plank"), avgHr = 0),
        session("h4", "2026-02-01T21:30", move("pushup", 52), set("L-Sit", seconds = 31), recovery = 33, outdoor = true,
            weather = WeatherSnapshot(4.0, 1.5)),
        session("h5", "2026-02-20T07:10", set("Pull-Up", reps = 6, kg = 12.5), estimated = true),
    )

    @Test
    fun `the same history in any order gives the same medals`() {
        val planned = setOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12))
        val reference = evaluate(mixedHistory, gallery, planned, now = "2026-02-21T12:00")
        repeat(5) { seed ->
            val shuffled = mixedHistory.shuffled(Random(seed))
            assertEquals(reference, evaluate(shuffled, gallery, planned, now = "2026-02-21T12:00"))
        }
        assertTrue(reference.awards.isNotEmpty())
    }

    @Test
    fun `awards come out oldest first`() {
        val awards = evaluate(mixedHistory, gallery).awards
        assertEquals(awards.sortedBy { it.earnedAtMs }.map { it.earnedAtMs }, awards.map { it.earnedAtMs })
        assertEquals("first-pull-up", awards.first().medalId)
    }

    @Test
    fun `deleting the earning session moves the medal to the next one that earns it`() {
        val sessions = listOf(
            session("s1", "2026-06-01T18:00", move("pullup", 10)),
            session("s2", "2026-06-05T18:00", move("pullup", 12)),
        )
        assertEquals("s1", evaluate(sessions).of("pull-ups").awards.single().sessionId)
        val after = evaluate(sessions.drop(1)).of("pull-ups").awards.single()
        assertEquals("s2", after.sessionId)
        assertEquals("12 REPS IN ONE SET", after.engraving)
    }
}
