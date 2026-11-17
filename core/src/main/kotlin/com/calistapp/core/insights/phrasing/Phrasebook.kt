package com.calistapp.core.insights.phrasing

import java.util.Random

/** A rendered sentence plus the id of exactly which template and word choices produced it. */
data class Phrase(val id: String, val text: String)

/**
 * One fact, many ways to say it. A template is plain text with two kinds of holes:
 *
 * - `{name}`: a value the caller supplies ("Pull-ups", "14"). A template that names a value the
 *   caller didn't supply is skipped, so a slot can mix sentences that need more or less data.
 * - `{~group}`: a word chosen from [Phrasebook.synonyms] ("rose", "climbed", "went up").
 *
 * Every combination of template x synonym choices is a distinct candidate with its own id, which is
 * what makes "almost never the same sentence twice" possible: a slot with ten templates and two
 * synonym holes of six words each has hundreds of candidates before the numbers even change.
 */
class Slot(val key: String, val templates: List<String>) {
    init {
        require(templates.isNotEmpty()) { "slot $key has no templates" }
    }
}

/**
 * Picks and renders a sentence for a [Slot].
 *
 * Selection is deterministic for a given seed (the same report renders the same words every time it
 * is opened), skips any candidate whose id is in `avoid` (the phrases the user saw recently), and
 * only falls back to a seen candidate when every one has been used.
 */
class Phrasebook(private val synonyms: Map<String, List<String>> = Synonyms.ALL) {

    fun pick(slot: Slot, vars: Map<String, String>, seed: Long, avoid: Set<String> = emptySet()): Phrase? {
        val candidates = candidates(slot, vars)
        if (candidates.isEmpty()) return null
        val shuffled = candidates.shuffled(Random(seed xor slot.key.hashCode().toLong()))
        val chosen = shuffled.firstOrNull { it.id !in avoid } ?: shuffled.first()
        return Phrase(chosen.id, render(slot.templates[chosen.template], vars, chosen.choices))
    }

    /** Every renderable candidate for [slot] given [vars]. Exposed for tests. */
    internal fun candidates(slot: Slot, vars: Map<String, String>): List<Candidate> {
        val out = ArrayList<Candidate>()
        slot.templates.forEachIndexed { t, template ->
            val holes = HOLE.findAll(template).map { it.groupValues[1] }.toList()
            val needed = holes.filterNot { it.startsWith("~") }
            if (needed.any { vars[it].isNullOrBlank() }) return@forEachIndexed
            val groups = holes.filter { it.startsWith("~") }.map { it.drop(1) }.distinct()
            val sizes = groups.map { g -> synonyms[g]?.size ?: error("unknown synonym group ~$g in ${slot.key}") }
            for (combo in combos(sizes)) {
                val choices = groups.zip(combo).toMap()
                val id = buildString {
                    append(slot.key).append('#').append(t)
                    groups.forEach { g -> append('.').append(choices[g]) }
                }
                out += Candidate(id, t, choices)
            }
        }
        return out
    }

    internal fun render(template: String, vars: Map<String, String>, choices: Map<String, Int>): String {
        val text = HOLE.replace(template) { m ->
            val name = m.groupValues[1]
            if (name.startsWith("~")) {
                val g = name.drop(1)
                synonyms.getValue(g)[choices.getValue(g)]
            } else {
                vars.getValue(name)
            }
        }
        return sentenceCase(text.replace(SPACES, " ").trim())
    }

    internal data class Candidate(val id: String, val template: Int, val choices: Map<String, Int>)

    private fun combos(sizes: List<Int>): List<List<Int>> =
        sizes.fold(listOf(emptyList())) { acc, n -> acc.flatMap { prefix -> (0 until n).map { prefix + it } } }

    private companion object {
        val HOLE = Regex("""\{(~?[A-Za-z][A-Za-z0-9_]*)\}""")
        val SPACES = Regex(" {2,}")

        /** Capitalise the first letter of each sentence, wherever a value or synonym landed there. */
        fun sentenceCase(s: String): String {
            val sb = StringBuilder(s)
            var capNext = true
            for (i in sb.indices) {
                val c = sb[i]
                if (capNext && c.isLetter()) {
                    sb.setCharAt(i, c.uppercaseChar()); capNext = false
                } else if ((c == '.' || c == '!' || c == '?') && (i + 1 == sb.length || sb[i + 1].isWhitespace())) {
                    capNext = true // a full stop inside "1.13x" or "e.g" doesn't end a sentence
                } else if (!c.isWhitespace() && capNext && c.isDigit()) {
                    capNext = false
                }
            }
            return sb.toString()
        }
    }
}

/** English plural helper used when filling vars: `plural(1, "rep")` = "1 rep", `plural(3, "rep")` = "3 reps". */
fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** "two", "three" … for small counts in prose; digits beyond twelve. */
fun spelled(n: Int): String = when (n) {
    0 -> "zero"; 1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; 6 -> "six"
    7 -> "seven"; 8 -> "eight"; 9 -> "nine"; 10 -> "ten"; 11 -> "eleven"; 12 -> "twelve"
    else -> n.toString()
}
