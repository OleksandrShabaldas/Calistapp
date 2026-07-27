package com.calistapp.app.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens Calistapp on the watch when a workout starts on the phone.
 *
 * A `START` control message reaches the watch either way — [WearListenerService] receives it and the
 * session begins tracking regardless. But the watch *screen* stayed on whatever it was showing, so
 * the workout appeared not to have started: you'd raise your wrist mid-set and find the watch face.
 *
 * Play Services carries messages between the two apps but cannot bring an Activity to the front;
 * that needs [RemoteActivityHelper], which routes an intent through the companion app to be launched
 * on the other device. The watch answers it via the `calistapp://workout` deep link declared in its
 * manifest.
 *
 * Best-effort by design. Launching fails when the watch has no companion route, when the user
 * declined the pairing prompt, or on watches that block remote starts — none of which should stop a
 * workout, so failures are swallowed and tracking continues over the message channel alone.
 */
@Singleton
class WatchAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val helper by lazy { RemoteActivityHelper(context) }
    private val nodeClient = Wearable.getNodeClient(context)

    /** Bring the watch app to the foreground. Safe to call when no watch is connected. */
    suspend fun launch() {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrNull().orEmpty()
        if (nodes.isEmpty()) return

        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(WORKOUT_URI))

        // Fire and forget: the returned future only reports whether the launch was dispatched, and
        // there is nothing useful to do about a refusal.
        nodes.forEach { node ->
            runCatching { helper.startRemoteActivity(intent, node.id) }
        }
    }

    private companion object {
        const val WORKOUT_URI = "calistapp://workout"
    }
}
