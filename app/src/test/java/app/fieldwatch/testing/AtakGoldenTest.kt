package app.fieldwatch.testing

import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.GnssMark
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitTakPackage
import app.fieldwatch.domain.TakInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * P1-A3: the ATAK data package for a sit (SitTakPackage.pack + zip, as FieldwatchViewModel.sitExportBytes calls
 * them), and live TAK events for an advertised drone and its pilot (as TakPublisher builds them). The sit is
 * [SitFixture]'s, with its watchlist and two GNSS marks, plus a synthetic Remote ID drone that advertises its
 * position, heading, speed and an operator fix. The serial is made up, and every position is near 0°, 0°.
 */
class AtakGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val tag = LiveCot.TAG

    private fun sit(): Pair<SitFixture.Sit, List<Sighting>> {
        val sit = SitFixture.build()
        return sit to sit.devices + SitFixture.matched(listOf(SitFixture.drone()), sit.fleets)
    }

    /** A spoofing episode and a later interference mark, both while the operator path was recording. */
    private val gnss = listOf(
        GnssMark(at = Golden.NOW - 4 * 60_000L, endedAt = Golden.NOW - 3 * 60_000L, kind = "spoofing", confidence = "Medium", detail = "Position jumped 2 km and back"),
        GnssMark(at = Golden.NOW - 60_000L, kind = "jamming", confidence = "Low", detail = "Signal-to-noise dropped on every constellation"),
    )

    private fun pack(callsign: String = ""): SitTakPackage.Packed? {
        val (sit, all) = sit()
        return SitTakPackage.pack(
            all, LogExportRadios.BOTH, SitFixture.track, gnss, sit.fleets, sit.watchlist, Golden.NOW, tag = tag, callsign = callsign,
        )
    }

    @Test
    fun dataPackage() {
        val zip = SitTakPackage.zip(pack()!!, "golden-package", "Golden sit", Golden.NOW)
        val entries = ArrayList<Pair<String, String>>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.forEach { e ->
                // Every entry carries the package's time (DOS time: 2 s steps, local zone, which GoldenRule pins).
                assertEquals(e.name, Golden.NOW / 2_000, e.time / 2_000)
                entries += e.name to input.readBytes().toString(Charsets.UTF_8)
            }
        }
        // Each marker file is named after the uid inside it.
        entries.filter { it.first.endsWith(".cot") }.forEach { (name, text) ->
            assertTrue(name, text.contains("uid=\"${name.substringAfterLast('/').removeSuffix(".cot")}\""))
        }
        // The zip's entries in order, the manifest, then every entry's exact text; one without a final newline
        // is marked, so adding or dropping one shows up.
        val out = buildString {
            append("ENTRIES\n")
            entries.forEach { (name, _) -> append("  ").append(name).append('\n') }
            entries.forEach { (name, text) ->
                append("\n==== ").append(name).append('\n').append(text)
                if (!text.endsWith("\n")) append("\n[no final newline]\n")
            }
        }
        Golden.assertText("atak/sit-package.txt", out)
    }

    /** A typed callsign names this phone in the self event and every "via" line. */
    @Test
    fun typedCallsign() {
        val packed = pack(callsign = "Unit 7")!!
        val phone = TakInstall.callsign("Unit 7", tag)
        val self = packed.items.single { it.uid == CotEvent.selfUid(tag) }.xml
        assertTrue(self, self.contains("callsign=\"$phone\""))
        val here = packed.items.filter { it.uid.contains("-HERE-") }
        assertTrue(here.isNotEmpty())
        assertTrue(here.all { it.xml.contains("via $phone") })
    }

    /** A sit with nothing to place gives no package (the app shows the empty hint instead). */
    @Test
    fun nothingToPackGivesNoPackage() {
        val packed = SitTakPackage.pack(
            emptyList(), LogExportRadios.BOTH, emptyList(), emptyList(), SitFixture.build().fleets, emptyList(), Golden.NOW,
            tag = tag, callsign = "",
        )
        assertNull(packed)
    }

    /** An advertised drone sends two live events: the aircraft at its own position, and its pilot. */
    @Test
    fun liveDroneAndPilot() {
        val (sit, devices) = sit()
        val drone = devices.single { it.kind == RadioKind.BLE && it.payloadUasId != null }
        Golden.assertText("cot/drone-live.xml", LiveCot.events(drone, sit.fleets, sit.watchlist))
    }
}
