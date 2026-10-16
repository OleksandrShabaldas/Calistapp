package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {

    private fun slot(id: String, sets: Int = 3) =
        PlannedExercise(slotId = id, exerciseId = id, name = id.uppercase(), targetSets = sets)

    private val split = WorkoutPlan(
        id = "p",
        exercises = listOf(slot("a"), slot("b"), slot("c")),
        style = WorkoutStyle.BY_EXERCISE,
    )

    private val circuit = WorkoutPlan(
        id = "p",
        exercises = listOf(slot("a"), slot("b"), slot("c")),
        style = WorkoutStyle.CIRCUIT,
        rounds = 3,
    )

    // ---- Split: finish one exercise before moving on -------------------------------------------

    @Test
    fun `split stays on the same exercise until its sets are done`() {
        assertEquals(NextUp("a", 2), split.nextUp("a", mapOf("a" to 1)))
        assertEquals(NextUp("a", 3), split.nextUp("a", mapOf("a" to 2)))
    }

    @Test
    fun `split moves to the next exercise once the current one is complete`() {
        assertEquals(NextUp("b", 1), split.nextUp("a", mapOf("a" to 3)))
    }

    @Test
    fun `split finishes when every exercise has had all its sets`() {
        assertNull(split.nextUp("c", mapOf("a" to 3, "b" to 3, "c" to 3)))
    }

    // ---- Opening block: before any set, the selected slot is what runs ---------------------------

    @Test
    fun `circuit opening works the selected exercise, not the one after it`() {
        // Regression: the live screen asked nextUp here and announced exercise 2 as "up next".
        assertEquals(NextUp("a", 1), circuit.upcoming("a", emptyMap()))
        assertEquals(NextUp("b", 1), circuit.upcoming("b", emptyMap()))
    }

    @Test
    fun `once a set is banked upcoming rotates like nextUp`() {
        assertEquals(circuit.nextUp("a", mapOf("a" to 1)), circuit.upcoming("a", mapOf("a" to 1)))
        assertEquals(split.nextUp("a", mapOf("a" to 3)), split.upcoming("a", mapOf("a" to 3)))
    }

    // ---- Circuit: one set of each, then round again ---------------------------------------------

    @Test
    fun `circuit advances to the next exercise after a single set`() {
        assertEquals(NextUp("b", 1), circuit.nextUp("a", mapOf("a" to 1)))
        assertEquals(NextUp("c", 1), circuit.nextUp("b", mapOf("a" to 1, "b" to 1)))
    }

    @Test
    fun `circuit wraps back to the first exercise for the next round`() {
        val afterRoundOne = mapOf("a" to 1, "b" to 1, "c" to 1)
        assertEquals(NextUp("a", 2), circuit.nextUp("c", afterRoundOne))
    }

    @Test
    fun `circuit finishes after the configured number of rounds`() {
        val allDone = mapOf("a" to 3, "b" to 3, "c" to 3)
        assertNull(circuit.nextUp("c", allDone))
    }

    @Test
    fun `circuit keeps going in order after a skipped exercise, and comes back for it`() {
        // "b" was skipped in round one. A circuit runs in order, so the next exercise is still "a"
        // — jumping backwards would break the rhythm the style exists for.
        val skipped = mapOf("a" to 1, "c" to 1)
        assertEquals(NextUp("a", 2), circuit.nextUp("c", skipped))

        // But "b" is not lost: it's picked up in its normal position on the way round.
        assertEquals(NextUp("b", 1), circuit.nextUp("a", skipped + ("a" to 2)))
    }

    @Test
    fun `circuit does not offer an exercise that has had all its rounds`() {
        // "a" is done for the whole workout; the round-two pass must skip over it.
        val aFinished = mapOf("a" to 3, "b" to 2, "c" to 2)
        assertEquals(NextUp("b", 3), circuit.nextUp("c", aFinished))
    }

    @Test
    fun `circuit round number tracks the least-completed exercise`() {
        assertEquals(1, circuit.roundOf(emptyMap()))
        assertEquals(1, circuit.roundOf(mapOf("a" to 1, "b" to 1)))
        assertEquals(2, circuit.roundOf(mapOf("a" to 1, "b" to 1, "c" to 1)))
    }

    // ---- Set totals -------------------------------------------------------------------------------

    @Test
    fun `total sets reflect the style`() {
        assertEquals(9, split.totalSets) // 3 exercises × 3 sets each
        assertEquals(9, circuit.totalSets) // 3 exercises × 3 rounds
        assertEquals(6, circuit.copy(rounds = 2).totalSets)
    }

    @Test
    fun `circuit target sets come from rounds not the per-exercise count`() {
        val mixed = circuit.copy(exercises = listOf(slot("a", sets = 7), slot("b", sets = 1)))
        assertEquals(3, mixed.targetSetsFor("a"))
        assertEquals(3, mixed.targetSetsFor("b"))
    }

    // ---- Added weight ------------------------------------------------------------------------------

    @Test
    fun `added weight shows in the name and target label`() {
        val weighted = slot("pullup").copy(
            name = "Pull-Up",
            metabolics = ExerciseMetabolics(externalLoadKg = 20.0),
        )
        assertEquals("Pull-Up +20 kg", weighted.displayName)
        assertTrue(weighted.isWeighted)
        assertTrue("Label should mention the load", weighted.targetLabel.contains("+20 kg"))
    }

    @Test
    fun `unweighted exercises read plainly`() {
        val plain = slot("pushup").copy(name = "Push-Up")
        assertEquals("Push-Up", plain.displayName)
        assertEquals("3 × 10", plain.targetLabel)
    }

    @Test
    fun `fractional plate weights keep one decimal`() {
        val weighted = slot("dip").copy(
            name = "Dip",
            metabolics = ExerciseMetabolics(externalLoadKg = 2.5),
        )
        assertEquals("Dip +2.5 kg", weighted.displayName)
    }

    // ---- Run-once blocks (warm-up / stretch) in a circuit --------------------------------------

    private fun once(id: String) = slot(id, sets = 1).copy(runsOnce = true)

    private val bracketed = WorkoutPlan(
        id = "p",
        exercises = listOf(once("w1"), once("w2"), slot("a"), slot("b"), once("s1")),
        style = WorkoutStyle.CIRCUIT,
        rounds = 2,
    )

    /** Walks the whole workout the way the controller does, returning the order sets are worked. */
    private fun WorkoutPlan.run(): List<String> {
        val done = mutableMapOf<String, Int>()
        var cur: String? = exercises.first().slotId
        val order = mutableListOf<String>()
        while (true) {
            val next = upcoming(cur, done) ?: break
            order += next.slotId
            done[next.slotId] = (done[next.slotId] ?: 0) + 1
            cur = next.slotId
        }
        return order
    }

    @Test
    fun `a circuit runs the warm-up once before round 1 and the stretch once after the last round`() {
        assertEquals(listOf("w1", "w2", "a", "b", "a", "b", "s1"), bracketed.run())
    }

    @Test
    fun `run-once blocks don't count toward rounds`() {
        assertEquals(2 * 2 + 3, bracketed.totalSets)
        assertEquals(1, bracketed.targetSetsFor("w1"))
        assertEquals(1, bracketed.roundOf(mapOf("w1" to 1, "w2" to 1)))
        assertEquals(2, bracketed.roundOf(mapOf("w1" to 1, "w2" to 1, "a" to 1, "b" to 1)))
        // During the trailing stretch the round read-out stays on the last round, not "3 / 2".
        assertEquals(2, bracketed.roundOf(mapOf("w1" to 1, "w2" to 1, "a" to 2, "b" to 2)))
    }

    @Test
    fun `a plain circuit still rotates exactly as before`() {
        assertEquals(listOf("a", "b", "c", "a", "b", "c", "a", "b", "c"), circuit.run())
    }

    @Test
    fun `circuit target label counts rounds, not the slot's ignored set count`() {
        val oneRound = circuit.copy(rounds = 1)
        assertEquals("1 × 10", oneRound.targetLabelFor(oneRound.exercises.first()))
        assertEquals("3 × 10", split.targetLabelFor(split.exercises.first()))
    }
}
