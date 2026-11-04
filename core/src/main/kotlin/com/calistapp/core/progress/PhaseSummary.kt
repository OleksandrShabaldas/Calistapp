package com.calistapp.core.progress

import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SlotPhase
import com.calistapp.core.model.WorkoutSession
import kotlin.math.roundToInt

/**
 * A warm-up or cool-down as one block — what the summary shows and what the coach is told, instead of
 * listing its moves as if they were exercises (they used to be, with their hold seconds totalled as
 * reps).
 */
data class PhaseSummary(
    val phase: SlotPhase,
    /** The routine it came from — "Upper-body cooldown". */
    val routineName: String?,
    val moves: Int,
    /** Moves actually held. */
    val done: Int,
    /** Moves skipped, including the rest of a routine skipped on the way to finishing. */
    val skipped: Int,
    /** Time spent in the holds themselves. */
    val heldMs: Long,
    /** Heart rate across the phase — for a cool-down, how far it settled. Null without readings. */
    val avgHr: Int?,
    val startHr: Int?,
    val endHr: Int?,
)

/** The [phase] block of this session, or null when the session didn't include one. */
fun WorkoutSession.phaseSummary(phase: SlotPhase): PhaseSummary? {
    val slots = plan.slotsIn(phase)
    if (slots.isEmpty()) return null
    val ids = slots.mapTo(HashSet()) { it.slotId }
    val logs = setLogs.filter { it.slotId in ids }
    val worked = segments.filter { it.type == SegmentType.ACTIVE && it.slotId in ids }
    val start = worked.minOfOrNull { it.startMs }
    val end = worked.maxOfOrNull { it.endMs ?: it.startMs }
    val window = if (start != null && end != null) samples.filter { it.timestampMs in start..end } else emptyList()
    return PhaseSummary(
        phase = phase,
        routineName = slots.first().routineName,
        moves = slots.size,
        done = logs.mapTo(HashSet()) { it.slotId }.size,
        skipped = skips.filter { it.slotId in ids }.mapTo(HashSet()) { it.slotId }.size,
        heldMs = logs.sumOf { it.durationMs },
        avgHr = window.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.roundToInt(),
        startHr = window.firstOrNull()?.bpm,
        endHr = window.lastOrNull()?.bpm,
    )
}
