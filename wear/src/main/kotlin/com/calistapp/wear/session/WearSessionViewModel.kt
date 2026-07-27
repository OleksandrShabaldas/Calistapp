package com.calistapp.wear.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.core.model.ExerciseType
import com.calistapp.core.model.UserProfile
import com.calistapp.core.sync.WearJson
import com.calistapp.core.sync.WearSync
import com.calistapp.wear.sync.WearProfileHolder
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Thin adapter between the watch UI and [WearSessionManager].
 *
 * Intentionally holds no session state of its own: the workout has to outlive this ViewModel (the
 * Activity is destroyed whenever the screen blanks mid-set), so the manager owns it and this just
 * forwards intent and re-exposes the stream.
 */
class WearSessionViewModel(app: Application) : AndroidViewModel(app) {

    val state: StateFlow<WearSessionState>

    init {
        WearSessionManager.attach(app)
        state = WearSessionManager.state

        // The profile may have synced down before this app was ever opened; DataClient keeps the
        // last value, so read it once rather than waiting for the next change event.
        viewModelScope.launch {
            runCatching {
                val items = Wearable.getDataClient(getApplication()).dataItems.await()
                items.forEach { item ->
                    if (item.uri.path == WearSync.PATH_PROFILE) {
                        item.data?.let {
                            WearProfileHolder.update(WearJson.decodeFromString(UserProfile.serializer(), String(it)))
                        }
                    }
                }
                items.release()
            }
        }
    }

    fun start(type: ExerciseType) {
        WearSessionService.start(getApplication())
        WearSessionManager.startLocal(type)
    }

    fun stop() {
        WearSessionManager.stopLocal()
        WearSessionService.stop(getApplication())
    }

    fun toggleSegment() = WearSessionManager.toggleSegmentLocal()
    fun adjustReps(delta: Int) = WearSessionManager.adjustReps(delta)
    fun nextExercise() = WearSessionManager.advanceToNextLocal()
    fun reconnect() = WearSessionManager.reconnectPhone()
}
