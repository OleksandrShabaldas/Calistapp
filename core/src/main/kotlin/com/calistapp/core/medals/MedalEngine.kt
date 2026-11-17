package com.calistapp.core.medals

import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.formatKg
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.effectiveAddedKg
import com.calistapp.core.time.startOfWeekMs
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Derives every medal from the training history.
 *
 * Medals are never stored as awards: the same history always strikes the same medals, so an imported,
 * edited or deleted session moves them with it, and the app only needs to remember which ones the
 * athlete has already seen. Pure and deterministic — the clock and the time zone are parameters.
 */
object MedalEngine {

    /**
     * @param sessions the whole history, in any order.
     * @param exercises the gallery by id, for each movement's primary muscles and push/pull force.
     * @param plannedDays the local dates the athlete planned to train, for Perfect month.
     * @param zone the athlete's time zone: weeks, months, early starts and late nights are local.
     * @param nowMs "now" for the lines whose progress is about the present (the current streak, this
     *   week's muscle groups, this month's plan). Defaults to the latest session's start.
     */
    fun evaluate(
        sessions: List<PerformedSession>,
        exercises: Map<String, Exercise>,
        plannedDays: Set<LocalDate>,
        zone: ZoneId,
        nowMs: Long? = null,
        catalog: List<MedalDefinition> = MedalCatalog.all,
    ): MedalReport {
        val history = History(sessions, exercises, plannedDays, zone, nowMs)
        return MedalReport(catalog.map { history.line(it) })
    }
}

/** One session's working sets, resolved against the gallery once and shared by every rule. */
private class SessionFacts(val session: PerformedSession, exercises: Map<String, Exercise>) {
    val sets: List<WorkSet> = session.setLogs.mapNotNull { log ->
        val slot = session.plan.slot(log.slotId)
        // The warm-up and cool-down routines are prescribed timers, and warm-up sets are not efforts:
        // neither earns a medal, exactly as neither counts toward a personal best.
        if (session.plan.phaseOf(log.slotId) != SlotPhase.MAIN) return@mapNotNull null
        if (slot?.isWarmup(log.setIndex) == true) return@mapNotNull null
        WorkSet(log, slot, exercises[log.exerciseId])
    }

    fun bestReps(movement: Movement): WorkSet? =
        sets.filter { it.reps > 0 && movement.matches(it.name) }.maxByOrNull { it.reps }

    fun bestHold(movement: Movement): WorkSet? =
        sets.filter { it.holdSeconds > 0 && movement.matches(it.name) }.maxByOrNull { it.holdSeconds }

    fun heaviest(movement: Movement, minReps: Int): WorkSet? =
        sets.filter { it.addedKg > 0.0 && it.reps >= minReps && movement.matches(it.name) }
            .maxWithOrNull(compareBy<WorkSet> { it.addedKg }.thenBy { it.reps })

    val groups: Set<MuscleGroup> by lazy { sets.flatMapTo(LinkedHashSet()) { it.groups } }

    /** Total reps of the most-repeated exercise — rep sets only, never hold seconds. */
    val mostRepsOfOneExercise: Int by lazy {
        sets.filter { it.reps > 0 && session.plan.countsReps(it.log.slotId) }
            .groupBy { it.key }.values.maxOfOrNull { group -> group.sumOf { it.reps } } ?: 0
    }

    /** How long it ran: the clock if known, else the working time. */
    val durationMs: Long get() = max(session.totalDurationMs, session.activeDurationMs)

    /** Enough of a session to count as one, not a mis-tap that was saved. */
    val substantial: Boolean get() = session.setLogs.isNotEmpty() || durationMs >= SUBSTANTIAL_MS
}

private class WorkSet(val log: SetLog, val slot: PlannedExercise?, val exercise: Exercise?) {
    /** The same key progress uses: older logs predate exercise ids and only carry a name. */
    val key: String = log.exerciseId.ifBlank { log.exerciseName }

