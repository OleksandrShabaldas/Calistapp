package com.calistapp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.calistapp.app.data.exercise.ExerciseSyncManager
import com.calistapp.app.data.fitpal.FitPalSyncCoordinator
import com.calistapp.app.data.session.SessionPrefsRepository
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchProfileSync
import com.calistapp.app.session.SessionController
import com.calistapp.app.ui.CalistApp
import com.calistapp.app.ui.theme.CalistTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var watchProfileSync: WatchProfileSync
    @Inject lateinit var watchConnectionMonitor: WatchConnectionMonitor
    @Inject lateinit var exerciseSyncManager: ExerciseSyncManager
    @Inject lateinit var fitPalSyncCoordinator: FitPalSyncCoordinator
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var sessionPrefs: SessionPrefsRepository
    @Inject lateinit var heartRateFillIn: com.calistapp.app.data.device.HeartRateFillIn

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        watchProfileSync.start()
        watchConnectionMonitor.start()
        exerciseSyncManager.start()
        // Reconcile with FitPal on every open: pull any missed step days, retry unsent workouts.
        fitPalSyncCoordinator.onAppOpen()
        askForNotifications()
        holdScreenDuringWorkouts()
        setContent {
            CalistTheme {
                CalistApp()
            }
        }
    }

    /**
     * Every time the app comes back: fill in heart rate for recent workouts that had none, from what a
     * ring or tracker has synced to Health Connect since (throttled inside).
     */
    override fun onStart() {
        super.onStart()
        heartRateFillIn.runIfDue()
    }

    /**
     * Keep the screen on whenever a workout is running and this activity is on screen — every screen,
     * not just the live one. It used to be held by the live screen alone, so opening an exercise's
     * info, adding an exercise or minimising mid-workout let the phone go to sleep. Set on the window
     * itself (the activity outlives every screen), and switchable in Settings / the pause screen.
     */
    private fun holdScreenDuringWorkouts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    sessionController.live.map { it != null }.distinctUntilChanged(),
                    sessionPrefs.prefs.map { it.keepScreenOn }.distinctUntilChanged(),
                ) { running, wanted -> running && wanted }
                    .distinctUntilChanged()
                    .collect { hold ->
                        if (hold) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
            }
        }
    }

    /**
     * The workout's ongoing notification is what the foreground service shows in exchange for
     * keeping the process alive. Denied, the service still runs and the workout is still safe — the
     * user just loses the at-a-glance view of it, so this asks once and never nags.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
