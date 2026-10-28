package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B7: a received RSSI is −127..−1 dBm. 0 is what an unset or unmeasured reading holds (a Wi-Fi scan's missing
 * level, a default), and 127 is Bluetooth's "not available"; neither is a hear.
 */
class RssiTest {
    @Test
    fun onlyNegativeReadingsAreMeasured() {
        assertTrue(Rssi.measured(-1))
        assertTrue(Rssi.measured(-127))
        assertTrue(Rssi.measured(-60))
        assertFalse(Rssi.measured(0))
        assertFalse(Rssi.measured(5))
        assertFalse(Rssi.measured(127))
        assertFalse(Rssi.measured(-128))
    }

    @Test
    fun anUnmeasuredZeroIsLeftOutOfRangesAndTheLastReading() {
        assertEquals("-70 to -50 dBm", Rssi.sessionRange(-70, 0, listOf(RssiSample(1L, -50), RssiSample(2L, 0))))
        assertEquals("Not available", Rssi.sessionRange(0, 0))
        assertEquals(-50, Rssi.lastMeasured(0, listOf(RssiSample(1L, -50), RssiSample(2L, 0))))
        assertNull(Rssi.lastMeasured(0, emptyList()))
    }
}