    /** The gallery's name first: a log carries the slot's display name, which may say "+20 kg". */
    val name: String = exercise?.name?.takeIf { it.isNotBlank() }
        ?: log.exerciseName.ifBlank { slot?.name ?: log.exerciseId }

    /** A hold logs its seconds in [SetLog.seconds]; older logs only in reps, known by the slot's measure. */
    private val isHold: Boolean = log.seconds > 0 || slot?.measure == ExerciseMeasure.SECONDS
    val holdSeconds: Int = when {
        log.seconds > 0 -> log.seconds
        isHold -> log.reps
        else -> 0
    }
    val reps: Int = if (isHold) 0 else log.reps
    val addedKg: Double = effectiveAddedKg(log, slot)

    val force: String? = exercise?.force?.trim()?.lowercase()

    val groups: Set<MuscleGroup> by lazy {
        val fromMuscles = exercise?.primaryMuscles.orEmpty().mapNotNullTo(LinkedHashSet(), ::muscleGroupOf)
        when {
            fromMuscles.isNotEmpty() -> fromMuscles
            else -> setOfNotNull((exercise?.bodyPart ?: slot?.bodyPart)?.let(::muscleGroupOf))
        }
    }
}

/** A session that moved a line's number, and what that number reads as on the rim. */
private class Step(val session: PerformedSession, val value: Double, val engraving: String)

