package com.calistapp.app.data.device

import com.calistapp.app.data.sync.HrSource
import com.calistapp.app.data.sync.LiveSessionBus
import com.calistapp.app.di.ApplicationScope
import com.calistapp.app.session.SessionController
import com.calistapp.core.model.HeartRateSample
import com.calistapp.core.model.HrConfidence
import com.calistapp.core.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where the chosen heart-rate band stands — for the live screen, the setup screen and Settings. */
sealed interface BandState {
    /** No band chosen, or no workout to stream into (outside a test from Settings). */
    data object None : BandState
    data class Connecting(val name: String) : BandState
    data class Streaming(val name: String, val bpm: Int, val contact: Boolean) : BandState

    /** The link dropped; trying again by itself. */
    data class Reconnecting(val name: String) : BandState
    data class NeedsPermission(val name: String) : BandState
    data class BluetoothOff(val name: String) : BandState

    val bandName: String? get() = when (this) {
        None -> null
        is Connecting -> name
        is Streaming -> name
        is Reconnecting -> name
        is NeedsPermission -> name
        is BluetoothOff -> name
    }
}

/**
 * Streams the chosen band's heart rate into the running workout — the same pipeline the watch feeds
 * ([LiveSessionBus.onHrBatch]), so calories, the HUD, the chart and the saved record work exactly as
 * they do with the watch. Connects when a workout starts, reconnects by itself if the band drops out
 * (out of range, a strap that lost contact), and lets go when the workout ends.
 *
 * Settings → Devices can also open a short [test] connection to show the band's live reading.
 */
@Singleton
class BandHeartRateManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val client: BleHeartRateClient,
    private val prefs: DevicePrefsRepository,
    private val controller: SessionController,
    private val bus: LiveSessionBus,
) {
    private val _state = MutableStateFlow<BandState>(BandState.None)
    val state: StateFlow<BandState> = _state.asStateFlow()

    private var started = false
    private var testJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val running = controller.live
                .map { live -> live?.takeIf { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }?.id }
                .distinctUntilChanged()
            combine(running, prefs.prefs.map { it.band }.distinctUntilChanged()) { id, band -> id to band }
                .distinctUntilChanged()
                .collectLatest { (sessionId, band) ->
                    if (sessionId == null || band == null) {
                        if (testJob?.isActive != true) _state.value = BandState.None
                        return@collectLatest
                    }
                    testJob?.cancel()
                    stream(band) { reading ->
                        bus.onHrBatch(
                            sessionId,
                            listOf(HeartRateSample(reading.atMs, reading.bpm, HrConfidence.HIGH)),
                            HrSource.BAND,
                        )
                    }
                }
        }
    }

    /**
     * Connect to [band] for a minute to show its live reading in Settings — unless a workout is already
     * streaming from it, in which case that connection's state is what's shown.
     */
    fun test(band: SavedBand) {
        if (controller.isRunning) return
        testJob?.cancel()
        testJob = scope.launch {
            kotlinx.coroutines.withTimeoutOrNull(TEST_MS) { stream(band) { } }
            _state.value = BandState.None
        }
    }

    fun stopTest() {
        testJob?.cancel()
        testJob = null
        if (!controller.isRunning) _state.value = BandState.None
    }

    /** Keep a connection to [band] going until cancelled, reconnecting with a short backoff. */
    private suspend fun stream(band: SavedBand, onReading: (BandEvent.Reading) -> Unit) {
        var backoffMs = 1_000L
        while (true) {
            when {
                !client.hasPermissions() -> _state.value = BandState.NeedsPermission(band.name)
                !client.isBluetoothOn() && !band.address.startsWith("SIM:") -> _state.value = BandState.BluetoothOff(band.name)
                else -> runCatching {
                    client.connect(band.address).collect { event ->
                        when (event) {
                            BandEvent.Connecting -> if (_state.value !is BandState.Reconnecting) _state.value = BandState.Connecting(band.name)
                            BandEvent.Connected -> backoffMs = 1_000L
                            is BandEvent.Reading -> {
                                _state.value = BandState.Streaming(band.name, event.bpm, event.contact)
                                // A strap off the skin reports nonsense; keep it out of the record.
                                if (event.contact) onReading(event)
                            }
                        }
                    }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
            if (_state.value is BandState.Streaming || _state.value is BandState.Connecting) {
                _state.value = BandState.Reconnecting(band.name)
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(10_000L)
        }
    }

    private companion object {
        const val TEST_MS = 60_000L
    }
}
