package com.calistapp.app.ui.exercises

import com.calistapp.core.model.BodyPart
import com.calistapp.core.model.Difficulty
import com.calistapp.core.model.Exercise
import com.calistapp.core.search.ExerciseSearch

/**
 * Searching, filtering and sorting the exercise gallery.
 *
 * Kept apart from any one ViewModel because two screens browse the same library — the gallery and
 * the workout planner's picker — and they have to behave identically. When this logic lived in the
 * gallery's ViewModel the planner had its own `name.contains(query)` instead, so typing "pullup"
 * found nothing in the one place you actually need to find an exercise.
 */
enum class ExerciseSort(val label: String) {
    RELEVANCE("Best match"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    EFFICIENCY_DESC("Most efficient"),
    EFFICIENCY_ASC("Least efficient"),
    DIFFICULTY_ASC("Easiest first"),
    DIFFICULTY_DESC("Hardest first"),
}

/**
 * Everything narrowing the gallery. Sets rather than single values throughout, because "chest or
 * shoulders" is a normal thing to want and single-select filters force you to search twice.
 */
data class ExerciseFilters(
    val query: String = "",
    val bodyParts: Set<BodyPart> = emptySet(),
    val primaryMuscles: Set<String> = emptySet(),
    val secondaryMuscles: Set<String> = emptySet(),
    val equipment: Set<String> = emptySet(),
    val difficulties: Set<Difficulty> = emptySet(),
    /**
     * Joints to steer clear of. This one **excludes** rather than includes: "show me exercises that
     * hurt my wrists" is almost never the question — "hide them" is.
     */
    val avoidAreas: Set<String> = emptySet(),
    val calisthenicsOnly: Boolean = false,
    val sort: ExerciseSort = ExerciseSort.RELEVANCE,
) {
    /** Number of narrowing choices in effect — shown on the Filters button. */
    val activeCount: Int
        get() = bodyParts.size + primaryMuscles.size + secondaryMuscles.size + equipment.size +
            difficulties.size + avoidAreas.size + if (calisthenicsOnly) 1 else 0

    fun <T> Set<T>.toggled(value: T): Set<T> = if (contains(value)) this - value else this + value
}

/** The filter options actually present in the loaded gallery, so no chip leads to zero results. */
data class FilterFacets(
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val problemAreas: List<String> = emptyList(),
) {
    companion object {
        fun of(list: List<Exercise>): FilterFacets {
            fun clean(values: List<String>) =
                values.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()

            return FilterFacets(
                muscles = clean(list.flatMap { it.primaryMuscles + it.secondaryMuscles }),
                equipment = clean(list.flatMap { it.equipment }),
                problemAreas = clean(list.flatMap { it.problematicAreas }),
            )
        }
    }
}

/**
 * Narrow, then rank, then order. Filtering first is both cheaper and more useful — relevance is
 * relative to what you're actually allowed to see.
 */
fun List<Exercise>.applyQuery(f: ExerciseFilters): List<Exercise> {
    val narrowed = filter { it.matches(f) }
    val hits = ExerciseSearch.search(narrowed, f.query)
    return hits.sortedBy(f)
}

private fun Exercise.matches(f: ExerciseFilters): Boolean {
    if (f.bodyParts.isNotEmpty() && bodyPart !in f.bodyParts) return false
    if (f.difficulties.isNotEmpty() && difficulty !in f.difficulties) return false
    if (f.calisthenicsOnly && !isBodyweight) return false
    if (f.primaryMuscles.isNotEmpty() && !primaryMuscles.anyOf(f.primaryMuscles)) return false
    if (f.secondaryMuscles.isNotEmpty() && !secondaryMuscles.anyOf(f.secondaryMuscles)) return false
    if (f.equipment.isNotEmpty() && !equipment.anyOf(f.equipment)) return false
    // Exclusion, not inclusion — see [ExerciseFilters.avoidAreas].
    if (f.avoidAreas.isNotEmpty() && problematicAreas.anyOf(f.avoidAreas)) return false
    return true
}

private fun List<String>.anyOf(selected: Set<String>): Boolean {
    val lowered = selected.map { it.lowercase() }
    return any { value -> lowered.any { it == value.lowercase() } }
}

private fun List<ExerciseSearch.Hit>.sortedBy(f: ExerciseFilters): List<Exercise> {
    val comparator: Comparator<ExerciseSearch.Hit> = when (f.sort) {
        // With no query there's no relevance signal, so fall back to something predictable.
        ExerciseSort.RELEVANCE ->
            if (f.query.isBlank()) compareBy { it.exercise.name.lowercase() }
            else return map { it.exercise }
        ExerciseSort.NAME_ASC -> compareBy { it.exercise.name.lowercase() }
        ExerciseSort.NAME_DESC -> compareByDescending { it.exercise.name.lowercase() }
        ExerciseSort.EFFICIENCY_DESC ->
            compareByDescending<ExerciseSearch.Hit> { it.exercise.efficiency }
                .thenBy { it.exercise.name.lowercase() }
        ExerciseSort.EFFICIENCY_ASC ->
            compareBy<ExerciseSearch.Hit> { it.exercise.efficiency }
                .thenBy { it.exercise.name.lowercase() }
        ExerciseSort.DIFFICULTY_ASC ->
            compareBy<ExerciseSearch.Hit> { it.exercise.difficulty.ordinal }
                .thenBy { it.exercise.name.lowercase() }
        ExerciseSort.DIFFICULTY_DESC ->
            compareByDescending<ExerciseSearch.Hit> { it.exercise.difficulty.ordinal }
                .thenBy { it.exercise.name.lowercase() }
    }
    return sortedWith(comparator).map { it.exercise }
}
