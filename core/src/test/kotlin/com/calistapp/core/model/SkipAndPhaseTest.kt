package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skips, "do it later", and the warm-up / workout / cool-down phases. The regression these exist for:
 * a skipped exercise used to stay owed, so a one-round circuit looped back to it after the last
 * exercise and never reached the stretch.
 */
class SkipAndPhaseTest {

    private fun slot(id: String) = PlannedExercise(slotId = id, exerciseId = id, name = id.uppercase())

    private fun stretch(id: String) = PlannedExercise(
        slotId = id, exerciseId = id, name = id.uppercase(), targetSets = 1, measure = ExerciseMeasure.SECONDS,
        targetSeconds = 30, runsOnce = true, phase = SlotPhase.COOL_DOWN, routineName = "Upper-body cooldown",
    )

    private fun warm(id: String) = stretch(id).copy(phase = SlotPhase.WARM_UP, routineName = "Full-body raise")

    /** The user's Oct 25 session: a one-round circuit of five, then a four-stretch cool-down. */
    private val oneRound = WorkoutPlan(
        id = "p",
        exercises = listOf("pull", "dips", "squat", "push", "roll").map(::slot) + listOf("s1", "s2", "s3", "s4").map(::stretch),
        style = WorkoutStyle.CIRCUIT,
        rounds = 1,
    )

    /** Walks the workout like the controller: [skip] slots are skipped (resolved) instead of worked. */
    private fun WorkoutPlan.run(skip: Set<String> = emptySet(), deferOnce: Set<String> = emptySet()): List<String> {
        val done = mutableMapOf<String, Int>()
        val deferred = mutableSetOf<String>()
        val pendingDefer = deferOnce.toMutableSet()
        var cur: String? = exercises.first().slotId
        val order = mutableListOf<String>()
        while (order.size < 100) {
            val next = upcoming(cur, done, deferred) ?: break
            cur = next.slotId
            when (next.slotId) {
                in pendingDefer -> { pendingDefer -= next.slotId; deferred += next.slotId; continue }
                in skip -> order += "(${next.slotId})"
                else -> order += next.slotId
            }
            deferred -= next.slotId
            done[next.slotId] = (done[next.slotId] ?: 0) + 1
        }
        return order
    }

    @Test
    fun `a skipped exercise is not revisited and the cool-down follows the last exercise`() {
        assertEquals(
            listOf("pull", "(dips)", "squat", "push", "roll", "s1", "s2", "s3", "s4"),
            oneRound.run(skip = setOf("dips")),
        )
    }

    @Test
    fun `a skip counts toward the round, so the workout still ends`() {
        val resolved = resolvedSets(
            mapOf("pull" to 1, "squat" to 1, "push" to 1, "roll" to 1, "s1" to 1, "s2" to 1, "s3" to 1, "s4" to 1),
            listOf(SetSkip("dips", 1, 0L)),
        )
        assertNull(oneRound.nextUp("s4", resolved))
    }

    @Test
    fun `do it later moves a set to the end of its round, before the cool-down`() {
        assertEquals(
            listOf("pull", "squat", "push", "roll", "dips", "s1", "s2", "s3", "s4"),
            oneRound.run(deferOnce = setOf("dips")),
        )
    }

    @Test
    fun `in a multi-round circuit a skip covers this round only`() {
        val three = oneRound.copy(exercises = oneRound.slotsIn(SlotPhase.MAIN).take(3), rounds = 3)
        // Skip "dips" in round one only: it's worked in rounds two and three.
        val done = mutableMapOf<String, Int>()
        var cur: String? = "pull"
        val order = mutableListOf<String>()
        var skippedOnce = false
        while (true) {
            val next = three.upcoming(cur, done) ?: break
            cur = next.slotId
            if (next.slotId == "dips" && !skippedOnce) { skippedOnce = true; order += "(dips)" } else order += next.slotId
            done[next.slotId] = (done[next.slotId] ?: 0) + 1
        }
        assertEquals(listOf("pull", "(dips)", "squat", "pull", "dips", "squat", "pull", "dips", "squat"), order)
    }

    @Test
    fun `do it later in a split comes after the other exercises but before the stretch`() {
        val split = WorkoutPlan(
            id = "p",
            exercises = listOf(slot("a"), slot("b"), slot("c"), stretch("s1")),
            style = WorkoutStyle.BY_EXERCISE,
        )
        // Defer "a" before its first set: b and c run first, then a's three sets, then the stretch.
        val order = split.run(deferOnce = setOf("a"))
        assertEquals(listOf("b", "b", "b", "c", "c", "c", "a", "a", "a", "s1"), order)
    }

