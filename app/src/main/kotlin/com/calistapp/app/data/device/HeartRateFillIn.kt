package com.calistapp.app.data.device

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.calistapp.app.data.fitpal.FitPalExportManager
import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.app.di.ApplicationScope
import com.calistapp.core.calorie.CalorieEngine
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrConfidence
import com.calistapp.core.model.mergeReadings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills in heart rate for finished workouts that have none (or too little) from **Health Connect** —
 * whatever wrote it there during the workout: a smart ring or a band that can't stream live (through
 * Samsung Health, Oura, Zepp, Mi Fitness, Garmin Connect, Fitbit…), or the watch's own background
 * readings when the Calistapp watch app wasn't tracking. Calistapp only reads Health Connect, never
 * writes to it, so nothing it reads back is its own.
 *
 * Those apps sync on their own schedule — minutes to hours after the workout — so this runs whenever
 * the app comes to the foreground (throttled) and looks back over the last few days. A workout that
 * gains enough heart rate is rescored by the calorie engine from the real readings; one that gains a
 * few sparse ones keeps its estimate but shows them in its chart.
 */
@Singleton
class HeartRateFillIn @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val sessions: SessionRepository,
    private val engine: CalorieEngine,
    private val profiles: ProfileRepository,
    private val prefs: DevicePrefsRepository,
    private val fitPal: FitPalExportManager,
) {
    val permission: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val mutex = Mutex()
    private var lastRunMs = 0L

    /** When it last filled a workout in, and which — for the Devices screen. */
    private val _lastFilled = MutableStateFlow<Long?>(null)
    val lastFilled: StateFlow<Long?> = _lastFilled.asStateFlow()

    fun isAvailable(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermission(): Boolean = isAvailable() &&
        runCatching { permission in client.permissionController.getGrantedPermissions() }.getOrDefault(false)

    /** On every foreground, at most every few minutes. */
    fun runIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastRunMs < THROTTLE_MS) return
        lastRunMs = now
        scope.launch { runCatching { run() } }
    }

    /** Look back over recent workouts and fill in what Health Connect has. Returns how many changed. */
    suspend fun run(): Int = mutex.withLock {
        if (!prefs.prefs.first().fillInFromHealthConnect || !hasPermission()) return 0
        val now = System.currentTimeMillis()
        val candidates = sessions.observeSessions().first().filter { s ->
            val end = s.endMs ?: return@filter false
            now - end < LOOK_BACK_MS && (s.summary == null || s.summary?.estimated == true || s.avgHr == 0)
        }
        var changed = 0
        val profile = profiles.profile.first()
        for (overview in candidates) {
            val session = sessions.getSession(overview.id) ?: continue
            val end = session.endMs ?: continue
            val readings = read(session.startMs, end)
            if (readings.isEmpty()) continue
            val merged = session.samples.toMutableList()
            if (merged.mergeReadings(readings) == 0) continue
            val summary = engine.compute(merged, session.segments, profile, endMs = end, plan = session.plan)
            val updated = session.copy(samples = merged, summary = summary)
            runCatching { sessions.saveSession(updated) }.onSuccess {
                changed++
                _lastFilled.value = now
                scope.launch { runCatching { fitPal.push(updated) } }
            }
        }
        changed
    }

    private suspend fun read(startMs: Long, endMs: Long): List<HeartRateSample> {
        val records = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    TimeRangeFilter.between(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)),
                ),
            ).records
        }.getOrNull().orEmpty()
        return records.flatMap { record ->
            record.samples.map { HeartRateSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt(), HrConfidence.MEDIUM) }
        }.filter { it.timestampMs in startMs..endMs && it.bpm > 0 }.sortedBy { it.timestampMs }
    }

    private companion object {
        const val THROTTLE_MS = 5 * 60_000L
        const val LOOK_BACK_MS = 3 * 24 * 60 * 60_000L
    }
}
