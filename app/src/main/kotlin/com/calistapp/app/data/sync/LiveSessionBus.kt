package com.calistapp.app.data.sync

import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.sync.ControlPayload
import com.calistapp.core.sync.SessionStatePayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A batch of heart-rate readings as the watch sent it — tied to its session, since a resend or a
 *  backfill can arrive after that session has already ended on the phone. */
data class HrBatch(val sessionId: String, val samples: List<HeartRateSample>)

/**
 * In-memory hub connecting the [PhoneWearListenerService] (which receives Data Layer events on a
 * background thread) to the rest of the app. The controller/VMs observe these flows.
 */
@Singleton
class LiveSessionBus @Inject constructor() {

    private val _watchState = MutableStateFlow<SessionStatePayload?>(null)
    val watchState: StateFlow<SessionStatePayload?> = _watchState.asStateFlow()

    private val _hrBatches = MutableSharedFlow<HrBatch>(extraBufferCapacity = 256)
    val hrBatches: SharedFlow<HrBatch> = _hrBatches.asSharedFlow()

    /** Commands the watch issued — start, stop, work/rest, exercise selection, logged sets. */
    private val _commands = MutableSharedFlow<ControlPayload>(extraBufferCapacity = 64)
    val commands: SharedFlow<ControlPayload> = _commands.asSharedFlow()

    fun onState(payload: SessionStatePayload) {
        _watchState.value = payload
    }

    fun onHrBatch(sessionId: String, samples: List<HeartRateSample>) {
        if (samples.isNotEmpty()) _hrBatches.tryEmit(HrBatch(sessionId, samples))
    }

    fun onControl(payload: ControlPayload) {
        _commands.tryEmit(payload)
    }

    fun clear() {
        _watchState.value = null
    }
}
