package com.calistapp.app.data.session

import com.calistapp.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeSession(id: String): Flow<WorkoutSession?>
    suspend fun getSession(id: String): WorkoutSession?
    suspend fun saveSession(session: WorkoutSession)
    suspend fun updateInsight(id: String, insight: String)
    suspend fun deleteSession(id: String)
}
