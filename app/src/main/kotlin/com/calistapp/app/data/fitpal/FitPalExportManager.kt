package com.calistapp.app.data.fitpal

import com.calistapp.app.data.profile.ProfileRepository
import com.calistapp.app.data.session.SessionRepository
import com.calistapp.core.model.HrZone
import com.calistapp.core.model.SessionSummary
import com.calistapp.core.model.WorkoutSession
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Result of trying to push the whole backlog. */
sealed interface PushOutcome {
    data class Pushed(val count: Int) : PushOutcome
    data object Unavailable : PushOutcome
    data object NothingToDo : PushOutcome
}

/**
 * Owns the Calistapp → FitPal exercise flow. Maps a finished [WorkoutSession] to ONE FitPal activity
 * row carrying Calistapp's HR-based total calories (FitPal stores it verbatim), with the exercise
 * breakdown + intensity packed into a details blob. De-duped by session id, so an automatic push and
 * a later manual "Transfer to FitPal" of the same workout update the same row.
 *
 * Every completed workout is pushed the moment it's saved; anything that fails (FitPal closed / not
 * installed) stays flagged unsynced and is retried on app open and by the manual button.
 */
@Singleton
class FitPalExportManager @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val client: FitPalSyncClient,
) {
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    /** Push one workout; on success, stamp it synced. Safe to call fire-and-forget. */
    suspend fun push(session: WorkoutSession): SyncResult {
        val summary = session.summary ?: return SyncResult.Error("no summary")
        val profile = profileRepository.profile.first()
        val payload = buildPayload(session, summary, profile.weightKg, profile.effectiveMaxHr)
        val result = client.upsertExercise(payload)
        if (result is SyncResult.Success) sessionRepository.markFitpalSynced(session.id)
        return result
    }

    /** Retry every finished-but-unsynced workout (the on-open catch-up + manual button). */
    suspend fun pushAllUnsynced(): PushOutcome {
        val pending = sessionRepository.getUnsyncedToFitpal()
        if (pending.isEmpty()) return PushOutcome.NothingToDo
        if (!client.isFitPalAvailable()) return PushOutcome.Unavailable
        var pushed = 0
        for (session in pending) {
            when (push(session)) {
                is SyncResult.Success -> pushed++
                SyncResult.Unavailable -> return if (pushed > 0) PushOutcome.Pushed(pushed) else PushOutcome.Unavailable
                is SyncResult.Error -> { /* skip this one, keep going */ }
            }
        }
        return PushOutcome.Pushed(pushed)
    }

    private fun buildPayload(
        session: WorkoutSession,
        summary: SessionSummary,
        weightKg: Double,
        maxHr: Int,
    ): ExercisePayload {
        val date = Instant.ofEpochMilli(session.startMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)
        val minutes = (summary.totalDurationMs / 60_000L).toInt().coerceAtLeast(1)
        val calories = summary.totalKcal.toFloat().coerceAtLeast(0f)
        val name = session.exerciseName?.takeIf { it.isNotBlank() }
            ?: "${session.exerciseType.displayName} workout"
        val hrForZone = summary.avgActiveHr.takeIf { it > 0 } ?: summary.avgHr
        val intensity = if (hrForZone > 0 && maxHr > 0) HrZone.forHr(hrForZone, maxHr).label else null

        // Equivalent MET purely for FitPal's display (calories are the authoritative field): the
        // HR-based kcal back-solved through kcal = MET × kg × hours.
        val hours = summary.totalDurationMs / 3_600_000.0
        val met = if (hours > 0 && weightKg > 0) (calories / (weightKg * hours)).toFloat().coerceIn(1f, 23f) else 0f

        val details = JSONObject().apply {
            put("type", session.exerciseType.displayName)
            intensity?.let { put("intensity", it) }
            put("avgHr", summary.avgHr)
            put("avgActiveHr", summary.avgActiveHr)
            put("peakHr", summary.peakHr)
            put("totalReps", summary.totalReps)
            session.rpe?.let { put("rpe", it) }
            put("activeMinutes", (summary.activeDurationMs / 60_000L).toInt())
            if (summary.perExercise.isNotEmpty()) {
                put("exercises", JSONArray().apply {
                    summary.perExercise.forEach { ex ->
                        put(JSONObject().apply {
                            put("name", ex.exerciseName)
                            put("kcal", ex.kcal)
                            put("reps", ex.reps)
                            put("sets", ex.sets)
                        })
                    }
                })
            }
        }.toString()

        return ExercisePayload(
            externalId = session.id,
            date = date,
            name = name,
            minutes = minutes,
            met = met,
            calories = calories,
            detailsJson = details,
            startMs = session.startMs,
        )
    }
}
