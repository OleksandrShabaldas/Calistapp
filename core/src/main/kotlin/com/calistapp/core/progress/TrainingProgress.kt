package com.calistapp.core.progress

import com.calistapp.core.model.SetLog
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.formatKg
import com.calistapp.core.time.startOfWeekMs
import kotlinx.serialization.Serializable
import java.time.ZoneId

/**
 * What a session contributes to a trend, without its heart-rate stream.
 *
 * The samples are the bulk of a stored session and none of them matter here — progress is built from
 * what was performed and what it scored, so the query that feeds this deliberately leaves them in
 * the database.
 */
@Serializable
data class PerformedSession(
    val id: String,
    val startMs: Long,
    val kcal: Int,
    val activeDurationMs: Long,
    val setLogs: List<SetLog>,
    /** Carried because added load lives on the plan's slots, not on the set log. */
    val plan: WorkoutPlan,
    /** Average heart rate over the session — the input TRIMP is defined on. */
    val avgHr: Int = 0,
    /** Wall-clock length, work and rest together. TRIMP is a function of the whole session. */
    val totalDurationMs: Long = 0,
    /** Rated perceived exertion, 1–10, if the athlete logged it. */
    val rpe: Int? = null,
    /** The athlete's own note on the session — clues the numbers don't carry. */
    val notes: String = "",
)

/** A single set worth remembering — the most reps, or the most weight. */
data class BestSet(val reps: Int, val addedWeightKg: Double, val atMs: Long)

/** Everything one movement has accumulated across every session it appeared in. */
data class ExerciseProgress(
    val key: String,
    val exerciseName: String,
    val sessionCount: Int,
    val totalSets: Int,
    val totalReps: Int,
    val lastPerformedMs: Long,
    /** Best single set by reps. Null for movements only ever logged as timed holds. */
    val mostReps: BestSet?,
    /** Best single set by added load. Null when the movement was never weighted. */
    val heaviest: BestSet?,
    /** Best single set by volume (added weight × reps). Null when the movement was never weighted. */
    val maxVolume: BestSet? = null,
)

data class WeekSummary(
    val weekStartMs: Long,
    val sessions: Int,
    val kcal: Int,
    val reps: Int,
    val activeMs: Long,
) {
    val trained: Boolean get() = sessions > 0
}

data class TrainingProgress(
    /** Oldest to newest, one entry per week including the empty ones — a gap is information. */
    val weeks: List<WeekSummary>,
    /** Most-trained movement first. */
    val exercises: List<ExerciseProgress>,
    val totalSessions: Int,
    val totalKcal: Int,
    val totalReps: Int,
    val totalActiveMs: Long,
    /** Consecutive weeks with at least one session, counting back from now. */
    val streakWeeks: Int,
    /** Null until there's a month of history to average this week against. */
    val ramp: TrainingLoad.Ramp? = null,
    /**
     * The last 30 calendar days as a train/rest strip, oldest first (index 29 is today). Independent
     * of the week window above — it drives the stats screen's day-dot calendar.
     */
    val recentDays: List<Boolean> = emptyList(),
) {
    val isEmpty: Boolean get() = totalSessions == 0

    /** The busiest week in the window, for scaling a chart against something real. */
    val peakWeekKcal: Int get() = weeks.maxOfOrNull { it.kcal } ?: 0

    /** Days trained in the last 30 — the headline for the day-dot strip. */
    val recentTrainedDays: Int get() = recentDays.count { it }
}

/**
 * Roll a training history up into trends and personal bests.
 *
 * Pure and deterministic — [nowMs] and [zone] are parameters rather than ambient reads so the whole
 * thing is testable, and so a week boundary means the same here as it does on the dashboard.
 */
