package com.calistapp.wear.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.calistapp.wear.MainActivity
import com.calistapp.wear.R

/**
 * Keeps the watch working on its own update while the screen is off.
 *
 * Without this, downloading an update meant holding your wrist up for the whole transfer. Blanking
 * the screen drops the app to the background, and Wear treats a background app harshly: the CPU is
 * parked and the Wi-Fi radio is powered down, which kills the open connection partway through. What
 * the user saw for that was an error message, on a watch, that they had to keep awake to avoid.
 *
 * Three things are needed and none of them are expensive:
 *  - a foreground service, so the process isn't background-restricted while it downloads;
 *  - a partial wake lock, so the CPU keeps running with the screen off;
 *  - a Wi-Fi lock, so the radio isn't taken down underneath the connection.
 *
 * The service holds no state — [WearUpdateHolder] owns the download and starts and stops this
 * around it.
 */
class WearUpdateService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            progressNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        acquireLocks()
        // Deliberately not sticky: a restart delivers a null intent with no download behind it, and
        // this would then sit there holding the CPU and radio awake for nothing.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wakeLock != null) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                // Bounded so a lock leaked by a crash can't quietly drain the battery. An update
                // that hasn't finished in this long has failed on its own anyway.
                acquire(MAX_DOWNLOAD_MS)
            }

        @Suppress("DEPRECATION") // FULL_HIGH_PERF is the mode that survives the screen going off.
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun progressNotification(): Notification = notification(
        title = "Updating Calistapp",
        text = "Downloading — you can lower your wrist",
        ongoing = true,
    )

    private fun notification(title: String, text: String, ongoing: Boolean): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "calistapp_update"
        private const val NOTIFICATION_ID = 2
        private const val READY_NOTIFICATION_ID = 3
        private const val WAKE_LOCK_TAG = "calistapp:update"
        private const val WIFI_LOCK_TAG = "calistapp:update"
        private const val MAX_DOWNLOAD_MS = 20 * 60 * 1000L

        /**
         * Android 12+ refuses foreground-service starts from the background, and a check the phone
         * triggered while the watch UI is closed is exactly that. The download still runs if this
         * is refused — it just isn't protected from the screen going off — so the refusal is
         * swallowed rather than allowed to take the update down with it.
         */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, WearUpdateService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WearUpdateService::class.java)) }
        }

        /**
         * Tell the user an update is waiting for them.
         *
         * The install prompt can't be raised on their behalf — it's the system's own dialog and it
         * has to be confirmed on the wrist. Now that the download no longer needs anyone watching
         * it, something has to say when it's done, or a finished update just sits there unnoticed.
         */
        fun notifyReadyToInstall(context: Context, versionName: String) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                // The service may never have run — if the foreground start was refused the channel
                // it normally creates doesn't exist yet, and posting to a missing channel is dropped.
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW),
                )
                manager.notify(
                    READY_NOTIFICATION_ID,
                    Notification.Builder(context, CHANNEL_ID)
                        .setContentTitle("Calistapp $versionName is ready")
                        .setContentText("Tap to install")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(
                            PendingIntent.getActivity(
                                context,
                                0,
                                Intent(context, MainActivity::class.java),
                                PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }

        fun clearReadyNotice(context: Context) {
            runCatching {
                context.getSystemService(NotificationManager::class.java).cancel(READY_NOTIFICATION_ID)
            }
        }
    }
}
