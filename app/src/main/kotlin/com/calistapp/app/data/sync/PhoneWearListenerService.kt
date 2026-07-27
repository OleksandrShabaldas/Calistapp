package com.calistapp.app.data.sync

import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.HrBatchPayload
import com.calistapp.core.sync.SessionStatePayload
import com.calistapp.core.sync.WearSync
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Receives real-time messages from the watch. Runs on a background binder thread managed by
 * Play Services; we just decode and hand off to [LiveSessionBus].
 */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject lateinit var bus: LiveSessionBus
    @Inject lateinit var json: Json

    override fun onMessageReceived(event: MessageEvent) {
        val data = String(event.data)
        runCatching {
            when (event.path) {
                WearSync.PATH_HR_BATCH ->
                    bus.onHrBatch(json.decodeFromString(HrBatchPayload.serializer(), data).samples)
                WearSync.PATH_SESSION_STATE ->
                    bus.onState(json.decodeFromString(SessionStatePayload.serializer(), data))
                WearSync.PATH_CONTROL ->
                    bus.onControl(json.decodeFromString(ControlPayload.serializer(), data))
            }
        }
    }
}
