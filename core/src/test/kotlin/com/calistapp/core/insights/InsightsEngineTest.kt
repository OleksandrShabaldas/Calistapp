package com.calistapp.core.insights

import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutStyle
import com.calistapp.core.progress.PerformedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsEngineTest {

    private val zone = ZoneId.of("Europe/Bratislava")
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** Sunday 15 Nov 2026, evening. The 4-week window is Oct 19 – Nov 15, the one before Sep 21 – Oct 18. */
    private val now = at("2026-11-15T20:00:00")

    private val catalog = listOf(
        Exercise("pullup", "Pull-up", BodyPart.BACK, primaryMuscles = listOf("lats"), force = "pull", isCalisthenics = true),
        Exercise("pushup", "Push-up", BodyPart.CHEST, primaryMuscles = listOf("chest"), force = "push", isCalisthenics = true),
        Exercise("dip", "Dip", BodyPart.CHEST, primaryMuscles = listOf("triceps"), force = "push", isCalisthenics = true),
        Exercise("squat", "Squat", BodyPart.OTHER, primaryMuscles = listOf("quadriceps"), force = "push", isCalisthenics = true),
        Exercise("nordic", "Nordic curl", BodyPart.OTHER, primaryMuscles = listOf("hamstrings"), force = "pull", isCalisthenics = true),
        Exercise("lsit", "L-sit", BodyPart.OTHER, primaryMuscles = listOf("abdominals"), force = "static", isCalisthenics = true),
    ).associateBy { it.id }

    private data class S(val ex: String, val reps: Int, val kg: Double = 0.0, val hold: Boolean = false)

    private var counter = 0
    private fun session(
        atText: String,
        sets: List<S>,
        rpe: Int? = null,
        avgHr: Int = 0,
        recovery: Int? = null,
        style: WorkoutStyle = WorkoutStyle.BY_EXERCISE,
        plan: String? = null,
    ): PerformedSession {
        val start = at(atText)
        val slots = sets.map { it.ex }.distinct().map { ex ->
            val hold = sets.first { it.ex == ex }.hold
            PlannedExercise(slotId = "slot-$ex", exerciseId = ex, name = catalog[ex]?.name ?: ex, measure = if (hold) ExerciseMeasure.SECONDS else ExerciseMeasure.REPS)
        }
        val id = "s${counter++}"
        return PerformedSession(
            id = id,
            startMs = start,
            kcal = 300,
            activeDurationMs = 40 * 60_000L,
            setLogs = sets.mapIndexed { i, s ->
                SetLog(slotId = "slot-${s.ex}", exerciseId = s.ex, exerciseName = catalog[s.ex]?.name ?: s.ex, setIndex = i + 1, reps = s.reps,
                    weightKg = s.kg, startMs = start + i * 60_000L, endMs = start + i * 60_000L + 40_000)
            },
            plan = WorkoutPlan(id = "plan-$id", name = plan ?: "", exercises = slots, style = style, sourceWorkoutId = plan),
            avgHr = avgHr,
            totalDurationMs = 50 * 60_000L,
            rpe = rpe,
            hrRecoveryMeanDrop = recovery,
        )
    }

    private fun report(
        sessions: List<PerformedSession>,
        planned: Set<LocalDate> = emptySet(),
        profile: UserProfile? = null,
        period: InsightsPeriod = InsightsPeriod.FOUR_WEEKS,
        nowMs: Long = now,
    ) = InsightsEngine.report(InsightsInput(sessions, catalog, planned, profile = profile, period = period, nowMs = nowMs, zone = zone))

    @Test fun `empty history is an empty issue with a headline`() {
        val r = report(emptyList())
        assertTrue(r.isEmpty)
        assertTrue(r.questions.all { it.verdict == Verdict.NOT_ENOUGH_DATA })
        assertTrue(r.headline.single().isNotBlank())
        assertEquals(listOf("NOT YET"), r.questions.map { it.pill }.distinct())
    }

    @Test fun `the four-week window ends today and the previous one sits right before it`() {
        val (cur, prev) = InsightsEngine.windows(InsightsPeriod.FOUR_WEEKS, now, zone)
        assertEquals(LocalDate.of(2026, 10, 19), cur.start)
        assertEquals(LocalDate.of(2026, 11, 15), cur.endInclusive)
        assertEquals(LocalDate.of(2026, 10, 18), prev.endInclusive)
        assertEquals(cur.startMs, prev.endMs)
    }

    private fun pullupHistory() = listOf(
        session("2026-09-25T18:00:00", listOf(S("pullup", 11), S("pushup", 30))),
        session("2026-10-05T18:00:00", listOf(S("pullup", 12), S("pushup", 32))),
        session("2026-10-22T18:00:00", listOf(S("pullup", 12), S("pushup", 33))),
        session("2026-11-02T18:00:00", listOf(S("pullup", 13), S("pushup", 34))),
        session("2026-11-12T18:00:00", listOf(S("pullup", 14), S("pushup", 35))),
    )

    @Test fun `new bests make stronger a yes, with the lead movement and a load suggestion`() {
        val r = report(pullupHistory())
        val q = r.questions.first { it.question == Question.STRONGER }
        assertEquals(Verdict.GOOD, q.verdict)
        assertEquals("YES", q.pill)
        val lead = q.keyMovements.first { it.exerciseKey == "pullup" }
        assertEquals(MovementStatus.IMPROVED, lead.status)
        assertEquals("▲ 2", lead.delta)
        assertTrue(q.fact, q.fact.isNotBlank() && !q.fact.contains("{"))
        assertEquals(MoveKind.ADD_LOAD, q.move?.kind)
        assertEquals(listOf("pullup"), q.move?.exerciseIds)
        assertTrue(q.chart is InsightChart.Trend)
    }

    @Test fun `the same number four sessions running is stuck, and nothing improving is watch it`() {
        val sessions = listOf(
            session("2026-10-01T18:00:00", listOf(S("dip", 8))),
            session("2026-10-21T18:00:00", listOf(S("dip", 8))),
            session("2026-10-28T18:00:00", listOf(S("dip", 8))),
            session("2026-11-04T18:00:00", listOf(S("dip", 8))),
            session("2026-11-11T18:00:00", listOf(S("dip", 8))),
        )
        val q = report(sessions).questions.first { it.question == Question.STRONGER }
        assertEquals(Verdict.WATCH, q.verdict)
        assertEquals(MovementStatus.STUCK, q.keyMovements.single().status)
        assertEquals(MoveKind.UNSTICK, q.move?.kind)
    }

    @Test fun `timed holds compare in seconds`() {
        val sessions = listOf(
            session("2026-10-01T18:00:00", listOf(S("lsit", 25, hold = true))),
            session("2026-10-25T18:00:00", listOf(S("lsit", 28, hold = true))),
            session("2026-11-10T18:00:00", listOf(S("lsit", 32, hold = true))),
        )
        val r = report(sessions)
        val lsit = r.questions.first { it.question == Question.STRONGER }.keyMovements.single()
        assertEquals("32 s", lsit.to)
        assertEquals("▲ 7 s", lsit.delta)
        assertEquals("32 s", r.records?.lead?.value)
    }

    @Test fun `records lead with the biggest gain and say what it beat`() {
        val board = report(pullupHistory()).records
        assertNotNull(board)
        assertEquals("pullup", board!!.lead.exerciseKey)
        assertEquals("14", board.lead.value)
        assertEquals("reps", board.lead.unit)
        assertEquals("▲ 2", board.lead.delta)
        assertTrue(board.lead.note.isNotBlank())
        assertEquals("pushup", board.others.single().exerciseKey)
    }

    @Test fun `a split with almost no legs is a gap, and the move is a leg day`() {
        val upper = listOf(S("pullup", 10), S("pullup", 9), S("pushup", 20), S("pushup", 18), S("dip", 10), S("dip", 9))
        val sessions = listOf(
            session("2026-09-30T18:00:00", upper + S("squat", 20)),
            session("2026-10-21T18:00:00", upper),
            session("2026-10-28T18:00:00", upper),
            session("2026-11-04T18:00:00", upper + S("squat", 20)),
            session("2026-11-11T18:00:00", upper),
        )
        val q = report(sessions).questions.first { it.question == Question.BALANCED }
        assertTrue(q.verdict == Verdict.WATCH || q.verdict == Verdict.MIXED)
        val split = q.chart as InsightChart.Split
        assertTrue(split.parts.first { it.group == MovementGroup.LEGS }.share < 0.1)
        assertTrue("hamstrings never trained should be neglected", split.neglected.any { it.muscle == "Hamstrings" })
        assertEquals(MoveKind.PLAN_GROUP_DAY, q.move?.kind)
        assertEquals(MovementGroup.LEGS, q.move?.group)
        assertTrue(q.move!!.detail.contains("Nordic curl"))
    }

    @Test fun `two missed Fridays out of sixteen planned days is mostly, with a shorter-Friday move`() {
        val planned = HashSet<LocalDate>()
        var d = LocalDate.of(2026, 10, 19)
        while (!d.isAfter(LocalDate.of(2026, 11, 15))) {
            if (d.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) planned += d
            d = d.plusDays(1)
        }
        val missed = setOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 13)) // both Fridays
        val sessions = listOf(session("2026-09-01T18:00:00", listOf(S("pushup", 20)))) +
            planned.filter { it !in missed }.map { session("${it}T18:00:00", listOf(S("pushup", 20))) }
        val q = report(sessions, planned = planned).questions.first { it.question == Question.CONSISTENT }
        assertEquals(Verdict.MIXED, q.verdict)
        assertEquals("MOSTLY", q.pill)
        assertEquals(MoveKind.SHORTEN_DAY, q.move?.kind)
        assertTrue(q.move!!.title, q.move!!.title.contains("Friday"))
        assertTrue(q.chart is InsightChart.Calendar)
    }

    @Test fun `recovery climbing against the period before makes fitter a yes`() {
        val sessions = listOf(
            session("2026-09-25T18:00:00", listOf(S("pushup", 20)), avgHr = 140, recovery = 20),
            session("2026-10-08T18:00:00", listOf(S("pushup", 20)), avgHr = 140, recovery = 22),
            session("2026-10-25T18:00:00", listOf(S("pushup", 20)), avgHr = 138, recovery = 28),
            session("2026-11-10T18:00:00", listOf(S("pushup", 20)), avgHr = 136, recovery = 30),
        )
        val q = report(sessions).questions.first { it.question == Question.FITTER }
        assertEquals(Verdict.GOOD, q.verdict)
        assertFalse(q.fact.contains("{"))
        assertTrue(q.chart is InsightChart.Trend)
    }

    @Test fun `sessions that feel much harder than the heart rate says are watch it`() {
        val profile = UserProfile(restingHr = 55, maxHr = 190)
        val calm = (1..6).map { i -> session("2026-10-${String.format("%02d", i * 3)}T18:00:00", listOf(S("pushup", 20)), rpe = 6, avgHr = 140) }
        val hard = listOf("2026-11-10", "2026-11-12", "2026-11-14").map { session("${it}T18:00:00", listOf(S("pushup", 20)), rpe = 9, avgHr = 138) }
        val q = report(calm + hard, profile = profile).questions.first { it.question == Question.RECOVERING }
        assertEquals(Verdict.WATCH, q.verdict)
        assertEquals("WATCH IT", q.pill)
        assertEquals(MoveKind.EASY_DAY, q.move?.kind)
        assertTrue(q.chart is InsightChart.EffortGap)
    }

    @Test fun `numbers compare with the period before`() {
        val r = report(pullupHistory())
        val sessions = r.numbers.first { it.key == NumberKey.SESSIONS }
        assertEquals("3", sessions.value)
        assertEquals("▲ 1", sessions.delta)
        assertEquals(true, sessions.good)
    }

    @Test fun `the same input renders the same issue`() {
        val history = pullupHistory()
        assertEquals(report(history), report(history))
    }

    @Test fun `the cover has a headline, a summary and at most three moves`() {
        val r = report(pullupHistory())
        assertTrue(r.headline.isNotEmpty() && r.headline.all { it.isNotBlank() && !it.contains("{") })
        assertTrue(r.summary, r.summary.contains("3") && r.summary.contains("1 more") || r.summary.contains("up 1"))
        assertTrue(r.moves.size <= 3)
        assertEquals(r.phraseIds.size, r.phraseIds.toSet().size)
    }
}
