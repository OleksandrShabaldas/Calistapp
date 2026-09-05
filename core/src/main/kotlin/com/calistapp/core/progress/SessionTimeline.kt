package com.calistapp.core.progress

import com.calistapp.core.model.ExerciseBreakdown
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SegmentType

/**
 * One exercise as it sits on the summary's Exercises list: its work aggregated across every set it
 * was trained for, placed in the order it was first performed, with the rest that preceded it.
 */
data class TimelineExercise(
    /** Stable within the session — the plan slot, or the name when there was no plan. */
    val key: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val kcal: Double,
    /** Time under tension — the ACTIVE segments summed. */
    val activeMs: Long,
    /**
     * Wall-clock from the first set's start to the last set's end, including the rests taken between
     * this exercise's own sets. This is "how long the exercise took"; the rest *between* exercises is
     * [restBeforeMs] and is never double-counted here.
     */
    val spanMs: Long,
    /** The rest between the previous exercise finishing and this one starting; 0 for the first. */
    val restBeforeMs: Long,
    val firstStartMs: Long,
)

/**
 * Fold a session's segments into a time-ordered, per-exercise timeline.
 *
 * Grouped by exercise key rather than by contiguous run, so a movement trained across several sets is
 * a single row. [breakdown] supplies the energy the engine already attributed per exercise, joined by
 * the same key. Order is by first appearance, which is workout order for straight sets.
 *
 * A note on circuits (A, B, A, B…): a scattered movement's [spanMs] stretches across the whole round
 * and its [restBeforeMs] can collapse to zero, because "between exercises" stops being well-defined
 * once they interleave. Straight-set training — what the planner builds — reconciles exactly.
 */
fun sessionTimeline(
    segments: List<Segment>,
    breakdown: List<ExerciseBreakdown>,
): List<TimelineExercise> {
    val active = segments.filter { it.type == SegmentType.ACTIVE && it.exerciseName != null }
    if (active.isEmpty()) return emptyList()

    val kcalByKey = breakdown.associate { (it.slotId ?: it.exerciseName) to it.kcal }

    class Acc(val name: String) {
        var sets = 0
        var reps = 0
        var activeMs = 0L
        var firstStart = Long.MAX_VALUE
        var lastEnd = 0L
    }

    val byKey = LinkedHashMap<String, Acc>()
    for (seg in active) {
        val name = seg.exerciseName ?: continue
        val key = seg.slotId ?: name
        val acc = byKey.getOrPut(key) { Acc(name) }
        acc.sets++
        acc.reps += seg.reps
        val end = seg.endMs ?: seg.startMs
        acc.activeMs += (end - seg.startMs).coerceAtLeast(0L)
        acc.firstStart = minOf(acc.firstStart, seg.startMs)
        acc.lastEnd = maxOf(acc.lastEnd, end)
    }

    var prevLastEnd = -1L
    return byKey.entries
        .sortedBy { it.value.firstStart }
        .map { (key, acc) ->
            val restBefore = if (prevLastEnd < 0L) 0L else (acc.firstStart - prevLastEnd).coerceAtLeast(0L)
            prevLastEnd = acc.lastEnd
            TimelineExercise(
                key = key,
                name = acc.name,
                sets = acc.sets,
                reps = acc.reps,
                kcal = kcalByKey[key] ?: 0.0,
                activeMs = acc.activeMs,
                spanMs = (acc.lastEnd - acc.firstStart).coerceAtLeast(0L),
                restBeforeMs = restBefore,
                firstStartMs = acc.firstStart,
            )
        }
}

/**
 * For each movement in [currentId], how its total reps compare to the most recent *earlier* session
 * that also contained it — the "▲▼ vs last session" figure. Keyed by exercise name so it joins onto a
 * [TimelineExercise] cleanly (plan slot ids are per-session and can't match across sessions).
 *
 * A movement making its first appearance has nothing to compare against and is simply absent from the
 * map, rather than reported as a spurious gain.
 */
fun lastSessionDeltas(
    currentId: String,
    sessions: List<PerformedSession>,
): Map<String, Int> {
    val current = sessions.firstOrNull { it.id == currentId } ?: return emptyMap()
    val prior = sessions
        .filter { it.id != currentId && it.startMs < current.startMs }
        .sortedByDescending { it.startMs }

    fun repsByName(s: PerformedSession): Map<String, Int> {
        val m = LinkedHashMap<String, Int>()
        for (log in s.setLogs) {
            val name = log.exerciseName
            if (name.isBlank()) continue
            m[name] = (m[name] ?: 0) + log.reps
        }
        return m
    }

    val currentReps = repsByName(current)
    return buildMap {
        for ((name, reps) in currentReps) {
            val previous = prior.firstNotNullOfOrNull { repsByName(it)[name] } ?: continue
            put(name, reps - previous)
        }
    }
}
