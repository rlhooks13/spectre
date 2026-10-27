package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.PayloadFix
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitTakPackage
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.WatchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * P1-A3: the ATAK data package for a sit (SitTakPackage.pack + zip, as FieldwatchViewModel.sitExportBytes calls
 * them), and live TAK events for an advertised drone and its pilot (as TakPublisher builds them). The sit is
 * [SitFixture]'s plus a synthetic Remote ID drone that advertises its position, heading, speed and an operator
 * fix. The serial is made up, and every position is near 0°, 0°.
 */
class AtakGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val tag = "AB23"

    /** A Remote ID drone matched through FFFA, decoded mid-flight, with its pilot's live GPS. */
    private fun drone(): Sighting = Sightings.sighting(
        key = "BLE:4E:11:22:33:44:07",
        mac = "4E:11:22:33:44:07",
        serviceUuids = listOf("FFFA"),
    ).copy(
        rssi = -74, rssiMin = -82, rssiMax = -70,
        firstSeen = Golden.NOW - 6 * 60_000L, lastSeen = Golden.NOW - 10_000L, hitCount = 120,
        latitude = 0.0015, longitude = 0.0024,
        gpsTrail = listOf(app.fieldwatch.domain.GpsSample(Golden.NOW - 10_000L, 0.0015, 0.0024, -74)),
        payloadUasId = "TESTSERIAL0000000001",
        payloadLat = 0.0021, payloadLon = 0.0031, payloadAlt = 86.0, payloadHeading = 270.0, payloadSpeed = 6.5,
        payloadAt = Golden.NOW - 10_000L,
        payloadOpLat = 0.0018, payloadOpLon = 0.0029, payloadOpKind = 1, payloadOpAlt = 4.0,
        payloadOpAt = Golden.NOW - 12_000L,
        // The advertised flight so far, ending at the current position: the package's drone track.
        payloadTrail = listOf(
            PayloadFix(Golden.NOW - 130_000L, 0.0019, 0.0035, 80.0, 270.0, 6.0),
            PayloadFix(Golden.NOW - 70_000L, 0.0020, 0.0033, 84.0, 270.0, 6.3),
            PayloadFix(Golden.NOW - 10_000L, 0.0021, 0.0031, 86.0, 270.0, 6.5),
        ),
    )

    private fun sit(): Pair<SitFixture.Sit, List<Sighting>> {
        val sit = SitFixture.build()
        return sit to sit.devices + SitFixture.matched(listOf(drone()), sit.fleets)
    }

    @Test
    fun dataPackage() {
        val (sit, devices) = sit()
        val watchlist = sit.customNames.map { (key, label) -> WatchTarget(id = "w-$key", deviceKey = key, label = label) }
        val packed = SitTakPackage.pack(
            devices, LogExportRadios.BOTH, SitFixture.track, emptyList(), sit.fleets, watchlist, Golden.NOW,
            tag = tag, callsign = "",
        )!!
        val zip = SitTakPackage.zip(packed, "golden-package", "Golden sit", Golden.NOW)

        // The zip's entries in order, the manifest, then every entry's text (timestamps inside the zip aren't pinned).
        val entries = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.map { it.name to input.readBytes().toString(Charsets.UTF_8) }.toList()
        }
        val out = buildString {
            append("ENTRIES\n")
            entries.forEach { (name, _) -> append("  ").append(name).append('\n') }
            entries.forEach { (name, text) -> append("\n==== ").append(name).append('\n').append(text.trimEnd('\n')).append('\n') }
        }
        Golden.assertText("atak/sit-package.txt", out)
        assertEquals(packed.items.size + 1 + (if (packed.tracksKml != null) 1 else 0), entries.size)
    }

    /** An advertised drone sends two live events: the aircraft at its own position, and its pilot. */
    @Test
    fun liveDroneAndPilot() {
        val (sit, devices) = sit()
        val drone = devices.single { it.kind == RadioKind.BLE && it.payloadUasId != null }
        val settings = AppSettings().copy(takEnabled = true, takAllSignatures = true, tagLocation = true)
        val markers = TakPublish.markers(drone, settings, sit.fleets, emptyList(), tag)
        assertEquals(2, markers.size)
        val xml = markers.joinToString("") { m ->
            val face = CotEvent.face(
                device = m.device, fleets = sit.fleets, watchlist = emptyList(), advertised = m.advertised,
                tag = tag, pilot = m.pilot, viaCallsign = TakInstall.viaLine(m.uid, tag, "SPECTRE-AB23"),
            )
            CotEvent.render(m.uid, face, m.lat, m.lon, Golden.NOW, TakDefaults.STALE_MS) + "\n"
        }
        Golden.assertText("cot/drone-live.xml", xml)
    }
}
