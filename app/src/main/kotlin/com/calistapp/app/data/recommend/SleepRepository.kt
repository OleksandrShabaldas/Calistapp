package com.calistapp.app.data.recommend

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Last night's sleep, condensed to what the readiness prompt needs. */
data class SleepSnapshot(
    val hours: Double,
    /** Deep + REM as a share of time asleep — a rough quality proxy. Null when no stages were logged. */
    val deepRemFraction: Double?,
    val endedAt: Instant?,
)

/**
 * Reads last night's sleep from **Health Connect** (whatever writes it there — Samsung Health, a
 * Galaxy Watch, another tracker). Everything degrades quietly: if Health Connect isn't installed, or
 * the read permission hasn't been granted, or nothing slept, this returns null and the readiness
 * widget falls back to training load alone.
 */
@Singleton
class SleepRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The one permission the widget asks for. Exposed so the UI can launch the HC permission flow. */
    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermission(): Boolean =
        isAvailable() && runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)

    /** Sleep that ended in the last 24 hours (i.e. last night for a daytime open), or null. */
    suspend fun lastNight(now: Instant = Instant.now()): SleepSnapshot? {
        if (!hasPermission()) return null
        val response = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now),
                ),
            )
        }.getOrNull() ?: return null

        if (response.records.isEmpty()) return null

        var asleepMs = 0L
        var deepRemMs = 0L
        var hasStages = false
        var latestEnd: Instant? = null
        response.records.forEach { session ->
            latestEnd = latestEnd?.let { maxOf(it, session.endTime) } ?: session.endTime
            if (session.stages.isNotEmpty()) {
                hasStages = true
                session.stages.forEach { stage ->
                    val ms = Duration.between(stage.startTime, stage.endTime).toMillis()
                    if (stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE &&
                        stage.stage != SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                    ) {
                        asleepMs += ms
                    }
                    if (stage.stage == SleepSessionRecord.STAGE_TYPE_DEEP ||
                        stage.stage == SleepSessionRecord.STAGE_TYPE_REM
                    ) {
                        deepRemMs += ms
                    }
                }
            } else {
                asleepMs += Duration.between(session.startTime, session.endTime).toMillis()
            }
        }
        if (asleepMs <= 0L) return null
        return SleepSnapshot(
            hours = asleepMs / 3_600_000.0,
            deepRemFraction = if (hasStages) deepRemMs.toDouble() / asleepMs else null,
            endedAt = latestEnd,
        )
    }
}