fun summarizeProgress(
    sessions: List<PerformedSession>,
    nowMs: Long,
    weekCount: Int = 12,
    zone: ZoneId = ZoneId.systemDefault(),
    /** Needed for training load, which is a function of your heart-rate reserve. Null skips it. */
    profile: UserProfile? = null,
): TrainingProgress {
    val currentWeek = startOfWeekMs(nowMs, zone)

    // Every week in the window, present or not: a chart that silently omits the weeks you didn't
    // train reads as unbroken consistency, which is the opposite of the truth.
    val buckets = LinkedHashMap<Long, MutableWeek>()
    for (i in (weekCount - 1) downTo 0) {
        buckets[startOfWeekMs(currentWeek - i * WEEK_PROBE_MS, zone)] = MutableWeek()
    }

    for (session in sessions) {
        val week = startOfWeekMs(session.startMs, zone)
        buckets[week]?.let { bucket ->
            bucket.sessions++
            bucket.kcal += session.kcal
            bucket.reps += session.setLogs.sumOf { it.reps }
            bucket.activeMs += session.activeDurationMs
        }
    }

    // The 30-day strip: a set of trained day-starts, then probed day by day so a clock change can't
    // skip or double-count a day (same reasoning as the week probe below).
    val trainedDays = sessions.mapTo(HashSet()) { startOfDayMs(it.startMs, zone) }
    val today = startOfDayMs(nowMs, zone)
    val recentDays = (RECENT_DAYS - 1 downTo 0).map { back ->
        startOfDayMs(today - back * DAY_PROBE_MS, zone) in trainedDays
    }

    return TrainingProgress(
        weeks = buckets.map { (start, w) -> WeekSummary(start, w.sessions, w.kcal, w.reps, w.activeMs) },
        exercises = exerciseProgress(sessions),
        totalSessions = sessions.size,
        totalKcal = sessions.sumOf { it.kcal },
        totalReps = sessions.sumOf { session -> session.setLogs.sumOf { it.reps } },
        totalActiveMs = sessions.sumOf { it.activeDurationMs },
        streakWeeks = streakWeeks(sessions, currentWeek, zone),
        ramp = profile?.let { TrainingLoad.ramp(dailyLoads(sessions, nowMs, it, zone)) },
        recentDays = recentDays,
    )
}

/** A record beaten in a session — the reason to celebrate on the summary screen. */
data class PersonalRecord(
    val exerciseName: String,
    val kind: RecordKind,
    /** Human label for the new best: "12 reps", "+22.5 kg", "270 kg volume". */
    val label: String,
)

enum class RecordKind { REPS, WEIGHT, VOLUME }

/**
 * The personal records set in [sessionId], judged against everything performed *before* it — so
 * re-opening an old session shows the records it set at the time, and the just-finished one shows what
 * you beat today. A movement's first-ever appearance is not a "record" (there was nothing to beat),
 * warm-up sets don't count, and each movement contributes at most one headline (weight, else reps,
 * else volume) so the card stays a short list of wins rather than a wall of near-duplicates.
 */
fun personalRecords(allSessions: List<PerformedSession>, sessionId: String): List<PersonalRecord> {
    val current = allSessions.firstOrNull { it.id == sessionId } ?: return emptyList()
    val prior = allSessions.filter { it.id != sessionId && it.startMs < current.startMs }

    val currentBests = bestsByExercise(listOf(current))
    val priorBests = bestsByExercise(prior)

    return currentBests.mapNotNull { (key, cur) ->
        val prev = priorBests[key] ?: return@mapNotNull null // first time isn't a record
        when {
            cur.weightKg > 0.0 && cur.weightKg > prev.weightKg ->
                PersonalRecord(cur.name, RecordKind.WEIGHT, "+${formatKg(cur.weightKg)} kg")
            cur.reps > prev.reps ->
                PersonalRecord(cur.name, RecordKind.REPS, "${cur.reps} reps")
            cur.volume > 0.0 && cur.volume > prev.volume ->
                PersonalRecord(cur.name, RecordKind.VOLUME, "${formatKg(cur.volume)} kg volume")
            else -> null
        }
    }
}

private class Best(val name: String) {
    var reps = 0
    var weightKg = 0.0
    var volume = 0.0
}

private fun bestsByExercise(sessions: List<PerformedSession>): Map<String, Best> {
    val byExercise = LinkedHashMap<String, Best>()
    for (session in sessions) {
        for (log in session.setLogs) {
            val key = log.exerciseId.ifBlank { log.exerciseName }
            if (key.isBlank()) continue
            val slot = session.plan.slot(log.slotId)
            if (slot?.isWarmup(log.setIndex) == true) continue
            val best = byExercise.getOrPut(key) { Best(log.exerciseName) }
            val kg = slot?.addedWeightKg ?: 0.0
            best.reps = maxOf(best.reps, log.reps)
            best.weightKg = maxOf(best.weightKg, kg)
            best.volume = maxOf(best.volume, kg * log.reps)
        }
    }
    return byExercise
}

/** Length of the day-dot strip on the stats screen. */
private const val RECENT_DAYS = 30

/**
 * Session load bucketed into days, most recent first, with zeros for rest days.
 *
 * The zeros matter: a chronic average that only counted days you trained would rise the *less* you
 * trained, which inverts the whole measure.
 */
