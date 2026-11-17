package com.calistapp.core.medals

import com.calistapp.core.medals.MedalTestKit.at
import com.calistapp.core.medals.MedalTestKit.evaluate
import com.calistapp.core.medals.MedalTestKit.of
import com.calistapp.core.medals.MedalTestKit.session
import com.calistapp.core.medals.MedalTestKit.set
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillAndStrengthMedalsTest {

    // ---- Skills ---------------------------------------------------------------------------------

    @Test
    fun `first pull-up needs one clean rep, not a regression of one`() {
        val before = listOf(
            session("a", "2026-03-01T10:00", set("Band-Assisted Pull-Up", reps = 8), set("Negative Pull-Up", reps = 5), set("Chin-Up", reps = 3)),
        )
        assertTrue(evaluate(before).of("first-pull-up").awards.isEmpty())

        val report = evaluate(before + session("b", "2026-03-14T18:30", set("Pull-Up", reps = 1)))
        val award = report.of("first-pull-up").awards.single()
        assertEquals("b", award.sessionId)
        assertEquals(at("2026-03-14T18:30"), award.earnedAtMs)
        assertEquals("1 CLEAN REP", award.engraving)
        assertEquals(MedalMetal.COPPER, award.metal)
    }

    @Test
    fun `a warm-up set is not an effort`() {
        val report = evaluate(listOf(session("a", "2026-03-01T10:00", set("Pull-Up", reps = 3, warmup = true))))
        assertTrue(report.of("first-pull-up").awards.isEmpty())
    }

    @Test
    fun `first muscle-up says bar or rings`() {
        val report = evaluate(listOf(session("a", "2026-08-02T10:00", set("Ring Muscle-Up", reps = 2))))
        assertEquals("2 REPS ON THE RINGS", report.of("first-muscle-up").awards.single().engraving)
        val bar = evaluate(listOf(session("a", "2026-08-02T10:00", set("Muscle-Up", reps = 1))))
        assertEquals("1 REP ON THE BAR", bar.of("first-muscle-up").awards.single().engraving)
    }

    @Test
    fun `first pistol needs a rep on each leg, which the log holds as a total of two`() {
        val oneLeg = evaluate(listOf(session("a", "2026-05-01T10:00", set("Pistol Squat", reps = 1), set("Box Pistol Squat", reps = 12))))
        val line = oneLeg.of("first-pistol-squat")
        assertTrue(line.awards.isEmpty())
        assertEquals("1 of 2 legs", line.progress!!.label)
        assertEquals("1 leg to go", line.progress!!.toGo)
        assertEquals(0.5, line.progress!!.fraction, 1e-9)

        val both = evaluate(listOf(session("a", "2026-05-02T10:00", set("Pistol Squat", reps = 2))))
        assertEquals("2 REPS, BOTH LEGS", both.of("first-pistol-squat").awards.single().engraving)
    }

    @Test
    fun `30-second L-sit is one hold of 30 s, read from seconds or from an old log's reps`() {
        val short = evaluate(listOf(session("a", "2026-11-01T10:00", set("L-Sit", seconds = 29), set("Hanging L-Sit", seconds = 60))))
        assertTrue(short.of("l-sit-30").awards.isEmpty())
        assertEquals("29 of 30 s", short.of("l-sit-30").progress!!.label)
        assertEquals("1 s to go", short.of("l-sit-30").progress!!.toGo)

        val exact = evaluate(listOf(session("a", "2026-11-06T10:00", set("L-Sit", seconds = 30))))
        assertEquals("L-SIT 30 S", exact.of("l-sit-30").awards.single().engraving)

        val legacy = evaluate(listOf(session("a", "2026-11-06T10:00", set("cal_l_sit", seconds = 32, legacyHold = true))))
        assertEquals("L-SIT 32 S", legacy.of("l-sit-30").awards.single().engraving)
    }

    @Test
    fun `tuck front lever is a 10 s hold, not reps of a raise`() {
        val below = evaluate(listOf(session("a", "2026-10-01T10:00", set("Tuck Front Lever", seconds = 9), set("Front Lever Raise", reps = 12))))
        assertTrue(below.of("tuck-front-lever").awards.isEmpty())
        assertEquals("9 of 10 s", below.of("tuck-front-lever").progress!!.label)

        val at = evaluate(listOf(session("a", "2026-10-02T10:00", set("Tuck Front Lever", seconds = 10))))
        assertEquals("LEVER 10 S", at.of("tuck-front-lever").awards.single().engraving)
    }

    // ---- Strength -------------------------------------------------------------------------------

    @Test
    fun `pull-up tiers strike on the first session to reach each, engraved with that set`() {
        val sessions = listOf(
            session("s1", "2026-06-01T18:00", set("Pull-Up", reps = 9)),
            session("s2", "2026-06-03T18:00", set("Pull-Up", reps = 8), set("Pull-Up", reps = 11)),
            session("s3", "2026-07-10T18:00", set("Pull-Up", reps = 13)),
            session("s4", "2026-08-20T18:00", set("Pullups", reps = 15)),
            session("s5", "2026-09-01T18:00", set("Pull-Up", reps = 14)),
        )
        val line = evaluate(sessions).of("pull-ups")
        assertEquals(listOf(0, 1), line.awards.map { it.tierIndex })
        assertEquals(listOf("s2", "s4"), line.awards.map { it.sessionId })
        assertEquals(listOf(at("2026-06-03T18:00"), at("2026-08-20T18:00")), line.awards.map { it.earnedAtMs })
        assertEquals("11 REPS IN ONE SET", line.awards[0].engraving)
        assertEquals("15 REPS IN ONE SET", line.awards[1].engraving)
        assertEquals(listOf(MedalMetal.COPPER, MedalMetal.SILVER), line.awards.map { it.metal })

        val progress = line.progress!!
        assertEquals(2, progress.nextTierIndex)
        assertEquals("15 of 20", progress.label)
        assertEquals("5 reps to go", progress.toGo)
        assertEquals(0.75, progress.fraction, 1e-9)
    }

    @Test
    fun `progress toward the next tier counts from zero`() {
        val line = evaluate(
            listOf(
                session("s1", "2026-06-01T18:00", set("Pull-Up", reps = 10)),
                session("s2", "2026-06-08T18:00", set("Pull-Up", reps = 13)),
            ),
        ).of("pull-ups")
        assertEquals("13 of 15", line.progress!!.label)
        assertEquals("2 reps to go", line.progress!!.toGo)
        assertEquals(13.0 / 15, line.progress!!.fraction, 1e-9)
    }

    @Test
    fun `one big set strikes every tier it passes at once`() {
        val line = evaluate(listOf(session("s1", "2026-06-01T18:00", set("Pull-Up", reps = 21)))).of("pull-ups")
        assertEquals(3, line.awards.size)
        assertTrue(line.awards.all { it.sessionId == "s1" && it.engraving == "21 REPS IN ONE SET" })
        assertTrue(line.progress!!.complete)
    }

    @Test
    fun `single-side variants never pass as bilateral reps`() {
        val line = evaluate(listOf(session("s1", "2026-06-01T18:00", set("Archer Pull-Up", reps = 20)))).of("pull-ups")
        assertTrue(line.awards.isEmpty())
        assertEquals("0 of 10", line.progress!!.label)
    }

    @Test
    fun `push-ups and dips at and just below each first tier`() {
        val below = evaluate(
            listOf(session("s1", "2026-06-01T18:00", set("Push-Up", reps = 24), set("Knee Push-Ups", reps = 60), set("Dips", reps = 14), set("Bench Dips", reps = 40))),
        )
        assertTrue(below.of("push-ups").awards.isEmpty())
        assertTrue(below.of("dips").awards.isEmpty())

        val at = evaluate(listOf(session("s1", "2026-06-01T18:00", set("Pushups", reps = 25), set("Parallel Bar Dips", reps = 15))))
        assertEquals("25 REPS IN ONE SET", at.of("push-ups").awards.single().engraving)
        assertEquals("15 REPS IN ONE SET", at.of("dips").awards.single().engraving)
    }

    @Test
    fun `weighted pull-up needs the load for five reps`() {
        val below = evaluate(
            listOf(session("s1", "2026-06-01T18:00", set("Pull-Up", reps = 5, kg = 9.5), set("Pull-Up", reps = 4, kg = 15.0))),
        ).of("weighted-pull-up")
        assertTrue(below.awards.isEmpty())
        assertEquals("+9.5 of +10 kg", below.progress!!.label)
        assertEquals("0.5 kg to go", below.progress!!.toGo)

        val line = evaluate(
            listOf(
                session("s1", "2026-06-01T18:00", set("Pull-Up", reps = 5, kg = 10.0)),
                // An older log with the load only on the plan slot still counts.
                session("s2", "2026-07-01T18:00", set("Weighted Plate Pull-Ups", reps = 6, slotKg = 20.0)),
            ),
        ).of("weighted-pull-up")
        assertEquals(listOf("s1", "s2"), line.awards.map { it.sessionId })
        assertEquals("+10 KG × 5 REPS", line.awards[0].engraving)
        assertEquals("+20 KG × 6 REPS", line.awards[1].engraving)
        assertEquals("+20 of +30 kg", line.progress!!.label)
    }
}
