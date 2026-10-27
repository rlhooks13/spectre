package app.fieldwatch.testing

import app.fieldwatch.data.LogStore
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.LogFormat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * P1-A3: the rotating log's exports, end to end on Robolectric: [SitFixture]'s devices appended to a real
 * [LogStore] (Android's org.json, so these are a phone's bytes), then exported as CSV and JSON lines and read
 * back into the log map exports the way `FieldwatchViewModel.mapExportText` calls them.
 */
@RunWith(RobolectricTestRunner::class)
class LogExportGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    /** The app's log map info shape (FieldwatchViewModel.mapExportText). */
    private val info = "model=SM-S911U,release=16,device=dm1q,display=golden,board=kalama,brand=samsung"

    private fun store(): Pair<LogStore, SitFixture.Sit> = runBlocking {
        val sit = SitFixture.build()
        val logs = LogStore(RuntimeEnvironment.getApplication())
        logs.configure(LogFormat.JSON, rotateKb = 1024)
        sit.devices.forEach { logs.append(it, sit.fleets) }
        logs to sit
    }

    @Test
    fun logCsv() = runBlocking {
        val (logs, _) = store()
        Golden.assertText("export/log.csv", logs.exportBundle(asJsonl = false).readText(Charsets.UTF_8))
    }

    @Test
    fun logJsonLinesBleOnly() = runBlocking {
        val (logs, _) = store()
        Golden.assertText(
            "export/log-ble.jsonl",
            logs.exportBundle(asJsonl = true, radios = LogExportRadios.BLE).readText(Charsets.UTF_8),
        )
    }

    private fun map(format: GeoExport.Format): String = runBlocking {
        val (logs, sit) = store()
        val pins = logs.readRadios().filter { it.hasPosition && LogExportRadios.BOTH.matches(it.kind) }
        GeoExport.render(format, pins, emptyMap(), "0.0.0-golden", info, customNames = sit.customNames)
    }

    @Test
    fun logGpx() = Golden.assertText("export/log.gpx", map(GeoExport.Format.GPX))

    @Test
    fun logWigle() = Golden.assertText("export/log-wigle.csv", map(GeoExport.Format.WIGLE))
}
