package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CaptureFormatTest {
    private val IMPLEMENTS_SINK = Regex("(class|object)\\s+\\w+[^{\\n]*:[^{\\n]*\\bCaptureSink\\b")

    private val records = listOf(
        CaptureRecord.Header(app = "0.0.0-dev+abc", sdk = 36, model = "SM-S911U", wallMs = 1L),
        CaptureRecord.Ble(
            tNs = 123_456_789_000L, wallMs = 2L, addr = "4A:1B:2C:3D:4E:5F", addrType = 1, rssi = -61,
            txPower = null, primaryPhy = 1, secondaryPhy = 0, sid = null, connectable = true,
            periodicInterval = null, adv = "02011A0AFF4C0010050318E4C1A0",
        ),
        CaptureRecord.Wifi(
            tNs = 987_000L, wallMs = 3L, bssid = "AA:BB:CC:00:00:01", ssid = "Home", rssi = -48, freq = 5180,
            centerFreq0 = 5190, centerFreq1 = 0, channelWidth = 1, standard = 6, capabilities = "[WPA2-PSK-CCMP][ESS]",
            ies = listOf(CaptureRecord.Ie(0, null, "486F6D65"), CaptureRecord.Ie(255, 35, "01")),
        ),
        CaptureRecord.Fix(tNs = 5L, wallMs = 4L, provider = "gps", lat = 47.6, lon = -122.3, alt = 30.0, acc = 4.5f),
    )

    @Test
    fun everyRecordRoundTripsAsOneLine() {
        for (r in records) {
            val line = CaptureRecord.encode(r)
            assertFalse(line.contains('\n'))
            assertEquals(r, CaptureRecord.decode(line))
        }
    }

    @Test
    fun linesCarryATypeAndOmitAbsentFields() {
        val ble = CaptureRecord.encode(records[1])
        assertTrue(ble.startsWith("{\"type\":\"ble\""))
        assertFalse(ble.contains("txPower"))
        assertTrue(CaptureRecord.encode(records[0]).contains("\"format\":\"spectre-capture\""))
    }

    @Test
    fun aNewerFieldDoesNotBreakAnOlderReader() {
        val line = CaptureRecord.encode(records[3]).dropLast(1) + ",\"satellites\":11}"
        assertEquals(records[3], CaptureRecord.decode(line))
    }

    @Test
    fun newerFieldsRoundTripAndATruncatedLineIsSkipped() {
        val full = listOf(
            CaptureRecord.Header(app = "a", sdk = 36, model = "m", wallMs = 1L, elapsedNs = 9L, bootCount = 3, part = 2),
            CaptureRecord.Stats(tNs = 1L, wallMs = 2L, dropped = 17L),
            (records[1] as CaptureRecord.Ble).copy(legacy = false, dataStatus = 2),
            (records[2] as CaptureRecord.Wifi).copy(ssidHex = "486F6D65", mld = "02:00:00:00:00:01", linkId = 1),
            (records[3] as CaptureRecord.Fix).copy(fixTimeMs = 3L),
        )
        for (r in full) assertEquals(r, CaptureRecord.decodeOrNull(CaptureRecord.encode(r)))
        val line = CaptureRecord.encode(records[1])
        assertEquals(null, CaptureRecord.decodeOrNull(line.take(line.length / 2)))
        assertEquals(null, CaptureRecord.decodeOrNull("{\"type\":\"future\",\"x\":1}"))
    }

    @Test
    fun hexIsUppercaseAndComplete() {
        assertEquals("00FF7F80", CaptureRecord.hex(byteArrayOf(0, -1, 127, -128)))
    }

    /** The recorder is debug-only: nothing in main or release may construct one (ADR-0016). */
    @Test
    fun theRecorderLivesOnlyInTheDebugSourceSet() {
        val src = listOf(File("src"), File("app/src")).first { it.exists() }
        val recorder = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("class CaptureRecorder(") }
            .map { it.relativeTo(src).invariantSeparatorsPath.substringBefore('/') }
            .filterNot { it.startsWith("test") || it.startsWith("androidTest") }
            .toSet()
        assertEquals(setOf("debug"), recorder)
        // Nor may anything outside debug install a sink or implement one.
        val installs = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.relativeTo(src).invariantSeparatorsPath.let { !it.startsWith("debug/") && !it.startsWith("test") } }
            .filter { f -> f.readText().let { "Capture.sink =" in it || IMPLEMENTS_SINK.containsMatchIn(it) } }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), installs)
    }
}
