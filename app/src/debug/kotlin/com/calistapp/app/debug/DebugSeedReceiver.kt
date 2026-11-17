package com.calistapp.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.calistapp.app.data.exercise.ExerciseRepository
import com.calistapp.app.data.insights.InsightsPrefsRepository
import com.calistapp.core.medals.MedalEngine
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.WeatherSnapshot
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.Exercise
import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HrRecovery
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.model.WorkoutStyle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

/**
 * Debug builds only: fills the app with demo training history so Insights (and later the medals) can
 * be checked in every state without weeks of real workouts.
 *
 *     adb shell am broadcast -n com.calistapp/com.calistapp.app.debug.DebugSeedReceiver --es scenario nine
 *
 * Scenarios: `empty`, `week2` (training under way, nothing struck), `nine` (about four months, the
 * typical case, nine or so medals), `heavy` (a year and a half, long Cyrillic names, weighted work: the
 * worst case), `unlock` (`nine`, then one session today that strikes two new medals). Every scenario
 * then re-runs the medal baseline, exactly as a first launch would: what the history already holds is
 * marked seen, so only `unlock`'s last session plays the unlock moment. Only ever adds or removes its own
 * `seed-` sessions, so real history on the device is never touched.
 */
@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {

    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var exercises: ExerciseRepository
    @Inject lateinit var prefs: InsightsPrefsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario") ?: "nine"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val catalog = exercises.observeAll().first()
                val demo = DemoHistory(catalog)
                val n = demo.seed(scenario, sessions)
                // The first-run baseline: everything already earned counts as seen.
                val report = MedalEngine.evaluate(
                    sessions.observePerformed().first(), catalog.associateBy { it.id }, emptySet(), ZoneId.systemDefault(),
                    nowMs = System.currentTimeMillis(),
                )
                prefs.replaceMedalsSeen(report.awards.map { it.key })
                val fresh = if (scenario == "unlock") demo.unlockSession().also { sessions.saveSession(it) } else null
                Log.i(TAG, "seeded '$scenario': $n sessions, ${report.awards.size} medals seen" + (fresh?.let { ", +1 unlocking session ${it.id}" } ?: ""))
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "DebugSeed" }
}

private class DemoHistory(private val catalog: List<Exercise>) {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val rnd = Random(7)

    private data class Ex(val id: String, val name: String, val measure: ExerciseMeasure = ExerciseMeasure.REPS)
    private data class Set(val ex: Ex, val value: Int, val kg: Double = 0.0)

    private fun find(vararg names: String, measure: ExerciseMeasure = ExerciseMeasure.REPS): Ex {
        val hit = catalog.firstOrNull { e -> names.any { e.name.equals(it, true) } }
            ?: catalog.firstOrNull { e -> names.any { e.name.contains(it, true) } }
        return Ex(hit?.id ?: "custom-${names.first().lowercase().replace(' ', '-')}", hit?.name ?: names.first(), measure)
    }

    private val pullup by lazy { find("Pull-up", "Pullups", "Pull-Up") }
    private val pushup by lazy { find("Push-up", "Pushups", "Push-Up") }
    private val dip by lazy { find("Dips - Triceps Version", "Parallel Bar Dip", "Dip") }
    private val row by lazy { find("Inverted Row", "Bodyweight Row") }
    private val squat by lazy { find("Bodyweight Squat", "Air Squat", "Squat") }
    private val pistol by lazy { find("Pistol Squat", "Pistol") }
    private val lsit by lazy { find("L-Sit", "L Sit", measure = ExerciseMeasure.SECONDS) }
    private val legRaise by lazy { find("Hanging Leg Raise", "Leg Raise") }

    suspend fun seed(scenario: String, repo: SessionRepository): Int {
        repo.observeSessions().first().filter { it.id.startsWith("seed-") }.forEach { repo.deleteSession(it.id) }
        val list = when (scenario) {
            "empty" -> emptyList()
            "week2" -> week2()
            "heavy" -> heavy()
            else -> typical()  // "nine", "unlock"

        }
        list.forEach { repo.saveSession(it) }
        return list.size
    }

    /**
     * Twelve days in, four sessions: enough for some verdicts, not for others. No pull-ups (one clean rep
     * would strike First pull-up), so the case is still empty with Push-ups 25 most of the way there.
     */
    private fun week2(): List<WorkoutSession> = listOf(11, 8, 5, 2).mapIndexed { i, back ->
        session(today.minusDays(back.toLong()), "Upper", listOf(Set(row, 10), Set(pushup, 19 + i), Set(pushup, 15), Set(dip, 8 + i), Set(squat, 20)), rpe = 6, hr = 138, rec = 20 + i)
    }

    /** Today, after the `nine` history: 16 pull-ups (Pull-ups 15, silver) and 52 push-ups (Push-ups 50). */
    fun unlockSession(): WorkoutSession = session(
        today, "Upper push",
        listOf(Set(pullup, 16), Set(pullup, 12), Set(pushup, 52), Set(pushup, 30), Set(dip, 12), Set(lsit, 24)),
        rpe = 8, hr = 142, rec = 24, at = LocalTime.now(zone).minusMinutes(55),
    )

