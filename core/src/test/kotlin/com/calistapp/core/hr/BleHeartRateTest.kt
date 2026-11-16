package com.calistapp.core.hr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Heart Rate Measurement packet, as the GATT spec lays it out — what every HR band sends. */
class BleHeartRateTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `8-bit heart rate`() {
        val m = BleHeartRate.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertEquals(BleHeartRate.Contact.NOT_SUPPORTED, m.contact)
    }

    @Test
    fun `16-bit heart rate`() {
        assertEquals(300, BleHeartRate.parse(bytes(0x01, 0x2C, 0x01))!!.bpm)
    }

    @Test
    fun `contact detected and not detected`() {
        assertEquals(BleHeartRate.Contact.DETECTED, BleHeartRate.parse(bytes(0x06, 90))!!.contact)
        assertEquals(BleHeartRate.Contact.NOT_DETECTED, BleHeartRate.parse(bytes(0x04, 90))!!.contact)
    }

    @Test
    fun `energy expended is skipped and RR intervals are read in milliseconds`() {
        // flags: RR present (0x10) + energy present (0x08); HR 120; energy 0x0102; RR 1024/1024 s and 512/1024 s.
        val m = BleHeartRate.parse(bytes(0x18, 120, 0x02, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(120, m.bpm)
        assertEquals(listOf(1000, 500), m.rrIntervalsMs)
    }

    @Test
    fun `a zero or truncated packet is no reading`() {
        assertNull(BleHeartRate.parse(bytes(0x00, 0)))
        assertNull(BleHeartRate.parse(bytes(0x01, 0x50)))
        assertNull(BleHeartRate.parse(bytes(0x00)))
    }
}
