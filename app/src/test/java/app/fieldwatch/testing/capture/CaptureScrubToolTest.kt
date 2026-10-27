package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureScrubToolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val header = CaptureRecord.Header(app = "t", sdk = 36, model = "SM-S911U", wallMs = 1L)

    private fun ble(t: Long, addr: String, adv: String = "020106" + "1009" + CaptureRecord.hex("Alice's AirPods".toByteArray())) =
        CaptureRecord.Ble(tNs = t, wallMs = 1, addr = addr, addrType = 1, rssi = -60, primaryPhy = 1, secondaryPhy = 0,
            connectable = true, adv = adv)

    private fun File.part(name: String, vararg records: CaptureRecord) =
        resolve(name).writeText(records.joinToString("\n", postfix = "\n") { CaptureRecord.encode(it) })

    private fun dirs(): Pair<File, File> {
        val raw = tmp.newFolder("raw")
        val corpus = tmp.newFolder("corpus").also { File(it, ".git").mkdirs() } // a clone of spectre-corpus
        return raw to corpus
    }

    @Test
    fun cleanPartsGoToTheCorpusUnderARandomIdAndRawIsDeleted() {
        val (raw, corpus) = dirs()
        raw.part("capture-1793109599567-0001.ndjson", header, ble(5, "4A:1B:2C:3D:4E:5F"))
        raw.part("capture-1793109599567-0002.ndjson", header, ble(6, "4A:1B:2C:3D:4E:5F"))

        val result = CaptureScrubTool.run(raw, corpus, random = java.util.Random(3))

        assertEquals(emptyList<String>(), result.problems)
        assertEquals(listOf("part-0001.ndjson", "part-0002.ndjson"), result.dir!!.list()!!.sorted())
        assertTrue(Regex("[0-9a-f]{12}").matches(result.dir.name))
        assertEquals(0, raw.list()!!.size)
        assertEquals(listOf(".git", result.dir.name), corpus.list()!!.sorted()) // no .tmp- folder left
        val written = result.dir.resolve("part-0001.ndjson").readText()
        assertFalse(written.contains("4A:1B:2C:3D:4E:5F"))
        assertFalse(written.contains(CaptureRecord.hex("Alice".toByteArray())))
    }

    @Test
    fun aLeakBlocksTheCaptureAndTheReportNamesNoIdentifier() {
        val (raw, corpus) = dirs()
        // The SSID repeated in a vendor IE the scrubber doesn't know (00:0C:43): v1 leaves it, so it must block.
        val wifi = CaptureRecord.Wifi(
            tNs = 5, wallMs = 1, bssid = "00:11:22:33:44:55", ssid = "\"HomeNet-Smith\"", rssi = -50, freq = 2437,
            centerFreq0 = 0, centerFreq1 = 0, channelWidth = 0, capabilities = "[ESS]",
            ies = listOf(CaptureRecord.Ie(221, null, "000C4301" + CaptureRecord.hex("HomeNet-Smith".toByteArray()))),
        )
        raw.part("capture-1793109599567-0001.ndjson", header, wifi)

        val result = CaptureScrubTool.run(raw, corpus, random = java.util.Random(3))

        assertEquals(0, result.parts)
        val report = result.problems.joinToString("\n")
        assertTrue(report, report.contains("part 1: 1 name"))
        assertFalse(report.contains("HomeNet"))
        assertFalse(report.uppercase().contains(CaptureRecord.hex("HomeNet".toByteArray())))
        // The values sit next to the raw data, which is kept for a scrubber fix.
        assertTrue(File(raw, CaptureScrubTool.LEAKS_FILE).readText().contains("HomeNet-Smith"))
        assertEquals(2, raw.list()!!.size)
        assertEquals(listOf(".git"), corpus.list()!!.toList())
    }

    @Test
    fun partsKeepTimeOrderAcrossAppRestarts() {
        val (raw, corpus) = dirs()
        // The recorder restarts part numbers at 1 after a restart; the start time decides.
        raw.part("capture-1793109600000-0001.ndjson", header, ble(9, "4A:1B:2C:3D:4E:5F"))
        raw.part("capture-1793109500000-0001.ndjson", header, ble(5, "4A:1B:2C:3D:4E:5F"), ble(6, "4A:1B:2C:3D:4E:5F"))

        val result = CaptureScrubTool.run(raw, corpus, random = java.util.Random(3))

        assertEquals(3, result.dir!!.resolve("part-0001.ndjson").readLines().size) // the earlier run first
        assertEquals(2, result.dir.resolve("part-0002.ndjson").readLines().size)
    }

    @Test
    fun anAddressFromAnotherPartIsRemappedInsidePayloads() {
        val (raw, corpus) = dirs()
        // A device in part 1; in part 2 its address appears only inside another device's payload.
        raw.part("capture-1793109500000-0001.ndjson", header, ble(5, "A1:B2:C3:D4:E5:F6", "020106"))
        raw.part("capture-1793109600000-0001.ndjson", header, ble(9, "4A:1B:2C:3D:4E:5F", "020106" + "07FF3412" + "A1B2C3D4E5F6".take(10)))
        raw.part("capture-1793109700000-0001.ndjson", header, ble(9, "4A:1B:2C:3D:4E:5F", "020106" + "09FF3412" + "A1B2C3D4E5F6"))

        val result = CaptureScrubTool.run(raw, corpus, random = java.util.Random(3))

        assertEquals(emptyList<String>(), result.problems)
        assertFalse(result.dir!!.resolve("part-0003.ndjson").readText().contains("A1B2C3D4E5F6"))
    }

    @Test
    fun unsafeFoldersAndStrayFilesAreRefused() {
        val (raw, corpus) = dirs()
        raw.part("capture-1793109599567-0001.ndjson", header, ble(5, "4A:1B:2C:3D:4E:5F"))
        fun refused(block: () -> Unit) = assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)

        refused { CaptureScrubTool.run(File("raw"), corpus) } // relative
        refused { CaptureScrubTool.run(raw, tmp.newFolder("not-a-clone")) } // not a git clone
        refused { CaptureScrubTool.run(raw, File(raw, "out").also { it.mkdirs(); File(it, ".git").mkdirs() }) } // inside raw
        File(raw, "out").deleteRecursively()
        refused { CaptureScrubTool.run(raw, corpus, projectRoot = tmp.root) } // inside the checkout
        File(raw, "notes.txt").writeText("x")
        refused { CaptureScrubTool.run(raw, corpus) } // a stray file
        File(raw, "notes.txt").delete()
        java.nio.file.Files.createSymbolicLink(File(raw, "capture-1793109599999-0002.ndjson").toPath(), tmp.newFile("elsewhere").toPath())
        refused { CaptureScrubTool.run(raw, corpus) } // a link
        assertEquals(listOf(".git"), corpus.list()!!.toList())
    }
}
