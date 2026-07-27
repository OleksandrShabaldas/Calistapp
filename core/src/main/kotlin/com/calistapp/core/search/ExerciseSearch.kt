package com.calistapp.core.search

import com.calistapp.core.model.Exercise
import kotlin.math.abs
import kotlin.math.min

/**
 * Relevance search over the exercise gallery.
 *
 * The dataset's naming is inconsistent and nobody remembers the exact spelling — "pullup",
 * "pull up" and "Pull-Up" all have to find the same movement. Three things make that work:
 *
 *  1. **Normalisation** — punctuation and spacing are stripped before comparing, so `pull-up`,
 *     `pull up` and `pullup` collapse to one key.
 *  2. **Token coverage** — a multi-word query matches when every term appears somewhere, in any
 *     order, so "wide pull up" finds "Wide-Grip Pull-Up".
 *  3. **Typo tolerance** — a one-character slip on a reasonably long word still matches, via a
 *     bounded edit distance.
 *
 * Ranking is built so the **plain version of a movement wins**: an exact name match scores far
 * above a partial one, and longer names are penalised in proportion to how much extra they carry.
 * Searching "pullup" therefore returns Pull-Up, then Weighted Pull-Up, then the grip variations —
 * rather than whichever happened to sort first alphabetically.
 */
object ExerciseSearch {

    /** An exercise together with why it matched, so the UI can show the best hits first. */
    data class Hit(val exercise: Exercise, val score: Double)

