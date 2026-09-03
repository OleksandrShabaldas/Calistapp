package com.calistapp.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /**
     * Finished sessions, as much of each as a list row needs.
     *
     * Explicitly *not* `SELECT *`: the row carries the whole heart-rate stream as JSON, and reading
     * it to draw two lines of text meant every history repaint deserialized every reading of every
     * workout ever recorded. Naming the columns keeps that cost off the list entirely.
     *
     * Finished only — an interrupted workout is checkpointed into this same table so it survives the
     * process dying, and history is a record of what you did, not of what is still running. Written
     * as an exclusion rather than `= 'COMPLETED'` so a row with an unexpected status still surfaces
     * instead of vanishing.
     */
    @Query(
        """
        SELECT id, exerciseType, startMs, endMs, exerciseName, summaryJson
        FROM sessions
        WHERE status NOT IN ('ACTIVE', 'PAUSED')
        ORDER BY startMs DESC
        """,
    )
    fun observeOverviews(): Flow<List<SessionOverviewRow>>

    /** Finished sessions with what was performed, for building trends and personal bests. */
    @Query(
        """
        SELECT id, startMs, summaryJson, setLogsJson, planJson
        FROM sessions
        WHERE status NOT IN ('ACTIVE', 'PAUSED')
        ORDER BY startMs DESC
        """,
    )
    fun observePerformed(): Flow<List<SessionPerformanceRow>>

    /** The checkpoint of a workout that never reached its Finish, if there is one. */
    @Query("SELECT * FROM sessions WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY startMs DESC LIMIT 1")
    suspend fun getUnfinished(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET aiInsight = :insight, aiInsightAtMs = :atMs WHERE id = :id")
    suspend fun updateInsight(id: String, insight: String, atMs: Long)

    @Query("UPDATE sessions SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("UPDATE sessions SET rpe = :rpe WHERE id = :id")
    suspend fun updateRpe(id: String, rpe: Int?)

    /** Finished workouts not yet pushed to FitPal — the auto-retry + manual transfer work list. */
    @Query("SELECT * FROM sessions WHERE status NOT IN ('ACTIVE', 'PAUSED') AND endMs IS NOT NULL AND fitpalSyncedAt IS NULL ORDER BY startMs DESC")
    suspend fun getUnsyncedToFitpal(): List<SessionEntity>

    /** How many finished workouts are still waiting to reach FitPal (for the settings status line). */
    @Query("SELECT COUNT(*) FROM sessions WHERE status NOT IN ('ACTIVE', 'PAUSED') AND endMs IS NOT NULL AND fitpalSyncedAt IS NULL")
    fun observeUnsyncedToFitpalCount(): Flow<Int>

    @Query("UPDATE sessions SET fitpalSyncedAt = :atMs WHERE id = :id")
    suspend fun markFitpalSynced(id: String, atMs: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}
