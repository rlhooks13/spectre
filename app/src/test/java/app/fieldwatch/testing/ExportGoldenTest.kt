package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CotEvent
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.SitExport
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.TakPublish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: goldens for the sit exports (CSV, JSON lines, GPX, KML, GeoJSON, WiGLE) and a live CoT event, from
 * [SitFixture], called the way the app calls them. Exports and CoT stay English. The ATAK package has its
 * own golden.
 */
class ExportGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val version = "0.0.0-golden"

    /** The app's WiGLE header info shape (FieldwatchViewModel.sitExportText). */
    private val deviceInfo = "model=SM-S911U,release=16,device=dm1q"

    @Test
    fun csv() {
        val sit = SitFixture.build()
        Golden.assertText(
            "export/sit.csv",
            SitExport.csv(sit.devices, LogExportRadios.BOTH, sit.customNames, sit.observerNotes, sit.extraKeys, sit.fleets, sit.mineKeys),
        )
    }

    @Test
    fun csvWifiOnly() {
        val sit = SitFixture.build()
        Golden.assertText(
            "export/sit-wifi.csv",
            SitExport.csv(sit.devices, LogExportRadios.WIFI, sit.customNames, sit.observerNotes, sit.extraKeys, sit.fleets, sit.mineKeys),
        )
    }

    @Test
    fun jsonLines() {
        val sit = SitFixture.build()
        val out = SitExport.jsonl(
            sit.devices, LogExportRadios.BOTH, sit.customNames, sit.observerNotes, sit.extraKeys, sit.fleets, sit.mineKeys,
        )
        // NDJSON framing: one object per LF-terminated line, no blank lines, no CR.
        assertTrue(out.endsWith("}\n"))
        assertFalse('\r' in out)
        val lines = out.removeSuffix("\n").split('\n')
        assertEquals(sit.devices.size, lines.size)
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
        // Each object compared after parsing, in order.
        Golden.assertJson("export/sit.jsonl.json", lines.joinToString(",", prefix = "[", postfix = "]"))
    }

    /** As the app renders a sit map export: no per-radio names map (FieldwatchViewModel.sitExportText). */
    private fun geo(format: GeoExport.Format): String {
        val sit = SitFixture.build()
        return GeoExport.render(
            format, SitExport.mapRadios(sit.devices, LogExportRadios.BOTH), emptyMap(), version, deviceInfo,
            customNames = sit.customNames, observerNotes = sit.observerNotes, track = SitFixture.track,
        )
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
    fun liveCotCamera() = Golden.assertText("cot/flock-live.xml", liveCot(SitFixture.FLOCK))

    @Test
    fun liveCotTracker() = Golden.assertText("cot/airtag-live.xml", liveCot(SitFixture.AIRTAG))

    /** Exports and CoT stay English (CHARTER): a German overlay changes nothing. */
    @Test
    fun exportsStayEnglishUnderALanguagePack() {
        // Compare only: a re-bless here would write any leaked German over the English goldens.
        assumeFalse(Golden.updating)
        LanguageText.apply(TestFiles.bundledPack("de"))
        val sit = SitFixture.build()
        Golden.assertText(
            "export/sit.csv",
            SitExport.csv(sit.devices, LogExportRadios.BOTH, sit.customNames, sit.observerNotes, sit.extraKeys, sit.fleets, sit.mineKeys),
        )
        Golden.assertText("export/sit.gpx", geo(GeoExport.Format.GPX))
        Golden.assertText("cot/flock-live.xml", liveCot(SitFixture.FLOCK))
    }

    /** A live TAK event, built the way TakPublisher builds one, with the sit's watchlist. */
    private fun liveCot(mac: String): String {
        val sit = SitFixture.build()
        return LiveCot.events(sit.device(mac), sit.fleets, sit.watchlist)
    }
}
