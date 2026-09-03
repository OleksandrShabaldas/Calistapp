package com.calistapp.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.calistapp.app.data.fitpal.FitPalContract

/**
 * Woken by FitPal's end-of-day nudge (an explicit broadcast of [FitPalContract.ACTION_PULL_STEPS]).
 * This is the "FitPal starts Calistapp to transfer steps" leg — the process is spun up even if the
 * app hasn't been opened, and we hand the actual work to [StepPullWorker] so it survives this
 * short-lived receiver and gets WorkManager's retry/backoff.
 *
 * The broadcast carries NO data — Calistapp pulls the numbers itself through the provider — so even
 * if some other app triggered this receiver, the worst case is a harmless, idempotent, coalesced
 * step pull. That's why it needs no strong sender check.
 */
class StepPullReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FitPalContract.ACTION_PULL_STEPS) return
        StepPullWorker.enqueueNow(context.applicationContext)
    }
}
