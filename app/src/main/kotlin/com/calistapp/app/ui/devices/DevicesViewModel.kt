package com.calistapp.app.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calistapp.app.data.device.BandHeartRateManager
import com.calistapp.app.data.device.BandState
import com.calistapp.app.data.device.BleHeartRateClient
import com.calistapp.app.data.device.DevicePrefs
import com.calistapp.app.data.device.DevicePrefsRepository
import com.calistapp.app.data.device.FoundBand
import com.calistapp.app.data.device.HeartRateFillIn
import com.calistapp.app.data.device.SavedBand
import com.calistapp.app.data.sync.WatchConnectionMonitor
import com.calistapp.app.data.sync.WatchLinkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Devices: the heart-rate band, the Health Connect fill-in, and the watch. */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val client: BleHeartRateClient,
    private val band: BandHeartRateManager,
    private val prefs: DevicePrefsRepository,
    private val fillIn: HeartRateFillIn,
    private val watch: WatchConnectionMonitor,
) : ViewModel() {

    val devicePrefs: StateFlow<DevicePrefs> = prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicePrefs())
    val bandState: StateFlow<BandState> = band.state
    val watchLink: StateFlow<WatchLinkState> = watch.state

    val bluetoothPermissions: Array<String> get() = client.requiredPermissions
    val healthPermission: String get() = fillIn.permission

    /** Readiness of the phone itself — rechecked whenever the screen asks (after a permission prompt, say). */
    data class Readiness(
        val hasBluetooth: Boolean = true,
        val bluetoothOn: Boolean = true,
        val bluetoothAllowed: Boolean = true,
        val healthConnectAvailable: Boolean = true,
        val healthAllowed: Boolean = false,
    )

    private val _readiness = MutableStateFlow(Readiness())
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    /** Scan results while looking; null when not scanning. */
    private val _found = MutableStateFlow<List<FoundBand>?>(null)
    val found: StateFlow<List<FoundBand>?> = _found.asStateFlow()

    /** "Filled in 2 workouts" / "Nothing new to fill in" after a manual check. */
    private val _checkResult = MutableStateFlow<String?>(null)
    val checkResult: StateFlow<String?> = _checkResult.asStateFlow()

    private var scanJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _readiness.value = Readiness(
            hasBluetooth = client.hasBluetooth(),
            bluetoothOn = client.isBluetoothOn(),
            bluetoothAllowed = client.hasPermissions(),
            healthConnectAvailable = fillIn.isAvailable(),
            healthAllowed = fillIn.hasPermission(),
        )
    }

    /** Look for bands for up to [SCAN_MS]; results stream into [found]. */
    fun startScan() {
        scanJob?.cancel()
        _found.value = emptyList()
        scanJob = viewModelScope.launch {
            val collector = launch { client.scan().collect { _found.value = it } }
            delay(SCAN_MS)
            collector.cancel()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _found.value = null
    }

    fun choose(found: FoundBand) = viewModelScope.launch {
        stopScan()
        val saved = SavedBand(found.address, found.name)
        prefs.setBand(saved)
        band.test(saved)
    }

    fun test() = viewModelScope.launch {
        devicePrefs.value.band?.let { band.test(it) }
    }

    fun forget() = viewModelScope.launch {
        band.stopTest()
        prefs.setBand(null)
    }

    fun setFillIn(on: Boolean) = viewModelScope.launch { prefs.setFillInFromHealthConnect(on) }

    fun checkNow() = viewModelScope.launch {
        _checkResult.value = null
        val n = fillIn.run()
        _checkResult.value = when (n) {
            0 -> "Nothing new to fill in"
            1 -> "Filled in 1 workout"
            else -> "Filled in $n workouts"
        }
    }

    fun reconnectWatch() = watch.reconnect()

    override fun onCleared() {
        band.stopTest()
    }

    private companion object {
        const val SCAN_MS = 15_000L
    }
}