private fun dailyLoads(
    sessions: List<PerformedSession>,
    nowMs: Long,
    profile: UserProfile,
    zone: ZoneId,
): List<Double> {
    val today = startOfDayMs(nowMs, zone)
    val byDay = HashMap<Long, Double>()
    for (session in sessions) {
        val day = startOfDayMs(session.startMs, zone)
        byDay[day] = (byDay[day] ?: 0.0) +
            TrainingLoad.trimp(session.avgHr, session.totalDurationMs, profile)
    }
    return (0 until TrainingLoad.CHRONIC_DAYS).map { back ->
        byDay[startOfDayMs(today - back * DAY_PROBE_MS, zone)] ?: 0.0
    }
}

private fun startOfDayMs(atMs: Long, zone: ZoneId): Long =
    java.time.Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().atStartOfDay(zone)
        .toInstant().toEpochMilli()

/** Twenty hours back, then re-anchored — immune to the 23-hour day a clock change produces. */
private const val DAY_PROBE_MS = 20L * 60 * 60 * 1000

private class MutableWeek {
    var sessions = 0
    var kcal = 0
    var reps = 0
    var activeMs = 0L
}

/**
 * Consecutive weeks with at least one session.
 *
 * A quiet current week doesn't break the streak — it isn't over yet. Counting from last week in that
 * case is what stops the number collapsing to zero every Monday morning.
 */
private fun streakWeeks(sessions: List<PerformedSession>, currentWeek: Long, zone: ZoneId): Int {
    if (sessions.isEmpty()) return 0
    val trained = sessions.mapTo(mutableSetOf()) { startOfWeekMs(it.startMs, zone) }

    var week = currentWeek
    if (week !in trained) {
        week = startOfWeekMs(currentWeek - WEEK_PROBE_MS, zone)
        if (week !in trained) return 0
    }

    var streak = 0
    while (week in trained) {
        streak++
        week = startOfWeekMs(week - WEEK_PROBE_MS, zone)
    }
    return streak
}

private fun exerciseProgress(sessions: List<PerformedSession>): List<ExerciseProgress> {
    class Acc(val name: String) {
        val sessionIds = mutableSetOf<String>()
        var sets = 0
        var reps = 0
        var lastMs = 0L
        var mostReps: BestSet? = null
        var heaviest: BestSet? = null
        var maxVolume: BestSet? = null
    }

    val byExercise = LinkedHashMap<String, Acc>()

    for (session in sessions) {
        for (log in session.setLogs) {
            // Older logs were written before exercise ids were carried on the set; the name is the
            // only stable handle those have.
            val key = log.exerciseId.ifBlank { log.exerciseName }
            if (key.isBlank()) continue

            // Warm-ups cost energy and are scored as such, but they are not volume and a record set
            // with an empty bar isn't one.
            val slot = session.plan.slot(log.slotId)
            if (slot?.isWarmup(log.setIndex) == true) continue

            val acc = byExercise.getOrPut(key) { Acc(log.exerciseName) }
            acc.sessionIds += session.id
            acc.sets++
            acc.reps += log.reps
            acc.lastMs = maxOf(acc.lastMs, log.startMs)

            val addedKg = slot?.addedWeightKg ?: 0.0
            if (log.reps > 0 && log.reps > (acc.mostReps?.reps ?: 0)) {
                acc.mostReps = BestSet(log.reps, addedKg, log.startMs)
            }
            if (addedKg > 0.0 && addedKg > (acc.heaviest?.addedWeightKg ?: 0.0)) {
                acc.heaviest = BestSet(log.reps, addedKg, log.startMs)
            }
            val volume = addedKg * log.reps
            val bestVolume = acc.maxVolume?.let { it.addedWeightKg * it.reps } ?: 0.0
            if (addedKg > 0.0 && volume > bestVolume) {
                acc.maxVolume = BestSet(log.reps, addedKg, log.startMs)
            }
        }
    }

    return byExercise.map { (key, acc) ->
        ExerciseProgress(
            key = key,
            exerciseName = acc.name,
            sessionCount = acc.sessionIds.size,
            totalSets = acc.sets,
            totalReps = acc.reps,
            lastPerformedMs = acc.lastMs,
            mostReps = acc.mostReps,
            heaviest = acc.heaviest,
            maxVolume = acc.maxVolume,
        )
    }.sortedByDescending { it.totalReps }
}

/**
 * Six days back, then re-anchored to that week's start. Stepping by a fixed seven days would drift
 * across a daylight-saving change; landing anywhere inside the previous week and re-anchoring can't.
 */
private const val WEEK_PROBE_MS = 6L * 24 * 60 * 60 * 1000
