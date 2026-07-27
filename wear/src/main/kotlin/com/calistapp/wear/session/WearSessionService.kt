package com.calistapp.wear.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.calistapp.wear.MainActivity
import com.calistapp.wear.R

/**
 * Keeps the watch process alive for the duration of a workout.
 *
 * Without this the session died with the Activity, so tracking stopped the moment the screen
 * blanked — useless for an app you wear while training. The state machine itself lives in
 * [WearSessionManager]; this service exists only to hold the process open and show the ongoing
 * notification the platform requires in exchange.
 */
class WearSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WearSessionManager.attach(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Workout tracking", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Workout in progress")
            .setContentText("Tracking heart rate")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "calistapp_workout"
        private const val NOTIFICATION_ID = 1

        /**
         * Starting a foreground service from the background is restricted on Android 12+, and a
         * command arriving from the phone while the watch UI is closed is exactly that case. The
         * session still runs if this is refused — [WearSessionManager] is independent of the service
         * — so the failure is swallowed rather than allowed to kill the workout.
         */
        fun start(context: Context) {
            val intent = Intent(context, WearSessionService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WearSessionService::class.java)) }
        }
    }
}
