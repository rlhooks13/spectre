package app.fieldwatch.testing

import app.fieldwatch.domain.GeoExport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P1-A2: the device's exact bytes for one JSON export. JVM goldens compare JSON after parsing, because the
 * JVM's org.json orders keys differently from Android's; this one runs on Robolectric's Android org.json,
 * so it pins the key order and number formatting a phone writes.
 */
@RunWith(RobolectricTestRunner::class)
class GoldenDeviceJsonTest {
    @get:Rule
    val golden = GoldenRule()

    @Test
    fun geoJsonExportBytesOnTheDevice() {
        val out = GeoExport.render(
            GeoExport.Format.GEOJSON, LogRadios.sample, names = emptyMap(), appVersion = "0.0.0-golden",
            deviceInfo = "", track = LogRadios.track,
        )
        Golden.assertText("export/map.device.geojson", out)
        // Different key order from the JVM, same document.
        Golden.assertJson("export/map.geojson", out)
    }
}
