package com.calistapp.core.insights

import com.calistapp.core.insights.phrasing.Phrasebook
import com.calistapp.core.insights.phrasing.Phrases
import com.calistapp.core.insights.phrasing.Slot
import com.calistapp.core.insights.phrasing.plural
import com.calistapp.core.insights.phrasing.spelled
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutStyle
import com.calistapp.core.model.formatKg
import com.calistapp.core.model.repsIn
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.TrainingLoad
import com.calistapp.core.progress.effectiveAddedKg
import com.calistapp.core.progress.summarizeProgress
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Builds an Insights issue: five questions answered by fixed rules, the records and numbers of the
 * period, and the moves for the recap. Rules decide every verdict and number; the [Phrasebook] only
 * chooses the words.
 *
 * Pure and deterministic for a given [InsightsInput], so the same issue reads the same every time it
 * is opened, and a unit test can pin any verdict.
 */
object InsightsEngine {

    fun report(input: InsightsInput, phrasebook: Phrasebook = Phrasebook()): InsightsReport {
        val (window, previous) = windows(input.period, input.nowMs, input.zone)
        val upToNow = input.sessions.filter { it.startMs < window.endMs }.sortedBy { it.startMs }
        val ctx = Ctx(input, window, previous, upToNow, phrasebook)

        val stronger = ctx.stronger()
        val fitter = ctx.fitter()
        val balanced = ctx.balanced()
        val consistent = ctx.consistent()
        val recovering = ctx.recovering()
        val evals = listOf(stronger, fitter, balanced, consistent, recovering)
        val questions = evals.map { it.verdict }

        val records = ctx.records()
        val numbers = ctx.numbers()
        val headline = ctx.headline(evals)
        val summary = ctx.summary(questions)
        val moves = pickMoves(questions)

        return InsightsReport(
            period = input.period,
            issue = issueNumber(upToNow, window, input.period, input.zone),
            window = window,
            previous = previous,
            headline = headline,
            summary = summary,
            questions = questions,
            records = records,
            numbers = numbers,
            moves = moves,
            sessionsInWindow = ctx.inWindow.size,
            phraseIds = ctx.used.toList(),
        )
    }

    /** The current period ending today, and the equally long one before it. */
    fun windows(period: InsightsPeriod, nowMs: Long, zone: ZoneId): Pair<InsightsWindow, InsightsWindow> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = today.minusDays(period.days - 1L)
        val prevEnd = start.minusDays(1)
        val prevStart = prevEnd.minusDays(period.days - 1L)
        fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return InsightsWindow(start, today, ms(start), ms(today.plusDays(1))) to
            InsightsWindow(prevStart, prevEnd, ms(prevStart), ms(start))
    }

    private fun issueNumber(sessions: List<PerformedSession>, window: InsightsWindow, period: InsightsPeriod, zone: ZoneId): Int {
        val first = sessions.firstOrNull() ?: return 1
        val firstDay = Instant.ofEpochMilli(first.startMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(firstDay, window.start)
        return if (days <= 0) 1 else (days / period.days).toInt() + 1
    }

    private fun pickMoves(questions: List<QuestionVerdict>): List<Move> {
        val order = questions.sortedBy { q ->
            when (q.verdict) { Verdict.WATCH -> 0; Verdict.MIXED -> 1; Verdict.GOOD -> 2; Verdict.NOT_ENOUGH_DATA -> 3 }
        }
        val out = ArrayList<Move>()
        for (q in order) {
            val m = q.move ?: continue
            if (q.verdict == Verdict.GOOD && m.kind != MoveKind.ADD_LOAD) continue
            if (out.none { it.kind == m.kind }) out += m
            if (out.size == 3) break
        }
        return out
    }
}

/** What one evaluator found, plus the hints the headline needs (lead movement, weak group, …). */
internal class Eval(val verdict: QuestionVerdict, val hints: Map<String, String> = emptyMap())

private enum class Metric { REPS, HOLD, WEIGHT }

