package com.calistapp.app.data.profile

import com.calistapp.app.data.local.WeightDao
import com.calistapp.app.data.local.WeightEntryEntity
import com.calistapp.core.progress.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bodyweight over time.
 *
 * Fed automatically from the profile rather than through a separate logging screen: the profile
 * already holds a current weight that has to be kept up to date for the calorie estimate to mean
 * anything, so every time it changes there is a dated reading worth keeping. Nobody has to remember
 * to log anything they weren't already going to update.
 */
@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightDao,
) {
    val entries: Flow<List<WeightEntry>> =
        dao.observeAll().map { list -> list.map { WeightEntry(it.dayMs, it.weightKg) } }

    /**
     * Record [weightKg] against today, unless today already has the same reading.
     *
     * Same-day re-entry overwrites rather than appends — a trend built from several readings taken
     * on one morning is measuring hydration, not progress.
     */
    suspend fun record(weightKg: Double, atMs: Long = System.currentTimeMillis()) {
        if (weightKg <= 0.0) return
        val day = startOfDay(atMs)
        val existing = dao.latest()
        if (existing?.dayMs == day && existing.weightKg == weightKg) return
        dao.upsert(WeightEntryEntity(dayMs = day, weightKg = weightKg))
    }

    private fun startOfDay(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
