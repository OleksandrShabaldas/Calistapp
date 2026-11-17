package com.calistapp.core.insights

import com.calistapp.core.insights.phrasing.Phrasebook
import com.calistapp.core.insights.phrasing.Phrases
import com.calistapp.core.insights.phrasing.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasebookTest {

    private val pb = Phrasebook()

    private val slots: List<Slot> = Phrases::class.java.declaredFields
        .filter { Slot::class.java.isAssignableFrom(it.type) }
        .map { it.isAccessible = true; it.get(Phrases) as Slot }

    /** A value for every hole any template uses, so every template is renderable. */
    private val allVars: Map<String, String> = slots
        .flatMap { s -> s.templates.flatMap { t -> Regex("""\{([A-Za-z][A-Za-z0-9_]*)\}""").findAll(t).map { it.groupValues[1] }.toList() } }
        .distinct()
        .associateWith { name ->
            when (name) {
                "ex", "stuck", "slip", "name" -> "Pull-up"
                "unit" -> "reps"
                "period" -> "in four weeks"
                "when" -> "in September"
                "since" -> "September"
                "low", "group" -> "Legs"
                "lowNoun", "topNoun" -> "legs"
                "muscle" -> "Hamstrings"
                "muscleLower" -> "hamstrings"
                "day" -> "Friday"
                "missLine" -> "Both misses were Fridays"
                "periodNoun" -> "month"
                "prev" -> "the four weeks before"
                "sessions" -> "14 sessions"
                "stuckAt", "slipTo" -> "5 reps"
                "stuckRuns", "count", "missedWord", "weeksWord" -> "three"
                "delta" -> "2 reps"
                "sleep" -> "6h 10m"
                "ratio" -> "1.62"
                "date" -> "Aug 3"
                "dayNoun" -> "leg"
                else -> "12"
            }
        }

    @Test fun `every template renders with no holes left and a capital start`() {
        for (slot in slots) {
            val cands = pb.candidates(slot, allVars)
            assertEquals("every template of ${slot.key} should be renderable", slot.templates.size, cands.map { it.template }.distinct().size)
            for (c in cands) {
                val text = pb.render(slot.templates[c.template], allVars, c.choices)
                assertTrue("${c.id}: $text", !text.contains('{') && !text.contains('}'))
                assertTrue("${c.id}: $text", !text.contains("  "))
                assertTrue("${c.id}: $text", text.first().isUpperCase() || text.first().isDigit() || text.first() == '+')
            }
        }
    }

    @Test fun `card facts have plenty of distinct ways to be said`() {
        val factSlots = listOf(Phrases.STR_GOOD, Phrases.FIT_GOOD, Phrases.BAL_MIXED, Phrases.CON_GOOD, Phrases.REC_WATCH)
        for (slot in factSlots) {
            val n = pb.candidates(slot, allVars).size
            assertTrue("${slot.key} has only $n variants", n >= 20)
        }
    }

    @Test fun `a year of weekly issues never repeats a sentence while it remembers what it said`() {
        val seen = LinkedHashSet<String>()
        repeat(52) { week ->
            val p = pb.pick(Phrases.STR_GOOD, allVars, seed = week * 7919L, avoid = seen)!!
            assertTrue("repeat at week $week: ${p.text}", seen.add(p.id))
        }
    }

    @Test fun `the same seed picks the same words`() {
        assertEquals(pb.pick(Phrases.FIT_GOOD, allVars, 42L), pb.pick(Phrases.FIT_GOOD, allVars, 42L))
    }

    @Test fun `templates needing a missing value are skipped, not rendered with a hole`() {
        val p = pb.pick(Phrases.STR_MIXED, mapOf("ex" to "Dips", "to" to "12", "unit" to "reps", "from" to "10", "stuck" to "Pistol", "stuckAt" to "5 reps", "stuckRuns" to "four"), 7L)!!
        assertTrue(p.text, !p.text.contains('{') && !p.text.contains("slip"))
    }

    @Test fun `a decimal does not start a new sentence`() {
        for (c in pb.candidates(Phrases.REC_LOAD, mapOf("ratio" to "1.62"))) {
            val text = pb.render(Phrases.REC_LOAD.templates[c.template], mapOf("ratio" to "1.62"), c.choices)
            assertTrue(text, !Regex("""1\.62× [A-Z]""").containsMatchIn(text))
        }
    }
}
