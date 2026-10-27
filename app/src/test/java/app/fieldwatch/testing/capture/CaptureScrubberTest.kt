package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.hex
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.unhex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

class CaptureScrubberTest {
    private val sec = 1_000_000_000L

    private fun ad(type: Int, vararg data: Int) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + ByteArray(data.size) { data[it].toByte() }
    private fun ad(type: Int, data: ByteArray) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data

    private val flags = ad(0x01, 0x06)
    private val ofKey = ByteArray(22) { (0x40 + it).toByte() }
    private val offlineFinding = ad(0xFF, byteArrayOf(0x4C, 0x00, 0x12, 0x19, 0x10) + ofKey + byteArrayOf(0x01, 0x7F))
    private val aliceName = ad(0x09, "Alice's AirPods".toByteArray())
    private val smartTag = ad(0x16, byteArrayOf(0x5A, 0xFD.toByte(), 0x01) + ByteArray(8) { (0x10 + it).toByte() })
    private val eid = ad(0x16, byteArrayOf(0xAA.toByte(), 0xFE.toByte(), 0x30) + ByteArray(8) { (0x60 + it).toByte() })
    private val tlm = ad(0x16, byteArrayOf(0xAA.toByte(), 0xFE.toByte(), 0x20, 0x00, 0x0B, 0xB8.toByte(), 0x14, 0x00))
    // Axon-style: the device's own public address, reversed, inside its manufacturer data.
    private val ownMacReversed = ad(0xFF, byteArrayOf(0x4D, 0x03, 0x33, 0x22, 0x11, 0xDF.toByte(), 0x25, 0x00))

    private fun ble(t: Long, addr: String, type: Int?, vararg ads: ByteArray) = CaptureRecord.Ble(
        tNs = t, wallMs = 1_700_000_000_000L + t / 1_000_000, addr = addr, addrType = type, rssi = -60,
        primaryPhy = 1, secondaryPhy = 0, connectable = true, adv = hex(ads.fold(ByteArray(0)) { a, b -> a + b }),
    )

