package com.calistapp.app.data.exercise

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exercisePrefs by preferencesDataStore(name = "exercise_prefs")

/**
 * The handful of exercises you actually use, out of eight hundred and thirty-four.
 *
 * Kept outside the exercise table on purpose: [ExerciseSyncManager] rewrites stored rows from the
 * authored overlays on every launch, and a favourite living in that row's tags would be wiped by the
 * next sync. This is the user's data, not the catalogue's.
 */
@Singleton
class ExercisePrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val favouritesKey = stringSetPreferencesKey("favourites")
    private val hiddenKey = stringSetPreferencesKey("hidden")
    private val recentKey = stringPreferencesKey("recent")

    val favourites: Flow<Set<String>> =
        context.exercisePrefs.data.map { it[favouritesKey] ?: emptySet() }

    suspend fun toggleFavourite(exerciseId: String) {
        context.exercisePrefs.edit { prefs ->
            val current = prefs[favouritesKey] ?: emptySet()
            prefs[favouritesKey] =
                if (exerciseId in current) current - exerciseId else current + exerciseId
        }
    }

    /**
     * Exercises the user has hidden from the library.
     *
     * A dataset exercise can't be truly deleted — [ExerciseSyncManager] re-seeds it every launch — so
     * hiding it is a preference, kept here alongside favourites for the same reason: it's the user's
     * choice, not the catalogue's, and must survive the next sync. The gallery and picker filter
     * these out; the restore list in Profile brings them back.
     */
    val hiddenIds: Flow<Set<String>> =
        context.exercisePrefs.data.map { it[hiddenKey] ?: emptySet() }

    suspend fun hide(exerciseId: String) {
        context.exercisePrefs.edit { prefs ->
            prefs[hiddenKey] = (prefs[hiddenKey] ?: emptySet()) + exerciseId
        }
    }

    suspend fun unhide(exerciseId: String) {
        context.exercisePrefs.edit { prefs ->
            prefs[hiddenKey] = (prefs[hiddenKey] ?: emptySet()) - exerciseId
        }
    }

    /**
     * Recently opened or used exercise ids, most-recent-first and bounded — drives the gallery's
     * "Recent" shortcut. Stored as one ordered string because a [Set] can't keep order.
     */
    val recentIds: Flow<List<String>> =
        context.exercisePrefs.data.map { prefs ->
            prefs[recentKey]?.split(RECENT_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
        }

    suspend fun markRecent(exerciseId: String) {
        if (exerciseId.isBlank()) return
        context.exercisePrefs.edit { prefs ->
            val current = prefs[recentKey]?.split(RECENT_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()
            val next = (listOf(exerciseId) + current.filterNot { it == exerciseId }).take(RECENT_MAX)
            prefs[recentKey] = next.joinToString(RECENT_DELIMITER)
        }
    }

    private companion object {
        const val RECENT_DELIMITER = "" // unit separator — safe against ids with punctuation
        const val RECENT_MAX = 12
    }
}
