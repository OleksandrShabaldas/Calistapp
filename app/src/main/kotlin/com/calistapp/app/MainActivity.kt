package com.calistapp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.calistapp.app.data.exercise.ExerciseSyncManager
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchProfileSync
import com.calistapp.app.ui.CalistApp
import com.calistapp.app.ui.theme.CalistTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var watchProfileSync: WatchProfileSync
    @Inject lateinit var watchConnectionMonitor: WatchConnectionMonitor
    @Inject lateinit var exerciseSyncManager: ExerciseSyncManager

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        watchProfileSync.start()
        watchConnectionMonitor.start()
        exerciseSyncManager.start()
        askForNotifications()
        setContent {
            CalistTheme {
                CalistApp()
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
