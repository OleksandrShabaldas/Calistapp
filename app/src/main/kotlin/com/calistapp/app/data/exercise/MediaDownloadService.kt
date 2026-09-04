package com.calistapp.app.data.exercise

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs the offline-video download in the foreground, with an ongoing progress notification.
 *
 * The download used to run in an app-scoped coroutine, which Android freezes (and whose network it
 * defers under Doze) the moment the app is backgrounded or the screen turns off — so it stalled. A
 * foreground service keeps the process alive and its network flowing for the length of the download.
 */
@AndroidEntryPoint
class MediaDownloadService : Service() {

    @Inject lateinit var manager: MediaDownloadManager

    private val scope = CoroutineScope(SupervisorJob())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(manager.progress.value))
        if (job?.isActive != true) {
            job = scope.launch {
                val notifier = launch {
                    manager.progress.collect { p ->
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(p))
                    }
                }
                runCatching { manager.download() }
                notifier.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // A restart with no intent would just re-run the download from the top; the user restarts it.
        return START_NOT_STICKY
    }

    /** Android 15 caps a dataSync foreground service; stopping cleanly leaves the cache resumable. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(p: MediaDownloadManager.Progress): android.app.Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = when {
            p.total <= 0 -> "Preparing…"
            else -> "${p.done} of ${p.total}" + if (p.failed > 0) " · ${p.failed} failed" else ""
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading exercise videos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(p.total.coerceAtLeast(1), p.done, p.total <= 0)
            .apply { open?.let { setContentIntent(it) } }
            .build()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Offline downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while exercise videos download for offline use"
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "media_downloads"
        const val NOTIFICATION_ID = 4210
    }
}
