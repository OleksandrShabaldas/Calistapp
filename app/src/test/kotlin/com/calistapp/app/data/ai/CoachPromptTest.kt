package com.calistapp.app.data.ai

import com.calistapp.core.model.ExerciseMetabolics
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.PlannedExercise
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.Sex
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.UserProfile
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.model.WorkoutStyle
import com.calistapp.core.progress.PerformedSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptTest {

    private val day = 86_400_000L
    private val now = 1_725_000_000_000L // fixed instant so week bucketing is deterministic

    private fun plan(weightKg: Double) = WorkoutPlan(
        id = "p",
        exercises = listOf(
            PlannedExercise(slotId = "s2", exerciseId = "push-up", name = "Push-Up", metabolics = ExerciseMetabolics(externalLoadKg = weightKg)),
        ),
    )

    private fun perf(id: String, startMs: Long, reps: Int, weightKg: Double) = PerformedSession(
        id = id, startMs = startMs, kcal = 120, activeDurationMs = 300_000,
        setLogs = listOf(SetLog("s2", "push-up", "Push-Up", 1, reps, 0, startMs, startMs + 45_000, weightKg)),
        plan = plan(weightKg), avgHr = 132, totalDurationMs = 600_000, rpe = 7, hrRecoveryMeanDrop = 20,
    )

    @Test
    fun `prompt carries the latest session, recent history and trends`() {
        val cur = perf("cur", now - 60_000, 14, 15.0)
        val history = listOf(
            cur,
            perf("p1", now - 8 * day, 12, 10.0),
            perf("p2", now - 15 * day, 13, 7.5),
        )
        val profile = UserProfile(sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0, restingHr = 55, maxHr = 190)
        val session = WorkoutSession(
            id = "cur", exerciseType = ExerciseType.CALISTHENICS, startMs = cur.startMs, endMs = cur.startMs + 600_000,
            plan = plan(15.0), setLogs = cur.setLogs, rpe = 7, notes = "grip gave out early",
        )
        val summary = SessionSummary.EMPTY.copy(
            totalKcal = 120.0, activeKcal = 70.0, restKcal = 50.0,
            activeDurationMs = 200_000, restDurationMs = 100_000,
            avgHr = 132, peakHr = 160, avgActiveHr = 142, minHr = 100, totalReps = 28,
        )

        val prompt = buildCoachPrompt(session, summary, profile, history, now)

        // The three context blocks that make it a coach rather than a summariser.
        assertTrue(prompt.contains("LATEST SESSION"))
        assertTrue(prompt.contains("RECENT TRAINING"))
        assertTrue(prompt.contains("TRENDS"))
        assertTrue(prompt.contains("Per-movement history"))
        // The athlete's real data flows through.
        assertTrue(prompt.contains("Push-Up"))
        assertTrue(prompt.contains("grip gave out early"))
        // Asks for an in-depth analysis, not a short summary.
        assertTrue(prompt.contains("400"))
        // The current session isn't duplicated into the "recent training" list.
        val recentBlock = prompt.substringAfter("RECENT TRAINING").substringBefore("TRENDS")
        assertFalse(recentBlock.contains("cur"))
    }

    @Test
    fun `a one-round circuit is described as a circuit and its planned sets are the round count`() {
        // A circuit's slots keep the default targetSets of 3, but the real per-exercise target is the
        // round count — reading targetSets made the coach report "1 of 3 planned" for a 1-round circuit.
        val circuitPlan = WorkoutPlan(
            id = "c",
            exercises = listOf(
                PlannedExercise(slotId = "s1", exerciseId = "dip", name = "Dip", targetSets = 3, targetReps = 8),
                PlannedExercise(slotId = "s2", exerciseId = "push-up", name = "Push-Up", targetSets = 3, targetReps = 12),
            ),
            style = WorkoutStyle.CIRCUIT,
            rounds = 1,
        )
        val profile = UserProfile(sex = Sex.MALE, ageYears = 30, weightKg = 75.0, heightCm = 178.0, restingHr = 55, maxHr = 190)
        val session = WorkoutSession(
            id = "cur", exerciseType = ExerciseType.CALISTHENICS, startMs = now - 60_000, endMs = now,
            plan = circuitPlan,
            setLogs = listOf(SetLog("s1", "dip", "Dip", 1, 7, 0, now - 60_000, now - 30_000)),
        )
        val summary = SessionSummary.EMPTY.copy(totalReps = 7)

        val prompt = buildCoachPrompt(session, summary, profile, emptyList(), now)

        // The coach is told it's a circuit and how the round count maps to sets.
        assertTrue(prompt.contains("Format: CIRCUIT"))
        // The planned figure is the round count, not the default "3 × 8".
        assertTrue(prompt.contains("Dip: planned 1 round × 8 reps"))
        assertFalse(prompt.contains("3 × 8"))
    }
}
