package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CaptureScrubToolTest {
    private fun part(name: String, vararg records: CaptureRecord) =
        records.joinToString("\n", postfix = "\n") { CaptureRecord.encode(it) }

    private val header = CaptureRecord.Header(app = "t", sdk = 36, model = "SM-S911U", wallMs = 1L)
    private val ble = CaptureRecord.Ble(
        tNs = 5, wallMs = 1, addr = "4A:1B:2C:3D:4E:5F", addrType = 1, rssi = -60, primaryPhy = 1, secondaryPhy = 0,
        connectable = true, adv = "020106" + "1009" + CaptureRecord.hex("Alice's AirPods".toByteArray()),
    )

    @Test
    fun cleanPartsGoToTheCorpusUnderARandomIdAndRawIsDeleted() {
        val raw = Files.createTempDirectory("raw").toFile()
        val corpus = Files.createTempDirectory("corpus").toFile()
        raw.resolve("capture-1793109599567-0001.ndjson").writeText(part("a", header, ble))
        raw.resolve("capture-1793109600000-0002.ndjson").writeText(part("b", header, ble))

        val result = CaptureScrubTool.run(raw, corpus, java.util.Random(3))

        assertEquals(2, result.parts)
        assertEquals(listOf("part-0001.ndjson", "part-0002.ndjson"), result.dir!!.list()!!.sorted())
        assertTrue(Regex("[0-9a-f]{12}").matches(result.dir.name))
        assertEquals(0, raw.listFiles()!!.size)
        val written = result.dir.resolve("part-0001.ndjson").readText()
        assertFalse(written.contains("4A:1B:2C:3D:4E:5F"))
        assertFalse(written.contains(CaptureRecord.hex("Alice".toByteArray())))
        raw.deleteRecursively()
        corpus.deleteRecursively()
    }

    @Test
    fun aPartThatLeaksBlocksTheWholeCaptureAndKeepsRaw() {
        val raw = Files.createTempDirectory("raw").toFile()
        val corpus = Files.createTempDirectory("corpus").toFile()
        // A WPS-less SSID in an IE the scrubber doesn't know (vendor 00:0C:43) that repeats the SSID text:
        // v1 doesn't scrub it, so the leak check must stop the capture.
        val wifi = CaptureRecord.Wifi(
            tNs = 5, wallMs = 1, bssid = "00:11:22:33:44:55", ssid = "\"HomeNet-Smith\"", rssi = -50, freq = 2437,
            centerFreq0 = 0, centerFreq1 = 0, channelWidth = 0, capabilities = "[ESS]",
            ies = listOf(CaptureRecord.Ie(221, null, "000C4301" + CaptureRecord.hex("HomeNet-Smith".toByteArray()))),
        )
        raw.resolve("capture-1-0001.ndjson").writeText(part("a", header, wifi))

        val result = CaptureScrubTool.run(raw, corpus, java.util.Random(3))

        assertEquals(0, result.parts)
        assertTrue(result.leaks.any { "HomeNet-Smith" in it })
        assertEquals(0, corpus.list()!!.size)
        assertEquals(1, raw.listFiles()!!.size)
        raw.deleteRecursively()
        corpus.deleteRecursively()
    }
}
