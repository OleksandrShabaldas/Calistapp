package com.calistapp.app.data.ai

import com.calistapp.core.model.HrZone
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.model.formatKg
import com.calistapp.core.progress.ExerciseProgress
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.summarizeProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a completed workout into an AI coaching analysis.
 *
 * The coach is only as good as what it can see, so the prompt carries the athlete's *training
 * context*, not just today's numbers: the latest session in full, the last several sessions, personal
 * bests and per-movement records, and the training-load trend. That's what lets it compare against the
 * trend and counsel rather than summarise one workout. Runs on the reasoning tier with ample output
 * room — the analysis is meant to be substantial, and thinking models share the token budget with it.
 */
@Singleton
class InsightsRepository @Inject constructor(
    private val gemini: GeminiClient,
) {
    suspend fun analyzeSession(
        session: WorkoutSession,
        summary: SessionSummary,
        profile: UserProfile,
        history: List<PerformedSession>,
    ): AiResult = gemini.generate(
        buildCoachPrompt(session, summary, profile, history, System.currentTimeMillis()),
        AiModelTier.THINKING,
    )
}

/**
 * Assemble the coach prompt. Top-level and pure (given [now]) so it can be unit-tested — the point of
 * the rework is that the prompt carries the athlete's training context, and that's worth asserting.
 */
