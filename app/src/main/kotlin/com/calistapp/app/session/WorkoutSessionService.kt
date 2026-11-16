package com.calistapp.app.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.calistapp.app.MainActivity
import com.calistapp.app.R
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the phone process open for the duration of a workout.
 *
 * Without it, a workout only existed in RAM: [SessionController] is an app-scoped singleton with no
 * Android component keeping it alive, so a backgrounded app killed under memory pressure took the
 * whole session with it — while the watch, which *does* run a foreground service, carried on
 * streaming heart rate to a phone that no longer had anywhere to put it.
 *
 * The service holds the process; [SessionController] checkpoints the session to the database as it
 * goes. The two are independent on purpose — if the platform refuses to start this service, the
 * workout still runs and is still recoverable.
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject lateinit var controller: SessionController

    private val scope = CoroutineScope(SupervisorJob())
    private var watchJob: Job? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            startAsForeground()
            observeSession()
        }
        // Restarting with no session to attach to would leave an ongoing notification for a workout
        // that isn't running, so the service is not sticky — recovery is the controller's job.
        return START_NOT_STICKY
    }

    /**
     * Foreground as dataSync (the watch link), plus connectedDevice when a Bluetooth heart-rate band
     * can be used — the type has to be named explicitly, because Android 14+ refuses a connectedDevice
     * service outright (a crash, not a fallback) when the Bluetooth permission isn't granted.
     */
    private fun startAsForeground() {
        val notification = buildNotification(null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
            return
        }
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        val bluetoothOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (bluetoothOk) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        runCatching { startForeground(NOTIFICATION_ID, notification, types) }
            .onFailure { startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
    }

    /**
     * Keeps the notification in step with the workout, and shuts the service down when the workout
     * ends — however it ended, including a stop that came from the watch.
     *
     * The elapsed clock is left to the platform's chronometer rather than re-posted every second;
     * only the things that actually change — work/rest, calories, the current movement — trigger a
     * new notification.
     */
    private fun observeSession() {
        watchJob?.cancel()
        watchJob = scope.launch {
            controller.live
                .map { it?.let(::NotificationState) }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == null) {
                        stopSelf()
                        return@collect
                    }
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(state))
                }
        }
    }

    /**
     * Android 15 caps a dataSync foreground service at six hours a day and calls this before it
     * force-stops us. Banking the workout is far better than losing it to an ANR.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch { controller.stop() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Only the fields the notification renders, so unrelated ticks don't re-post it. */
    private data class NotificationState(
        val startMs: Long,
        val paused: Boolean,
        val working: Boolean,
        val kcal: Int,
        val bpm: Int,
        val exerciseName: String?,
    ) {
        constructor(live: LiveSession) : this(
            startMs = live.startMs,
            paused = live.status == SessionStatus.PAUSED,
            working = live.currentSegment == SegmentType.ACTIVE,
            kcal = live.summary.totalKcal.toInt(),
            bpm = live.lastBpm,
            exerciseName = live.currentExercise?.displayName,
        )
    }

    private fun buildNotification(state: NotificationState?): Notification {
        createChannel()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when {
            state == null -> "Workout in progress"
            state.paused -> "Workout paused"
            state.working -> state.exerciseName ?: "Working"
            else -> "Resting"
        }
        // The title already carries the state, so this is just the two live numbers.
        val detail = buildList {
            state?.let {
                add("${it.kcal} kcal")
                if (it.bpm > 0) add("${it.bpm} bpm")
            }
        }.joinToString(" · ").ifEmpty { "Tracking your session" }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(detail)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                // The platform ticks the clock itself, so the notification stays live between posts.
                state?.let { if (!it.paused) setWhen(it.startMs).setUsesChronometer(true) }
            }
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Workout tracking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    companion object {
        private const val CHANNEL_ID = "calistapp_workout"
        private const val NOTIFICATION_ID = 42

        /**
         * Android 12+ forbids starting a foreground service from the background, and a workout
         * started on the *watch* while the phone app is closed is precisely that. The session is
         * checkpointed to the database either way, so a refusal costs process priority — not the
         * workout — and is swallowed rather than allowed to take the session down with it.
         */
        fun start(context: Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        // No stop(): the service watches the session and shuts itself down when it ends, which
        // covers every exit — Finish, Discard, and a stop that came from the watch — through one
        // path rather than three call sites that could each forget.
    }
}
