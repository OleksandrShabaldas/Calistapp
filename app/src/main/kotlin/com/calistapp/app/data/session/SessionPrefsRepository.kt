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
)

@Singleton
class SessionPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val soundKey = booleanPreferencesKey("sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    private val autoplayKey = booleanPreferencesKey("autoplay_video")
    private val handsFreeKey = booleanPreferencesKey("hands_free")

    val prefs: Flow<SessionPrefs> = context.sessionPrefs.data.map {
        SessionPrefs(
            sound = it[soundKey] ?: true,
            vibration = it[vibrationKey] ?: true,
            autoplayVideo = it[autoplayKey] ?: true,
            handsFree = it[handsFreeKey] ?: false,
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
}
