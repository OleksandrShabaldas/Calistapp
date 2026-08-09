package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One bodyweight reading.
 *
 * Keyed by the day it was taken rather than the instant, so weighing yourself twice on a Tuesday
 * corrects Tuesday instead of adding a second Tuesday — a trend built from duplicate same-day
 * readings mostly measures what time you got up.
 */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val dayMs: Long,
    val weightKg: Double,
)

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_entries ORDER BY dayMs ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY dayMs DESC LIMIT 1")
    suspend fun latest(): WeightEntryEntity?

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)
}
