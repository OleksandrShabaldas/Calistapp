package com.calistapp.app.data.ai

import com.calistapp.core.model.HrZone
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutSession
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a completed workout into an AI analysis. Owns the prompt engineering so screens/VMs
 * stay dumb, and can be extended later (progress-over-time analysis, weekly summaries, coaching).
 */
@Singleton
class InsightsRepository @Inject constructor(
    private val gemini: GeminiClient,
) {
    suspend fun analyzeSession(
        session: WorkoutSession,
        summary: SessionSummary,
        profile: UserProfile,
    ): AiResult = gemini.generate(buildSessionPrompt(session, summary, profile), AiModelTier.THINKING)

    private fun buildSessionPrompt(
        session: WorkoutSession,
        s: SessionSummary,
        p: UserProfile,
    ): String = buildString {
        appendLine("You are an expert exercise physiologist and personal coach reviewing ONE workout.")
        appendLine("Be specific, encouraging, and practical. Use the athlete's real numbers.")
        appendLine()
        appendLine("ATHLETE PROFILE:")
        appendLine("- Sex: ${p.sex}, Age: ${p.ageYears}, Weight: ${p.weightKg} kg, Height: ${p.heightCm} cm")
        appendLine("- Resting HR: ${p.restingHr} bpm, Max HR: ${p.effectiveMaxHr} bpm" +
            (p.vo2Max?.let { ", VO2max: $it ml/kg/min" } ?: ", VO2max: not provided"))
        appendLine()
        appendLine("SESSION: ${session.exerciseType.displayName}")
        appendLine("- Total time: ${fmt(s.totalDurationMs)} (active ${fmt(s.activeDurationMs)}, rest ${fmt(s.restDurationMs)})")
        appendLine("- Work-to-rest ratio: ${(s.activeRatio * 100).toInt()}% active")
        appendLine("- Calories: ${s.totalKcal.toInt()} kcal total (${s.activeKcal.toInt()} active, ${s.restKcal.toInt()} rest)")
        appendLine("- Heart rate: avg ${s.avgHr}, active-avg ${s.avgActiveHr}, peak ${s.peakHr}, min ${s.minHr} bpm")
        appendLine("- Time in HR zones:")
        HrZone.entries.forEach { z ->
            val ms = s.timeInZonesMs[z] ?: 0L
            if (ms > 0) appendLine("    ${z.label} (${z.name}): ${fmt(ms)}")
        }

        // The exercise-level detail is what makes the critique specific rather than generic
        // cardio advice — it's the difference between "good session" and "your pulling volume
        // is outpacing your pushing".
        if (s.perExercise.isNotEmpty()) {
            appendLine("- Total reps logged: ${s.totalReps}")
            appendLine("- Per exercise (energy attributed by the calorie engine):")
            s.perExercise.forEach { e ->
                appendLine(
                    "    ${e.exerciseName}: ${e.sets} sets, ${e.reps} reps, " +
                        "${e.kcal.toInt()} kcal, ${fmt(e.activeDurationMs)} under load",
                )
            }
        }
        if (!session.plan.isEmpty) {
            appendLine("- Planned vs performed:")
            session.plan.exercises.forEach { slot ->
                val done = session.setLogs.filter { it.slotId == slot.slotId }
                appendLine(
                    "    ${slot.name}: planned ${slot.targetLabel}, " +
                        "performed ${done.size} sets (${done.sumOf { it.reps }} reps)",
                )
            }
        }
        // The subjective side. Where RPE and heart rate disagree is the interesting part — a hard
        // session that read easy, or an easy one that didn't.
        session.rpe?.let { appendLine("- Rated perceived exertion: $it/10 (Borg CR10)") }
        if (session.notes.isNotBlank()) appendLine("- Athlete notes: ${session.notes}")
        appendLine()
        appendLine("Respond in this exact structure using short paragraphs and bullet points:")
        appendLine("1. Overall assessment (2-3 sentences)")
        appendLine("2. What went well")
        appendLine("3. What to improve")
        appendLine("4. Two concrete recommendations for next session")
        appendLine("Keep it under 220 words. Do not invent data you weren't given.")
    }

    private fun fmt(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val m = totalSec / 60
        val sec = totalSec % 60
        return "${m}m ${sec}s"
    }
}
