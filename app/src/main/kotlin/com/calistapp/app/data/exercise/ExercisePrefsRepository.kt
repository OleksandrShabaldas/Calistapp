package com.calistapp.app.data.exercise

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    val favourites: Flow<Set<String>> =
        context.exercisePrefs.data.map { it[favouritesKey] ?: emptySet() }

    suspend fun toggleFavourite(exerciseId: String) {
        context.exercisePrefs.edit { prefs ->
            val current = prefs[favouritesKey] ?: emptySet()
            prefs[favouritesKey] =
                if (exerciseId in current) current - exerciseId else current + exerciseId
        }
    }
}