private class History(
    sessions: List<PerformedSession>,
    exercises: Map<String, Exercise>,
    private val plannedDays: Set<LocalDate>,
    private val zone: ZoneId,
    nowMs: Long?,
) {
    /** Oldest first; the id breaks ties so equal start times can't reorder between runs. */
    private val facts: List<SessionFacts> = sessions
        .sortedWith(compareBy<PerformedSession>({ it.startMs }, { it.id }))
        .map { SessionFacts(it, exercises) }

    private val nowMs: Long? = nowMs ?: facts.lastOrNull()?.session?.startMs

    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun week(ms: Long): Long = startOfWeekMs(ms, zone)
    private fun previousWeek(week: Long): Long = startOfWeekMs(week - WEEK_PROBE_MS, zone)

    fun line(def: MedalDefinition): MedalLine {
        val (awards, progress) = evaluate(def)
        val visible = if (def.hidden && awards.isEmpty()) null else progress
        return MedalLine(def, awards, visible)
    }

    private fun evaluate(def: MedalDefinition): Pair<List<MedalAward>, MedalProgress> = when (val rule = def.rule) {
        is MedalRule.RepsInOneSet -> best(def) { f ->
            f.bestReps(rule.movement)?.let { set -> set.reps.toDouble() to repsRim(def, rule.movement, set) }
        }
        is MedalRule.HoldSeconds -> best(def) { f ->
            f.bestHold(rule.movement)?.let { set ->
                set.holdSeconds.toDouble() to "${holdRim(rule.movement)} ${set.holdSeconds} S"
            }
        }
        is MedalRule.WeightedReps -> best(def) { f ->
            f.heaviest(rule.movement, rule.minReps)?.let { set ->
                set.addedKg to "+${formatKg(set.addedKg)} KG × ${set.reps} ${plural(set.reps, "REP", "REPS")}"
            }
        }
        is MedalRule.HrRecovery -> best(def) { f ->
            f.session.hrRecoveryMeanDrop?.takeIf { it > 0 }?.let { it.toDouble() to "−$it BPM IN 1 MIN" }
        }
        is MedalRule.RepsInOneSession -> best(def) { f ->
            f.mostRepsOfOneExercise.takeIf { it > 0 }?.let { it.toDouble() to "$it REPS IN ONE SESSION" }
        }
        MedalRule.SessionCount -> cumulative(def, { 1.0 }) { n -> "SESSION ${n.toInt()}" }
        is MedalRule.FullHrSessions -> cumulative(def, { if (trustedHr(it.session, rule.minCoverage)) 1.0 else 0.0 }) { n ->
            "${n.toInt()} FULL-HR SESSIONS"
        }
        is MedalRule.ZoneHours -> cumulative(def, { f ->
            (f.session.zoneMinutes.getOrElse(rule.zone - 1) { 0 }.coerceAtLeast(0)) / 60.0
        }) { hours -> "${hoursRim(hours)} H IN ZONE ${rule.zone}" }
        is MedalRule.StartsBetween -> cumulative(def, { f ->
            if (startsBetween(f.session.startMs, rule.fromHour, rule.untilHour)) 1.0 else 0.0
        }) { n -> "${n.toInt()} STARTS ${hourRim(rule.fromHour, rule.untilHour)}" }
        MedalRule.DistinctExercises -> distinctExercises(def)
        MedalRule.WeekStreak -> streak(def, { true }) { n -> "$n WEEKS IN A ROW" }
        MedalRule.LegsEveryWeek -> streak(def, { MuscleGroup.LEGS in it.groups }) { n -> "LEGS $n WEEKS RUNNING" }
        is MedalRule.PerfectMonth -> perfectMonth(def, rule)
        is MedalRule.Comeback -> comeback(def, rule)
        is MedalRule.SameWorkoutHrDrop -> sameWorkoutHrDrop(def, rule)
        MedalRule.FullBodyWeek -> fullBodyWeek(def)
        is MedalRule.EvenSplit -> evenSplit(def, rule)
        is MedalRule.ColdOutdoor -> event(def) { f ->
            val feels = f.session.weather?.feelsLikeC
            if (f.session.outdoor == true && feels != null && feels < rule.belowFeelsLikeC) {
                Step(f.session, feels, "OUTSIDE, FEELS LIKE ${degrees(feels)}°")
            } else {
                null
            }
        }
        is MedalRule.TwoInOneDay -> twoInOneDay(def, rule)
    }

    // ---- Rule shapes ---------------------------------------------------------------------------

    /** A per-session best (a set, a hold, a recovery): each tier goes to the first session that reached it. */
    private fun best(
        def: MedalDefinition,
        measure: (SessionFacts) -> Pair<Double, String>?,
    ): Pair<List<MedalAward>, MedalProgress> {
        val steps = facts.mapNotNull { f -> measure(f)?.let { (value, rim) -> Step(f.session, value, rim) } }
        val awards = strike(def, steps)
        return awards to progress(def, awards.size, steps.maxOfOrNull { it.value } ?: 0.0)
    }

    /** A running total (sessions, hours, early starts): each tier goes to the session that carried it over. */
    private fun cumulative(
        def: MedalDefinition,
        add: (SessionFacts) -> Double,
        rim: (Double) -> String,
    ): Pair<List<MedalAward>, MedalProgress> {
        var total = 0.0
        val steps = facts.mapNotNull { f ->
            val delta = add(f)
            if (delta <= 0.0) return@mapNotNull null
            total += delta
            Step(f.session, total, rim(total))
        }
        val awards = strike(def, steps)
        return awards to progress(def, awards.size, total)
    }

    /**
     * Calendar weeks in a row (Monday-first) with a session that passes [counts]. A tier goes to the
     * first qualifying session of the week that made the run long enough. Progress is the run that is
     * still alive now — a quiet current week doesn't end it yet, as on the dashboard.
     */
    private fun streak(
        def: MedalDefinition,
        counts: (SessionFacts) -> Boolean,
        rim: (Int) -> String,
    ): Pair<List<MedalAward>, MedalProgress> {
        val runByWeek = HashMap<Long, Int>()
        val steps = facts.mapNotNull { f ->
            if (!counts(f)) return@mapNotNull null
            val w = week(f.session.startMs)
            if (w in runByWeek) return@mapNotNull null
            val run = (runByWeek[previousWeek(w)] ?: 0) + 1
            runByWeek[w] = run
            Step(f.session, run.toDouble(), rim(run))
        }
        val awards = strike(def, steps)
        val current = nowMs?.let { now ->
            val thisWeek = week(now)
            runByWeek[thisWeek] ?: runByWeek[previousWeek(thisWeek)]
        } ?: 0
        return awards to progress(def, awards.size, current.toDouble())
    }

    /** Something that happens or doesn't: the first session it happens in earns it. */
    private fun event(def: MedalDefinition, happened: (SessionFacts) -> Step?): Pair<List<MedalAward>, MedalProgress> {
        val step = facts.firstNotNullOfOrNull(happened)
        val awards = step?.let { listOf(award(def, 0, it)) }.orEmpty()
        return awards to progress(def, awards.size, 0.0)
    }

    // ---- Rules with their own shape ------------------------------------------------------------

    private fun distinctExercises(def: MedalDefinition): Pair<List<MedalAward>, MedalProgress> {
        val seen = LinkedHashSet<String>()
        val steps = facts.mapNotNull { f ->
            val before = seen.size
            f.sets.forEach { if (it.key.isNotBlank()) seen += it.key }
            if (seen.size == before) null else Step(f.session, seen.size.toDouble(), "${seen.size} DIFFERENT EXERCISES")
        }
        val awards = strike(def, steps)
        return awards to progress(def, awards.size, seen.size.toDouble())
    }

    /**
     * Every planned day of one calendar month trained (on that date, in the athlete's zone), in a month
     * planned for at least [MedalRule.PerfectMonth.minPlannedDays] days so a one-day plan isn't a
     * "perfect month". Earned by the session that trained the month's last planned day.
     */
    private fun perfectMonth(def: MedalDefinition, rule: MedalRule.PerfectMonth): Pair<List<MedalAward>, MedalProgress> {
        val firstOnDate = LinkedHashMap<LocalDate, PerformedSession>()
        facts.forEach { firstOnDate.putIfAbsent(date(it.session.startMs), it.session) }

        val byMonth = plannedDays.groupBy { YearMonth.from(it) }.toSortedMap()
        val completion = byMonth.values.mapNotNull { days ->
            if (days.size < rule.minPlannedDays || !days.all { it in firstOnDate }) return@mapNotNull null
            val closing = firstOnDate.getValue(days.maxBy { it.toEpochDay() })
            Step(closing, days.size.toDouble(), "ALL ${days.size} PLANNED DAYS")
        }.minWithOrNull(compareBy<Step>({ it.session.startMs }, { it.session.id }))
        val awards = completion?.let { listOf(award(def, 0, it)) }.orEmpty()
        if (awards.isNotEmpty()) return awards to complete()

        val today = nowMs?.let(::date) ?: return awards to notYet()
        val planned = plannedDays.filter { YearMonth.from(it) == YearMonth.from(today) }
        if (planned.size < rule.minPlannedDays) {
            return awards to MedalProgress(0, 0.0, "Plan ${rule.minPlannedDays}+ days this month", "", measurable = false)
        }
        val trained = planned.count { it in firstOnDate }
        val missed = planned.any { it < today && it !in firstOnDate }
        val label = MedalUnit.DAYS.ofLabel(trained.toDouble(), planned.size.toDouble())
        return awards to if (missed) {
            // A missed day ends this month's chance; the bar empties rather than promising the impossible.
            MedalProgress(0, 0.0, label, "Missed a planned day", measurable = true)
        } else {
            MedalProgress(
                0, trained.toDouble() / planned.size, label,
                MedalUnit.DAYS.toGo((planned.size - trained).toDouble()), measurable = true,
            )
        }
    }

    /** A session after at least [MedalRule.Comeback.daysOff] whole calendar days with none. */
    private fun comeback(def: MedalDefinition, rule: MedalRule.Comeback): Pair<List<MedalAward>, MedalProgress> {
        var previous: LocalDate? = null
        val step = facts.firstNotNullOfOrNull { f ->
            val day = date(f.session.startMs)
            val off = previous?.let { ChronoUnit.DAYS.between(it, day) - 1 }
            previous = day
            if (off != null && off >= rule.daysOff) Step(f.session, off.toDouble(), "BACK AFTER $off DAYS") else null
        }
        val awards = step?.let { listOf(award(def, 0, it)) }.orEmpty()
        return awards to progress(def, awards.size, 0.0)
    }

    /**
     * The same saved workout at an average heart rate at least the threshold below its first runs.
     *
     * "The same workout" is the saved workout a session was started from ([WorkoutPlan.sourceWorkoutId]);
     * sessions from before that link existed fall back to the plan's name plus its set of working
     * exercises. The baseline is the mean of the first [MedalRule.SameWorkoutHrDrop.baselineRuns] runs
     * with trustworthy heart rate; only later runs can earn it.
     */
    private fun sameWorkoutHrDrop(def: MedalDefinition, rule: MedalRule.SameWorkoutHrDrop): Pair<List<MedalAward>, MedalProgress> {
        val runs = LinkedHashMap<String, MutableList<Int>>()
        var bestDrop = 0.0
        val steps = facts.mapNotNull { f ->
            val s = f.session
            if (!trustedHr(s, rule.minCoverage)) return@mapNotNull null
            val key = workoutKey(f) ?: return@mapNotNull null
            val history = runs.getOrPut(key) { mutableListOf() }
            val step = if (history.size >= rule.baselineRuns) {
                val baseline = history.take(rule.baselineRuns).average()
                val drop = baseline - s.avgHr
                bestDrop = max(bestDrop, drop)
                // Floored, so the rim never claims a bpm the session didn't drop.
                Step(s, drop, "−${floor(drop + EPSILON).toInt()} BPM, SAME WORKOUT")
            } else {
                null
            }
            history += s.avgHr
            step
        }
        val awards = strike(def, steps)
        return awards to progress(def, awards.size, bestDrop)
    }

    private fun workoutKey(f: SessionFacts): String? {
        val plan = f.session.plan
        plan.sourceWorkoutId?.takeIf { it.isNotBlank() }?.let { return "saved:$it" }
        if (plan.name.isBlank()) return null
        val moves = plan.slotsIn(SlotPhase.MAIN).map { it.exerciseId.ifBlank { it.name } }.distinct().sorted()
        if (moves.isEmpty()) return null
        return "named:${plan.name.trim().lowercase()}|${moves.joinToString(",")}"
    }

    /** All six muscle groups inside one calendar week, judged on primary muscles. */
    private fun fullBodyWeek(def: MedalDefinition): Pair<List<MedalAward>, MedalProgress> {
        val groupsByWeek = HashMap<Long, MutableSet<MuscleGroup>>()
        val sessionsByWeek = HashMap<Long, Int>()
        val steps = facts.map { f ->
            val w = week(f.session.startMs)
            val groups = groupsByWeek.getOrPut(w) { LinkedHashSet() }.apply { addAll(f.groups) }
            val sessions = (sessionsByWeek[w] ?: 0) + 1
            sessionsByWeek[w] = sessions
            Step(
                f.session, groups.size.toDouble(),
                "ALL ${groups.size} GROUPS IN $sessions ${plural(sessions, "SESSION", "SESSIONS")}",
            )
        }
        val awards = strike(def, steps)
        val thisWeek = nowMs?.let { groupsByWeek[week(it)]?.size } ?: 0
        return awards to progress(def, awards.size, thisWeek.toDouble())
    }

    /**
     * Push and pull working sets within [MedalRule.EvenSplit.maxRatio] of each other — either way round,
     * so all-pull is as uneven as all-push — over the [MedalRule.EvenSplit.weeks] calendar weeks ending
     * with the session, with enough of each side that one balanced session can't stand in for a month.
     */
    private fun evenSplit(def: MedalDefinition, rule: MedalRule.EvenSplit): Pair<List<MedalAward>, MedalProgress> {
        val pushByWeek = HashMap<Long, Int>()
        val pullByWeek = HashMap<Long, Int>()
        fun window(endWeek: Long): Pair<Int, Int> {
            var w = endWeek
            var push = 0
            var pull = 0
            repeat(rule.weeks) {
                push += pushByWeek[w] ?: 0
                pull += pullByWeek[w] ?: 0
                w = previousWeek(w)
            }
            return push to pull
        }

        var earned: Step? = null
        for (f in facts) {
            val w = week(f.session.startMs)
            pushByWeek[w] = (pushByWeek[w] ?: 0) + f.sets.count { it.force == "push" }
            pullByWeek[w] = (pullByWeek[w] ?: 0) + f.sets.count { it.force == "pull" }
            if (earned != null) continue
            val (push, pull) = window(w)
            if (min(push, pull) >= rule.minSetsEachSide && max(push, pull) <= rule.maxRatio * min(push, pull) + EPSILON) {
                earned = Step(f.session, max(push, pull).toDouble() / min(push, pull), "$push PUSH · $pull PULL SETS")
            }
        }
        val awards = earned?.let { listOf(award(def, 0, it)) }.orEmpty()
        if (awards.isNotEmpty()) return awards to complete()

        // Progress: what the lagging side still needs, over the window ending this week.
        val (push, pull) = nowMs?.let { window(week(it)) } ?: (0 to 0)
        val needPush = max(rule.minSetsEachSide, ceil(pull / rule.maxRatio - EPSILON).toInt())
        val needPull = max(rule.minSetsEachSide, ceil(push / rule.maxRatio - EPSILON).toInt())
        val missingPush = (needPush - push).coerceAtLeast(0)
        val missingPull = (needPull - pull).coerceAtLeast(0)
        val fraction = (push + pull).toDouble() / (push + pull + missingPush + missingPull).coerceAtLeast(1)
        val pullLags = missingPull >= missingPush
        val side = if (pullLags) "pull" else "push"
        val have = if (pullLags) pull else push
        val need = if (pullLags) needPull else needPush
        val missing = if (pullLags) missingPull else missingPush
        return awards to MedalProgress(
            nextTierIndex = 0,
            fraction = fraction.coerceIn(0.0, 1.0),
            label = "$have of $need $side sets",
            toGo = "$missing $side ${plural(missing, "set", "sets")} to go",
            measurable = true,
        )
    }

    /**
     * Two real sessions on one calendar day with at least [MedalRule.TwoInOneDay.minGapMinutes] between
     * the first one's end and the next one's start — so a session that crashed and was restarted, or a
     * saved mis-tap, doesn't count as training twice.
     */
    private fun twoInOneDay(def: MedalDefinition, rule: MedalRule.TwoInOneDay): Pair<List<MedalAward>, MedalProgress> {
        val gapMs = rule.minGapMinutes * 60_000L
        val byDay = HashMap<LocalDate, MutableList<SessionFacts>>()
        val step = facts.firstNotNullOfOrNull { f ->
            if (!f.substantial) return@firstNotNullOfOrNull null
            val earlier = byDay.getOrPut(date(f.session.startMs)) { mutableListOf() }
            val hit = earlier.any { it.session.startMs + it.durationMs + gapMs <= f.session.startMs }
            earlier += f
            if (hit) Step(f.session, earlier.size.toDouble(), "${earlier.size} SESSIONS IN ONE DAY") else null
        }
        val awards = step?.let { listOf(award(def, 0, it)) }.orEmpty()
        return awards to progress(def, awards.size, 0.0)
    }

    // ---- Shared ---------------------------------------------------------------------------------

    /** Each tier to the first step that reached it, in order. Steps are oldest first. */
    private fun strike(def: MedalDefinition, steps: List<Step>): List<MedalAward> {
        val awards = mutableListOf<MedalAward>()
        for (step in steps) {
            while (awards.size < def.tiers.size && step.value >= def.tiers[awards.size].threshold - EPSILON) {
                awards += award(def, awards.size, step)
            }
            if (awards.size == def.tiers.size) break
        }
        return awards
    }

    private fun award(def: MedalDefinition, tier: Int, step: Step) = MedalAward(
        medalId = def.id,
        tierIndex = tier,
        metal = def.tiers[tier].metal,
        earnedAtMs = step.session.startMs,
        sessionId = step.session.id,
        value = step.value,
        engraving = step.engraving,
    )

    private fun progress(def: MedalDefinition, earned: Int, current: Double): MedalProgress {
        if (earned >= def.tiers.size) return complete()
        if (!def.unit.measurable) return notYet()
        val target = def.tiers[earned].threshold
        val value = current.coerceIn(0.0, target)
        return MedalProgress(
            nextTierIndex = earned,
            fraction = value / target,
            label = def.unit.ofLabel(value, target),
            toGo = def.unit.toGo(target - value),
            measurable = true,
        )
    }

    private fun complete() = MedalProgress(null, 1.0, "Earned", "", measurable = true)
    private fun notYet() = MedalProgress(0, 0.0, "Not yet", "", measurable = false)

    /** Measured, not estimated, and heart rate covered nearly all of it. */
    private fun trustedHr(s: PerformedSession, minCoverage: Double): Boolean =
        !s.estimated && s.avgHr > 0 && s.hrCoverage >= minCoverage - EPSILON

    private fun startsBetween(ms: Long, fromHour: Int, untilHour: Int): Boolean {
        val hour = Instant.ofEpochMilli(ms).atZone(zone).hour
        return if (fromHour < untilHour) hour in fromHour until untilHour else hour >= fromHour || hour < untilHour
    }
}

