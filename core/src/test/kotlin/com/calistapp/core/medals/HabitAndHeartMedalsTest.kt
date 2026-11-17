package com.calistapp.core.medals

import com.calistapp.core.medals.MedalTestKit.at
import com.calistapp.core.medals.MedalTestKit.evaluate
import com.calistapp.core.medals.MedalTestKit.of
import com.calistapp.core.medals.MedalTestKit.session
import com.calistapp.core.medals.MedalTestKit.set
import com.calistapp.core.progress.PerformedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitAndHeartMedalsTest {

    /** [count] sessions, one a day from [first], at 18:00. */
    private fun daily(count: Int, first: LocalDate = LocalDate.of(2026, 1, 1), build: (Int, String) -> PerformedSession = { i, start ->
        session("d$i", start, set("Push-Up", reps = 10))
    }): List<PerformedSession> = (0 until count).map { i -> build(i, "${first.plusDays(i.toLong())}T18:00") }

    // ---- Showing up -----------------------------------------------------------------------------

    @Test
    fun `week streak strikes on the first session of the fourth week in a row`() {
        // Mondays 7 Sep 2026 onward.
        val sessions = listOf(
            session("w1", "2026-09-08T18:00"),
            session("w2", "2026-09-16T18:00"),
            session("w3a", "2026-09-21T07:00"),
            session("w3b", "2026-09-25T18:00"),
            session("w4", "2026-10-02T18:00"),
        )
        val three = evaluate(sessions.dropLast(1)).of("week-streak")
        assertTrue(three.awards.isEmpty())
        assertEquals("3 of 4", three.progress!!.label)
        assertEquals("1 week to go", three.progress!!.toGo)

        val four = evaluate(sessions).of("week-streak")
        assertEquals("w4", four.awards.single().sessionId)
        assertEquals("4 WEEKS IN A ROW", four.awards.single().engraving)
    }

    @Test
    fun `a missed week resets the streak, and a lapsed streak shows no progress`() {
        val sessions = listOf(
            session("w1", "2026-09-08T18:00"),
            session("w2", "2026-09-16T18:00"),
            session("w3", "2026-09-22T18:00"),
            // week of 28 Sep skipped
            session("w5", "2026-10-06T18:00"),
        )
        val line = evaluate(sessions, now = "2026-10-07T12:00").of("week-streak")
        assertTrue(line.awards.isEmpty())
        assertEquals("1 of 4", line.progress!!.label)
        // A quiet current week doesn't end a streak; two quiet weeks do.
        assertEquals("1 of 4", evaluate(sessions, now = "2026-10-13T12:00").of("week-streak").progress!!.label)
        assertEquals("0 of 4", evaluate(sessions, now = "2026-10-20T12:00").of("week-streak").progress!!.label)
    }

    @Test
    fun `sessions ladder counts every session and engraves its number`() {
        val fortyNine = evaluate(daily(49)).of("sessions")
        assertTrue(fortyNine.awards.isEmpty())
        assertEquals("49 of 50", fortyNine.progress!!.label)
        assertEquals("1 to go", fortyNine.progress!!.toGo)

        val fifty = evaluate(daily(50)).of("sessions").awards.single()
        assertEquals("d49", fifty.sessionId)
        assertEquals("SESSION 50", fifty.engraving)
        assertEquals(at("2026-02-19T18:00"), fifty.earnedAtMs)
    }

    @Test
    fun `perfect month needs every planned day of the month trained`() {
        val planned = setOf(5, 8, 12, 19).map { LocalDate.of(2026, 10, it) }.toSet()
        val three = listOf(
            session("a", "2026-10-05T18:00"),
            session("b", "2026-10-08T18:00"),
            session("c", "2026-10-12T18:00"),
            session("x", "2026-10-18T18:00"), // an unplanned day doesn't stand in for a planned one
        )
        val partial = evaluate(three, plannedDays = planned, now = "2026-10-18T20:00").of("perfect-month")
        assertTrue(partial.awards.isEmpty())
        assertEquals("3 of 4 days", partial.progress!!.label)
        assertEquals("1 day to go", partial.progress!!.toGo)

        val all = evaluate(three + session("d", "2026-10-19T07:30"), plannedDays = planned).of("perfect-month")
        assertEquals("d", all.awards.single().sessionId)
        assertEquals("ALL 4 PLANNED DAYS", all.awards.single().engraving)
    }

    @Test
    fun `a month planned for under four days can't be perfect, and a missed day empties the bar`() {
        val tiny = setOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8))
        val sessions = listOf(session("a", "2026-10-05T18:00"), session("b", "2026-10-08T18:00"))
        assertTrue(evaluate(sessions, plannedDays = tiny).of("perfect-month").awards.isEmpty())

        val planned = setOf(5, 8, 12, 19).map { LocalDate.of(2026, 10, it) }.toSet()
        val missed = evaluate(sessions, plannedDays = planned, now = "2026-10-13T09:00").of("perfect-month").progress!!
        assertEquals(0.0, missed.fraction, 0.0)
        assertEquals("2 of 4 days", missed.label)
    }

    @Test
    fun `comeback is a session after fourteen whole days off`() {
        val thirteenOff = listOf(session("a", "2026-04-01T18:00"), session("b", "2026-04-15T18:00"))
        assertTrue(evaluate(thirteenOff).of("comeback").awards.isEmpty())
        assertFalse(evaluate(thirteenOff).of("comeback").progress!!.measurable)

        val fourteenOff = listOf(session("a", "2026-04-01T18:00"), session("b", "2026-04-16T09:00"))
        val award = evaluate(fourteenOff).of("comeback").awards.single()
        assertEquals("b", award.sessionId)
        assertEquals("BACK AFTER 14 DAYS", award.engraving)
    }

    // ---- Heart ----------------------------------------------------------------------------------

    @Test
    fun `recovery 30 at and just below`() {
        val below = evaluate(listOf(session("a", "2026-11-01T18:00", recovery = 29))).of("recovery-30")
        assertTrue(below.awards.isEmpty())
        assertEquals("29 of 30 bpm", below.progress!!.label)
        assertEquals("1 bpm to go", below.progress!!.toGo)

        val award = evaluate(listOf(session("a", "2026-11-12T18:00", recovery = 31))).of("recovery-30").awards.single()
        assertEquals("−31 BPM IN 1 MIN", award.engraving)
        assertEquals(31.0, award.value, 0.0)
    }

    @Test
    fun `ten bpm lower compares a saved workout with the mean of its first three runs`() {
        fun run(id: String, day: String, hr: Int, saved: String = "w-upper", estimated: Boolean = false) =
            session(id, "${day}T18:00", set("Pull-Up", reps = 8), avgHr = hr, sourceWorkoutId = saved, estimated = estimated)

        val baseline = listOf(run("r1", "2026-06-01", 150), run("r2", "2026-06-08", 152), run("r3", "2026-06-15", 148))
        val history = baseline + listOf(
            run("est", "2026-06-20", 120, estimated = true), // no trustworthy heart rate: ignored
            run("other", "2026-06-21", 120, saved = "w-legs"), // a different workout: not compared
            run("r4", "2026-06-22", 141), // 9 below
        )
        val below = evaluate(history).of("ten-bpm-lower")
        assertTrue(below.awards.isEmpty())
        assertEquals("9 of 10 bpm", below.progress!!.label)

        val award = evaluate(history + run("r5", "2026-06-29", 140)).of("ten-bpm-lower").awards.single()
        assertEquals("r5", award.sessionId)
        assertEquals("−10 BPM, SAME WORKOUT", award.engraving)
    }

    @Test
    fun `older sessions without a saved-workout link match by name and exercises`() {
        fun run(id: String, day: String, hr: Int) =
            session(id, "${day}T18:00", set("Pull-Up", reps = 8), set("Dips", reps = 10), avgHr = hr, planName = "Upper A")
        val history = listOf(run("r1", "2026-06-01", 160), run("r2", "2026-06-08", 160), run("r3", "2026-06-15", 160), run("r4", "2026-06-22", 148))
        assertEquals("r4", evaluate(history).of("ten-bpm-lower").awards.single().sessionId)
    }

    @Test
    fun `every beat counts only measured sessions with full heart-rate coverage`() {
        val trusted = daily(24) { i, start -> session("d$i", start, avgHr = 130, hrCoverage = 0.95) }
        val untrusted = listOf(
            session("est", "2026-03-01T18:00", avgHr = 130, estimated = true),
            session("gappy", "2026-03-02T18:00", avgHr = 130, hrCoverage = 0.94),
            session("nohr", "2026-03-03T18:00", avgHr = 0),
        )
        val line = evaluate(trusted + untrusted).of("every-beat")
        assertTrue(line.awards.isEmpty())
        assertEquals("24 of 25", line.progress!!.label)
        assertEquals("1 session to go", line.progress!!.toGo)

        val award = evaluate(trusted + untrusted + session("full", "2026-03-04T18:00", avgHr = 130)).of("every-beat").awards.single()
        assertEquals("full", award.sessionId)
        assertEquals("25 FULL-HR SESSIONS", award.engraving)
    }

    @Test
    fun `steady engine adds up ten hours in zone two`() {
        fun zone2(minutes: Int) = intArrayOf(5, minutes, 10, 0, 0)
        val sessions = listOf(
            session("a", "2026-01-01T18:00", zoneMinutes = zone2(300)),
            session("b", "2026-01-02T18:00", zoneMinutes = zone2(299)),
            session("c", "2026-01-03T18:00", zoneMinutes = IntArray(0)), // no breakdown: adds nothing
        )
        val below = evaluate(sessions).of("steady-engine")
        assertTrue(below.awards.isEmpty())
        assertEquals("9 of 10 h", below.progress!!.label)
        assertEquals("1 min to go", below.progress!!.toGo)

        val award = evaluate(sessions + session("d", "2026-01-04T18:00", zoneMinutes = zone2(16))).of("steady-engine").awards.single()
        assertEquals("d", award.sessionId)
        assertEquals("10.2 H IN ZONE 2", award.engraving)
    }
}