    /**
     * About four months, three sessions a week: pull-ups climbing 9 → 14, dips going weighted, legs
     * neglected, most misses on Fridays, recovery improving after circuit days began, and the last
     * three sessions rated much harder than their heart rate.
     */
    private fun typical(): List<WorkoutSession> {
        val out = ArrayList<WorkoutSession>()
        val start = today.minusDays(120)
        var d = start
        var k = 0
        while (d.isBefore(today)) {
            val dow = d.dayOfWeek
            val planned = dow == DayOfWeek.MONDAY || dow == DayOfWeek.WEDNESDAY || dow == DayOfWeek.FRIDAY
            val skip = dow == DayOfWeek.FRIDAY && rnd.nextInt(3) == 0
            if (planned && !skip) {
                val progress = k / 50.0
                val pull = (9 + progress * 5).toInt().coerceAtMost(14)
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(d, today)
                val circuit = daysLeft < 40 && dow == DayOfWeek.WEDNESDAY
                val sets = buildList {
                    add(Set(pullup, pull)); add(Set(pullup, pull - 2))
                    add(Set(pushup, 30 + (progress * 10).toInt())); add(Set(pushup, 26))
                    add(Set(dip, 10, kg = if (progress > 0.5) 15.0 + (progress * 6).toInt() else 0.0))
                    add(Set(row, 12)); add(Set(legRaise, 10)); add(Set(lsit, 18 + (progress * 14).toInt()))
                    if (k % 4 == 0) add(Set(squat, 20))
                }
                val fatigue = daysLeft <= 6
                // Two outdoor sessions on cold days, for the cold-day medal.
                val cold = k == 12 || k == 30
                out += session(
                    d, if (circuit) "Circuit" else "Upper pull", sets,
                    weather = if (cold) WeatherSnapshot(tempC = 6.0, feelsLikeC = 3.5) else null,
                    outdoor = if (cold) true else null,
                    rpe = if (fatigue) 9 else 6 + (k % 2), hr = if (fatigue) 136 else 140 - (progress * 6).toInt(),
                    rec = (19 + progress * 10).toInt() + if (circuit) 2 else 0,
                    style = if (circuit) WorkoutStyle.CIRCUIT else WorkoutStyle.BY_EXERCISE,
                )
                k++
            }
            d = d.plusDays(1)
        }
        return out
    }

    /** A year and a half, five days a week, long non-Latin custom names, weighted and timed work. */
    private fun heavy(): List<WorkoutSession> {
        val cyr1 = Ex("custom-pidtyaguvannya", "Підтягування з вагою на поясі широким хватом")
        val cyr2 = Ex("custom-vidzhymannya", "Віджимання на брусах з паузою внизу")
        val out = ArrayList<WorkoutSession>()
        var d = today.minusDays(540)
        var k = 0
        while (d.isBefore(today)) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) {
                val p = k / 380.0
                out += session(
                    d, "Повна програма тренувань ${k % 3 + 1}",
                    listOf(
                        Set(cyr1, 5, kg = 5.0 + (p * 27.5)), Set(cyr1, 5, kg = 5.0 + (p * 25)),
                        Set(cyr2, 8 + (p * 6).toInt()), Set(pullup, 12 + (p * 10).toInt()), Set(pistol, 4 + (p * 8).toInt()),
                        Set(squat, 30 + (p * 20).toInt()), Set(lsit, 20 + (p * 40).toInt()), Set(pushup, 40 + (p * 35).toInt()),
                    ),
                    rpe = 7, hr = 145 - (p * 12).toInt(), rec = (22 + p * 14).toInt(),
                )
                k++
            }
            d = d.plusDays(1)
        }
        return out
    }

    private fun session(
        date: LocalDate, name: String, sets: List<Set>, rpe: Int?, hr: Int, rec: Int?, style: WorkoutStyle = WorkoutStyle.BY_EXERCISE,
        weather: WeatherSnapshot? = null, outdoor: Boolean? = null, at: LocalTime = LocalTime.of(18, 10),
    ): WorkoutSession {
        val start = date.atTime(at).atZone(zone).toInstant().toEpochMilli()
        val id = "seed-${date}-${name.hashCode().toUInt()}"
        val slots = sets.map { it.ex }.distinct().map { ex ->
            PlannedExercise(slotId = "slot-${ex.id}", exerciseId = ex.id, name = ex.name, measure = ex.measure)
        }
        val logs = sets.mapIndexed { i, s ->
            SetLog(
                slotId = "slot-${s.ex.id}", exerciseId = s.ex.id, exerciseName = s.ex.name, setIndex = i + 1,
                reps = s.value, weightKg = s.kg, startMs = start + i * 150_000L, endMs = start + i * 150_000L + 45_000L,
            )
        }
        val active = 34 * 60_000L
        return WorkoutSession(
            id = id,
            exerciseType = ExerciseType.CALISTHENICS,
            startMs = start,
            endMs = start + 52 * 60_000L,
            status = SessionStatus.COMPLETED,
            plan = WorkoutPlan(id = "plan-$id", name = name, exercises = slots, style = style, sourceWorkoutId = "seed-$name"),
            setLogs = logs,
            rpe = rpe,
            weather = weather,
            outdoor = outdoor,
            summary = SessionSummary(
                totalKcal = 330.0 + rnd.nextInt(80), activeKcal = 260.0, restKcal = 70.0,
                activeDurationMs = active, restDurationMs = 18 * 60_000L,
                avgHr = hr, peakHr = hr + 32, avgActiveHr = hr + 8, minHr = hr - 35,
                totalReps = logs.sumOf { it.reps },
                hrRecovery = rec?.let { HrRecovery(meanDropBpm = it, bestDropBpm = it + 7, measuredRests = 5) },
                // 50.5 of 52 minutes in zones: a fully measured session (heart rate covered 97 %).
                timeInZonesMs = mapOf(
                    HrZone.ZONE1 to 6 * 60_000L, HrZone.ZONE2 to 14 * 60_000L, HrZone.ZONE3 to 18 * 60_000L,
                    HrZone.ZONE4 to 10 * 60_000L, HrZone.ZONE5 to 150_000L,
                ),
                hrCoverage = 0.97,
            ),
        )
    }
}
