package com.calistapp.app.data.recommend

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Last night's sleep, condensed to what a readiness read needs — not just how long, but how
 * restorative (stage split, sleeping heart rate and HRV) and how consistent recent nights have been.
 */
data class SleepSnapshot(
    val hours: Double,
    val deepHours: Double?,
    val remHours: Double?,
    val lightHours: Double?,
    val awakeHours: Double?,
    /** (deep + REM) / time asleep — the restorative share. Null when no stages were logged. */
    val deepRemFraction: Double?,
    /** Average heart rate while asleep — lower than usual signals good recovery. */
    val avgHrBpm: Int?,
    /** Average RMSSD (ms) while asleep — higher signals a rested autonomic system. */
    val avgHrvMs: Double?,
    /** Std-dev of sleep length across recent nights (h) — smaller is more consistent. */
    val durationStdevH: Double?,
    /** Std-dev of bedtime across recent nights (min) — smaller is a steadier schedule. */
    val bedtimeStdevMin: Double?,
    val endedAt: Instant?,
)

/**
 * Reads sleep, sleeping heart rate and HRV from **Health Connect** (whatever writes them — Samsung
 * Health, a Galaxy Watch, another tracker). Everything degrades quietly: no Health Connect, no
 * permission, or no data → null, and readiness falls back to training load. Heart-rate and HRV are
 * best-effort on top of sleep: if only the sleep permission is granted, those fields stay null.
 */
@Singleton
class SleepRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    /** The permissions the readiness widget asks for — sleep, plus sleeping HR and HRV for recovery. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    /** The one permission we truly need; the others only enrich. */
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** True once the *sleep* read is granted — the connect-sleep button's success condition. */
    suspend fun hasPermission(): Boolean = isAvailable() && sleepPermission in granted()

    /** Sleep that ended in the last 24 hours (i.e. last night for a daytime open), or null. */
    suspend fun lastNight(now: Instant = Instant.now()): SleepSnapshot? {
        if (!isAvailable()) return null
        val grants = granted()
        if (sleepPermission !in grants) return null

        val sessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)),
            ).records
        }.getOrNull().orEmpty()
        if (sessions.isEmpty()) return null

        var deepMs = 0L
        var remMs = 0L
        var lightMs = 0L
        var awakeMs = 0L
        var asleepMs = 0L
        var hasStages = false
        var windowStart: Instant? = null
        var windowEnd: Instant? = null
        sessions.forEach { session ->
            windowStart = windowStart?.let { minOf(it, session.startTime) } ?: session.startTime
            windowEnd = windowEnd?.let { maxOf(it, session.endTime) } ?: session.endTime
            if (session.stages.isNotEmpty()) {
                session.stages.forEach { stage ->
                    val ms = Duration.between(stage.startTime, stage.endTime).toMillis()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> { deepMs += ms; asleepMs += ms; hasStages = true }
                        SleepSessionRecord.STAGE_TYPE_REM -> { remMs += ms; asleepMs += ms; hasStages = true }
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> { lightMs += ms; asleepMs += ms; hasStages = true }
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                        -> awakeMs += ms
                        else -> asleepMs += ms
                    }
                }
            } else {
                asleepMs += Duration.between(session.startTime, session.endTime).toMillis()
            }
        }
        if (asleepMs <= 0L) return null

        val start = windowStart
        val end = windowEnd
        val hrPerm = HealthPermission.getReadPermission(HeartRateRecord::class) in grants
        val hrvPerm = HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in grants
        val avgHr = if (hrPerm && start != null && end != null) averageHr(start, end) else null
        val avgHrv = if (hrvPerm && start != null && end != null) averageHrv(start, end) else null
        val consistency = consistency(now)

        fun hrs(ms: Long): Double? = if (hasStages) ms / 3_600_000.0 else null
        return SleepSnapshot(
            hours = asleepMs / 3_600_000.0,
            deepHours = hrs(deepMs),
            remHours = hrs(remMs),
            lightHours = hrs(lightMs),
            awakeHours = if (awakeMs > 0) awakeMs / 3_600_000.0 else null,
            deepRemFraction = if (hasStages) (deepMs + remMs).toDouble() / asleepMs else null,
            avgHrBpm = avgHr,
            avgHrvMs = avgHrv,
            durationStdevH = consistency?.first,
            bedtimeStdevMin = consistency?.second,
            endedAt = end,
        )
    }

    private suspend fun averageHr(start: Instant, end: Instant): Int? {
        val records = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(start, end))).records
        }.getOrNull().orEmpty()
        val bpms = records.flatMap { it.samples }.map { it.beatsPerMinute }
        return if (bpms.isEmpty()) null else (bpms.average()).toInt()
    }

    private suspend fun averageHrv(start: Instant, end: Instant): Double? {
        val records = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(start, end))).records
        }.getOrNull().orEmpty()
        val values = records.map { it.heartRateVariabilityMillis }
        return if (values.isEmpty()) null else values.average()
    }

    /** Std-dev of sleep duration (h) and bedtime (min) over the last ~8 nights, or null with <3 nights. */
    private suspend fun consistency(now: Instant): Pair<Double, Double>? {
        val sessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(8)), now)),
            ).records
        }.getOrNull().orEmpty()
        if (sessions.size < 3) return null

        // Bucket by wake date; a night's duration is its session span, its bedtime the start-of-night.
        data class Night(var durationMs: Long, val bedtimeMin: Double)
        val byNight = HashMap<java.time.LocalDate, Night>()
        sessions.forEach { s ->
            val wake = s.endTime.atZone(zone).toLocalDate()
            val startLocal = s.startTime.atZone(zone)
            // Fold evening/early-morning onto a continuous axis so 23:30 and 00:30 sit next to each other.
            val minutes = startLocal.hour * 60 + startLocal.minute + if (startLocal.hour < 12) 24 * 60 else 0
            val dur = Duration.between(s.startTime, s.endTime).toMillis()
            val existing = byNight[wake]
            if (existing == null) byNight[wake] = Night(dur, minutes.toDouble())
            else existing.durationMs += dur
        }
        if (byNight.size < 3) return null
        val durationsH = byNight.values.map { it.durationMs / 3_600_000.0 }
        val bedtimes = byNight.values.map { it.bedtimeMin }
        return stdev(durationsH) to stdev(bedtimes)
    }

    private fun stdev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}
