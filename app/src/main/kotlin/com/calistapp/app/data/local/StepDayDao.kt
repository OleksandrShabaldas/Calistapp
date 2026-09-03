package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDayDao {

    /** Insert or replace a day's imported steps (date is the primary key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StepDayEntity)

    @Query("SELECT * FROM step_days WHERE date = :date")
    fun observeForDate(date: String): Flow<StepDayEntity?>

    @Query("SELECT * FROM step_days WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<StepDayEntity>>

    /** Which days in a range already have imported steps — used to find the gaps to re-pull. */
    @Query("SELECT date FROM step_days WHERE date BETWEEN :from AND :to")
    suspend fun existingDates(from: String, to: String): List<String>

    /** Most recent import time across all days (epoch millis; null = never imported). */
    @Query("SELECT MAX(importedAtMs) FROM step_days")
    fun observeLastImportedAt(): Flow<Long?>

    @Query("SELECT * FROM step_days WHERE date = :date")
    suspend fun getForDate(date: String): StepDayEntity?
}
