package com.calistapp.app.data.device

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.devicePrefs by preferencesDataStore(name = "device_prefs")

/** A heart-rate band the user picked in Settings → Devices. */
data class SavedBand(val address: String, val name: String)

data class DevicePrefs(
    /** The Bluetooth heart-rate band to stream from during workouts, or null for none. */
    val band: SavedBand? = null,
    /**
     * After a workout, fill in heart rate from Health Connect — what a ring, a band without live
     * streaming, or the watch's own background readings recorded during it. Acts only once Health
     * Connect's heart-rate permission is granted.
     */
    val fillInFromHealthConnect: Boolean = true,
)

@Singleton
class DevicePrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bandAddressKey = stringPreferencesKey("band_address")
    private val bandNameKey = stringPreferencesKey("band_name")
    private val fillInKey = booleanPreferencesKey("fill_in_health_connect")

    val prefs: Flow<DevicePrefs> = context.devicePrefs.data.map {
        val address = it[bandAddressKey]
        DevicePrefs(
            band = address?.let { a -> SavedBand(a, it[bandNameKey] ?: "Heart-rate band") },
            fillInFromHealthConnect = it[fillInKey] ?: true,
        )
    }

    suspend fun setBand(band: SavedBand?) {
        context.devicePrefs.edit {
            if (band == null) {
                it.remove(bandAddressKey)
                it.remove(bandNameKey)
            } else {
                it[bandAddressKey] = band.address
                it[bandNameKey] = band.name
            }
        }
    }

    suspend fun setFillInFromHealthConnect(on: Boolean) {
        context.devicePrefs.edit { it[fillInKey] = on }
    }
}
