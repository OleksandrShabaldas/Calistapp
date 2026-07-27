package com.calistapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        watchProfileSync.start()
        watchConnectionMonitor.start()
        exerciseSyncManager.start()
        setContent {
            CalistTheme {
                CalistApp()
            }
        }
    }
}
