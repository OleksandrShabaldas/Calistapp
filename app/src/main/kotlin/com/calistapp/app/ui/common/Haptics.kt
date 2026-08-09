package com.calistapp.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Buzzes.
 *
 * The one feedback channel that works when you aren't looking at the screen, which mid-set is
 * always. Until now the app had none at all — the rest timer running out was something you could
 * only find out by checking, which defeats the point of having one.
 */
class Haptics(private val vibrator: Vibrator?) {

    /** Rest is up. Two firm pulses — distinct from a notification, hard to miss between sets. */
    fun restOver() = play(longArrayOf(0, 220, 130, 220), intArrayOf(0, 255, 0, 255))

    /** A set was banked. One short tick, purely confirmatory. */
    fun setBanked() = play(longArrayOf(0, 60), intArrayOf(0, 180))

    private fun play(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.vibrator()) }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

/**
 * Fires [onElapsed] the moment [remainingSeconds] first crosses zero, and not again until the timer
 * is reset.
 *
 * Edge-triggered on purpose: the value ticks every second and goes on counting into negative
 * territory, so anything level-triggered would buzz continuously for as long as you overran.
 */
@Composable
fun RestAlert(remainingSeconds: Int?, resetKey: Any?, onElapsed: () -> Unit) {
    val fired = remember(resetKey) { booleanArrayOf(false) }
    LaunchedEffect(remainingSeconds, resetKey) {
        val remaining = remainingSeconds ?: return@LaunchedEffect
        if (remaining <= 0 && !fired[0]) {
            fired[0] = true
            onElapsed()
        }
    }
}
