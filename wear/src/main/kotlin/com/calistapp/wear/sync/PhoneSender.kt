package com.calistapp.wear.sync

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Sends real-time messages from the watch to the phone over the Wearable Data Layer.
 *
 * The connected-node list is **cached**. Resolving it is a round trip to Play Services, and doing
 * that per message — once per heart-rate sample, as this class used to — was a material part of why
 * the watch felt sluggish. The list changes only when the phone connects or disconnects, so a short
 * TTL plus invalidation on send failure keeps it fresh without the constant lookups.
 */
class PhoneSender(context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    private val mutex = Mutex()
    private var cachedNodeIds: List<String> = emptyList()
    private var cachedAtMs = 0L

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

    /** Whether a phone is currently reachable — drives the watch's connection indicator. */
    suspend fun hasPhone(): Boolean = nodeIds().isNotEmpty()

    /** Drop the cached node list so the next send re-resolves — used by manual reconnect. */
    suspend fun invalidateNow() = invalidate()

    /** Fire a message at every connected node. Failures drop the cache so the next send re-resolves. */
    suspend fun send(path: String, bytes: ByteArray) {
        val ids = nodeIds()
        if (ids.isEmpty()) return
        var anyFailed = false
        ids.forEach { id ->
            runCatching { messageClient.sendMessage(id, path, bytes).await() }
                .onFailure { anyFailed = true }
        }
        if (anyFailed) invalidate()
    }

    private companion object {
        const val NODE_CACHE_TTL_MS = 30_000L
    }
}