    @Test
    fun `phases come from the tag, and older plans infer them from run-once position`() {
        assertEquals(SlotPhase.COOL_DOWN, oneRound.phaseOf("s1"))
        assertEquals(SlotPhase.MAIN, oneRound.phaseOf("dips"))
        val legacy = WorkoutPlan(
            id = "p",
            exercises = listOf(slot("w").copy(runsOnce = true), slot("a"), slot("s").copy(runsOnce = true)),
        )
        assertEquals(SlotPhase.WARM_UP, legacy.phaseOf("w"))
        assertEquals(SlotPhase.MAIN, legacy.phaseOf("a"))
        assertEquals(SlotPhase.COOL_DOWN, legacy.phaseOf("s"))
    }

    @Test
    fun `holds and routine moves never count as reps`() {
        val plan = oneRound.copy(exercises = oneRound.exercises + slot("plank").copy(measure = ExerciseMeasure.SECONDS))
        assertTrue(plan.countsReps("push"))
        assertFalse(plan.countsReps("s1"))
        assertFalse(plan.countsReps("plank"))
        val logs = listOf(
            SetLog("push", "push", "PUSH", 1, reps = 10, startMs = 0, endMs = 1),
            SetLog("s1", "s1", "S1", 1, reps = 40, startMs = 0, endMs = 1),
            SetLog("plank", "plank", "PLANK", 1, reps = 45, startMs = 0, endMs = 1),
        )
        assertEquals(10, plan.repsIn(logs))
        assertFalse(Segment(SegmentType.ACTIVE, 0, 1, reps = 40, metabolics = ExerciseMetabolics(isometric = true)).countsReps)
    }

    // ---- Per-session edits ------------------------------------------------------------------------

    @Test
    fun `an exercise added mid-session lands before the cool-down`() {
        val added = oneRound.withAddedSlot(slot("lsit"))
        assertEquals(listOf("pull", "dips", "squat", "push", "roll", "lsit", "s1", "s2", "s3", "s4"), added.exercises.map { it.slotId })
    }

    @Test
    fun `reordering stays inside the slot's own phase`() {
        val moved = oneRound.withSlotMoved("roll", 0)
        assertEquals(listOf("roll", "pull", "dips", "squat", "push"), moved.slotsIn(SlotPhase.MAIN).map { it.slotId })
        assertEquals(listOf("s1", "s2", "s3", "s4"), moved.slotsIn(SlotPhase.COOL_DOWN).map { it.slotId })
    }

    @Test
    fun `retargeting changes the remaining sets, not the ones already done`() {
        val perSet = slot("a").copy(plannedSets = listOf(PlannedSet(10), PlannedSet(10), PlannedSet(10)))
        val plan = WorkoutPlan("p", exercises = listOf(perSet)).withTarget("a", fromSetIndex = 2, value = 8, weightKg = 5.0)
        assertEquals(listOf(10, 8, 8), plan.exercises.single().plannedSets.map { it.reps })
        assertEquals(5.0, plan.exercises.single().addedWeightKg, 0.0)
    }

    @Test
    fun `save-back keeps the workout block only and drops removed exercises`() {
        val original = oneRound.copy(exercises = oneRound.slotsIn(SlotPhase.MAIN))
        val edited = oneRound.withAddedSlot(slot("lsit"))
        val savable = edited.savableOver(original, removed = setOf("dips"))
        assertEquals(listOf("pull", "squat", "push", "roll", "lsit"), savable.exercises.map { it.slotId })
        assertEquals(listOf("Removed DIPS", "Added LSIT"), describeChanges(original, savable))
    }

    @Test
    fun `no edits means nothing to ask about`() {
        val original = oneRound.copy(exercises = oneRound.slotsIn(SlotPhase.MAIN))
        assertTrue(describeChanges(original, oneRound.savableOver(original, emptySet())).isEmpty())
    }

    @Test
    fun `a changed round count is one line, not one per exercise`() {
        val original = oneRound.copy(exercises = oneRound.slotsIn(SlotPhase.MAIN))
        val twoRounds = oneRound.copy(rounds = 2).savableOver(original, emptySet())
        assertEquals(listOf("Rounds: 1 → 2"), describeChanges(original, twoRounds))
    }
}
