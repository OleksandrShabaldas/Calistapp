package com.calistapp.core.model

import kotlinx.serialization.Serializable

/**
 * A curated, timed sequence bolted onto a workout — a warm-up run before the working sets, or a
 * stretch run after them.
 *
 * Deliberately lighter than a [WorkoutPlan]: a routine is a fixed list of timed movements you flow
 * through, not something you build set-by-set. It's threaded into the session as an opening or
 * closing block of timed holds that score for calories but not for volume or records.
 */
@Serializable
enum class RoutineKind(val label: String) {
    WARM_UP("Warm-up"),
    STRETCH("Stretch"),
}

/** One movement in a [Routine] — held (or performed) for [seconds]. */
@Serializable
data class RoutineItem(
    val exerciseId: String,
    val name: String,
    val seconds: Int,
)

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val kind: RoutineKind,
    /** A short "who it's for" line — "Full body", "Lower body", shown under the name. */
    val bodyFocus: String,
    val items: List<RoutineItem>,
) {
    val totalSeconds: Int get() = items.sumOf { it.seconds }

    /** "5 moves · 3 min" — the summary shown on the routine chip. */
    val summary: String
        get() {
            val minutes = (totalSeconds + 59) / 60
            return "${items.size} moves · ${minutes} min"
        }
}
