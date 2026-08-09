package com.calistapp.app.data.profile

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.calistapp.core.model.Sex
import com.calistapp.core.model.TrainingGoals
import com.calistapp.core.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "profile")

/**
 * Stores the user's physiological profile. These values are the personalization backbone —
 * every calorie estimate depends on them, so we prompt the user to fill them in on first run.
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val name = stringPreferencesKey("name")
        val sex = stringPreferencesKey("sex")
        val age = intPreferencesKey("age")
        val weight = doublePreferencesKey("weight")
        val height = doublePreferencesKey("height")
        val restingHr = intPreferencesKey("resting_hr")
        val maxHr = intPreferencesKey("max_hr")
        val vo2Max = doublePreferencesKey("vo2max")
        val onboarded = booleanPreferencesKey("onboarded")
        val weeklyKcal = intPreferencesKey("goal_weekly_kcal")
        val weeklySessions = intPreferencesKey("goal_weekly_sessions")
    }

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        UserProfile(
            name = p[Keys.name] ?: "",
            sex = p[Keys.sex]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.MALE,
            ageYears = p[Keys.age] ?: 30,
            weightKg = p[Keys.weight] ?: 75.0,
            heightCm = p[Keys.height] ?: 178.0,
            restingHr = p[Keys.restingHr] ?: 60,
            maxHr = p[Keys.maxHr],
            vo2Max = p[Keys.vo2Max],
        )
    }

    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboarded] ?: false }

    /** What the week is measured against. Stored alongside the profile; unset means the defaults. */
    val goals: Flow<TrainingGoals> = context.dataStore.data.map { p ->
        TrainingGoals(
            weeklyKcal = p[Keys.weeklyKcal] ?: TrainingGoals.DEFAULT_WEEKLY_KCAL,
            weeklySessions = p[Keys.weeklySessions] ?: TrainingGoals.DEFAULT_WEEKLY_SESSIONS,
        )
    }

    suspend fun saveGoals(goals: TrainingGoals) {
        context.dataStore.edit { p ->
            p[Keys.weeklyKcal] = goals.weeklyKcal
            p[Keys.weeklySessions] = goals.weeklySessions
        }
    }

    suspend fun save(profile: UserProfile) {
        context.dataStore.edit { p ->
            p[Keys.name] = profile.name
            p[Keys.sex] = profile.sex.name
            p[Keys.age] = profile.ageYears
            p[Keys.weight] = profile.weightKg
            p[Keys.height] = profile.heightCm
            p[Keys.restingHr] = profile.restingHr
            profile.maxHr?.let { p[Keys.maxHr] = it } ?: p.remove(Keys.maxHr)
            profile.vo2Max?.let { p[Keys.vo2Max] = it } ?: p.remove(Keys.vo2Max)
            p[Keys.onboarded] = true
        }
    }
}
