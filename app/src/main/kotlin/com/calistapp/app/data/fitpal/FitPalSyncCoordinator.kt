package com.calistapp.app.data.fitpal

import android.content.Context
import com.calistapp.app.di.ApplicationScope
import com.calistapp.app.sync.StepPullWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The on-open leg of the FitPal bridge, and the guaranteed floor under everything else. Called from
 * `MainActivity` every time the app is opened, it:
 *  1. makes sure Calistapp's own daily step-pull job is scheduled (belt-and-suspenders with FitPal's
 *     end-of-day nudge),
 *  2. pulls the recent window of steps right now (reconciles any days missed while the app was
 *     closed), and
 *  3. retries any finished workouts that never made it to FitPal.
 *
 * All three are idempotent and fail quietly when FitPal isn't installed, so opening the app can
 * only ever move the two apps closer to being in sync.
 */
@Singleton
class FitPalSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val exportManager: FitPalExportManager,
) {
    fun onAppOpen() {
        StepPullWorker.schedulePeriodic(context)
        StepPullWorker.enqueueNow(context)
        scope.launch { runCatching { exportManager.pushAllUnsynced() } }
    }
}
