package app.fieldwatch.testing

import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.SitExport
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: goldens for the sit exports (CSV, JSON lines, GPX, KML, GeoJSON, WiGLE) and the CoT event, from
 * [SitFixture]. Exports and CoT stay English. The ATAK package has its own golden.
 */
class ExportGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val version = "0.0.0-golden"
    private val extra = setOf(SitFixture.devices[0].key)
    private val mine = setOf(SitFixture.devices[3].key)

    @Test
    fun csv() = Golden.assertText(
        "export/sit.csv",
        SitExport.csv(
            SitFixture.devices, LogExportRadios.BOTH, SitFixture.customNames, SitFixture.observerNotes, extra,
            SitFixture.fleets, mine,
        ),
    )

    @Test
    fun csvWifiOnly() = Golden.assertText(
        "export/sit-wifi.csv",
        SitExport.csv(SitFixture.devices, LogExportRadios.WIFI, emptyMap(), emptyMap(), emptySet(), SitFixture.fleets),
    )

    @Test
    fun jsonLines() {
        val out = SitExport.jsonl(
            SitFixture.devices, LogExportRadios.BOTH, SitFixture.customNames, SitFixture.observerNotes, extra,
            SitFixture.fleets, mine,
        )
        // One JSON object per line; each is compared after parsing, in order.
        val lines = out.trimEnd('\n').split('\n')
        Golden.assertJson("export/sit.jsonl.json", lines.joinToString(",", prefix = "[", postfix = "]"))
    }

    private fun geo(format: GeoExport.Format) = GeoExport.render(
        format, SitExport.mapRadios(SitFixture.devices, LogExportRadios.BOTH), names = names(), appVersion = version,
        deviceInfo = "SM-S911U; Android 16", customNames = SitFixture.customNames, observerNotes = SitFixture.observerNotes,
        track = LogRadios.track,
    )

    private fun names(): Map<String, List<String>> {
        val byId = SitFixture.fleets.associateBy { it.id }
        return SitFixture.devices.associate { d -> d.key to d.fleetIds.mapNotNull { byId[it]?.name }.sorted() }
    }

    @Test
    fun gpx() = Golden.assertText("export/sit.gpx", geo(GeoExport.Format.GPX))

    @Test
    fun kml() = Golden.assertText("export/sit.kml", geo(GeoExport.Format.KML))

    @Test
    fun geoJson() = Golden.assertJson("export/sit.geojson", geo(GeoExport.Format.GEOJSON))

    @Test
    fun wigle() = Golden.assertText("export/sit-wigle.csv", geo(GeoExport.Format.WIGLE))

    @Test
    fun cotEvent() {
        val flock = SitFixture.devices[0]
        val pin = SitExport.hearPoint(flock)!!
        Golden.assertText(
            "cot/flock.xml",
            CotEvent.xml(flock, SitFixture.fleets, emptyList(), pin.lat, pin.lon, advertised = false, now = Golden.NOW, tag = "golden") + "\n",
        )
    }
}
