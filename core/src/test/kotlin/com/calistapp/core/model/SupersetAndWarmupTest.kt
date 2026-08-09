package com.calistapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `nextUp` is the one rule the phone and the watch each evaluate independently, so a disagreement
 * here doesn't show up as a wrong number — it shows up as two devices displaying different exercises
 * for the rest of the workout. Supersets add a branch to it, which is why this exists.
 */
class SupersetAndWarmupTest {

    private fun slot(id: String, name: String, sets: Int = 3, group: String? = null) =
        PlannedExercise(slotId = id, exerciseId = id, name = name, targetSets = sets, groupId = group)

    // A superset of pull-ups and dips, then squats on their own.
    private val plan = WorkoutPlan(
        id = "p",
        exercises = listOf(
            slot("pull", "Pull-Up", group = "A"),
            slot("dip", "Dip", group = "A"),
            slot("squat", "Squat"),
        ),
    )

    @Test
    fun `a superset rotates between its members instead of finishing one first`() {
        assertEquals("dip", plan.nextUp("pull", mapOf("pull" to 1))?.slotId)
        assertEquals("pull", plan.nextUp("dip", mapOf("pull" to 1, "dip" to 1))?.slotId)
    }

    @Test
    fun `the set number keeps counting per exercise across the rotation`() {
        val next = plan.nextUp("dip", mapOf("pull" to 1, "dip" to 1))!!

        assertEquals("pull", next.slotId)
        assertEquals(2, next.setIndex)
    }

    @Test
    fun `a member that is finished is skipped, the other carries on`() {
        // Dips done, pull-ups still owe a set.
        val next = plan.nextUp("pull", mapOf("pull" to 2, "dip" to 3))

        assertEquals("pull", next?.slotId)
        assertEquals(3, next?.setIndex)
    }

    @Test
    fun `once the whole group is done the plan moves past it`() {
        val next = plan.nextUp("dip", mapOf("pull" to 3, "dip" to 3))

        assertEquals("squat", next?.slotId)
        assertEquals(1, next?.setIndex)
    }

    @Test
    fun `an ungrouped exercise still finishes before the next one starts`() {
        // The split rule must survive the superset branch being added next to it.
        val next = plan.nextUp("squat", mapOf("pull" to 3, "dip" to 3, "squat" to 1))

        assertEquals("squat", next?.slotId)
        assertEquals(2, next?.setIndex)
    }

    @Test
    fun `a finished plan is finished`() {
        assertNull(plan.nextUp("squat", mapOf("pull" to 3, "dip" to 3, "squat" to 3)))
    }

    @Test
    fun `a plan with no groups behaves exactly as it did before`() {
        val plain = WorkoutPlan(
            id = "p",
            exercises = listOf(slot("a", "A"), slot("b", "B")),
        )

        assertEquals("a", plain.nextUp("a", mapOf("a" to 1))?.slotId)
        assertEquals("b", plain.nextUp("a", mapOf("a" to 3))?.slotId)
    }

    @Test
    fun `warm-up sets are the first ones and nothing else`() {
        val withWarmups = slot("pull", "Pull-Up", sets = 5).copy(warmupSets = 2)

        assertTrue(withWarmups.isWarmup(1))
        assertTrue(withWarmups.isWarmup(2))
        assertFalse(withWarmups.isWarmup(3))
        assertFalse(withWarmups.isWarmup(5))
    }

    @Test
    fun `no warm-ups means no set is one`() {
        assertFalse(slot("pull", "Pull-Up").isWarmup(1))
    }
}
