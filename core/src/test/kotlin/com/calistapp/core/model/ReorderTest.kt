package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reordering a running session. The regression these exist for (Nov 8 device test): moving the first
 * exercise to the end before set 1 was ignored — the session kept the slot it had picked at the start,
 * so it played first anyway and the progress dashes lit it in its new, last position.
 */
class ReorderTest {

    private fun slot(id: String, sets: Int = 3) = PlannedExercise(slotId = id, exerciseId = id, name = id.uppercase(), targetSets = sets)

    private val split = WorkoutPlan(id = "s", exercises = listOf("pull", "dips", "push", "roll").map { slot(it) })

    private val circuit = WorkoutPlan(
        id = "c",
        exercises = listOf("pull", "dips", "push", "roll").map { slot(it) },
        style = WorkoutStyle.CIRCUIT,
        rounds = 2,
    )

    private fun ReorderEffect.anchorId() = (this as ReorderEffect.Apply).anchor?.slotId

    // ---- Before anything is done ------------------------------------------------------------------

    @Test
    fun `before set 1 the session starts from the top of the new order`() {
        val after = split.withSlotMoved("pull", 3)
        val effect = reorderEffect(split, after, "pull", currentSlotId = "pull", completed = emptyMap(), deferred = emptySet(), working = false)
        assertEquals("dips", effect.anchorId())
        // And with the anchor in place, "what's next" agrees — the live screen and the dashes read this.
        assertEquals(NextUp("dips", 1), after.upcoming("dips", emptyMap()))
    }

    @Test
    fun `before set 1 a circuit also starts from the top of the new order`() {
        val after = circuit.withSlotMoved("pull", 3)
        val effect = reorderEffect(circuit, after, "pull", "pull", emptyMap(), emptySet(), working = false)
        assertEquals("dips", effect.anchorId())
    }

    // ---- Exercise by exercise -----------------------------------------------------------------------

    @Test
    fun `moving the exercise you're resting on hands over to the first unfinished one`() {
        val done = mapOf("pull" to 1)
        val after = split.withSlotMoved("pull", 3)
        assertEquals("dips", reorderEffect(split, after, "pull", "pull", done, emptySet(), working = false).anchorId())
    }

    @Test
    fun `moving the exercise you're working waits for the set to finish`() {
        val after = split.withSlotMoved("pull", 3)
        val effect = reorderEffect(split, after, "pull", "pull", mapOf("pull" to 1), emptySet(), working = true)
        assertEquals(ReorderEffect.Apply(afterSet = true), effect)
    }

    @Test
    fun `moving another exercise keeps you on the current one until it's finished`() {
        val done = mapOf("pull" to 3, "dips" to 1)
        val after = split.withSlotMoved("roll", 0) // roll now sits above dips (and pull)
        val effect = reorderEffect(split, after, "roll", "dips", done, emptySet(), working = false)
        assertEquals(ReorderEffect.Apply(afterExercise = true), effect)
        // Once dips is finished, the first unfinished exercise in the new order is roll.
        assertEquals("roll", after.nextUp(null, done + ("dips" to 3), emptySet())?.slotId)
    }

    @Test
    fun `moving another exercise while resting after a finished one re-points at once`() {
        val done = mapOf("pull" to 3)
        val after = split.withSlotMoved("roll", 1)
        assertEquals("roll", reorderEffect(split, after, "roll", "pull", done, emptySet(), working = false).anchorId())
    }

    // ---- Circuits -------------------------------------------------------------------------------------

    @Test
    fun `a circuit only asks when an owed exercise lands behind you`() {
        // Round 1: pull and dips done, resting after dips. Push and roll still owed this round.
        val done = mapOf("pull" to 1, "dips" to 1)
        // Moving roll ahead of push keeps both ahead of you — just take the new order.
        val ahead = circuit.withSlotMoved("roll", 2)
        assertEquals(ReorderEffect.Apply(), reorderEffect(circuit, ahead, "roll", "dips", done, emptySet(), working = false))
        // Moving push to the top puts it behind dips while it's still owed this round — ask.
        val behind = circuit.withSlotMoved("push", 0)
        assertEquals(ReorderEffect.AskBehind(listOf("push")), reorderEffect(circuit, behind, "push", "dips", done, emptySet(), working = false))
    }

    @Test
    fun `moving exercises already done this round never asks`() {
        val done = mapOf("pull" to 1, "dips" to 1)
        val after = circuit.withSlotMoved("pull", 3)
        assertEquals(ReorderEffect.Apply(), reorderEffect(circuit, after, "pull", "dips", done, emptySet(), working = false))
    }

    @Test
    fun `next round keeps this round's old order, then the new one takes over`() {
        val done = mapOf("pull" to 1, "dips" to 1)
        val after = circuit.withSlotMoved("push", 0) // push, pull, dips, roll
        val hold = RoundOrder(round = 1, order = circuit.exercises.map { it.slotId })
        // This round carries on as before: push, then roll.
        assertEquals("push", after.nextUp("dips", done, roundOrder = hold)?.slotId)
        val done3 = done + ("push" to 1)
        assertEquals("roll", after.nextUp("push", done3, roundOrder = hold)?.slotId)
        // Round 2 runs the new order from its top.
        assertEquals("push", after.nextUp("roll", done3 + ("roll" to 1), roundOrder = hold)?.slotId)
    }

    @Test
    fun `later this round comes back round to the ones behind you, in the new order`() {
        val done = mapOf("pull" to 1, "dips" to 1)
        val after = circuit.withSlotMoved("push", 0) // push, pull, dips, roll
        assertEquals("roll", after.nextUp("dips", done)?.slotId)
        assertEquals("push", after.nextUp("roll", done + ("roll" to 1))?.slotId)
    }

    @Test
    fun `owed-behind is empty at the start of a round`() {
        assertTrue(circuit.owedBehind("roll", mapOf("pull" to 1, "dips" to 1, "push" to 1, "roll" to 1), working = false).isEmpty())
    }
}
