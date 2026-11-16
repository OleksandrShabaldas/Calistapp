package com.calistapp.app.data.device

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * DEBUG BUILDS ONLY. A simulated heart-rate band, so the band path (Settings → Devices, a live workout
 * streaming from a band, losing and regaining it) can be exercised on the emulator, which has no real
 * band to find. It lives in the debug source set; the release build compiles an empty stand-in
 * (`src/release/.../DebugBands.kt`), so no simulated heart rate exists in the app you install.
 */
object DebugBands {
    private const val ADDRESS = "SIM:00:00:00:00:01"

    fun devices(): List<FoundBand> = listOf(FoundBand(ADDRESS, "Simulated band (debug)", rssi = -40))

    /** Readings once a second: a set-and-rest rhythm between ~100 and ~150 bpm with a little noise. */
    fun connect(address: String): Flow<BandEvent>? {
        if (address != ADDRESS) return null
        return flow {
            emit(BandEvent.Connecting)
            delay(700)
            emit(BandEvent.Connected)
            val start = System.currentTimeMillis()
            while (true) {
                val t = (System.currentTimeMillis() - start) / 1000.0
                val effort = (sin(2 * PI * t / 120.0) + 1) / 2 // a two-minute work/rest cycle
                val bpm = (100 + 50 * effort + Random.nextDouble(-3.0, 3.0)).roundToInt()
                emit(BandEvent.Reading(bpm, System.currentTimeMillis(), contact = true))
                delay(1_000)
            }
        }
    }
}
