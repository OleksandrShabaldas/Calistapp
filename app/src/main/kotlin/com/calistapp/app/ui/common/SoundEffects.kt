package com.calistapp.app.ui.common

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * Short audible cues for the live workout — the lead-in "3·2·1·go", and the final seconds of a timed
 * hold. Sound is the one channel that reaches you when your eyes are on the bar and the phone is
 * face-up on the floor, which mid-set is exactly where it is.
 *
 * Built on the platform [ToneGenerator] rather than bundled audio: the cues are pure beeps, so
 * synthesising them needs no assets, no decoder and no media session, and the generator is cheap to
 * hold open for the length of a set.
 */
class SoundEffects {
    // Media stream, so it rides the volume the user already set for the app and stays silent when
    // they've silenced media. Created lazily and defensively — a device that refuses one just goes
    // quiet rather than taking the workout down.
    private val tone: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()
    }

    /** One of the "3 · 2 · 1" lead-in ticks, or a per-second cue near the end of a hold. */
    fun tick() = play(ToneGenerator.TONE_PROP_BEEP, 120)

    /** The brighter "go" at the end of the lead-in, or when a hold reaches its target. */
    fun go() = play(ToneGenerator.TONE_PROP_BEEP2, 300)

    private fun play(type: Int, durationMs: Int) {
        runCatching { tone?.startTone(type, durationMs) }
    }

    fun release() {
        runCatching { tone?.release() }
    }

    private companion object {
        /** Out of 100. Present but not startling in a gym. */
        const val VOLUME = 85
    }
}

/** A [SoundEffects] tied to the composition, released when the screen leaves it. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val fx = remember { SoundEffects() }
    DisposableEffect(fx) { onDispose { fx.release() } }
    return fx
}