private fun repsRim(def: MedalDefinition, movement: Movement, set: WorkSet): String {
    val n = set.reps
    if (def.tiers.size > 1) return "$n ${plural(n, "REP", "REPS")} IN ONE SET"
    return when (movement) {
        Movement.PULL_UP -> "$n CLEAN ${plural(n, "REP", "REPS")}"
        Movement.MUSCLE_UP -> {
            val where = if (tokenize(set.name).any { it == "ring" || it == "rings" }) "THE RINGS" else "THE BAR"
            "$n ${plural(n, "REP", "REPS")} ON $where"
        }
        Movement.PISTOL_SQUAT -> "$n REPS, BOTH LEGS"
        else -> "$n ${plural(n, "REP", "REPS")}"
    }
}

private fun holdRim(movement: Movement): String = when (movement) {
    Movement.L_SIT -> "L-SIT"
    Movement.FRONT_LEVER -> "LEVER"
    else -> "HOLD"
}

/** "7 AM" style, for the start-time medals' rims. */
private fun hourRim(fromHour: Int, untilHour: Int): String {
    fun clock(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    return if (fromHour < untilHour) "BEFORE ${clock(untilHour)}" else "AFTER ${clock(fromHour)}"
}

/** One decimal, floored: 10.27 h reads "10.2", a whole 10 reads "10". */
private fun hoursRim(hours: Double): String {
    val tenths = floor(hours * 10 + EPSILON).toInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

/**
 * Tenths cut toward zero, so the rim never overstates the number either way: 7.96° reads "7.9" (not the
 * 8° it stayed under) and −3.25° reads "−3.2". Whole degrees drop the ".0"; below zero is a true minus.
 */
private fun degrees(c: Double): String {
    val tenths = (c * 10).toInt()
    val magnitude = kotlin.math.abs(tenths)
    val text = if (magnitude % 10 == 0) "${magnitude / 10}" else "${magnitude / 10}.${magnitude % 10}"
    return if (tenths < 0) "−$text" else text
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

/** Six days back, then re-anchored to that week's start — immune to daylight-saving's short week. */
private const val WEEK_PROBE_MS = 6L * 24 * 60 * 60 * 1000

private const val SUBSTANTIAL_MS = 10L * 60 * 1000

private const val EPSILON = 1e-9
