package com.calistapp.app.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.calistapp.core.hr.BleHeartRate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A heart-rate band seen while scanning. */
data class FoundBand(val address: String, val name: String, val rssi: Int)

/** What a band connection is doing. */
sealed interface BandEvent {
    data object Connecting : BandEvent
    data object Connected : BandEvent

    /** One reading. [contact] false when the band says it's lost skin contact. */
    data class Reading(val bpm: Int, val atMs: Long, val contact: Boolean) : BandEvent
}

/** The band dropped the connection (out of range, switched off, battery). The caller reconnects. */
class BandDisconnected(message: String) : Exception(message)

/**
 * Bluetooth Low Energy access to standard heart-rate bands — anything exposing the Heart Rate Service
 * ([BleHeartRate]): chest straps, armbands, screenless bands, and watches with a "broadcast heart rate"
 * mode. No app is installed on the device; the phone scans for the service, connects, and subscribes
 * to its measurement notifications (~1 a second).
 *
 * Kept free of session logic — [BandHeartRateManager] decides when to connect and where readings go.
 */
@Singleton
class BleHeartRateClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** The runtime permissions scanning and connecting need on this Android version. */
    val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    fun hasBluetooth(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    /**
     * Bands advertising the Heart Rate Service nearby, strongest signal first, updating as more answer.
     * Runs until the collector stops. (A debug build also lists a simulated band — see [DebugBands].)
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<List<FoundBand>> = callbackFlow {
        val found = linkedMapOf<String, FoundBand>()
        DebugBands.devices().forEach { found[it.address] = it }
        fun publish() = trySend(found.values.sortedByDescending { it.rssi })
        publish()

        val scanner = if (hasPermissions() && isBluetoothOn()) adapter?.bluetoothLeScanner else null
        if (scanner == null) {
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address ?: return
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "Heart-rate band"
                found[address] = FoundBand(address, name, result.rssi)
                publish()
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onScanResult(0, it) }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleHeartRate.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(filter), settings, callback) }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /**
     * Connect to the band at [address] and stream its readings. Completes with [BandDisconnected] when
     * the link drops; cancelling the collector disconnects.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String): Flow<BandEvent> = DebugBands.connect(address) ?: callbackFlow {
        val device = adapter?.getRemoteDevice(address) ?: throw BandDisconnected("Bluetooth unavailable")
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        trySend(BandEvent.Connected)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> close(BandDisconnected("Disconnected (status $status)"))
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = gatt.getService(BleHeartRate.SERVICE)?.getCharacteristic(BleHeartRate.MEASUREMENT)
                if (characteristic == null) {
                    close(BandDisconnected("No heart-rate service on this device"))
                    return
                }
                gatt.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(BleHeartRate.CCCD) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                handle(characteristic, value)
            }

            @Deprecated("Pre-Android 13 delivery")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                // Android 13+ calls the overload above; only older versions arrive here.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                @Suppress("DEPRECATION")
                handle(characteristic, characteristic.value ?: return)
            }

            private fun handle(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid != BleHeartRate.MEASUREMENT) return
                val m = BleHeartRate.parse(value) ?: return
                trySend(BandEvent.Reading(m.bpm, System.currentTimeMillis(), m.contact != BleHeartRate.Contact.NOT_DETECTED))
            }
        }
        trySend(BandEvent.Connecting)
        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }
}
