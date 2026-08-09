package com.calistapp.app.data.session

import com.calistapp.app.data.local.SessionDao
import com.calistapp.app.data.local.SessionEntity
import com.calistapp.app.data.local.SessionOverviewRow
import com.calistapp.app.data.local.SessionPerformanceRow
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.Segment
import com.calistapp.core.model.SessionOverview
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.SetLog
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.model.WorkoutSession
import com.calistapp.core.progress.PerformedSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val dao: SessionDao,
    private val json: Json,
) : SessionRepository {

    override fun observeSessions(): Flow<List<SessionOverview>> =
        dao.observeOverviews().map { list -> list.map { it.toOverview() } }

    override fun observePerformed(): Flow<List<PerformedSession>> =
        dao.observePerformed().map { list -> list.map { it.toPerformed() } }

    override fun observeSession(id: String): Flow<WorkoutSession?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getSession(id: String): WorkoutSession? =
        dao.getById(id)?.toDomain()

    override suspend fun findUnfinished(): WorkoutSession? =
        dao.getUnfinished()?.toDomain()

    override suspend fun saveSession(session: WorkoutSession) {
        // Preserve any previously generated AI insight — @Upsert overwrites the whole row.
        val existing = dao.getById(session.id)
        dao.upsert(session.toEntity(existing?.aiInsight, existing?.aiInsightAtMs))
    }

    override suspend fun updateInsight(id: String, insight: String) =
        dao.updateInsight(id, insight, System.currentTimeMillis())

    override suspend fun updateNotes(id: String, notes: String) = dao.updateNotes(id, notes)

    override suspend fun updateRpe(id: String, rpe: Int?) = dao.updateRpe(id, rpe)

    override suspend fun deleteSession(id: String) = dao.delete(id)

    // ---- mapping ----

    private val sampleListSerializer = ListSerializer(HeartRateSample.serializer())
    private val segmentListSerializer = ListSerializer(Segment.serializer())
    private val setLogListSerializer = ListSerializer(SetLog.serializer())

    private fun WorkoutSession.toEntity(
        preservedInsight: String? = null,
        preservedInsightAtMs: Long? = null,
    ) = SessionEntity(
        id = id,
        exerciseType = exerciseType.name,
        startMs = startMs,
        endMs = endMs,
        status = status.name,
        exerciseName = exerciseName,
        notes = notes,
        samplesJson = json.encodeToString(sampleListSerializer, samples),
        segmentsJson = json.encodeToString(segmentListSerializer, segments),
        summaryJson = summary?.let { json.encodeToString(SessionSummary.serializer(), it) },
        aiInsight = aiInsight ?: preservedInsight,
        aiInsightAtMs = aiInsightAtMs ?: preservedInsightAtMs,
        planJson = if (plan.isEmpty) "" else json.encodeToString(WorkoutPlan.serializer(), plan),
        setLogsJson = if (setLogs.isEmpty()) "" else json.encodeToString(setLogListSerializer, setLogs),
        rpe = rpe,
    )

    private fun SessionOverviewRow.toOverview() = SessionOverview(
        id = id,
        exerciseType = runCatching { ExerciseType.valueOf(exerciseType) }.getOrDefault(ExerciseType.OTHER),
        startMs = startMs,
        endMs = endMs,
        exerciseName = exerciseName,
        summary = summaryJson?.let { json.decodeFromString(SessionSummary.serializer(), it) },
    )

    private fun SessionPerformanceRow.toPerformed(): PerformedSession {
        val summary = summaryJson?.let { json.decodeFromString(SessionSummary.serializer(), it) }
        return PerformedSession(
            id = id,
            startMs = startMs,
            kcal = summary?.totalKcal?.toInt() ?: 0,
            activeDurationMs = summary?.activeDurationMs ?: 0L,
            setLogs = setLogsJson.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(setLogListSerializer, it) }
                ?: emptyList(),
            plan = planJson.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString(WorkoutPlan.serializer(), it) }
                ?: WorkoutPlan.EMPTY,
            avgHr = summary?.avgHr ?: 0,
            totalDurationMs = summary?.totalDurationMs ?: 0L,
        )
    }

    private fun SessionEntity.toDomain() = WorkoutSession(
        id = id,
        exerciseType = runCatching { ExerciseType.valueOf(exerciseType) }.getOrDefault(ExerciseType.OTHER),
        startMs = startMs,
        endMs = endMs,
        status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.COMPLETED),
        samples = json.decodeFromString(sampleListSerializer, samplesJson),
        segments = json.decodeFromString(segmentListSerializer, segmentsJson),
        exerciseName = exerciseName,
        notes = notes,
        summary = summaryJson?.let { json.decodeFromString(SessionSummary.serializer(), it) },
        aiInsight = aiInsight,
        aiInsightAtMs = aiInsightAtMs,
        // Sessions recorded before plans existed simply have none.
        plan = planJson.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(WorkoutPlan.serializer(), it) }
            ?: WorkoutPlan.EMPTY,
        setLogs = setLogsJson.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(setLogListSerializer, it) }
            ?: emptyList(),
        rpe = rpe,
    )
}