    private val ssid = "HomeNet-Smith"
    private val wps = byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x04) +
        byteArrayOf(0x10, 0x11, 0x00, 12) + "Smith-Laptop".toByteArray() +
        byteArrayOf(0x10, 0x47, 0x00, 16) + ByteArray(16) { (0x70 + it).toByte() }
    private val bssid = "00:11:22:33:44:55"
    private val vendorWithBssid = byteArrayOf(0x00, 0x0C, 0x43, 0x01) + unhex("001122334455")

    private fun wifi(t: Long) = CaptureRecord.Wifi(
        tNs = t, wallMs = 1_700_000_000_000L + t / 1_000_000, bssid = bssid, ssid = "\"$ssid\"", rssi = -50,
        freq = 2437, centerFreq0 = 0, centerFreq1 = 0, channelWidth = 0, capabilities = "[ESS]",
        ies = listOf(
            CaptureRecord.Ie(0, null, hex(ssid.toByteArray())),
            CaptureRecord.Ie(221, null, hex(wps)),
            CaptureRecord.Ie(221, null, hex(vendorWithBssid)),
        ),
        ssidHex = hex(ssid.toByteArray()), mld = "02:00:00:AB:CD:EF",
    )

    /** 31 fixes, 110 m apart (so none sits on a 500 m boundary), due north: a 3.3 km path. */
    private fun fixes(count: Int = 31, stepM: Double = 110.0) = (0 until count).map { i ->
        CaptureRecord.Fix(tNs = i * sec, wallMs = 1_700_000_000_000L + i * 1000L, provider = "gps",
            lat = 47.6 + i * stepM / 111_195.0, lon = -122.3, bearing = 0f)
    }

    private fun capture(fixList: List<CaptureRecord.Fix> = fixes()): List<String> = (
        listOf(CaptureRecord.Header(app = "t", sdk = 36, model = "SM-S911U", wallMs = 1L, bootCount = 7)) +
            fixList +
            listOf(
                ble(2 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, aliceName, offlineFinding), // first 200 m: trimmed
                ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, aliceName, offlineFinding),
                ble(11 * sec, "00:25:DF:11:22:33", 0, flags, ownMacReversed),
                ble(12 * sec, "7E:00:11:22:33:44", 1, flags, smartTag, eid, tlm),
                ble(13 * sec, "7E:00:11:22:33:45", 1, flags, smartTag, eid, tlm),
                wifi(14 * sec),
                wifi(15 * sec),
            )
        ).map(CaptureRecord::encode)

    private fun scrub(lines: List<String>, seed: Long = 42) = CaptureScrubber(java.util.Random(seed)).scrub(lines)
    private fun records(lines: List<String>) = lines.map(CaptureRecord::decode)

    @Test
    fun theLeakCheckFindsEverythingInAnUnscrubbedCapture() {
        val original = capture()
        val leaks = CaptureLeakCheck.leaks(original, original)
        for (needle in listOf("4A:1B:2C:3D:4E:5F", "00:25:DF:11:22:33", bssid, "02:00:00:AB:CD:EF", "Alice's AirPods", ssid, "Smith-Laptop")) {
            assertTrue(needle, leaks.any { needle in it })
        }
    }

    @Test
    fun aScrubbedCaptureLeaksNoAddressOrName() {
        val original = capture()
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, scrub(original)))
    }

    @Test
    fun addressesKeepWhatDetectionNeedsAndMapConsistently() {
        val out = records(scrub(capture()))
        val ble = out.filterIsInstance<CaptureRecord.Ble>()
        // Random BLE keeps its two type bits (0x4A is 01......).
        assertEquals(0x40, ble.first().addr.take(2).toInt(16) and 0xC0)
        // Public BLE and universal Wi-Fi keep the OUI.
        assertTrue(ble.any { it.addr.startsWith("00:25:DF:") && it.addr != "00:25:DF:11:22:33" })
        val wifi = out.filterIsInstance<CaptureRecord.Wifi>()
        assertTrue(wifi.all { it.bssid.startsWith("00:11:22:") && it.bssid != bssid })
        assertEquals(1, wifi.map { it.bssid }.toSet().size)
        // The device's own address inside its payload got the same stand-in, reversed.
        val axon = ble.single { it.addr.startsWith("00:25:DF:") }
        val standIn = CaptureScrubber.macBytes(axon.addr)!!
        assertTrue(axon.adv.contains(hex(standIn.reversedArray())))
        // So did the BSSID inside a vendor IE.
        assertTrue(wifi.first().ies.any { it.hex.contains(hex(CaptureScrubber.macBytes(wifi.first().bssid)!!)) })
    }

    @Test
    fun rotatingIdsAreRemappedConsistentlyAndTelemetryStays() {
        val out = records(scrub(capture())).filterIsInstance<CaptureRecord.Ble>()
        val tags = out.filter { it.adv.contains("5AFD01") }
        assertEquals(2, tags.size)
        assertEquals(tags[0].adv, tags[1].adv) // same rotating id in, same stand-in out
        assertTrue(tags.none { it.adv.contains(hex(ByteArray(8) { (0x10 + it).toByte() })) })
        assertTrue(tags.none { it.adv.contains(hex(ByteArray(8) { (0x60 + it).toByte() })) })
        assertTrue(tags.all { it.adv.contains("AAFE2000" + "0BB81400") })
        val of = out.single { it.adv.contains("4C00121910") }
        assertTrue(!of.adv.contains(hex(ofKey)))
    }

    @Test
    fun namesBecomeSameLengthStandIns() {
        val out = records(scrub(capture()))
        val wifi = out.filterIsInstance<CaptureRecord.Wifi>().first()
        val stand = String(unhex(wifi.ssidHex!!))
        assertEquals(ssid.length, stand.length)
        assertNotEquals(ssid, stand)
        assertEquals("\"$stand\"", wifi.ssid)
        assertEquals(wifi.ssidHex, wifi.ies.first { it.id == 0 }.hex)
    }

    @Test
    fun timesShiftTogetherAndThePathIsTrimmedAndMoved() {
        val original = capture()
        val out = records(scrub(original))
        val header = out.first() as CaptureRecord.Header
        assertEquals("scrubbed", header.model)
        assertEquals(null, header.bootCount)

        val fixesOut = out.filterIsInstance<CaptureRecord.Fix>()
        // 0..3300 m in 110 m steps; fixes within 500 m of either end are gone: 550 m..2750 m stay.
        assertEquals(21, fixesOut.size)
        // The radio record from the first 220 m went with them.
        assertEquals(6, out.filterIsInstance<CaptureRecord.Ble>().size + out.filterIsInstance<CaptureRecord.Wifi>().size)
        // Spacing survives, the place doesn't (synthetic origin), and times shift by one offset.
        val a = fixesOut.first()
        val b = fixesOut.last()
        assertEquals(2200.0, meters(a, b), 2.0)
        assertTrue(kotlin.math.abs(a.lat) < 1 && kotlin.math.abs(a.lon) < 1)
        assertEquals(20 * sec, b.tNs - a.tNs)
        val ble = out.filterIsInstance<CaptureRecord.Ble>()
        assertEquals(1 * sec, ble[1].tNs - ble[0].tNs)
        assertNotEquals(10 * sec, ble[0].tNs)
    }

    @Test
    fun aShortPathKeepsNoFixesButKeepsTheRadioData() {
        val out = records(scrub(capture(fixes(count = 8)))) // 770 m: under 1 km
        assertEquals(0, out.filterIsInstance<CaptureRecord.Fix>().size)
        assertEquals(7, out.filterIsInstance<CaptureRecord.Ble>().size + out.filterIsInstance<CaptureRecord.Wifi>().size)
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(capture(fixes(count = 8)), scrub(capture(fixes(count = 8)))))
    }

    @Test
    fun twoCapturesUseUnrelatedStandIns() {
        val original = capture()
        val one = records(scrub(original, seed = 1)).filterIsInstance<CaptureRecord.Wifi>().first().bssid
        val two = records(scrub(original, seed = 2)).filterIsInstance<CaptureRecord.Wifi>().first().bssid
        assertNotEquals(one, two)
    }

    private fun meters(a: CaptureRecord.Fix, b: CaptureRecord.Fix): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
        return sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
    }
}
