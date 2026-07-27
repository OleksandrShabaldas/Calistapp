package com.calistapp.core.sync

import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.WorkoutPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The contract shared between the phone (`:app`) and the watch (`:wear`) over the Wearable
 * Data Layer. Both sides serialize these payloads to JSON with [WearJson] and send the bytes
 * via MessageClient (real-time) or DataClient (last-known state).
 *
 * Keeping this in `:core` guarantees both modules agree on paths and shapes.
 *
 * **Control flows both ways.** Either device may issue a [ControlPayload]; the receiver applies it
 * locally and does *not* re-broadcast it. That single rule is what keeps start/stop and work/rest
 * toggles in step without the two devices echoing each other into a loop.
 */
object WearSync {
    /** Watch → phone: a batch of freshly captured HR samples. */
    const val PATH_HR_BATCH = "/calistapp/hr_batch"

    /** Watch → phone: the current live session state (status, segment, live kcal, last bpm). */
    const val PATH_SESSION_STATE = "/calistapp/session_state"

    /** Either direction: a control command (start/stop/toggle/select exercise/log a set). */
    const val PATH_CONTROL = "/calistapp/control"

    /** Phone → watch: the user's profile (via DataClient so it persists and syncs on reconnect). */
    const val PATH_PROFILE = "/calistapp/profile"

    const val CAPABILITY_PHONE = "calistapp_phone_app"
    const val CAPABILITY_WEAR = "calistapp_wear_app"
}

val WearJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Which device a command came from — for display ("paused from watch") and diagnostics. */
@Serializable
enum class DeviceOrigin { PHONE, WATCH, UNKNOWN }

@Serializable
data class HrBatchPayload(
    val sessionId: String,
    val samples: List<HeartRateSample>,
)

/** The watch's authoritative live snapshot, mirrored on the phone for at-a-glance display. */
@Serializable
data class SessionStatePayload(
    val sessionId: String,
    val exerciseType: ExerciseType,
    val status: SessionStatus,
    val currentSegment: SegmentType,
    val startMs: Long,
    val elapsedActiveMs: Long,
    val elapsedRestMs: Long,
    val liveKcal: Double,
    val lastBpm: Int,
    val avgHr: Int,
    /** Plan slot the watch is currently on, if the session was built from a plan. */
    val slotId: String? = null,
    val exerciseName: String? = null,
    /** Reps counted in the current block, and which set of that exercise it is. */
    val currentReps: Int = 0,
    val setIndex: Int = 0,
)

@Serializable
enum class ControlCommand {
    START,
    STOP,
    PAUSE,
    RESUME,
    SET_ACTIVE,
    SET_REST,

    /** Switch the live session to a different exercise in the plan. */
    SET_EXERCISE,

    /** Record reps for the block just finished. */
    LOG_SET,

    /** Push a plan revision to the other device mid-session. */
    SYNC_PLAN,

    /**
     * "Are you there?" — the receiver answers with [HELLO] and its current state. Gives the UI a
     * real round trip to report rather than inferring liveness from pairing status alone.
     */
    PING,

    /** Reply to [PING]: this device's app is installed, running and reachable. */
    HELLO,

    /**
     * Phone → watch: "go and check for a new version of yourself."
     *
     * A phone app cannot install a package onto a watch — Play Services carries messages, not APKs
     * — so the watch has to download and install its own update. This command is what lets the
     * phone's "update watch too" button start that, rather than the user having to open the watch
     * app and look for it.
     */
    CHECK_UPDATE,
}

/** A command originating from either device (e.g. phone taps "start", watch toggles rest). */
@Serializable
data class ControlPayload(
    val command: ControlCommand,
    val timestampMs: Long,
    val origin: DeviceOrigin = DeviceOrigin.UNKNOWN,
    val exerciseType: ExerciseType? = null,
    val sessionId: String? = null,
    /** Sent with START and SYNC_PLAN so the watch runs the same workout the phone built. */
    val plan: WorkoutPlan? = null,
    /** Target slot for SET_EXERCISE, or the slot a LOG_SET belongs to. */
    val slotId: String? = null,
    /** Payload of LOG_SET. */
    val reps: Int = 0,
    val seconds: Int = 0,
    val setIndex: Int = 0,
)
