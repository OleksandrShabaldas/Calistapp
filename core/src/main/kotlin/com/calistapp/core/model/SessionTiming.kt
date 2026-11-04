package com.calistapp.core.model

/** Where a stretch of session time belongs: the workout proper (work, rest) or either side of it. */
enum class TimeBucket { WARM_UP, WORK, REST, COOL_DOWN }

/**
 * Sorts each segment into the session's time split, by [plan]'s phases. The workout proper runs from the
 * first main set to the last: everything before it is warm-up (the opening preparation, a guided warm-up
 * and the hand-off into the first set) and, once a guided cool-down has run, everything after it is
 * cool-down. A warm-up or cool-down hold is never work wherever it lands, so the work time, the work
 * ratio, between-set rest and heart-rate recovery all describe the training alone.
 *
 * A plan without phases reduces to the split this replaced: the rest before the first set is warm-up.
 * The one rule is shared by the summary, the calorie breakdown and the coach so they can't disagree.
 */
fun List<Segment>.timeBuckets(plan: WorkoutPlan = WorkoutPlan.EMPTY): List<TimeBucket> {
    fun phase(s: Segment) = plan.phaseOf(s.slotId)
    val main = filter { it.type == SegmentType.ACTIVE && phase(it) == SlotPhase.MAIN }
    val mainStart = main.minOfOrNull { it.startMs }
    val mainEnd = main.maxOfOrNull { it.endMs ?: Long.MAX_VALUE }
    val coolStart = filter { it.type == SegmentType.ACTIVE && phase(it) == SlotPhase.COOL_DOWN }.minOfOrNull { it.startMs }
    // Trailing rest only becomes cool-down when there was one; otherwise it's the last rest of the workout.
    val coolFrom = coolStart?.let { mainEnd ?: it }
    return map { s ->
        when {
            s.type == SegmentType.ACTIVE -> when (phase(s)) {
                SlotPhase.WARM_UP -> TimeBucket.WARM_UP
                SlotPhase.COOL_DOWN -> TimeBucket.COOL_DOWN
                SlotPhase.MAIN -> TimeBucket.WORK
            }
            coolFrom != null && s.startMs >= coolFrom -> TimeBucket.COOL_DOWN
            mainStart == null || s.startMs < mainStart -> TimeBucket.WARM_UP
            else -> TimeBucket.REST
        }
    }
}

/** Wall-clock phase durations, independent of HR coverage. Gaps between segments are pauses. */
fun SessionSummary.withSessionTiming(
    segments: List<Segment>,
    endMs: Long?,
    plan: WorkoutPlan = WorkoutPlan.EMPTY,
): SessionSummary {
    if (segments.isEmpty()) return this
    val end = endMs ?: segments.mapNotNull { it.endMs }.maxOrNull() ?: return this
    val buckets = segments.timeBuckets(plan)
    fun total(bucket: TimeBucket) = segments.indices
        .filter { buckets[it] == bucket }
        .sumOf { (minOf(segments[it].endMs ?: end, end) - segments[it].startMs).coerceAtLeast(0) }
    return copy(
        activeDurationMs = total(TimeBucket.WORK),
        restDurationMs = total(TimeBucket.REST),
        warmupDurationMs = total(TimeBucket.WARM_UP),
        coolDownDurationMs = total(TimeBucket.COOL_DOWN),
    )
}

/**
 * Energy of the working sets alone. [SessionSummary.activeKcal] covers every block of movement, the
 * warm-up's and cool-down's holds included; those are their own category, not the workout's.
 */
fun SessionSummary.workingKcal(plan: WorkoutPlan): Double {
    val routines = perExercise.filter { plan.phaseOf(it.slotId) != SlotPhase.MAIN }.sumOf { it.kcal }
    return (activeKcal - routines).coerceIn(0.0, totalKcal.coerceAtLeast(0.0))
}
