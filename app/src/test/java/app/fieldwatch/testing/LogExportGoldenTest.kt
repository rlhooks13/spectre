package app.fieldwatch.testing

import app.fieldwatch.data.LogStore
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.LogFormat
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * P1-A3: the rotating log's exports, end to end on Robolectric. [SitFixture]'s devices are appended to a real
 * [LogStore] (Android's org.json, so these are a phone's bytes) next to a legacy CSV part, then exported as CSV
 * and JSON lines, and read back into the log map exports the way `FieldwatchViewModel.mapExportText` calls them.
 */
@RunWith(RobolectricTestRunner::class)
class LogExportGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private var logs: LogStore? = null

    @After
    fun closeLog() {
        runBlocking { logs?.clear() }
    }

    /**
     * A legacy CSV part, as older builds wrote them: header line, CRLF rows, a blank line. It repeats the Flock
     * camera's MAC from an earlier minute (merged on read) and adds a radio only it has.
     */
    private val legacyPart = "timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie\n" +
        "1793101800000,2026-10-27T11:50:00.000Z,WIFI,B4:1E:52:0A:0B:0C,Flock-1A2B,-61,6,2437,B4:1E:52,Flock Safety,Flock Safety Cameras,,,,,0.001100,0.002000,\r\n" +
        "\n" +
        "1793101860000,2026-10-27T11:51:00.000Z,BLE,4A:11:22:33:44:05,Legacy-tag,-77,0,0,4A:11:22,,,,FEED,RAND,,0.001150,0.002050,\r\n"

    /** The app's log map info shape (FieldwatchViewModel.mapExportText). */
    private val info = "model=SM-S911U,release=16,device=dm1q,display=golden,board=kalama,brand=samsung"

    private suspend fun store(): SitFixture.Sit {
        val sit = SitFixture.build()
        val app = RuntimeEnvironment.getApplication()
        val store = LogStore(app).also { logs = it }
        store.configure(LogFormat.JSON, rotateKb = 1024)
        sit.devices.forEach { store.append(it, sit.fleets) }
        File(File(app.filesDir, "logs"), "fieldwatch-001.csv").writeText(legacyPart, Charsets.UTF_8)
        return sit
    }

    private fun export(asJsonl: Boolean, radios: LogExportRadios): String = runBlocking {
        store()
        logs!!.exportBundle(asJsonl = asJsonl, radios = radios).readText(Charsets.UTF_8)
    }

    @Test
    fun logCsv() = Golden.assertText("export/log.csv", export(asJsonl = false, LogExportRadios.BOTH))

    @Test
    fun logJsonLines() = Golden.assertText("export/log.jsonl", export(asJsonl = true, LogExportRadios.BOTH))

    @Test
    fun logJsonLinesBleOnly() = Golden.assertText("export/log-ble.jsonl", export(asJsonl = true, LogExportRadios.BLE))

    /**
     * As mapExportText: radios read back from every part, filtered to those with a fix, names from the
     * watchlist's labels. A CSV export is written first, so its file must not be read back as a log part.
     */
    private fun map(format: GeoExport.Format, radios: LogExportRadios = LogExportRadios.BOTH): String = runBlocking {
        val sit = store()
        logs!!.exportBundle(asJsonl = false)
        val watchlist = sit.customNames.map { (key, label) -> WatchTarget(id = "w-$key", deviceKey = key, label = label) }
        val pins = logs!!.readRadios().filter { it.hasPosition && radios.matches(it.kind) }
        GeoExport.render(format, pins, emptyMap(), "0.0.0-golden", info, customNames = RadioBookmarks.labels(watchlist))
    }

    @Test
    fun logGpx() = Golden.assertText("export/log.gpx", map(GeoExport.Format.GPX))

    @Test
    fun logKml() = Golden.assertText("export/log.kml", map(GeoExport.Format.KML))

    @Test
    fun logGeoJson() = Golden.assertJson("export/log.geojson", map(GeoExport.Format.GEOJSON))

    @Test
    fun logWigle() = Golden.assertText("export/log-wigle.csv", map(GeoExport.Format.WIGLE))

    /** WiGLE is Wi-Fi-first; the radio filter leaves only the camera (the AP has no fix). */
    @Test
    fun logWigleWifiOnly() = Golden.assertText("export/log-wigle-wifi.csv", map(GeoExport.Format.WIGLE, LogExportRadios.WIFI))

    /** Exports stay English (CHARTER): a German overlay changes nothing. */
    @Test
    fun logExportsStayEnglishUnderALanguagePack() {
        assumeFalse(Golden.updating) // compare only: never write a leaked translation into a golden
        LanguageText.apply(TestFiles.bundledPack("de"))
        Golden.assertText("export/log.csv", export(asJsonl = false, LogExportRadios.BOTH))
        runBlocking { logs?.clear() }
        Golden.assertText("export/log.gpx", map(GeoExport.Format.GPX))
    }
}
