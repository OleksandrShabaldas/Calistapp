package com.calistapp.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Buzzes.
 *
 * The one feedback channel that works when you aren't looking at the screen, which mid-set is always.
 * Rest is a count-up stopwatch now, so there's no "rest over" pulse — the one cue left is a short
 * confirmatory tick when a set is banked.
 */
class Haptics(private val vibrator: Vibrator?) {

    /** A set was banked. One short tick, purely confirmatory. */
    fun setBanked() = play(longArrayOf(0, 60), intArrayOf(0, 180))

    /** Something landed: a medal hitting the end of its ribbon. One firm knock. */
    fun impact() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, longArrayOf(0, 28), intArrayOf(0, 255))

    /** A detent passed: a carousel landing on the next medal, a medal turning over. Barely there. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK, longArrayOf(0, 8), intArrayOf(0, 90))

    private fun predefined(effect: Int, fallbackTimings: LongArray, fallbackAmplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { v.vibrate(VibrationEffect.createPredefined(effect)) }
        } else {
            play(fallbackTimings, fallbackAmplitudes)
        }
    }

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
