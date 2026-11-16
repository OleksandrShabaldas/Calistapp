package com.calistapp.core.hr

import java.util.UUID

/**
 * The Bluetooth SIG **Heart Rate Service** — the one standard every chest strap, armband and screenless
 * band that "broadcasts heart rate" speaks (Polar, Garmin HRM, Wahoo, Whoop's broadcast mode, Huawei's HR
 * data broadcast, Fitbit/Pixel heart-rate sharing…). Talking to it needs no app on the device: the phone
 * subscribes to notifications on [MEASUREMENT] and gets a reading roughly every second.
 */
object BleHeartRate {
    /** Heart Rate Service, 0x180D. */
    val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Measurement characteristic, 0x2A37 — notify only. */
    val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration descriptor, 0x2902 — written to switch notifications on. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** One Heart Rate Measurement notification, decoded. */
    data class Measurement(
        val bpm: Int,
        /** Whether the strap reports skin contact at all, and if so whether it has it. */
        val contact: Contact,
        /** Beat-to-beat intervals in milliseconds, when the strap sends them. */
        val rrIntervalsMs: List<Int> = emptyList(),
    )

    enum class Contact { NOT_SUPPORTED, DETECTED, NOT_DETECTED }

    /**
     * Decode a Heart Rate Measurement value (GATT spec, "Heart Rate Service 1.0", §3.1):
     *
     * - byte 0 — flags: bit 0 = value is UINT16 (else UINT8); bits 1–2 = sensor contact status
     *   (bit 2 = contact feature supported, bit 1 = contact detected); bit 3 = Energy Expended present
     *   (UINT16, skipped); bit 4 = RR intervals present (UINT16 each, units of 1/1024 s).
     * - then the heart rate, then the optional fields in that order.
     *
     * Returns null for a malformed packet or a reading of 0 (some straps send 0 while they settle).
     */
    fun parse(value: ByteArray): Measurement? {
        if (value.size < 2) return null
        val flags = value[0].toInt() and 0xFF
        var i = 1
        val wide = flags and 0x01 != 0
        val bpm = if (wide) {
            if (value.size < 3) return null
            u16(value, i).also { i += 2 }
        } else {
            (value[i].toInt() and 0xFF).also { i += 1 }
        }
        if (bpm <= 0) return null
        val contact = when {
            flags and 0x04 == 0 -> Contact.NOT_SUPPORTED
            flags and 0x02 != 0 -> Contact.DETECTED
            else -> Contact.NOT_DETECTED
        }
        if (flags and 0x08 != 0) i += 2 // Energy Expended — not used.
        val rr = buildList {
            if (flags and 0x10 != 0) {
                while (i + 1 < value.size) {
                    add((u16(value, i) * 1000L / 1024L).toInt())
                    i += 2
                }
            }
        }
        return Measurement(bpm, contact, rr)
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
}
