package app.fieldwatch.testing

import app.fieldwatch.domain.EnglishCopy
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale
import java.util.TimeZone

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
        LanguageText.apply(TestFiles.bundledPack("de"))
        val lines = LogExportKind.entries.map { "${it.titleKey} = ${it.label()}" } +
            LogExportRadios.entries.map { "${it.titleKey} = ${it.label()}" }
        Golden.assertText("language/de-export-labels.txt", lines.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun aChangedOutputFails() {
        // A re-bless would write these wrong values over the goldens, so this test sits it out.
        assumeFalse(Golden.updating)
        // The goldens exist, so a failure below is the mismatch, not "missing".
        assertTrue(Golden.exists("language/de-export-labels.txt") && Golden.exists("export/map.geojson"))
        val changed = runCatching { Golden.assertText("language/de-export-labels.txt", "export.gpx = GPX\n") }
        assertTrue(changed.exceptionOrNull() is org.junit.ComparisonFailure)
        val json = runCatching { Golden.assertJson("export/map.geojson", """{"type":"FeatureCollection","features":[]}""") }
        assertTrue(json.exceptionOrNull() is AssertionError)
        val missing = runCatching { Golden.assertText("missing/does-not-exist.txt", "x") }
        assertTrue(missing.exceptionOrNull()?.message.orEmpty().contains("is missing"))
        assertFalse(Golden.exists("missing/does-not-exist.txt"))
    }

    @Test
    fun jsonComparesKeysInAnyOrderAndNumbersByValue() {
        // The JVM's and Android's org.json print some numbers differently, and order keys differently.
        assertTrue(Golden.sameJson("""{"a":[0,0.002,-48],"b":"x"}""", """{"b":"x","a":[-0.0,2.0E-3,-48.0]}"""))
        assertFalse(Golden.sameJson("""{"a":[0.002]}""", """{"a":[0.0025]}"""))
        assertFalse(Golden.sameJson("""{"a":"0"}""", """{"a":0}""")) // a string is not a number
    }

    @Test
    fun theRulePinsZoneLocaleAndOverlayAndRestoresThem() {
        val zone = TimeZone.getDefault()
        val locale = Locale.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            LanguageText.apply(mapOf("export.gpx" to "GPX dışa aktar"))
            val rule = GoldenRule()
            rule.apply(object : Statement() {
                override fun evaluate() {
                    assertEquals("UTC", TimeZone.getDefault().id)
                    assertEquals(Locale.US, Locale.getDefault())
                    assertEquals(EnglishCopy.byKey.getValue("export.gpx").text, LanguageText.text("export.gpx"))
                }
            }, Description.EMPTY).evaluate()
            assertEquals("Pacific/Kiritimati", TimeZone.getDefault().id)
            assertEquals(Locale.forLanguageTag("tr-TR"), Locale.getDefault())
        } finally {
            TimeZone.setDefault(zone)
            Locale.setDefault(locale)
            LanguageText.clear()
        }
    }
}
