package com.calistapp.app.data.sync

import android.content.Context
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.DeviceOrigin
import com.calistapp.core.sync.WearSync
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** How healthy the phone↔watch link is, from "nothing paired" through "actively streaming". */
enum class WatchLinkStatus {
    /** Still working it out — shown only on first check. */
    CHECKING,

    /** No watch is connected to this phone at all. */
    NO_DEVICE,

    /** A watch is connected, but the Calistapp watch app isn't reachable on it. */
    APP_UNREACHABLE,

    /** Watch app is installed and answered, but no live data is flowing right now. */
    READY,

    /** Heart rate / session state is arriving right now. */
    STREAMING,
}

data class WatchLinkState(
    val status: WatchLinkStatus = WatchLinkStatus.CHECKING,
    val deviceName: String? = null,
    /** When we last received anything at all from the watch app. */
    val lastHeardMs: Long? = null,
    /** A manual reconnect is in flight. */
    val refreshing: Boolean = false,
) {
    val isUsable: Boolean
        get() = status == WatchLinkStatus.READY || status == WatchLinkStatus.STREAMING
}

/**
 * Tracks and reports the state of the link to the watch.
 *
 * Deliberately distinguishes "a watch is paired" from "the watch *app* is reachable" from "data is
 * actually arriving" — those are three genuinely different failures with three different fixes, and
 * collapsing them into one "connected" boolean is what makes sync problems feel unexplainable.
 *
 * Reachability is resolved via [CapabilityClient] against the capability the watch module declares
 * in its `wear.xml`, so it reflects the app specifically rather than the hardware pairing.
 */
@Singleton
class WatchConnectionMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val commands: WatchCommandSender,
    private val bus: LiveSessionBus,
) {
    private val nodeClient = Wearable.getNodeClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _state = MutableStateFlow(WatchLinkState())
    val state: StateFlow<WatchLinkState> = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true

        // Anything arriving from the watch proves the link is alive.
        scope.launch { bus.hrSamples.collect { markHeard() } }
        scope.launch { bus.watchState.collect { if (it != null) markHeard() } }
        scope.launch {
            bus.commands.collect { payload ->
                markHeard()
                // Answer the watch's "are you there?" so its own indicator can go green.
                if (payload.command == ControlCommand.PING) {
                    commands.send(
                        ControlPayload(
                            command = ControlCommand.HELLO,
                            timestampMs = System.currentTimeMillis(),
                            origin = DeviceOrigin.PHONE,
                        ),
                    )
                }
            }
        }

        scope.launch {
            while (true) {
                probe()
                delay(POLL_MS)
            }
        }
    }

    private fun markHeard() {
        val now = System.currentTimeMillis()
        _state.update { it.copy(lastHeardMs = now, status = WatchLinkStatus.STREAMING) }
    }

    /**
     * Force a fresh check: drop cached node ids, re-resolve, and ping the watch so the reported
     * status reflects a real round trip rather than a stale lookup.
     */
    fun reconnect() = scope.launch {
        _state.update { it.copy(refreshing = true) }
        commands.invalidateNow()
        commands.send(
            ControlPayload(
                command = ControlCommand.PING,
                timestampMs = System.currentTimeMillis(),
                origin = DeviceOrigin.PHONE,
            ),
        )
        // Give the watch a moment to answer before re-reading the link state.
        delay(PING_GRACE_MS)
        probe()
        _state.update { it.copy(refreshing = false) }
    }

    private suspend fun probe() {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        val reachable = runCatching {
            capabilityClient
                .getCapability(WearSync.CAPABILITY_WEAR, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())

        val now = System.currentTimeMillis()
        _state.update { cur ->
            val heardRecently = cur.lastHeardMs?.let { now - it < STREAMING_WINDOW_MS } == true
            val status = when {
                nodes.isEmpty() && reachable.isEmpty() -> WatchLinkStatus.NO_DEVICE
                heardRecently -> WatchLinkStatus.STREAMING
                reachable.isNotEmpty() -> WatchLinkStatus.READY
                else -> WatchLinkStatus.APP_UNREACHABLE
            }
            cur.copy(
                status = status,
                deviceName = nodes.firstOrNull()?.displayName ?: reachable.firstOrNull()?.displayName,
            )
        }
    }

    private companion object {
        const val POLL_MS = 10_000L
        const val PING_GRACE_MS = 1_200L

        /** Nothing heard for this long and we stop calling it "streaming". */
        const val STREAMING_WINDOW_MS = 12_000L
    }
}
