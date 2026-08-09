package com.calistapp.app.data.session

import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.progress.PerformedSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /**
     * Finished sessions, newest first, without their heart-rate streams. Excludes a workout that is
     * still in progress. Use [observeSession] when the samples are actually needed.
     */
    fun observeSessions(): Flow<List<SessionOverview>>

    /** Finished sessions with what was performed, for trends and personal bests. No samples. */
    fun observePerformed(): Flow<List<PerformedSession>>

    fun observeSession(id: String): Flow<WorkoutSession?>
    suspend fun getSession(id: String): WorkoutSession?

    /**
     * The checkpoint left behind by a workout that never finished — the app was killed, or the
     * device rebooted, mid-session. Null when the last session ended cleanly.
     */
    suspend fun findUnfinished(): WorkoutSession?

    suspend fun saveSession(session: WorkoutSession)
    suspend fun updateInsight(id: String, insight: String)
    suspend fun updateNotes(id: String, notes: String)

    /** Rating of perceived exertion, 1–10. Null clears it. */
    suspend fun updateRpe(id: String, rpe: Int?)
    suspend fun deleteSession(id: String)
}
