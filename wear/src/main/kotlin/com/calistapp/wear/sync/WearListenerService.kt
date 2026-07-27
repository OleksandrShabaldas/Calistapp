package com.calistapp.wear.sync

import com.calistapp.core.model.UserProfile
import com.calistapp.core.sync.ControlCommand
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.WearJson
import com.calistapp.core.sync.WearSync
import com.calistapp.wear.session.WearSessionManager
import com.calistapp.wear.session.WearSessionService
import com.calistapp.wear.update.WearUpdateHolder
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * The watch's single inbound door from the phone: control commands over MessageClient, and the
 * user's profile over DataClient.
 *
 * This is the half of the link that never existed — `PATH_CONTROL` was declared in the shared
 * contract but nothing on the watch listened for it, which is why starting a workout on the phone
 * or toggling work/rest there had no effect here.
 */
class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.PATH_CONTROL) return
        val payload = runCatching {
            WearJson.decodeFromString(ControlPayload.serializer(), String(event.data))
        }.getOrNull() ?: return

        WearSessionManager.attach(this)

        // An update check is about the app, not the workout, so it's handled here and never
        // reaches the session state machine.
        if (payload.command == ControlCommand.CHECK_UPDATE) {
            WearUpdateHolder.attach(this)
            WearUpdateHolder.checkAndDownload()
            return
        }

        // Keep the process alive for the workout the phone just started, and let it go on stop.
        // PING deliberately doesn't touch the service — a liveness check shouldn't spin one up.
        when (payload.command) {
            ControlCommand.START -> WearSessionService.start(this)
            ControlCommand.STOP -> WearSessionService.stop(this)
            else -> Unit
        }
        WearSessionManager.applyControl(payload)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearSync.PATH_PROFILE) {
                event.dataItem.data?.let { bytes ->
                    runCatching {
                        WearProfileHolder.update(WearJson.decodeFromString(UserProfile.serializer(), String(bytes)))
                    }
                }
            }
        }
    }
}
