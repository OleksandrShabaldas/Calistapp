package com.calistapp.app.data.recommend

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Where you are, coarsely — enough for a weather lookup, never stored or sent anywhere but Open-Meteo. */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** A current coarse fix, falling back to the last known one; null if permission is off or none exists. */
    @SuppressLint("MissingPermission")
    suspend fun currentOrLast(): Coordinates? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token,
            ).await()
        }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull()
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }
}

data class Coordinates(val latitude: Double, val longitude: Double)
