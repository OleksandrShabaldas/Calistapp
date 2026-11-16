package com.calistapp.app.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionPrefs by preferencesDataStore(name = "session_prefs")

/**
 * The live-workout toggles surfaced on the pause screen. Persisted so a preference — muting the
 * cue tones, say — survives the next workout rather than resetting each session.
 */
data class SessionPrefs(
    /** Play the tick/go cue tones during countdowns and holds. */
    val sound: Boolean = true,
    /** Buzz on rest-over and phase changes. */
    val vibration: Boolean = true,
    /** Auto-play the demonstration video (vs. tap to start it). */
    val autoplayVideo: Boolean = true,
    /** Speak cues aloud so the phone needn't be touched. */
    val handsFree: Boolean = false,
    /**
     * When a workout has been done before, open each set's counter on the reps you actually did last
     * time (scoped to that same saved workout) instead of the plan's target — so you're matching or
     * beating yourself. On by default; a setting turns it off to always show the planned target.
     */
    val startFromLastTime: Boolean = true,
    /**
     * Run the warm-up and cool-down as a guided routine: each move counts down its hold and the next
     * one starts by itself, so the phone needn't be touched mid-stretch. On by default.
     */
    val autoAdvanceRoutines: Boolean = true,
    /**
     * Keep the phone's screen on for as long as a workout is running and Calistapp is on screen — on
     * every screen, not only the live one (opening an exercise's info mid-workout used to let it sleep).
     */
    val keepScreenOn: Boolean = true,
)

@Singleton
class SessionPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val soundKey = booleanPreferencesKey("sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    private val autoplayKey = booleanPreferencesKey("autoplay_video")
    private val handsFreeKey = booleanPreferencesKey("hands_free")
    private val startFromLastTimeKey = booleanPreferencesKey("start_from_last_time")
    private val autoAdvanceKey = booleanPreferencesKey("auto_advance_routines")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    val prefs: Flow<SessionPrefs> = context.sessionPrefs.data.map {
        SessionPrefs(
            sound = it[soundKey] ?: true,
            vibration = it[vibrationKey] ?: true,
            autoplayVideo = it[autoplayKey] ?: true,
            handsFree = it[handsFreeKey] ?: false,
            startFromLastTime = it[startFromLastTimeKey] ?: true,
            autoAdvanceRoutines = it[autoAdvanceKey] ?: true,
            keepScreenOn = it[keepScreenOnKey] ?: true,
        )
    }

    suspend fun setSound(on: Boolean) {
        context.sessionPrefs.edit { it[soundKey] = on }
    }

    suspend fun setVibration(on: Boolean) {
        context.sessionPrefs.edit { it[vibrationKey] = on }
    }

    suspend fun setAutoplay(on: Boolean) {
        context.sessionPrefs.edit { it[autoplayKey] = on }
    }

    suspend fun setHandsFree(on: Boolean) {
        context.sessionPrefs.edit { it[handsFreeKey] = on }
    }

    suspend fun setStartFromLastTime(on: Boolean) {
        context.sessionPrefs.edit { it[startFromLastTimeKey] = on }
    }

    suspend fun setAutoAdvanceRoutines(on: Boolean) {
        context.sessionPrefs.edit { it[autoAdvanceKey] = on }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        context.sessionPrefs.edit { it[keepScreenOnKey] = on }
    }
}
