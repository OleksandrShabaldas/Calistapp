package com.calistapp.app.session

/**
 * Where an attempt to bring the watch back into the running workout stands — what the heart-rate
 * panel shows after Reconnect, and what the HUD chip shows when the phone does it by itself.
 *
 * Reconnect used to ping the watch and re-check the link, silently. It gave no feedback, and it could
 * not fix the failure that actually happens: the watch's copy of the workout gone (its app restarted),
 * leaving the phone "connected" to a watch that measured nothing. A rejoin re-sends the workout itself.
 */
sealed interface WatchRejoin {
    data object Idle : WatchRejoin

    /** Re-sending the workout and waiting for heart rate. [auto]: the phone noticed the silence itself. */
    data class Working(val auto: Boolean) : WatchRejoin

    /** Heart rate is arriving again. Shown briefly, then back to [Idle]. */
    data object Back : WatchRejoin

    data class Failed(val reason: Reason) : WatchRejoin

    enum class Reason {
        /** No watch is connected to the phone at all. */
        NO_WATCH,

        /** A watch is connected but Calistapp on it didn't answer. */
        NO_ANSWER,

        /** The watch rejoined the workout, but no heart rate came through. */
        NO_HEART_RATE,
    }
}
