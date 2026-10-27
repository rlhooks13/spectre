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
                ble(2 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, aliceName, offlineFinding), // at 220 m: trimmed
                ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, aliceName, offlineFinding),
                ble(11 * sec, "00:25:DF:11:22:33", 0, flags, ownMacReversed),
                ble(12 * sec, "7E:00:11:22:33:44", 1, flags, smartTag, eid, tlm),
                ble(13 * sec, "7E:00:11:22:33:45", 1, flags, smartTag, eid, tlm),
                wifi(14 * sec),
                wifi(15 * sec),
            )
        ).map(CaptureRecord::encode)

    private fun scrub(lines: List<String>, seed: Long = 42) = CaptureScrubber.scrubPart(lines, java.util.Random(seed))
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
        // 0..3300 m in 110 m steps. The anchors are the medians of the first and last five fixes (220 m and
        // 3080 m); fixes within 500 m of either are gone, so 770 m..2530 m stay.
        assertEquals(17, fixesOut.size)
        // The radio record from 220 m went with them.
        assertEquals(6, out.filterIsInstance<CaptureRecord.Ble>().size + out.filterIsInstance<CaptureRecord.Wifi>().size)
        // Spacing survives, the place doesn't (synthetic origin), and times shift by one offset.
        val a = fixesOut.first()
        val b = fixesOut.last()
        assertEquals(1760.0, meters(a, b), 2.0)
        assertTrue(kotlin.math.abs(a.lat) < 1 && kotlin.math.abs(a.lon) < 1)
        assertEquals(16 * sec, b.tNs - a.tNs)
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

    @Test
    fun jitterAtHomeDoesNotCountAsTravel() {
        // Five minutes at 1 Hz, wandering a few metres around the door (adds ~1.5 km along the path), then a walk.
        val rnd = java.util.Random(7)
        val home = (0 until 300).map { i ->
            CaptureRecord.Fix(tNs = i * sec, wallMs = i * 1000L, provider = "gps",
                lat = 47.6 + (rnd.nextDouble() - 0.5) * 9e-5, lon = -122.3 + (rnd.nextDouble() - 0.5) * 9e-5)
        }
        val walk = fixes().map { it.copy(tNs = it.tNs + 300 * sec) }
        val original = (home + walk).map(CaptureRecord::encode)
        val kept = records(scrub(original)).filterIsInstance<CaptureRecord.Fix>()
        // The start anchor is home (median of the jitter); the walk's fixes from 550 m to 2530 m survive.
        assertEquals(19, kept.size)
        assertTrue(kept.all { it.tNs >= 300 * sec })
    }

    @Test
    fun offlineFindingIsFoundWhereverItSitsAndOnlyItIsRemapped() {
        val key = ByteArray(22) { (0x20 + it).toByte() }
        val payload = byteArrayOf(0x4C, 0x00, 0x10, 0x02, 0x05, 0x03) + // Continuity 0x10 first
            byteArrayOf(0x12, 0x19, 0x00) + key + byteArrayOf(0x01, 0x7F) + // Offline Finding second
            byteArrayOf(0x07, 0x03, 0x0A, 0x0B, 0x0C) // a TLV after it
        val out = records(scrub(listOf(CaptureRecord.encode(ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, ad(0xFF, payload))))))
            .filterIsInstance<CaptureRecord.Ble>().single()
        assertTrue(!out.adv.contains(hex(key)))
        assertTrue(out.adv.contains("4C0010020503" + "121900"))
        assertTrue(out.adv.endsWith("07030A0B0C"))
    }

    @Test
    fun aTruncatedNameIsStillScrubbedAndChecked() {
        val name = "Alice's AirPods".toByteArray()
        // Length byte promises 2 more bytes than the record holds (DATA_TRUNCATED).
        val truncated = byteArrayOf((name.size + 3).toByte(), 0x09) + name
        val original = listOf(CaptureRecord.encode(ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, flags, truncated)))
        assertTrue(CaptureLeakCheck.leaks(original, original).any { "Alice" in it })
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, scrub(original)))
    }

    @Test
    fun aWpsNameSplitAcrossTwoIesIsScrubbed() {
        val body = byteArrayOf(0x10, 0x11, 0x00, 12) + "Smith-Laptop".toByteArray()
        val head = byteArrayOf(0x00, 0x50, 0xF2.toByte(), 0x04)
        val ies = listOf(
            CaptureRecord.Ie(221, null, hex(head + body.copyOfRange(0, 9))), // the name breaks after "Smith"
            CaptureRecord.Ie(221, null, hex(head + body.copyOfRange(9, body.size))),
        )
        val original = listOf(CaptureRecord.encode(wifi(14 * sec).copy(ies = ies)))
        assertTrue(CaptureLeakCheck.leaks(original, original).any { "Smith-Laptop" in it })
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, scrub(original)))
    }

    @Test
    fun aCommonNameIsNotAFalseLeak() {
        // "wifi" is in every Wi-Fi line's JSON key; only field values and payload bytes count.
        val original = listOf(CaptureRecord.encode(wifi(14 * sec).copy(ssid = "\"wifi\"", ssidHex = hex("wifi".toByteArray()), ies = emptyList())))
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, scrub(original)))
    }

    @Test
    fun timeOfDayAndAltitudeMoveToo() {
        val withAlt = fixes().map { it.copy(alt = 1600.0 + it.tNs / sec) }
        val original = capture(withAlt)
        val kept = records(scrub(original)).filterIsInstance<CaptureRecord.Fix>()
        val day = 86_400_000L
        assertNotEquals(withAlt[7].wallMs % day, kept.first().wallMs % day)
        assertNotEquals(withAlt[7].alt!!, kept.first().alt!!, 1.0)
        assertEquals(16.0, kept.last().alt!! - kept.first().alt!!, 1e-6)
    }

    @Test
    fun networkAndLastKnownFixesNeverSurviveOrAnchor() {
        // A coarse network fix far from home at the very start would move a naive trim onto the home fix.
        val outlier = CaptureRecord.Fix(tNs = 0, wallMs = 0, provider = "network", lat = 47.62, lon = -122.3, acc = 900f)
        val lastKnown = fixes()[15].copy(tNs = 15 * sec + 1, lastKnown = true)
        val original = capture(listOf(outlier) + fixes().map { it.copy(tNs = it.tNs + 1) } + lastKnown)
        val kept = records(scrub(original)).filterIsInstance<CaptureRecord.Fix>()
        assertEquals(17, kept.size)
        assertTrue(kept.all { it.provider == "gps" && it.lastKnown == null })
    }

    @Test
    fun theLeakCheckCatchesAnAddressOnlyInsideAPayload() {
        // The address appears nowhere as a field: only reversed, and only forward, inside other payloads.
        val hidden = unhex("A1B2C3D4E5F6")
        val reversedOnly = ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, ad(0xFF, byteArrayOf(0x34, 0x12) + hidden.reversedArray()))
        val carrier = ble(11 * sec, "A1:B2:C3:D4:E5:F6", 1, flags)
        val original = listOf(carrier, reversedOnly).map(CaptureRecord::encode)
        val tampered = listOf(carrier.copy(addr = "00:00:00:00:00:01"), reversedOnly).map(CaptureRecord::encode)
        assertTrue(CaptureLeakCheck.leaks(original, tampered).any { "A1:B2:C3:D4:E5:F6" in it })
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, scrub(original)))
    }

    @Test
    fun aNameAfterZeroPaddingAndAnIdentityAddressAreScrubbed() {
        // 31-byte legacy advertising data padded with zeros, then the scan response with the name.
        val padded = flags + ByteArray(31 - flags.size)
        val identity = ad(0x1B, unhex("665544332211") + byteArrayOf(0x00)) // public identity 11:22:33:44:55:66
        val original = listOf(CaptureRecord.encode(ble(10 * sec, "4A:1B:2C:3D:4E:5F", 1, padded, aliceName, identity)))
        val out = scrub(original)
        assertEquals(emptyList<String>(), CaptureLeakCheck.leaks(original, out))
        val adv = (records(out).single() as CaptureRecord.Ble).adv
        assertTrue(!adv.contains(hex("Alice's AirPods".toByteArray())))
        assertTrue(!adv.contains("665544332211") && !adv.contains("112233445566"))
    }

    @Test
    fun wifiDirectAndNeighborReportsAreRemappedAndAddressesStayLowercaseSafe() {
        val p2p = byteArrayOf(0x50, 0x6F, 0x9A.toByte(), 0x09) + "DIRECT-Bobs-TV".toByteArray()
        val rnr = byteArrayOf(0x00, 0x04, 0x51, 0x24) + unhex("0099887766554433") // with a neighbor BSSID
        val r = wifi(14 * sec).copy(
            bssid = bssid.lowercase(),
            ies = listOf(CaptureRecord.Ie(221, null, hex(p2p)), CaptureRecord.Ie(201, null, hex(rnr))),
        )
        val out = records(scrub(listOf(CaptureRecord.encode(r)))).single() as CaptureRecord.Wifi
        assertTrue(out.bssid.uppercase().startsWith("00:11:22:") && !out.bssid.equals(bssid, ignoreCase = true))
        val p2pOut = unhex(out.ies[0].hex)
        assertEquals("506F9A09", hex(p2pOut.copyOf(4)))
        assertTrue(!out.ies[0].hex.contains(hex("Bobs".toByteArray())))
        assertNotEquals(hex(rnr), out.ies[1].hex)
    }

    @Test
    fun aPlaceholderAddressDoesNotRewritePadding() {
        val padded = flags + ByteArray(20)
        val r = ble(10 * sec, "02:00:00:00:00:00", 1, padded)
        val out = records(scrub(listOf(CaptureRecord.encode(r)))).single() as CaptureRecord.Ble
        assertTrue(out.adv.endsWith("00".repeat(20)))
    }

    @Test
    fun headerTimesAndBearingsMove() {
        val original = capture(fixes().map { it.copy(bearing = 0f, fixTimeMs = it.wallMs) })
        val out = records(scrub(original))
        val header = out.first() as CaptureRecord.Header
        assertNotEquals(1L, header.wallMs)
        val kept = out.filterIsInstance<CaptureRecord.Fix>()
        assertEquals(1, kept.map { it.bearing }.toSet().size) // one rotation for all
        assertNotEquals(0f, kept.first().bearing!!, 1e-3f)
        assertEquals(kept.first().wallMs, kept.first().fixTimeMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun onePartAtATime() {
        val header = CaptureRecord.encode(CaptureRecord.Header(app = "t", sdk = 36, model = "m", wallMs = 1L))
        scrub(listOf(header, header))
    }

    private fun meters(a: CaptureRecord.Fix, b: CaptureRecord.Fix): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
        return sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
    }
}
