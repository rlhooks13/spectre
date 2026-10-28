package app.fieldwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneFixTest {
    @Test
    fun aFixSlightlyAheadOfTheClockStillCounts() {
        val fixAt = 10_000L
        assertTrue(phoneFixIsCurrent(fixAt, fixAt))
        assertTrue(phoneFixIsCurrent(fixAt, fixAt - 1_000L))
        assertTrue(phoneFixIsCurrent(fixAt, fixAt + 30_000L))
        assertFalse(phoneFixIsCurrent(fixAt, fixAt - 5_001L))
        assertFalse(phoneFixIsCurrent(fixAt, fixAt + 30_001L))
    }

    /** P1-C1: hears and TAK's self marker get the phone fix only while it is current (30 s). */
    @Test
    fun aStaleFixIsNotCurrent() {
        val now = 1_000_000L
        val fix = PhoneFix(36.2, -115.1, now - 10_000L)
        assertEquals(36.2 to -115.1, currentFixOf(fix, now))
        assertEquals(36.2 to -115.1, currentFixOf(fix.copy(at = now - 30_000L), now))
        assertNull(currentFixOf(fix.copy(at = now - 30_001L), now))
        assertNull(currentFixOf(null, now))
    }

    /** P1-C1: a phone clock behind GPS time doesn't make every fix look future-dated and stale. */
    @Test
    fun aFixAheadOfThePhoneClockCountsAsNow() {
        val now = 1_000_000L
        assertEquals(now, fixTime(now + 20_000L, now))
        assertEquals(now - 3_000L, fixTime(now - 3_000L, now))
        assertEquals(36.2 to -115.1, currentFixOf(PhoneFix(36.2, -115.1, fixTime(now + 20_000L, now)), now))
    }

    /** P1-C1: a GNSS interference mark still goes at the last fix after it stops being current, up to 10 minutes. */
    @Test
    fun aGnssMarkUsesTheLastFixForTenMinutes() {
        val now = 1_000_000L
        val fix = PhoneFix(36.2, -115.1, now - 5 * 60_000L)
        assertNull(currentFixOf(fix, now))
        assertEquals(36.2 to -115.1, gnssMarkFixOf(fix, now))
        assertEquals(36.2 to -115.1, gnssMarkFixOf(fix.copy(at = now - GNSS_MARK_FIX_MS), now))
        assertNull(gnssMarkFixOf(fix.copy(at = now - GNSS_MARK_FIX_MS - 1), now))
        assertNull(gnssMarkFixOf(null, now))
    }

    @Test
    fun aFreshHuntKeepsTheAccuracyTheFixReported() {
        val now = 20_000L
        val fix = 36.2 to -115.1
        val tight = huntStartFix(null, fix, now, 6f, now)
        assertEquals(fix.first, tight!!.first, 0.0)
        assertEquals(fix.second, tight.second, 0.0)
        assertEquals(6f, tight.third, 0f)
        val coarse = huntStartFix(null, fix, now, 18f, now)
        assertEquals(18f, coarse!!.third, 0f)
        assertNull(huntStartFix(null, fix, now, Float.NaN, now))
        assertNull(huntStartFix(null, fix, now - 60_000L, 6f, now))
        val seed = 36.3 to -115.2
        val resumed = huntStartFix(seed, fix, now, 18f, now)
        assertEquals(seed.first, resumed!!.first, 0.0)
        assertEquals(8f, resumed.third, 0f)
    }
}
