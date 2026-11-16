package com.calistapp.wear.session

import android.content.Context
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.RoundOrder
import com.calistapp.core.model.SegmentType
import com.calistapp.core.model.SessionStatus
import com.calistapp.core.model.WorkoutPlan
import com.calistapp.core.sync.WearJson
import kotlinx.serialization.Serializable

/**
 * The watch's running workout, written to disk at every structural change so it survives the app's
 * process being restarted.
 *
 * The watch used to keep its workout in memory only. When its process died mid-workout (Nov 8: two
 * heart-rate readings, then nothing, and the watch back on its start screen eight minutes later) the
 * workout was simply gone — heart rate stopped, and nothing on either device brought it back. Now the
 * keep-alive service's restart (or any wake-up of the app) picks the workout up again from here, and
 * the phone's rejoin re-sends it if this copy is lost too.
 */
@Serializable
data class SavedWatchSession(
    val sessionId: String,
    val exerciseType: ExerciseType,
    val plan: WorkoutPlan,
    val startMs: Long,
    val status: SessionStatus,
    val currentSegment: SegmentType,
    val currentSlotId: String? = null,
    val setIndex: Int = 1,
    val completedSets: Map<String, Int> = emptyMap(),
    val deferred: Set<String> = emptySet(),
    val roundOrder: RoundOrder? = null,
    val savedAtMs: Long,
)

class WearSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("wear_session", Context.MODE_PRIVATE)

    fun save(session: SavedWatchSession) {
        prefs.edit().putString(KEY, WearJson.encodeToString(SavedWatchSession.serializer(), session)).apply()
    }

    fun load(): SavedWatchSession? = prefs.getString(KEY, null)?.let {
        runCatching { WearJson.decodeFromString(SavedWatchSession.serializer(), it) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "running"
    }
}
