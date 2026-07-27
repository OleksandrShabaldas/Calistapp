package com.calistapp.core.model

import kotlinx.serialization.Serializable

/** Whether a slice of time was working effort or recovery — the key input to accurate calories. */
@Serializable
enum class SegmentType { ACTIVE, REST }

/**
 * A contiguous stretch of a session tagged as active or rest. [endMs] is null while ongoing.
 * Segments are created from the user's real-time active/rest input on the watch or phone.
 *
 * An ACTIVE segment additionally carries *which exercise* was being performed and how many reps
 * were completed. That context is what lets the calorie engine correct for heart rate's known blind
 * spots (see [com.calistapp.core.calorie.ExerciseIntensity]) instead of treating all effort alike.
 * The physical profile is denormalized onto the segment so the engine never needs the plan or the
 * exercise gallery to score a session.
 */
@Serializable
data class Segment(
    val type: SegmentType,
    val startMs: Long,
    val endMs: Long? = null,
    /** Plan slot this block belongs to; null for unstructured work. */
    val slotId: String? = null,
    val exerciseName: String? = null,
    /** Reps completed during this block. 0 when unlogged or resting. */
    val reps: Int = 0,
    /** The movement's physical profile; null falls back to pure heart-rate estimation. */
    val metabolics: ExerciseMetabolics? = null,
) {
    fun durationMs(nowMs: Long): Long = (endMs ?: nowMs) - startMs
}

@Serializable
enum class ExerciseType(val displayName: String) {
    CALISTHENICS("Calisthenics"),
    STRENGTH("Strength"),
    RUNNING("Running"),
    CYCLING("Cycling"),
    HIIT("HIIT"),
    MOBILITY("Mobility"),
    OTHER("Other"),
}

@Serializable
enum class SessionStatus { ACTIVE, PAUSED, COMPLETED }

/**
 * A full workout. During a live session this is built incrementally as HR samples and
 * segment toggles stream in; once [status] is COMPLETED it's persisted and can be analyzed.
 */
@Serializable
data class WorkoutSession(
    val id: String,
    val exerciseType: ExerciseType,
    val startMs: Long,
    val endMs: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val samples: List<HeartRateSample> = emptyList(),
    val segments: List<Segment> = emptyList(),
    /** The exercises this workout was built from. Empty for a free-form session. */
    val plan: WorkoutPlan = WorkoutPlan.EMPTY,
    /** What was actually performed, set by set. */
    val setLogs: List<SetLog> = emptyList(),
    /** The specific exercise this session was launched for, if started from the gallery. */
    val exerciseName: String? = null,
    val notes: String = "",
    /** Cached engine output, filled in when the session is summarized. */
    val summary: SessionSummary? = null,
    /** Latest AI feedback for this session, if generated. */
    val aiInsight: String? = null,
    val aiInsightAtMs: Long? = null,
)