    /**
     * Gym shorthand → the vocabulary the dataset actually uses. Without this, the words people
     * genuinely type ("abs", "quads", "ohp") return nothing at all.
     */
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "abs" to listOf("abdominals", "core"),
        "ab" to listOf("abdominals"),
        "core" to listOf("abdominals"),
        "obliques" to listOf("abdominals"),
        "quads" to listOf("quadriceps"),
        "quad" to listOf("quadriceps"),
        "hams" to listOf("hamstrings"),
        "hammies" to listOf("hamstrings"),
        "glute" to listOf("glutes"),
        "delts" to listOf("shoulders"),
        "delt" to listOf("shoulders"),
        "shoulder" to listOf("shoulders"),
        "pecs" to listOf("chest"),
        "pec" to listOf("chest"),
        "lat" to listOf("lats"),
        "bi" to listOf("biceps"),
        "bis" to listOf("biceps"),
        "tri" to listOf("triceps"),
        "tris" to listOf("triceps"),
        "traps" to listOf("traps"),
        "calf" to listOf("calves"),
        "ohp" to listOf("overhead", "press"),
        "bw" to listOf("body", "only"),
        "bodyweight" to listOf("body", "only"),
        "db" to listOf("dumbbell"),
        "bb" to listOf("barbell"),
        "kb" to listOf("kettlebell"),
    )
    // Note: no entries decomposing "pullup" into "pull" + "up". Normalisation already matches those
    // against the name, and feeding the fragments into field matching made every exercise with
    // force="pull" a hit — 800 results for a query that should return a dozen.

    /** Lowercase, letters and digits only — the key that makes `pull-up` == `pullup`. */
    fun normalize(text: String): String = buildString {
        for (c in text) if (c.isLetterOrDigit()) append(c.lowercaseChar())
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(TOKEN_DELIMITERS).filter { it.isNotBlank() }

    private val TOKEN_DELIMITERS = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Rank [all] against [rawQuery]. A blank query returns everything unscored, so callers can
     * apply their own ordering.
     */
    fun search(all: List<Exercise>, rawQuery: String): List<Hit> {
        val query = rawQuery.trim()
        if (query.isBlank()) return all.map { Hit(it, 0.0) }

        val normQuery = normalize(query)
        val queryTokens = tokenize(query)
        val expanded = queryTokens.flatMap { listOf(it) + (SYNONYMS[it] ?: emptyList()) }.distinct()

        return all.mapNotNull { exercise ->
            val score = score(exercise, normQuery, queryTokens, expanded)
            if (score > 0.0) Hit(exercise, score) else null
        }.sortedWith(
            compareByDescending<Hit> { it.score }
                .thenBy { it.exercise.name.length }
                .thenBy { it.exercise.name.lowercase() },
        )
    }

    private fun score(
        exercise: Exercise,
        normQuery: String,
        queryTokens: List<String>,
        expandedTokens: List<String>,
    ): Double {
        val normName = normalize(exercise.name)
        val nameTokens = tokenize(exercise.name)

        // How much name there is beyond what was searched for. This is what puts the plain
        // movement above its weighted and grip-specific variations.
        val extra = (normName.length - normQuery.length).coerceAtLeast(0)
        val lengthPenalty = min(extra * 3.0, 240.0)

        var best = when {
            normName == normQuery -> 1000.0
            normName.startsWith(normQuery) -> 700.0 - lengthPenalty
            normName.contains(normQuery) -> 560.0 - lengthPenalty
            else -> 0.0
        }

        // Every query term present somewhere in the name, in any order.
        if (best == 0.0 && queryTokens.isNotEmpty()) {
            val covered = queryTokens.count { q -> nameTokens.any { tokenMatches(q, it) } }
            if (covered == queryTokens.size) {
                best = 420.0 - lengthPenalty
            } else if (covered > 0) {
                best = 180.0 * (covered.toDouble() / queryTokens.size) - lengthPenalty / 2
            }
        }

        val nameScore = best.coerceAtLeast(0.0)

        // Fields you'd genuinely browse by. A match on one of these is enough to be a result in
        // its own right — searching "lats" or "barbell" should return things.
        val strong = fieldScore(exercise.primaryMuscles, expandedTokens, 130.0) +
            fieldScore(exercise.secondaryMuscles, expandedTokens, 65.0) +
            fieldScore(exercise.equipment, expandedTokens, 55.0) +
            fieldScore(listOf(exercise.bodyPart.displayName), expandedTokens, 45.0)

        // Descriptive metadata. Far too coarse to qualify a result on its own — nearly half the
        // gallery shares any given force or category — so these only refine an existing hit.
        val weak = fieldScore(exercise.tags, expandedTokens, 20.0) +
            fieldScore(listOfNotNull(exercise.force, exercise.mechanic, exercise.category), expandedTokens, 15.0) +
            fieldScore(exercise.problematicAreas, expandedTokens, 12.0)

        if (nameScore <= 0.0 && strong <= 0.0) return 0.0

        var total = nameScore + strong + weak

        // Tie-breakers only — never enough to reorder a genuinely better name match.
        total += exercise.efficiency * 2.0
        if (exercise.imageUrls.isNotEmpty()) total += 4.0
        if (exercise.isCalisthenics) total += 3.0
        return total
    }

    /**
     * Score a metadata field. Matching is anchored — equal, or the value starts with the term — and
     * terms under three characters are ignored. Loose substring matching in both directions let
     * fragments like "up" hit unrelated values and was a large part of why results ballooned.
     */
    private fun fieldScore(values: List<String>, tokens: List<String>, weight: Double): Double {
        if (values.isEmpty()) return 0.0
        val hits = values.count { value ->
            val n = normalize(value)
            if (n.isEmpty()) return@count false
            tokens.any { t ->
                val nt = normalize(t)
                nt.length >= 3 && (n == nt || n.startsWith(nt))
            }
        }
        return if (hits == 0) 0.0 else weight * min(hits, 2)
    }

    /**
     * Prefix or near-miss match between a query term and a word in the text.
     *
     * Only the candidate may extend the query ("squa" finds "squat"), never the reverse — letting
     * a longer query match a shorter word made "pullup" match the word "pull", and so match
     * Face Pull.
     */
    private fun tokenMatches(query: String, candidate: String): Boolean {
        if (candidate.startsWith(query)) return true
        val tolerance = when {
            query.length >= 7 -> 2
            query.length >= 4 -> 1
            else -> 0
        }
        return tolerance > 0 && editDistanceWithin(query, candidate, tolerance)
    }

    /** Bounded Levenshtein — returns early once the distance can't come in under [max]. */
    private fun editDistanceWithin(a: String, b: String, max: Int): Boolean {
        if (abs(a.length - b.length) > max) return false
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                rowMin = min(rowMin, cur[j])
            }
            if (rowMin > max) return false
            val swap = prev; prev = cur; cur = swap
        }
        return prev[b.length] <= max
    }
}
