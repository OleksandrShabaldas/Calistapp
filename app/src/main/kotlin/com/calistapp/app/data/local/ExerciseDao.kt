package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    // Both reads span several 2 MB cursor windows (834 exercises with their coaching text) while the
    // launch sync rewrites rows; a transaction keeps each read one consistent snapshot instead of
    // failing mid-way with "Couldn't read row N from CursorWindow". See SessionDao.observeOverviews.
    @Transaction
    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Transaction
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExerciseEntity>

    @Upsert
    suspend fun upsertAll(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)
}