internal fun buildCoachPrompt(
    session: WorkoutSession,
    s: SessionSummary,
    p: UserProfile,
    history: List<PerformedSession>,
    now: Long,
): String = buildString {
        val progress = summarizeProgress(history, now, profile = p)
        val recent = history.asSequence()
            .filter { it.id != session.id }
            .sortedByDescending { it.startMs }
            .take(10)
            .toList()

        appendLine(
            "You are an elite strength & conditioning coach and exercise physiologist reviewing an " +
                "athlete's latest training session in the context of their recent training.",
        )
        appendLine(
            "Write an in-depth, genuinely useful coaching analysis — not a summary of one workout. " +
                "Reason about the physiology, use the athlete's real numbers and movement names, compare " +
                "this session against the trend, and give concrete programming they can act on. Let the " +
                "data decide what matters most this time; don't force a fixed template if the numbers " +
                "point somewhere specific.",
        )
        appendLine()

        appendLine("ATHLETE PROFILE")
        appendLine("- ${p.sex}, ${p.ageYears}y, ${p.weightKg} kg, ${p.heightCm} cm")
        appendLine(
            "- Resting HR ${p.restingHr} bpm, max HR ${p.effectiveMaxHr} bpm" +
                (p.vo2Max?.let { ", VO2max ${it} ml/kg/min" } ?: ", VO2max not provided"),
        )
        appendLine()

        appendLine("LATEST SESSION — ${session.exerciseType.displayName}, ${dateTimeOf(session.startMs)}")
        appendLine("- Total time ${fmt(s.totalDurationMs)} (active ${fmt(s.activeDurationMs)}, rest ${fmt(s.restDurationMs)}); ${(s.activeRatio * 100).toInt()}% working")
        appendLine("- Calories ${s.totalKcal.toInt()} kcal (${s.activeKcal.toInt()} active, ${s.restKcal.toInt()} rest)")
        appendLine("- Heart rate: avg ${s.avgHr}, active-avg ${s.avgActiveHr}, peak ${s.peakHr}, min ${s.minHr} bpm")
        val zones = HrZone.entries.filter { (s.timeInZonesMs[it] ?: 0L) > 0 }
        if (zones.isNotEmpty()) {
            appendLine("- Time in zones: " + zones.joinToString(", ") { "${it.label} ${fmt(s.timeInZonesMs[it] ?: 0L)}" })
        }
        s.hrRecovery?.let { appendLine("- Heart-rate recovery: ${it.meanDropBpm} bpm/min (best ${it.bestDropBpm}, across ${it.measuredRests} rests)") }
        session.rpe?.let { appendLine("- Rated perceived exertion: $it/10 (Borg CR10)") }
        if (s.perExercise.isNotEmpty()) {
            appendLine("- Total reps ${s.totalReps}. Per exercise:")
            s.perExercise.forEach { e ->
                appendLine("    ${e.exerciseName}: ${e.sets} sets, ${e.reps} reps, ${e.kcal.toInt()} kcal, ${fmt(e.activeDurationMs)} under load")
            }
        }
        if (!session.plan.isEmpty) {
            appendLine("- Planned vs performed:")
            session.plan.exercises.forEach { slot ->
                val done = session.setLogs.filter { it.slotId == slot.slotId }
                appendLine("    ${slot.name}: planned ${slot.targetLabel}, performed ${done.size} sets (${done.sumOf { it.reps }} reps)")
            }
        }
        if (session.notes.isNotBlank()) appendLine("- Athlete's note: ${session.notes}")
        appendLine()

        if (recent.isNotEmpty()) {
            appendLine("RECENT TRAINING (last ${recent.size} sessions, newest first)")
            recent.forEach { r ->
                val vol = volumeOf(r)
                appendLine(
                    "- ${dateOf(r.startMs)}: ${fmt(r.totalDurationMs)}, ${r.setLogs.sumOf { it.reps }} reps" +
                        (if (vol > 0) ", ${formatKg(vol)} kg volume" else "") +
                        ", avg HR ${r.avgHr}" +
                        (r.rpe?.let { ", RPE $it" } ?: "") +
                        (r.hrRecoveryMeanDrop?.let { ", recovery $it bpm/min" } ?: "") +
                        ", ${r.kcal} kcal",
                )
            }
            appendLine()
        }

        appendLine("TRENDS")
        progress.ramp?.let {
            appendLine(
                "- Training load (Banister TRIMP): this week ${it.acuteLoad.toInt()}, 4-week average " +
                    "${it.chronicLoad.toInt()}, ratio ${"%.2f".format(it.ratio)} — ${it.band.label}",
            )
        }
        appendLine("- Consecutive weeks trained: ${progress.streakWeeks}")
        val weeklyReps = progress.weeks.takeLast(6).map { it.reps }
        if (weeklyReps.any { it > 0 }) appendLine("- Weekly reps (last ${weeklyReps.size} weeks, oldest→newest): ${weeklyReps.joinToString(", ")}")
        if (progress.exercises.isNotEmpty()) {
            appendLine("- Per-movement history:")
            progress.exercises.take(8).forEach { appendLine("    ${exerciseLine(it)}") }
        }
        appendLine()

        appendLine("WRITE YOUR ANALYSIS")
        appendLine(
            "Around 400–500 words. Use short section headers, and bullets where they help readability. " +
                "Cover, in whatever order and weighting the data warrants:",
        )
        appendLine("- How this session fits the trend — progressing, plateauing, over-reaching, or detraining, with the numbers that show it.")
        appendLine("- A physiological read — what the heart rate, recovery, RPE-vs-heart-rate, and zone split say about fitness and fatigue right now.")
        appendLine("- Concrete programming — what to do in the next session and across the coming week: load, volume, exercise selection, movement balance.")
        appendLine("- One thing to watch.")
        appendLine(
            "Ground every claim in the numbers above; never invent data you weren't given. This is " +
                "coaching, not clinical advice — no medical diagnosis or injury prediction.",
        )
    }

    /** "Push-Up: 6 sessions · best +15 kg × 14 · last 3 Sep" — a movement's headline for the trend block. */
    private fun exerciseLine(e: ExerciseProgress): String = buildString {
        append("${e.exerciseName}: ${e.sessionCount} ${if (e.sessionCount == 1) "session" else "sessions"}, ${e.totalReps} reps")
        e.heaviest?.let { append("; best +${formatKg(it.addedWeightKg)} kg × ${it.reps}") }
        e.mostReps?.let { append("; most reps ${it.reps}") }
        append("; last ${dateOf(e.lastPerformedMs)}")
    }

    private fun volumeOf(session: PerformedSession): Double =
        session.setLogs.sumOf { log -> (session.plan.slot(log.slotId)?.addedWeightKg ?: 0.0) * log.reps }

    private fun fmt(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val m = totalSec / 60
        val sec = totalSec % 60
        return "${m}m ${sec}s"
    }

private val dayFmt = SimpleDateFormat("d MMM", Locale.getDefault())
private val dayTimeFmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
private fun dateOf(ms: Long) = dayFmt.format(Date(ms))
private fun dateTimeOf(ms: Long) = dayTimeFmt.format(Date(ms))
