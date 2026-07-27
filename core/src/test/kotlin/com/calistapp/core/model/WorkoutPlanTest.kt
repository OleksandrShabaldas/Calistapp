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
}
