package app.fieldwatch.testing

import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LanguagePack
import app.fieldwatch.domain.LanguageParse
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** P1-A2: the golden rule's first goldens, one JSON (compared after parsing) and one localized. */
class GoldenTest {
    @get:Rule
    val golden = GoldenRule()

    @Test
    fun geoJsonExport() {
        val out = GeoExport.render(
            GeoExport.Format.GEOJSON, LogRadios.sample, names = emptyMap(), appVersion = "0.0.0-golden",
            deviceInfo = "", track = LogRadios.track,
        )
        Golden.assertJson("export/map.geojson", out)
    }

    @Test
    fun exportLabelsInGerman() {
        LanguageText.apply(bundledPack("de"))
        val lines = LogExportKind.entries.map { "${it.titleKey} = ${it.label()}" } +
            LogExportRadios.entries.map { "${it.titleKey} = ${it.label()}" }
        Golden.assertText("language/de-export-labels.txt", lines.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun aChangedOutputFails() {
        // A re-bless would write these wrong values over the goldens, so this test sits it out.
        assumeFalse(Golden.updating)
        val changed = runCatching { Golden.assertText("language/de-export-labels.txt", "export.gpx = GPX\n") }
        assertTrue(changed.exceptionOrNull() is AssertionError)
        val json = runCatching { Golden.assertJson("export/map.geojson", """{"type":"FeatureCollection","features":[]}""") }
        assertTrue(json.exceptionOrNull() is AssertionError)
    }

    private fun bundledPack(tag: String): Map<String, String> {
        val file = listOf(File("src/main/assets/lang/$tag.json"), File("app/src/main/assets/lang/$tag.json")).first { it.isFile }
        val parsed = LanguagePack.parse(file.readBytes())
        assertTrue(parsed.toString(), parsed is LanguageParse.Ok)
        return (parsed as LanguageParse.Ok).preview.accepted
    }
}
