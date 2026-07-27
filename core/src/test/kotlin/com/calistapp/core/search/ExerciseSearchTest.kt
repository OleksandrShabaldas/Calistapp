package com.calistapp.core.search

import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {

    private fun ex(
        name: String,
        primary: List<String> = emptyList(),
        secondary: List<String> = emptyList(),
        equipment: List<String> = emptyList(),
        bodyPart: BodyPart = BodyPart.OTHER,
        efficiency: Int = 0,
    ) = Exercise(
        id = name.lowercase().replace(" ", "_"),
        name = name,
        bodyPart = bodyPart,
        difficulty = Difficulty.BEGINNER,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        equipment = equipment,
        efficiency = efficiency,
    )

    /** Same shape as the real dataset: a `force` of push/pull on nearly every entry. */
    private fun ex(name: String, force: String, primary: List<String>, bodyPart: BodyPart) =
        ex(name, primary = primary, bodyPart = bodyPart).copy(force = force)

    private val gallery = listOf(
        ex("Pull-Up", primary = listOf("Lats"), secondary = listOf("Biceps"), bodyPart = BodyPart.BACK),
        ex("Weighted Pull-Up", primary = listOf("Lats"), bodyPart = BodyPart.BACK),
        ex("Wide-Grip Pull-Up", primary = listOf("Lats"), bodyPart = BodyPart.BACK),
        ex("Close-Grip Weighted Pull-Up", primary = listOf("Lats"), bodyPart = BodyPart.BACK),
        ex("Chin-Up", primary = listOf("Biceps"), bodyPart = BodyPart.BACK),
        ex("Push-Up", primary = listOf("Chest"), secondary = listOf("Triceps"), bodyPart = BodyPart.CHEST),
        ex("Barbell Squat", primary = listOf("Quadriceps"), equipment = listOf("Barbell"), bodyPart = BodyPart.LEGS),
        ex("Crunch", primary = listOf("Abdominals"), bodyPart = BodyPart.CORE),
        ex("Hanging Leg Raise", primary = listOf("Abdominals"), bodyPart = BodyPart.CORE),
        // Pulling movements that share force="pull" but have nothing to do with a pull-up.
        ex("Barbell Row", force = "pull", primary = listOf("Middle Back"), bodyPart = BodyPart.BACK),
        ex("Seated Cable Row", force = "pull", primary = listOf("Middle Back"), bodyPart = BodyPart.BACK),
        ex("Face Pull", force = "pull", primary = listOf("Shoulders"), bodyPart = BodyPart.SHOULDERS),
    )

    private fun names(query: String) = ExerciseSearch.search(gallery, query).map { it.exercise.name }

    // ---- The headline problem: spelling shouldn't matter ---------------------------------------

    @Test
    fun `pullup finds pull-up despite the missing hyphen`() {
        val results = names("pullup")
        assertTrue("Expected Pull-Up in $results", results.contains("Pull-Up"))
        assertEquals("Pull-Up", results.first())
    }

    @Test
    fun `spacing and casing variants all resolve to the same exercise`() {
        listOf("pull up", "Pull-Up", "PULLUP", "pUlL uP").forEach { q ->
            assertEquals("Query '$q' should lead with Pull-Up", "Pull-Up", names(q).first())
        }
    }

    @Test
    fun `pushup and situp style queries work too`() {
        assertEquals("Push-Up", names("pushup").first())
    }

    // ---- Relevance ordering --------------------------------------------------------------------

    @Test
    fun `plain movement ranks above its weighted and grip variations`() {
        val results = names("pullup")
        val plain = results.indexOf("Pull-Up")
        val weighted = results.indexOf("Weighted Pull-Up")
        val wide = results.indexOf("Wide-Grip Pull-Up")
        val closeWeighted = results.indexOf("Close-Grip Weighted Pull-Up")

        assertTrue("Plain should come first, got $results", plain == 0)
        assertTrue("Weighted should precede the longer close-grip variant, got $results", weighted < closeWeighted)
        assertTrue("Grip variants should follow the plain movement, got $results", wide > plain)
    }

    @Test
    fun `multi-word queries match in any order`() {
        val results = names("wide pull up")
        assertEquals("Wide-Grip Pull-Up", results.first())
    }

    // ---- Typos and shorthand -------------------------------------------------------------------

    @Test
    fun `a single typo still finds the exercise`() {
        assertTrue("Expected a hit for 'squt'", names("squt").contains("Barbell Squat"))
    }

    @Test
    fun `gym shorthand maps onto dataset vocabulary`() {
        assertTrue("'abs' should find abdominal work", names("abs").contains("Crunch"))
        assertTrue("'quads' should find quadriceps work", names("quads").contains("Barbell Squat"))
    }

    @Test
    fun `muscle names find exercises that train them`() {
        val results = names("lats")
        assertTrue("Expected lat exercises, got $results", results.contains("Pull-Up"))
        assertTrue("Push-Up doesn't train lats", !results.contains("Push-Up"))
    }

    @Test
    fun `equipment is searchable`() {
        assertTrue(names("barbell").contains("Barbell Squat"))
    }

    // ---- Non-matches ---------------------------------------------------------------------------

    @Test
    fun `a specific name query does not drag in everything sharing its force`() {
        // Regression: expanding "pullup" into "pull" + "up" matched force="pull" on most of the
        // gallery, turning a dozen-result query into hundreds.
        val results = names("pullup")
        assertTrue("Barbell Row is not a pull-up, got $results", !results.contains("Barbell Row"))
        assertTrue("Face Pull is not a pull-up, got $results", !results.contains("Face Pull"))
        assertTrue("Expected a focused result set, got ${results.size}: $results", results.size <= 5)
    }

    @Test
    fun `unrelated query returns nothing rather than everything`() {
        assertTrue("Expected no hits, got ${names("kayaking")}", names("kayaking").isEmpty())
    }

    @Test
    fun `blank query returns the full gallery untouched`() {
        assertEquals(gallery.size, ExerciseSearch.search(gallery, "   ").size)
    }
}