private class SessionBest(val sessionId: String, val atMs: Long, val value: Double, val reps: Int)

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Shared state for one report: the windows, the sessions in them, and the phrase picker. */
private class Ctx(
    val input: InsightsInput,
    val window: InsightsWindow,
    val prev: InsightsWindow,
    /** Every session up to the end of the window, oldest first. */
    val all: List<PerformedSession>,
    val pb: Phrasebook,
) {
    val zone: ZoneId = input.zone
    val inWindow = all.filter { window.contains(it.startMs) }
    val inPrev = all.filter { prev.contains(it.startMs) }
    val used = LinkedHashSet<String>()
    private val seedBase = window.start.toEpochDay() * 1_000_003L + input.period.ordinal * 7919L + inWindow.size

    fun say(slot: Slot, vars: Map<String, String>, salt: Int = 0): String? {
        val p = pb.pick(slot, vars, seedBase + salt * 104_729L, input.recentPhraseIds + used) ?: return null
        used += p.id
        return p.text
    }

    fun date(atMs: Long): LocalDate = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()

    /**
     * One point per week for trend lines longer than a week, so a chart shows the shape of the
     * period rather than session-to-session noise (a circuit day's higher recovery, a light day).
     */
    fun weekly(points: List<ChartPoint>, combine: (List<Double>) -> Double): List<ChartPoint> {
        if (input.period == InsightsPeriod.WEEK) return points
        return points.groupBy { Math.floorDiv(it.atMs - prev.startMs, 7 * DAY_MS) }.toSortedMap()
            .map { (_, ps) -> ChartPoint(ps.map { it.atMs }.average().toLong(), combine(ps.map { it.value })) }
    }
    val today: LocalDate get() = window.endInclusive

    private val prevMonth: String
        get() = prev.start.plusDays(input.period.days / 2L).month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /** "in September", "last week" … the comparison point in a sentence. */
    val whenText: String
        get() = when (input.period) {
            InsightsPeriod.WEEK -> "last week"
            InsightsPeriod.FOUR_WEEKS -> "in $prevMonth"
            InsightsPeriod.QUARTER -> "last quarter"
            InsightsPeriod.YEAR -> "a year ago"
        }
    val sinceText: String
        get() = when (input.period) {
            InsightsPeriod.WEEK -> "last week"
            InsightsPeriod.FOUR_WEEKS -> prevMonth
            InsightsPeriod.QUARTER -> "last quarter"
            InsightsPeriod.YEAR -> "last year"
        }

    // ------------------------------------------------------------ set helpers
    fun key(log: SetLog) = log.exerciseId.ifBlank { log.exerciseName }
    fun working(s: PerformedSession): List<SetLog> =
        s.setLogs.filter { log -> key(log).isNotBlank() && s.plan.slot(log.slotId)?.isWarmup(log.setIndex) != true }
    fun hold(s: PerformedSession, log: SetLog): Int? {
        val slot = s.plan.slot(log.slotId)
        return if (slot?.measure == ExerciseMeasure.SECONDS || (log.seconds > 0 && log.reps == 0)) maxOf(log.seconds, log.reps) else null
    }
    fun kg(s: PerformedSession, log: SetLog) = effectiveAddedKg(log, s.plan.slot(log.slotId))

    private fun metricFor(key: String, sessions: List<PerformedSession>): Metric {
        var held = false
        for (s in sessions) for (log in working(s)) {
            if (key(log) != key) continue
            if (kg(s, log) > 0.0) return Metric.WEIGHT
            if (hold(s, log) != null) held = true
        }
        return if (held) Metric.HOLD else Metric.REPS
    }

    private fun bests(key: String, metric: Metric, sessions: List<PerformedSession>): List<SessionBest> = sessions.mapNotNull { s ->
        var value = 0.0; var reps = 0
        for (log in working(s)) {
            if (key(log) != key) continue
            val v = when (metric) {
                Metric.REPS -> log.reps.toDouble()
                Metric.HOLD -> (hold(s, log) ?: 0).toDouble()
                Metric.WEIGHT -> kg(s, log)
            }
            if (v > value || (v == value && log.reps > reps)) { value = v; reps = log.reps }
        }
        if (value > 0.0) SessionBest(s.id, s.startMs, value, reps) else null
    }

    private fun fmt(metric: Metric, v: Double): String = when (metric) {
        Metric.REPS, Metric.HOLD -> v.roundToInt().toString()
        Metric.WEIGHT -> "+" + formatKg(v)
    }
    private fun unit(metric: Metric) = when (metric) { Metric.REPS -> "reps"; Metric.HOLD -> "s"; Metric.WEIGHT -> "kg" }
    private fun deltaWord(metric: Metric, d: Double) = when (metric) {
        Metric.REPS -> plural(d.roundToInt(), "rep")
        Metric.HOLD -> "${d.roundToInt()} s"
        Metric.WEIGHT -> "${formatKg(d)} kg"
    }
    private fun deltaMark(metric: Metric, d: Double) = when (metric) {
        Metric.REPS -> "▲ ${d.roundToInt()}"
        Metric.HOLD -> "▲ ${d.roundToInt()} s"
        Metric.WEIGHT -> "▲ ${formatKg(d)} kg"
    }

    private class Line(val key: String, val name: String, val metric: Metric, val bests: List<SessionBest>, val prior: Double?, val winBest: Double, val runs: Int, val status: MovementStatus) {
        val gain: Double get() = if (prior == null || prior <= 0.0) 0.0 else (winBest - prior) / prior
    }

    private val lines: List<Line> by lazy {
        val setCounts = LinkedHashMap<String, Int>()
        val names = HashMap<String, String>()
        for (s in inWindow) for (log in working(s)) {
            setCounts[key(log)] = (setCounts[key(log)] ?: 0) + 1
            names[key(log)] = log.exerciseName.ifBlank { key(log) }
        }
        setCounts.entries.sortedByDescending { it.value }.take(6).mapNotNull { (key, _) ->
            val metric = metricFor(key, inWindow + inPrev)
            val b = bests(key, metric, all)
            val prior = b.filter { it.atMs < window.startMs }.maxOfOrNull { it.value }
            val win = b.filter { window.contains(it.atMs) }
            val winBest = win.maxOfOrNull { it.value } ?: return@mapNotNull null // only zero-rep sets this period
            val status = when {
                prior == null -> MovementStatus.NEW
                winBest > prior -> MovementStatus.IMPROVED
                winBest < prior -> if (win.size >= 2) MovementStatus.SLIPPED else MovementStatus.STEADY
                win.size >= 3 -> MovementStatus.STUCK
                else -> MovementStatus.STEADY
            }
            Line(key, names[key] ?: key, metric, b, prior, winBest, win.size, status)
        }
    }

    // ------------------------------------------------------------ 01 stronger
    fun stronger(): Eval {
        val q = Question.STRONGER
        val comparable = lines.filter { it.status != MovementStatus.NEW }
        if (inWindow.size < 2 || comparable.isEmpty()) {
            val top = lines.firstOrNull()
            val pts = top?.let { l -> weekly(l.bests.filter { it.atMs >= prev.startMs }.map { ChartPoint(it.atMs, it.value) }) { it.max() } }.orEmpty()
            val noneChart = if (top != null && pts.size >= 2) InsightChart.Trend(
                pts, unit(top.metric), "${fmt(top.metric, pts.minOf { it.value })} ${unit(top.metric)}", "${fmt(top.metric, pts.maxOf { it.value })} ${unit(top.metric)}",
            ) else InsightChart.None
            return Eval(none(q, Phrases.STR_NONE, chart = noneChart, keyMoves = lines.map(::keyMovement)))
        }
        val improved = lines.filter { it.status == MovementStatus.IMPROVED }.sortedByDescending { it.gain }
        val stuck = lines.firstOrNull { it.status == MovementStatus.STUCK }
        val slipped = lines.firstOrNull { it.status == MovementStatus.SLIPPED }
        val badCount = lines.count { it.status == MovementStatus.STUCK || it.status == MovementStatus.SLIPPED }
        val lead = improved.firstOrNull()
        val verdict = when {
            lead != null && improved.size >= badCount -> Verdict.GOOD
            lead != null -> Verdict.MIXED
            badCount > 0 -> Verdict.WATCH
            else -> Verdict.MIXED
        }
        val vars = HashMap<String, String>()
        vars["period"] = input.period.within
        lead?.let {
            vars["ex"] = it.name; vars["from"] = fmt(it.metric, it.prior!!); vars["to"] = fmt(it.metric, it.winBest)
            vars["unit"] = unit(it.metric); vars["delta"] = deltaWord(it.metric, it.winBest - it.prior)
        }
        stuck?.let {
            vars["stuck"] = it.name; vars["stuckAt"] = "${fmt(it.metric, it.winBest)} ${unit(it.metric)}"; vars["stuckRuns"] = spelled(it.runs)
        }
        slipped?.let {
            vars["slip"] = it.name; vars["slipFrom"] = fmt(it.metric, it.prior!!); vars["slipTo"] = "${fmt(it.metric, it.winBest)} ${unit(it.metric)}"
        }
        val slot = when {
            verdict == Verdict.GOOD -> Phrases.STR_GOOD
            verdict == Verdict.MIXED && lead != null -> Phrases.STR_MIXED
            else -> Phrases.STR_WATCH
        }
        val fact = say(slot, vars, 1) ?: say(Phrases.STR_WATCH, vars, 1).orEmpty()
        val focus = lead ?: stuck ?: slipped ?: comparable.first()
        val chartPoints = weekly(focus.bests.filter { it.atMs >= prev.startMs }.map { ChartPoint(it.atMs, it.value) }) { it.max() }
        val lo = chartPoints.minOfOrNull { it.value } ?: 0.0
        val hi = chartPoints.maxOfOrNull { it.value } ?: 0.0
        val chart = InsightChart.Trend(chartPoints, unit(focus.metric), "${fmt(focus.metric, lo)} ${unit(focus.metric)}", "${fmt(focus.metric, hi)} ${unit(focus.metric)}")
        val move = when {
            lead != null && lead.metric == Metric.REPS && lead.winBest >= 12 -> Move(
                MoveKind.ADD_LOAD,
                say(Phrases.MOVE_ADD_LOAD, vars, 11).orEmpty(),
                say(Phrases.MOVE_ADD_LOAD_D, vars, 12).orEmpty(),
                action = "Add", exerciseIds = listOf(lead.key),
            )
            stuck != null -> Move(
                MoveKind.UNSTICK,
                say(Phrases.MOVE_UNSTICK, vars, 13).orEmpty(),
                say(Phrases.MOVE_UNSTICK_D, vars, 14).orEmpty(),
                exerciseIds = listOf(stuck.key),
            )
            else -> null
        }
        val ids = inWindow.filter { s -> working(s).any { log -> lines.any { it.key == key(log) } } }.sortedByDescending { it.startMs }.map { it.id }
        val hints = buildMap {
            lead?.let { put("ex", it.name); put("group", groupNoun(groupOfKey(it.key, it.name))) }
            (stuck ?: slipped)?.let { put("stuck", it.name) }
        }
        return Eval(
            QuestionVerdict(q, verdict, pill(q, verdict), fact, chart, move, lines.map(::keyMovement), sessionIds = ids),
            hints,
        )
    }

    private fun keyMovement(l: Line): KeyMovement {
        val from = l.prior?.let { fmt(l.metric, it) } ?: fmt(l.metric, l.bests.firstOrNull { window.contains(it.atMs) }?.value ?: l.winBest)
        val repsAtBest = l.bests.filter { window.contains(it.atMs) }.maxByOrNull { it.value }?.reps ?: 0
        val to = when (l.metric) {
            Metric.WEIGHT -> "${fmt(l.metric, l.winBest)} kg × $repsAtBest"
            Metric.HOLD -> "${fmt(l.metric, l.winBest)} s"
            Metric.REPS -> "${fmt(l.metric, l.winBest)} reps"
        }
        val fromLabel = if (l.metric == Metric.WEIGHT) "$from kg" else from
        return KeyMovement(
            exerciseKey = l.key,
            name = l.name,
            from = fromLabel,
            to = to,
            delta = if (l.status == MovementStatus.IMPROVED) deltaMark(l.metric, l.winBest - (l.prior ?: l.winBest)) else "",
            status = l.status,
            note = if (l.status == MovementStatus.STUCK) "${fmt(l.metric, l.winBest)} ${unit(l.metric)}, ${spelled(l.runs)} sessions running" else "",
        )
    }

    // ------------------------------------------------------------ 02 fitter
    fun fitter(): Eval {
        val q = Question.FITTER
        val recPoints = weekly((inPrev + inWindow).mapNotNull { s -> s.hrRecoveryMeanDrop?.let { ChartPoint(s.startMs, it.toDouble()) } }) { it.average() }
        val cur = inWindow.mapNotNull { it.hrRecoveryMeanDrop }
        val pre = inPrev.mapNotNull { it.hrRecoveryMeanDrop }
        val series = sameWorkoutSeries()
        val circuitsNow = inWindow.count { it.plan.style == WorkoutStyle.CIRCUIT }
        val circuitsBefore = inPrev.count { it.plan.style == WorkoutStyle.CIRCUIT }
        val circuitsAdded = circuitsNow >= 2 && circuitsNow > circuitsBefore
        val markers = if (circuitsAdded) {
            inWindow.firstOrNull { it.plan.style == WorkoutStyle.CIRCUIT }?.let { listOf(ChartMarker(it.startMs, "Circuit days added")) }.orEmpty()
        } else emptyList()

        val vars = HashMap<String, String>()
        vars["when"] = whenText; vars["since"] = sinceText
        val verdict: Verdict
        val slot: Slot
        if (cur.size >= 2 && pre.size >= 2) {
            val now = cur.average(); val before = pre.average()
            val delta = now - before
            vars["now"] = now.roundToInt().toString(); vars["prev"] = before.roundToInt().toString()
            vars["delta"] = abs(delta).roundToInt().toString()
            verdict = when { delta >= 2 -> Verdict.GOOD; delta <= -2 -> Verdict.WATCH; else -> Verdict.MIXED }
            slot = when (verdict) { Verdict.GOOD -> Phrases.FIT_GOOD; Verdict.WATCH -> Phrases.FIT_WATCH; else -> Phrases.FIT_MIXED }
        } else if (series != null && series.first - series.last >= 5) {
            verdict = Verdict.GOOD
            slot = Phrases.FIT_GOOD_HR
        } else {
            // No comparison to judge, but the line itself is still worth seeing on the question's page.
            val noneChart = if (recPoints.size >= 2) InsightChart.Trend(
                recPoints, "bpm", "${recPoints.minOf { it.value }.roundToInt()} bpm", "${recPoints.maxOf { it.value }.roundToInt()} bpm", markers,
            ) else InsightChart.None
            return Eval(none(q, Phrases.FIT_NONE, chart = noneChart))
        }
        series?.let {
            vars["name"] = it.name; vars["first"] = it.first.toString(); vars["last"] = it.last.toString()
            vars["runs"] = it.runs.toString(); vars["diff"] = (it.first - it.last).toString()
        }
        val fact = say(slot, vars, 2).orEmpty()
        val perWeek = perWeek(inWindow.size)
        vars["perWeek"] = perWeek; vars["periodNoun"] = input.period.noun
        val move = when (verdict) {
            Verdict.GOOD -> if (circuitsAdded) Move(MoveKind.KEEP_DOING, say(Phrases.MOVE_KEEP_CIRCUITS, vars, 21).orEmpty(), say(Phrases.MOVE_KEEP_CIRCUITS_D, vars, 22).orEmpty())
            else Move(MoveKind.KEEP_DOING, say(Phrases.MOVE_KEEP_RHYTHM, vars, 21).orEmpty(), say(Phrases.MOVE_KEEP_RHYTHM_D, vars, 22).orEmpty())
            Verdict.WATCH -> Move(MoveKind.EASY_CIRCUIT, say(Phrases.MOVE_EASY_CARDIO, vars, 23).orEmpty(), say(Phrases.MOVE_EASY_CARDIO_D, vars, 24).orEmpty(), action = "Plan it")
            else -> Move(MoveKind.KEEP_DOING, say(Phrases.MOVE_KEEP_RHYTHM, vars, 21).orEmpty(), say(Phrases.MOVE_KEEP_RHYTHM_D, vars, 22).orEmpty())
        }
        val evidence = buildList {
            series?.takeIf { it.first > it.last }?.let { add(Evidence("Same ${it.name} workout, lower heart rate", "${it.first} → ${it.last} bpm over ${it.runs} runs")) }
            if (circuitsAdded) add(Evidence("$circuitsNow circuit days ${input.period.within}", "Recovery climbed once you added them"))
        }
        val lo = recPoints.minOfOrNull { it.value } ?: 0.0
        val hi = recPoints.maxOfOrNull { it.value } ?: 0.0
        val chart = if (recPoints.size >= 2) InsightChart.Trend(recPoints, "bpm", "${lo.roundToInt()} bpm", "${hi.roundToInt()} bpm", markers) else InsightChart.None
        val ids = (inWindow + inPrev).filter { it.hrRecoveryMeanDrop != null || it.avgHr > 0 }.sortedByDescending { it.startMs }.map { it.id }
        return Eval(QuestionVerdict(q, verdict, pill(q, verdict), fact, chart, move, evidence = evidence, sessionIds = ids))
    }

    private class Series(val name: String, val first: Int, val last: Int, val runs: Int)

    /** The saved workout whose average heart rate fell the most from its first runs to its latest. */
    private fun sameWorkoutSeries(): Series? = all
        .filter { it.avgHr > 0 }
        .groupBy { it.plan.sourceWorkoutId ?: it.plan.name.ifBlank { null } }
        .filterKeys { it != null }
        .mapNotNull { (_, runs) ->
            if (runs.size < 4) return@mapNotNull null
            val sorted = runs.sortedBy { it.startMs }
            val first = sorted.take(2).map { it.avgHr }.average().roundToInt()
            val last = sorted.takeLast(2).map { it.avgHr }.average().roundToInt()
            Series(sorted.last().plan.name.ifBlank { "this" }, first, last, sorted.size)
        }
        .maxByOrNull { it.first - it.last }

    // ------------------------------------------------------------ 03 balanced
    fun balanced(): Eval {
        val q = Question.BALANCED
        val sets = HashMap<MovementGroup, Int>()
        for (s in inWindow) for (log in working(s)) {
            val g = groupOfKey(key(log), log.exerciseName) ?: continue
            sets[g] = (sets[g] ?: 0) + 1
        }
        val total = sets.values.sum()
        var neglected = neglected()
        if (inWindow.size < 3 || total < 20) {
            return Eval(none(q, Phrases.BAL_NONE, chart = if (total > 0) split(sets, total, neglected) else InsightChart.None))
        }
        val expected = mapOf(MovementGroup.PUSH to 0.2, MovementGroup.PULL to 0.2, MovementGroup.LEGS to 0.2, MovementGroup.CORE to 0.08)
        val share = MovementGroup.entries.associateWith { (sets[it] ?: 0).toDouble() / total }
        val ratioOf = { g: MovementGroup -> share.getValue(g) / expected.getValue(g) }
        // A real push/pull/legs gap outranks thin core work, which compound moves partly cover anyway.
        val bigGaps = MovementGroup.entries.filter { it != MovementGroup.CORE && ratioOf(it) < 0.5 }
        val worst = (bigGaps.ifEmpty { MovementGroup.entries }).minBy(ratioOf)
        val ratio = share.getValue(worst) / expected.getValue(worst)
        val verdict = when { ratio >= 1.0 -> Verdict.GOOD; ratio >= 0.5 -> Verdict.MIXED; else -> Verdict.WATCH }
        if (verdict != Verdict.GOOD) neglected = neglected(worst)
        val top = MovementGroup.entries.maxBy { share.getValue(it) }
        val muscle = neglected.firstOrNull { muscleGroup(it.muscle) == worst } ?: neglected.firstOrNull()
        val vars = HashMap<String, String>()
        vars["period"] = input.period.within
        vars["minPct"] = (MovementGroup.entries.minOf { share.getValue(it) } * 100).roundToInt().toString()
        vars["legsPct"] = (share.getValue(MovementGroup.LEGS) * 100).roundToInt().toString()
        vars["low"] = worst.label; vars["lowNoun"] = worst.noun; vars["topNoun"] = top.noun
        vars["lowPct"] = (share.getValue(worst) * 100).roundToInt().toString()
        muscle?.let { vars["muscle"] = it.muscle; vars["muscleLower"] = it.muscle.lowercase(); vars["days"] = it.days.toString() }
        val slot = when (verdict) { Verdict.GOOD -> Phrases.BAL_GOOD; Verdict.MIXED -> Phrases.BAL_MIXED; else -> Phrases.BAL_WATCH }
        val fact = say(slot, vars, 3).orEmpty()
        val move = if (verdict == Verdict.GOOD) null else groupDayMove(worst, neglected)
        val ids = inWindow.sortedByDescending { it.startMs }.map { it.id }
        val hints = if (verdict != Verdict.GOOD) mapOf("group" to badGroupName(worst)) else emptyMap()
        return Eval(QuestionVerdict(q, verdict, pill(q, verdict), fact, split(sets, total, neglected), move, sessionIds = ids), hints)
    }

    private fun split(sets: Map<MovementGroup, Int>, total: Int, neglected: List<Neglected>) = InsightChart.Split(
        MovementGroup.entries.map { SplitPart(it, sets[it] ?: 0, if (total == 0) 0.0 else (sets[it] ?: 0).toDouble() / total) },
        neglected,
    )

    /**
     * Tracked muscles not trained at all (not even as a secondary muscle) for a week or more. Muscles
     * of [focus] (the weak group) come first, then the rest, each longest-quiet first.
     */
    private fun neglected(focus: MovementGroup? = null): List<Neglected> {
        val first = all.firstOrNull()?.startMs ?: return emptyList()
        val last = HashMap<String, Long>()
        for (s in all) for (log in working(s)) {
            for (m in MovementGroups.trainedMusclesOf(input.exercises[log.exerciseId], log.exerciseName)) {
                if (m in MovementGroups.TRACKED) last[m] = maxOf(last[m] ?: 0L, s.startMs)
            }
        }
        return MovementGroups.TRACKED.mapNotNull { (m, display) ->
            val days = ((input.nowMs - (last[m] ?: first)) / DAY_MS).toInt()
            if (days >= 7) Neglected(display, days) else null
        }.sortedWith(compareByDescending<Neglected> { focus != null && muscleGroup(it.muscle) == focus }.thenByDescending { it.days }).take(3)
    }

    private fun muscleGroup(display: String): MovementGroup? = when (display) {
        "Hamstrings", "Calves", "Glutes", "Quads" -> MovementGroup.LEGS
        "Chest", "Shoulders", "Triceps" -> MovementGroup.PUSH
        "Lats", "Biceps" -> MovementGroup.PULL
        "Abs" -> MovementGroup.CORE
        else -> null
    }

    private fun groupDayMove(group: MovementGroup, neglected: List<Neglected>): Move {
        val done = all.flatMapTo(HashSet()) { s -> s.setLogs.map { it.exerciseId } }
        val wantMuscles = neglected.filter { muscleGroup(it.muscle) == group }.map { n -> MovementGroups.TRACKED.entries.first { it.value == n.muscle }.key }
        val picks = ArrayList<Pair<String, String>>() // id to name
        // Bodyweight strength moves only: a stretch or a cardio drill isn't a leg day.
        val catalog = input.exercises.values.filter { e ->
            MovementGroups.groupOf(e, e.name) == group && e.category.equals("strength", true) &&
                (e.equipment.isEmpty() || e.equipment.any { it.equals("body only", true) })
        }
        for (m in wantMuscles.ifEmpty { listOf("") }) {
            val match = catalog
                .filter { m.isEmpty() || it.primaryMuscles.any { pm -> pm.equals(m, true) } }
                .filter { c -> picks.none { it.first == c.id } }
                .sortedWith(PICK_ORDER(done))
                .firstOrNull()
            match?.let { picks += it.id to it.name }
        }
        for (c in catalog.sortedWith(PICK_ORDER(done))) {
            if (picks.size >= 3) break
            if (picks.none { it.first == c.id }) picks += c.id to c.name
        }
        val names = picks.map { it.second }.ifEmpty { FALLBACK.getValue(group) }.take(3)
        val vars = mapOf("dayNoun" to group.dayNoun)
        return Move(
            MoveKind.PLAN_GROUP_DAY,
            say(Phrases.MOVE_GROUP_DAY, vars, 31).orEmpty(),
            names.joinToString(", ") + " · 25 min",
            action = "Build it", exerciseIds = picks.map { it.first }, group = group,
        )
    }

    // ------------------------------------------------------------ 04 consistent
    fun consistent(): Eval {
        val q = Question.CONSISTENT
        val first = all.firstOrNull() ?: return Eval(none(q, Phrases.CON_NONE))
        val firstDay = date(first.startMs)
        if (ChronoUnit.DAYS.between(firstDay, today) < 6) return Eval(none(q, Phrases.CON_NONE))
        val effStart = maxOf(window.start, firstDay)
        val trained = inWindow.mapTo(HashSet()) { date(it.startMs) }
        val days = generateSequence(window.start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val chart = InsightChart.Calendar(days.map { DayMark(it, it in input.plannedDays, it in trained) })
        val planned = input.plannedDays.filter { !it.isBefore(effStart) && !it.isAfter(today) && (it.isBefore(today) || it in trained) }
        val vars = HashMap<String, String>()
        vars["period"] = input.period.within; vars["periodNoun"] = input.period.noun
        val ids = inWindow.sortedByDescending { it.startMs }.map { it.id }
        if (planned.size >= 4) {
            val done = planned.count { it in trained }
            val misses = planned.filter { it !in trained }
            val pct = done.toDouble() / planned.size
            val verdict = when { pct >= 0.9 -> Verdict.GOOD; pct >= 0.7 -> Verdict.MIXED; else -> Verdict.WATCH }
            val top = misses.groupingBy { it.dayOfWeek }.eachCount().maxByOrNull { it.value }
            val missDay = top?.takeIf { it.value >= 2 && it.value * 2 >= misses.size }?.key
            vars["done"] = done.toString(); vars["planned"] = planned.size.toString()
            vars["pct"] = (pct * 100).roundToInt().toString()
            if (misses.size in 1..2) vars["missedWord"] = spelled(misses.size)
            if (misses.isNotEmpty() && verdict != Verdict.GOOD) vars["missedWord"] = spelled(misses.size)
            if (missDay != null && top != null) {
                val dayName = missDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                vars["day"] = dayName
                val line = if (misses.size == 2 && top.value == 2) say(Phrases.MISS_BOTH, mapOf("day" to dayName), 41)
                else say(Phrases.MISS_SOME, mapOf("day" to dayName, "n" to spelled(top.value), "m" to spelled(misses.size)), 41)
                line?.let { vars["missLine"] = it }
            }
            val slot = when (verdict) { Verdict.GOOD -> Phrases.CON_GOOD; Verdict.MIXED -> Phrases.CON_MIXED; else -> Phrases.CON_WATCH }
            val fact = say(slot, vars, 4).orEmpty()
            val move = when {
                missDay != null -> Move(MoveKind.SHORTEN_DAY, say(Phrases.MOVE_SHORT_DAY, vars, 42).orEmpty(), say(Phrases.MOVE_SHORT_DAY_D, vars, 43).orEmpty(), action = "Plan it")
                verdict == Verdict.WATCH -> Move(MoveKind.PLAN_WEEK, say(Phrases.MOVE_PLAN_WEEK, vars, 44).orEmpty(), say(Phrases.MOVE_PLAN_WEEK_D, vars, 45).orEmpty(), action = "Plan")
                else -> null
            }
            val hints = buildMap { missDay?.let { put("day", it.getDisplayName(TextStyle.FULL, Locale.ENGLISH)) } }
            return Eval(QuestionVerdict(q, verdict, pill(q, verdict), fact, chart, move, sessionIds = ids), hints)
        }
        // No schedule to judge against: judge by how many weeks had a session.
        val spanDays = ChronoUnit.DAYS.between(effStart, today) + 1
        val weeks = ceil(spanDays / 7.0).toInt().coerceAtLeast(1)
        val trainedWeeks = inWindow.mapTo(HashSet()) { ChronoUnit.DAYS.between(effStart, date(it.startMs)) / 7 }.size
        val ratio = trainedWeeks.toDouble() / weeks
        val verdict = when {
            input.period == InsightsPeriod.WEEK -> when { inWindow.size >= 2 -> Verdict.GOOD; inWindow.size == 1 -> Verdict.MIXED; else -> Verdict.WATCH }
            ratio >= 0.9 -> Verdict.GOOD
            ratio >= 0.6 -> Verdict.MIXED
            else -> Verdict.WATCH
        }
        vars["perWeek"] = perWeek(inWindow.size, weeks)
        vars["weeks"] = weeks.toString(); vars["trainedWeeks"] = trainedWeeks.toString(); vars["weeksWord"] = spelled(weeks)
        val slot = when (verdict) { Verdict.GOOD -> Phrases.CON_GOOD_NOPLAN; Verdict.MIXED -> Phrases.CON_MIXED_NOPLAN; else -> Phrases.CON_WATCH_NOPLAN }
        val fact = say(slot, vars, 4).orEmpty()
        val move = Move(MoveKind.PLAN_WEEK, say(Phrases.MOVE_PLAN_WEEK, vars, 44).orEmpty(), say(Phrases.MOVE_PLAN_WEEK_D, vars, 45).orEmpty(), action = "Plan")
        return Eval(QuestionVerdict(q, verdict, pill(q, verdict), fact, chart, move, sessionIds = ids))
    }

    private fun perWeek(sessions: Int, weeks: Int = (input.period.days / 7).coerceAtLeast(1)): String =
        (sessions.toDouble() / weeks).roundToInt().coerceAtLeast(1).toString()

    // ------------------------------------------------------------ 05 recovering
    fun recovering(): Eval {
        val q = Question.RECOVERING
        val profile = input.profile
        val reserve = profile?.let { it.effectiveMaxHr - it.restingHr } ?: 0
        val rated = all.filter { it.rpe != null && it.avgHr > 0 && it.startMs >= input.nowMs - 90 * DAY_MS }
        if (profile == null || reserve <= 0 || rated.size < 5) return Eval(none(q, Phrases.REC_NONE))
        fun expected(s: PerformedSession) = 1 + 9 * ((s.avgHr - profile.restingHr).toDouble() / reserve).coerceIn(0.0, 1.0)
        val gaps = rated.map { it.rpe!! - expected(it) }
        val older = gaps.dropLast(3)
        val baseline = median(if (older.size >= 2) older else gaps)
        val recent = rated.takeLast(3)
        val dev = gaps.takeLast(3).map { it - baseline }
        val avgDev = dev.average()
        val ramp = summarizeProgress(all, input.nowMs, zone = zone, profile = profile).ramp
        var verdict = when {
            avgDev >= 1.5 -> Verdict.WATCH
            avgDev >= 0.8 || dev.any { it >= 2.5 } -> Verdict.MIXED
            else -> Verdict.GOOD
        }
        val sharp = ramp?.band == TrainingLoad.Band.SHARP_JUMP
        if (sharp && verdict == Verdict.GOOD) verdict = Verdict.MIXED
        val streak = dev.reversed().takeWhile { it >= 1.0 }.size.coerceAtLeast(2)
        val sleeps = recent.mapNotNull { input.sleepBefore[date(it.startMs)] }
        val sleepLine = if (sleeps.size >= 2 && sleeps.average() < 390) duration(sleeps.average().roundToInt() * 60_000L) else null
        val vars = HashMap<String, String>()
        vars["count"] = spelled(streak)
        ramp?.let { vars["ratio"] = String.format(Locale.US, "%.2f", it.ratio) }
        sleepLine?.let { vars["sleep"] = it }
        val fact = when {
            verdict == Verdict.WATCH -> say(Phrases.REC_WATCH, vars, 5)
            verdict == Verdict.MIXED && sharp && avgDev < 0.8 -> say(Phrases.REC_LOAD, vars, 5)
            verdict == Verdict.MIXED -> say(Phrases.REC_MIXED, vars, 5)
            else -> say(Phrases.REC_GOOD, vars, 5)
        }.orEmpty()
        val move = if (verdict == Verdict.GOOD) {
            Move(MoveKind.KEEP_DOING, say(Phrases.MOVE_AS_PLANNED, vars, 51).orEmpty(), say(Phrases.MOVE_AS_PLANNED_D, vars, 52).orEmpty())
        } else {
            Move(MoveKind.EASY_DAY, say(Phrases.MOVE_EASY_DAY, vars, 51).orEmpty(), say(Phrases.MOVE_EASY_DAY_D, vars, 52).orEmpty(), action = "Plan it")
        }
        val chart = InsightChart.EffortGap(rated.takeLast(8).map { EffortPoint(it.startMs, it.rpe!!.toDouble(), (expected(it) + baseline).coerceIn(1.0, 10.0)) })
        val evidence = buildList {
            ramp?.takeIf { it.band != TrainingLoad.Band.STEADY }?.let { add(Evidence("Training load", "${String.format(Locale.US, "%.2f", it.ratio)}× · ${it.band.label.lowercase()}")) }
            sleepLine?.let { add(Evidence("Sleep before your last sessions", "$it on average")) }
        }
        return Eval(QuestionVerdict(q, verdict, pill(q, verdict), fact, chart, move, evidence = evidence, sessionIds = recent.reversed().map { it.id }))
    }

    private fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // ------------------------------------------------------------ records, numbers
    fun records(): RecordBoard? {
        val entries = lines.filter { it.status == MovementStatus.IMPROVED }.sortedByDescending { it.gain }.mapIndexed { i, l ->
            val prior = l.prior!!
            val priorAt = l.bests.filter { it.atMs < window.startMs && it.value == prior }.maxOfOrNull { it.atMs }
            val bestAt = l.bests.first { window.contains(it.atMs) && it.value == l.winBest }.atMs
            val (value, unit) = when (l.metric) {
                Metric.REPS -> fmt(l.metric, l.winBest) to "reps"
                Metric.HOLD -> "${fmt(l.metric, l.winBest)} s" to ""
                Metric.WEIGHT -> "${fmt(l.metric, l.winBest)} kg" to ""
            }
            val delta = deltaMark(l.metric, l.winBest - prior).uppercase()
            val note = if (i == 0) {
                val vars = buildMap {
                    put("prev", if (l.metric == Metric.REPS) fmt(l.metric, prior) else "${fmt(l.metric, prior)} ${unit(l.metric)}")
                    priorAt?.let { put("date", DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH).format(date(it))) }
                }
                say(Phrases.RECORD_NOTE, vars, 61).orEmpty()
            } else ""
            val since = window.endMs - 84 * DAY_MS
            val spark = l.bests.filter { it.atMs >= since }.map { ChartPoint(it.atMs, it.value) }
                .groupBy { Math.floorDiv(it.atMs - since, 7 * DAY_MS) }.toSortedMap().map { (_, ps) -> ps.maxBy { it.value } }
            RecordEntry(l.key, l.name, value, unit, delta, bestAt, note, spark)
        }
        if (entries.isEmpty()) return null
        return RecordBoard(entries.first(), entries.drop(1).take(4))
    }

    fun numbers(): List<NumberStat> {
        val out = ArrayList<NumberStat>()
        val hasPrev = inPrev.isNotEmpty()
        fun pct(cur: Double, before: Double): String? {
            if (!hasPrev || before <= 0.0) return null
            val p = ((cur - before) / before * 100).roundToInt()
            return if (p == 0) "= same" else (if (p > 0) "▲ $p%" else "▼ ${-p}%")
        }
        val sCur = inWindow.size; val sPrev = inPrev.size
        out += NumberStat(NumberKey.SESSIONS, "Sessions", sCur.toString(),
            if (!hasPrev) null else when { sCur > sPrev -> "▲ ${sCur - sPrev}"; sCur < sPrev -> "▼ ${sPrev - sCur}"; else -> "= same" },
            if (!hasPrev) null else sCur >= sPrev)
        val aCur = inWindow.sumOf { it.activeDurationMs }; val aPrev = inPrev.sumOf { it.activeDurationMs }
        out += NumberStat(NumberKey.ACTIVE_TIME, "Active time", duration(aCur), pct(aCur.toDouble(), aPrev.toDouble()), if (!hasPrev) null else aCur >= aPrev)
        val kCur = inWindow.sumOf { it.kcal }; val kPrev = inPrev.sumOf { it.kcal }
        out += NumberStat(NumberKey.KCAL, "kcal, from HR", thousands(kCur), pct(kCur.toDouble(), kPrev.toDouble()), if (!hasPrev) null else kCur >= kPrev)
        val rCur = inWindow.sumOf { it.plan.repsIn(it.setLogs) }; val rPrev = inPrev.sumOf { it.plan.repsIn(it.setLogs) }
        out += NumberStat(NumberKey.REPS, "Reps", thousands(rCur), pct(rCur.toDouble(), rPrev.toDouble()), if (!hasPrev) null else rCur >= rPrev)
        input.profile?.let { p ->
            summarizeProgress(all, input.nowMs, zone = zone, profile = p).ramp?.let { r ->
                out += NumberStat(NumberKey.TRAINING_LOAD, "Training load", String.format(Locale.US, "%.2f×", r.ratio), r.band.label.lowercase(),
                    when (r.band) { TrainingLoad.Band.STEADY, TrainingLoad.Band.RAMPING -> true; TrainingLoad.Band.SHARP_JUMP -> false; TrainingLoad.Band.EASING_OFF -> null })
            }
        }
        val weights = input.weights.filter { it.atMs < window.endMs }.sortedBy { it.atMs }
        weights.lastOrNull()?.let { latest ->
            val before = weights.lastOrNull { it.atMs < window.startMs }
            val d = before?.let { latest.weightKg - it.weightKg }
            val delta = d?.takeIf { abs(it) >= 0.05 }?.let { (if (it > 0) "▲ " else "▼ ") + String.format(Locale.US, "%.1f", abs(it)) }
            out += NumberStat(NumberKey.BODYWEIGHT, "Bodyweight", String.format(Locale.US, "%.1f kg", latest.weightKg), delta, null)
        }
        return out
    }

    // ------------------------------------------------------------ cover
    fun headline(evals: List<Eval>): List<String> {
        if (all.isEmpty()) return listOfNotNull(say(Phrases.HL_EMPTY, emptyMap(), 71))
        if (evals.all { it.verdict.verdict == Verdict.NOT_ENOUGH_DATA }) return listOfNotNull(say(Phrases.HL_EARLY, emptyMap(), 71))
        val byQ = evals.associateBy { it.verdict.question }
        val goodOrder = listOf(Question.STRONGER, Question.FITTER, Question.CONSISTENT, Question.BALANCED, Question.RECOVERING)
        val badOrder = listOf(Question.BALANCED, Question.RECOVERING, Question.CONSISTENT, Question.STRONGER, Question.FITTER)
        val good = goodOrder.firstOrNull { byQ.getValue(it).verdict.verdict == Verdict.GOOD }
        val bad = badOrder.firstOrNull { byQ.getValue(it).verdict.verdict == Verdict.WATCH }
            ?: badOrder.firstOrNull { byQ.getValue(it).verdict.verdict == Verdict.MIXED }
        val vars = HashMap<String, String>()
        vars["periodNoun"] = input.period.noun
        evals.forEach { vars.putAll(it.hints) }
        val goodLine = good?.let { q ->
            val slot = when (q) {
                Question.STRONGER -> Phrases.HL_GOOD_STRONGER
                Question.FITTER -> Phrases.HL_GOOD_FITTER
                Question.BALANCED -> Phrases.HL_GOOD_BALANCED
                Question.CONSISTENT -> Phrases.HL_GOOD_CONSISTENT
                Question.RECOVERING -> Phrases.HL_GOOD_RECOVERING
            }
            say(slot, byQ.getValue(q).hints + vars.filterKeys { it == "periodNoun" }, 72)
        }
        val badLine = bad?.let { q ->
            val slot = when (q) {
                Question.STRONGER -> Phrases.HL_BAD_STRONGER
                Question.FITTER -> Phrases.HL_BAD_FITTER
                Question.BALANCED -> Phrases.HL_BAD_BALANCED
                Question.CONSISTENT -> Phrases.HL_BAD_CONSISTENT
                Question.RECOVERING -> Phrases.HL_BAD_RECOVERING
            }
            say(slot, byQ.getValue(q).hints + vars.filterKeys { it == "periodNoun" }, 73)
        }
        return when {
            goodLine != null && badLine != null -> listOf(goodLine, badLine)
            goodLine != null -> listOfNotNull(goodLine, if (evals.count { it.verdict.verdict == Verdict.GOOD } >= 4) say(Phrases.HL_ALL_GOOD, vars, 74) else null)
            badLine != null -> listOfNotNull(badLine, say(Phrases.HL_ALL_BAD, vars, 74))
            else -> listOfNotNull(say(Phrases.HL_EARLY, emptyMap(), 71))
        }
    }

    fun summary(questions: List<QuestionVerdict>): String {
        val parts = ArrayList<String>()
        val good = questions.count { it.verdict == Verdict.GOOD }
        val work = questions.count { it.verdict == Verdict.MIXED || it.verdict == Verdict.WATCH }
        val counts = mapOf("good" to good.toString(), "work" to work.toString())
        when {
            good > 0 && work > 0 -> say(Phrases.SUM_VERDICTS, counts, 81)
            good > 0 -> say(Phrases.SUM_ALL_GOOD, counts, 81)
            work > 0 -> say(Phrases.SUM_ALL_WORK, counts, 81)
            else -> null
        }?.let(parts::add)
        val n = inWindow.size
        val vars = mapOf(
            "sessions" to plural(n, "session"), "sessionsBare" to n.toString(), "prev" to input.period.previous,
            "diff" to abs(n - inPrev.size).toString(), "periodNoun" to input.period.noun,
        )
        val beforeWindow = all.any { it.startMs < window.startMs }
        val slot = when {
            !beforeWindow -> Phrases.SUM_FIRST
            n > inPrev.size -> Phrases.SUM_UP
            n < inPrev.size -> Phrases.SUM_DOWN
            else -> Phrases.SUM_SAME
        }
        say(slot, vars, 82)?.let(parts::add)
        return parts.joinToString(" ")
    }

    // ------------------------------------------------------------ shared bits
    fun groupOfKey(key: String, name: String): MovementGroup? = MovementGroups.groupOf(input.exercises[key], name)

    private fun groupNoun(g: MovementGroup?): String = when (g) {
        MovementGroup.PULL -> "Pulling"; MovementGroup.PUSH -> "Pushing"; MovementGroup.LEGS -> "Legs"
        MovementGroup.CORE -> "Your core"; null -> "Your training"
    }
    private fun badGroupName(g: MovementGroup): String = when (g) {
        MovementGroup.LEGS -> "Legs"; MovementGroup.PULL -> "Pulling"; MovementGroup.PUSH -> "Pushing"; MovementGroup.CORE -> "Core work"
    }

    private fun none(q: Question, slot: Slot, chart: InsightChart = InsightChart.None, keyMoves: List<KeyMovement> = emptyList()): QuestionVerdict =
        QuestionVerdict(q, Verdict.NOT_ENOUGH_DATA, pill(q, Verdict.NOT_ENOUGH_DATA), say(slot, emptyMap(), q.ordinal + 90).orEmpty(), chart, null, keyMoves)

    fun pill(q: Question, v: Verdict): String = when (v) {
        Verdict.GOOD -> "YES"
        Verdict.MIXED -> if (q == Question.BALANCED) "NOT QUITE" else "MOSTLY"
        Verdict.WATCH -> "WATCH IT"
        Verdict.NOT_ENOUGH_DATA -> "NOT YET"
    }

    private companion object {
        /**
         * Which catalog move to suggest: one you've done, then the strongest builders by the app's own
         * efficiency rating (authored for the curated set), then calisthenics, then the plainer name.
         * Without the rating, a 900-entry catalog surfaces drills like "Clock Taps" for a leg day.
         */
        fun PICK_ORDER(done: Set<String>) = compareByDescending<com.calistapp.core.model.Exercise> { it.id in done }
            .thenByDescending { it.efficiency }
            .thenByDescending { it.isCalisthenics }
            .thenBy { it.name.length }

        val FALLBACK = mapOf(
            MovementGroup.LEGS to listOf("Nordic curls", "Pistol squats", "Calf raises"),
            MovementGroup.PULL to listOf("Pull-ups", "Inverted rows", "Chin-ups"),
            MovementGroup.PUSH to listOf("Push-ups", "Dips", "Pike push-ups"),
            MovementGroup.CORE to listOf("Hollow hold", "L-sit", "Hanging leg raises"),
        )
    }
}

internal fun duration(ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    val h = totalMin / 60; val m = totalMin % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h ${m}m" }
}

internal fun thousands(n: Int): String = String.format(Locale.US, "%,d", n)
