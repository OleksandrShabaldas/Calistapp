package com.calistapp.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Short audible cues for the live workout — the lead-in "3·2·1·go", and the final seconds of a timed
 * hold. Sound is the one channel that reaches you when your eyes are on the bar and the phone is
 * face-up on the floor, which mid-set is exactly where it is.
 *
 * Built on the platform [ToneGenerator] rather than bundled audio: the cues are pure beeps, so
 * synthesising them needs no assets, no decoder and no media session, and the generator is cheap to
 * hold open for the length of a set.
 *
 * **Cuts through music.** Each cue briefly requests transient audio focus that permits ducking, so
 * whatever the user is listening to dips for the length of the beep and the cue is actually audible
 * over it — a "3·2·1" you can't hear is no cue at all. Focus is released a short beat after the last
 * cue (re-scheduled on every cue so a run of ticks doesn't thrash it), restoring the music's volume.
 */
class SoundEffects(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Media stream, so it rides the volume the user already set for the app and stays silent when
    // they've silenced media. Created lazily and defensively — a device that refuses one just goes
    // quiet rather than taking the workout down.
    private val tone: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }.getOrNull()
    }

    // Transient, ducking-friendly focus: it lowers other apps' audio without stopping it, which is
    // exactly right for a half-second beep over a playlist.
    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .build()

    private var holdingFocus = false
    private val releaseFocus = Runnable { abandonFocus() }

    /** One of the "3 · 2 · 1" lead-in ticks, or a per-second cue near the end of a hold. */
    fun tick() = play(ToneGenerator.TONE_PROP_BEEP, 120)

    /** The brighter "go" at the end of the lead-in, or when a hold reaches its target. */
    fun go() = play(ToneGenerator.TONE_PROP_BEEP2, 300)

    private fun play(type: Int, durationMs: Int) {
        duckOtherAudio(durationMs)
        runCatching { tone?.startTone(type, durationMs) }
    }

    /** Grab (or keep) ducking focus, and schedule its release for just after this cue ends. */
    private fun duckOtherAudio(durationMs: Int) {
        val am = audioManager ?: return
        if (!holdingFocus) {
            holdingFocus = runCatching {
                am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }.getOrDefault(false)
        }
        // Re-arm the release: a run of ticks keeps the music ducked until the last one finishes.
        mainHandler.removeCallbacks(releaseFocus)
        mainHandler.postDelayed(releaseFocus, durationMs + FOCUS_TAIL_MS)
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        if (holdingFocus) {
            runCatching { am.abandonAudioFocusRequest(focusRequest) }
            holdingFocus = false
        }
    }

    fun release() {
        mainHandler.removeCallbacks(releaseFocus)
        abandonFocus()
        runCatching { tone?.release() }
    }

    private companion object {
        /** Out of 100. Present but not startling in a gym. */
        const val VOLUME = 90

        /** How long to keep the music ducked after a cue ends, so the tail isn't clipped. */
        const val FOCUS_TAIL_MS = 250L
    }
}

/** A [SoundEffects] tied to the composition, released when the screen leaves it. */
@Composable
fun rememberSoundEffects(): SoundEffects {
    val context = LocalContext.current
    val fx = remember { SoundEffects(context) }
    DisposableEffect(fx) { onDispose { fx.release() } }
    return fx
}
