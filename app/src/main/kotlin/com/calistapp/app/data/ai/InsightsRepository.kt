package com.calistapp.app.data.ai

import com.calistapp.core.model.ExerciseMeasure
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SkipReason
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.model.formatKg
import com.calistapp.core.model.isUnilateral
import com.calistapp.core.model.repsIn
import com.calistapp.core.model.withSessionTiming
import com.calistapp.core.model.workingKcal
import com.calistapp.core.progress.ExerciseProgress
import com.calistapp.core.progress.PerformedSession
import com.calistapp.core.progress.phaseSummary
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
    summary: SessionSummary,
    p: UserProfile,
    history: List<PerformedSession>,
    now: Long,
): String = buildString {
        val s = summary.withSessionTiming(session.segments, session.endMs, session.plan)
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
        appendLine("- Total time ${fmt(s.totalDurationMs)} (warm-up ${fmt(s.warmupDurationMs)}, working sets ${fmt(s.activeDurationMs)}, between-set rest ${fmt(s.restDurationMs)}" + (if (s.coolDownDurationMs > 0) ", cool-down ${fmt(s.coolDownDurationMs)}" else "") + "); ${(s.activeRatio * 100).toInt()}% working")
        appendLine("- Warm-up (preparation and any guided warm-up before the first set) and cool-down are neither work nor between-set rest. Do not include them when assessing rest length or training density.")
        val workKcal = s.workingKcal(session.plan)
        appendLine("- Calories ${s.totalKcal.toInt()} kcal (${workKcal.toInt()} in the working sets, ${(s.totalKcal - workKcal).toInt()} across warm-up, cool-down and between-set recovery; non-working time uses the conservative estimate)")
        appendLine("- Heart rate: avg ${s.avgHr}, active-avg ${s.avgActiveHr}, peak ${s.peakHr}, min ${s.minHr} bpm")
        val zones = HrZone.entries.filter { (s.timeInZonesMs[it] ?: 0L) > 0 }
        if (zones.isNotEmpty()) {
            appendLine("- Time in zones: " + zones.joinToString(", ") { "${it.label} ${fmt(s.timeInZonesMs[it] ?: 0L)}" })
        }
        s.hrRecovery?.let { appendLine("- Heart-rate recovery: ${it.meanDropBpm} bpm/min (best ${it.bestDropBpm}, across ${it.measuredRests} rests)") }
        session.rpe?.let { appendLine("- Rated perceived exertion: $it/10 (Borg CR10)") }
        // The workout proper only — the warm-up and cool-down are described as their own blocks below,
        // never as exercises (their holds used to be listed here with seconds counted as reps).
        val perExercise = s.perExercise.filter { session.plan.phaseOf(it.slotId) == SlotPhase.MAIN }
        if (perExercise.isNotEmpty()) {
            appendLine("- Total reps ${session.plan.repsIn(session.setLogs)}. Per exercise:")
            perExercise.forEach { e ->
                appendLine("    ${e.exerciseName}: ${e.sets} sets, ${e.reps} reps${perSide(e.exerciseName, e.reps)}, ${e.kcal.toInt()} kcal, ${fmt(e.activeDurationMs)} under load")
            }
            if (perExercise.any { isUnilateral(it.exerciseName) }) {
                appendLine(
                    "- Movements marked PER-SIDE are worked one side at a time, and their reps (planned " +
                        "and performed) are the TOTAL across both sides — \"40 reps\" means 20 per leg/arm. " +
                        "Always reason and prescribe per side for these (e.g. \"3 × 20 per leg\"); never " +
                        "read the total as one side's count.",
                )
            }
        }
        if (!session.plan.isEmpty) {
            val plan = session.plan
            val main = plan.slotsIn(SlotPhase.MAIN)
            appendLine(
                if (plan.isCircuit) {
                    "- Format: CIRCUIT — ${plan.rounds} ${if (plan.rounds == 1) "round" else "rounds"} " +
                        "through ${main.size} exercises, one set of each per round. So the " +
                        "planned sets per exercise IS the round count (${plan.rounds}); there is no " +
                        "separate per-exercise sets target to fall short of."
                } else {
                    "- Format: exercise-by-exercise (all sets of one movement, then the next)."
                },
            )
            appendLine("- Planned vs performed:")
            main.forEach { slot ->
                val done = session.setLogs.filter { it.slotId == slot.slotId }
                val reps = done.sumOf { it.reps }
                val skipped = session.skips.filter { it.slotId == slot.slotId }
                val skipNote = when {
                    skipped.any { it.reason == SkipReason.REMOVED } -> " — the athlete REMOVED it from this session"
                    skipped.isNotEmpty() -> " — ${skipped.size} ${if (skipped.size == 1) "set" else "sets"} deliberately SKIPPED by the athlete"
                    else -> ""
                }
                val unit = if (slot.measure == ExerciseMeasure.SECONDS) "s held" else " reps"
                appendLine("    ${slot.name}: planned ${plannedLabel(plan, slot)}${if (isUnilateral(slot.name)) " (PER-SIDE, total of both sides)" else ""}, performed ${done.size} sets ($reps$unit${perSide(slot.name, reps)})$skipNote")
            }
            if (session.skips.any { s -> main.any { it.slotId == s.slotId } }) {
                appendLine(
                    "- Skipped or removed sets were the athlete's choice mid-session, not failed sets: don't " +
                        "read them as missed reps, but you may comment on the pattern if it recurs.",
                )
            }
            phaseLine(session, SlotPhase.WARM_UP)?.let(::appendLine)
            phaseLine(session, SlotPhase.COOL_DOWN)?.let(::appendLine)
        }
        if (session.notes.isNotBlank()) appendLine("- Athlete's note: ${session.notes}")
        appendLine()

        if (recent.isNotEmpty()) {
            appendLine("RECENT TRAINING (last ${recent.size} sessions, newest first)")
            recent.forEach { r ->
                val vol = volumeOf(r)
                appendLine(
                    "- ${dateOf(r.startMs)}: ${fmt(r.totalDurationMs)}, ${r.plan.repsIn(r.setLogs)} reps" +
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
        appendLine()

        appendLine("FORMATTING")
        appendLine(
            "- Markdown: **bold** for key terms and figures, *italic* for lighter emphasis. Write the " +
                "word \"at\" in full — never the \"@\" sign (e.g. \"3 sets of 10 at +15 kg\").",
        )
        appendLine(
            "- After the analysis, output a line containing only ===GLOSSARY===. Then, for EVERY " +
                "technical or jargon term you used that a non-specialist might not know — e.g. detraining, " +
                "Banister TRIMP, acute:chronic ratio, time under tension (TUT), eccentric, RPE, TRIMP — " +
                "write one line as:  term :: a plain one-sentence explanation in everyday language.",
        )
        appendLine(
            "- Use the exact spelling the term appears with in your text, define each term once, and keep " +
                "each explanation to a single short sentence. The glossary is required, even if short.",
        )
    }

    /**
     * The planned target for a slot, phrased correctly for the workout style. For an exercise-by-exercise
     * split, [PlannedExercise.targetLabel] already reads right ("3 × 10"). For a circuit it does NOT:
     * `sets()` returns the slot's ignored `targetSets` (a default 3), not the round count that actually
     * drives a circuit — which is what made the coach report "1 of 3 planned" for a one-round circuit.
     * Here the planned set count is the round count, and the reps are the per-round target.
     */
    private fun plannedLabel(plan: WorkoutPlan, slot: PlannedExercise): String {
        if (!plan.isCircuit || slot.runsOnce) return slot.targetLabel
        val sets = plan.targetSetsFor(slot.slotId)
        val unit = if (slot.measure == ExerciseMeasure.SECONDS) "${slot.targetSeconds}s" else "${slot.targetReps} reps"
        return "$sets ${if (sets == 1) "round" else "rounds"} × $unit"
    }

    /** "Push-Up: 6 sessions · best +15 kg × 14 · last 3 Sep" — a movement's headline for the trend block. */
    private fun exerciseLine(e: ExerciseProgress): String = buildString {
        append("${e.exerciseName}: ${e.sessionCount} ${if (e.sessionCount == 1) "session" else "sessions"}, ${e.totalReps} reps")
        if (isUnilateral(e.exerciseName)) append(" (PER-SIDE: totals of both sides)")
        e.heaviest?.let { append("; best +${formatKg(it.addedWeightKg)} kg × ${it.reps}") }
        e.mostReps?.let { append("; most reps ${it.reps}") }
        append("; last ${dateOf(e.lastPerformedMs)}")
    }

    /** ", PER-SIDE: ≈20 per side" for a one-side-at-a-time movement, so the total isn't read as one side. */
    private fun perSide(name: String, reps: Int): String =
        if (reps > 0 && isUnilateral(name)) " — PER-SIDE: total of both sides, ≈${(reps + 1) / 2} per side" else ""

    private fun volumeOf(session: PerformedSession): Double =
        session.setLogs
            .filter { session.plan.countsReps(it.slotId) }
            .sumOf { log -> (session.plan.slot(log.slotId)?.addedWeightKg ?: 0.0) * log.reps }

    /**
     * The warm-up or cool-down as one line for the coach: what it was, how much of it was done, and —
     * for a cool-down — how far heart rate settled across it. Null when the session had none.
     */
    private fun phaseLine(session: WorkoutSession, phase: SlotPhase): String? {
        val p = session.phaseSummary(phase) ?: return null
        val word = if (phase == SlotPhase.COOL_DOWN) "stretches" else "moves"
        val name = p.routineName?.let { " \"$it\"" }.orEmpty()
        if (p.done == 0) return "- ${phase.label.uppercase()}$name (${p.moves} $word): skipped entirely."
        val hr = when {
            phase == SlotPhase.COOL_DOWN && p.startHr != null && p.endHr != null ->
                "; heart rate went from ${p.startHr} to ${p.endHr} bpm across it"
            p.avgHr != null -> "; average heart rate ${p.avgHr} bpm"
            else -> ""
        }
        return "- ${phase.label.uppercase()}$name: ${p.done} of ${p.moves} $word held for ${fmt(p.heldMs)} total$hr. " +
            "A separate phase — light mobility, not training: never count it as exercises, sets, reps or volume."
    }

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
