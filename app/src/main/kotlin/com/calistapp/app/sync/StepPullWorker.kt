package com.calistapp.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.calistapp.app.data.fitpal.ImportOutcome
import com.calistapp.app.data.fitpal.StepsImportRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Pulls the recent window of steps from FitPal into `step_days`. Kicked off three ways, all here so
 * there's one code path: FitPal's wake broadcast ([StepPullReceiver]), Calistapp's own daily
 * self-scheduled job ([schedulePeriodic]), and the on-open reconciliation. Idempotent, so running
 * more than once is harmless.
 *
 * Deps come from a Hilt @EntryPoint (no `hilt-work` needed — matches how FitPal does its workers).
 */
class StepPullWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun stepsImportRepository(): StepsImportRepository
    }

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .stepsImportRepository()
        return when (runCatching { repo.reconcileRecent() }.getOrNull()) {
            // FitPal wasn't reachable — ask WorkManager to retry with backoff.
            ImportOutcome.Unavailable, null -> Result.retry()
            is ImportOutcome.Imported -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_ONESHOT = "calistapp_step_pull_oneshot"
        const val UNIQUE_PERIODIC = "calistapp_step_pull_periodic"

        /** Enqueue an immediate pull (wake broadcast / app open). Coalesces if one is already queued. */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONESHOT,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StepPullWorker>().build()
            )
        }

        /** Calistapp's own daily job (belt-and-suspenders with FitPal's nudge). KEEP the period. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StepPullWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
