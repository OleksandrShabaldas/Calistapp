package com.calistapp.app.data.sync

import android.content.Context
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.WearSync
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone → watch control channel.
 *
 * This direction previously did not exist at all: `PATH_CONTROL` was defined in the shared contract
 * but the phone never sent on it, which is why starting a workout on the phone left the watch idle
 * and toggling work/rest on the phone never reached the wrist.
 *
 * Node ids are cached for the same reason as on the watch — resolving them is a Play Services round
 * trip and the list only changes when the watch connects or disconnects.
 */
@Singleton
class WatchCommandSender @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val mutex = Mutex()
    private var cachedNodeIds: List<String> = emptyList()
    private var cachedAtMs = 0L

    /** Names of the currently connected watch nodes, for display. */
    suspend fun connectedNodeNames(): List<String> =
        runCatching { nodeClient.connectedNodes.await().map { it.displayName } }.getOrDefault(emptyList())

    /** Drop the cached node list so the next send re-resolves — used by manual reconnect. */
    suspend fun invalidateNow() = invalidate()

    private suspend fun nodeIds(): List<String> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedNodeIds.isNotEmpty() && now - cachedAtMs < NODE_CACHE_TTL_MS) return cachedNodeIds
        val fresh = runCatching { nodeClient.connectedNodes.await().map { it.id } }.getOrDefault(emptyList())
        if (fresh.isNotEmpty()) {
            cachedNodeIds = fresh
            cachedAtMs = now
        }
        fresh
    }

    private suspend fun invalidate() = mutex.withLock {
        cachedNodeIds = emptyList()
        cachedAtMs = 0L
    }

    suspend fun send(payload: ControlPayload) {
        val bytes = json.encodeToString(ControlPayload.serializer(), payload).toByteArray()
        val ids = nodeIds()
        if (ids.isEmpty()) return
        var anyFailed = false
        ids.forEach { id ->
            runCatching { messageClient.sendMessage(id, WearSync.PATH_CONTROL, bytes).await() }
                .onFailure { anyFailed = true }
        }
        if (anyFailed) invalidate()
    }

    private companion object {
        const val NODE_CACHE_TTL_MS = 30_000L
    }
}
