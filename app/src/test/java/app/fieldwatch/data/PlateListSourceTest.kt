package app.fieldwatch.data

import app.fieldwatch.domain.EnglishCopy
import app.fieldwatch.domain.LanguageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-7c: before the plate-reader download, the dialog names the source and points to its terms. */
class PlateListSourceTest {
    @Test
    fun dialogNamesTheHostTheListComesFrom() {
        assertEquals("data.dontgetflocked.com", FlockFiles.sourceHost(SensorList.PLATES))
        val english = EnglishCopy.byKey.getValue("settings.layer.download.source").text
        assertTrue(english.contains("{host}") && english.contains("{size}"))
        val shown = LanguageText.text(
            "settings.layer.download.source",
            mapOf("host" to FlockFiles.PLATES_HOST, "size" to FlockFiles.PLATES_MB.toString()),
        )
        assertTrue(shown, shown.contains("about 38 MB, from data.dontgetflocked.com."))
        assertTrue(shown, shown.contains("Spectre does not run that site; check it for its terms"))
        assertTrue(shown, shown.contains("© OpenStreetMap contributors, ODbL"))
        val note = LanguageText.text("settings.layer.source", mapOf("host" to FlockFiles.PLATES_HOST))
        assertTrue(note, note.startsWith("From data.dontgetflocked.com, which Spectre does not run."))
    }

    @Test
    fun onlyTheNamedHostOverHttpsCountsAsTheSource() {
        val ok = listOf("https://data.dontgetflocked.com/cameras.geojson.gz", "https://cdn.data.dontgetflocked.com/x")
        ok.forEach { assertTrue(it, FlockFiles.servedFromSource(java.net.URL(it), SensorList.PLATES)) }
        val bad = listOf(
            "https://evil.example/cameras.geojson.gz",
            "https://data.dontgetflocked.com.evil.example/x",
            "http://data.dontgetflocked.com/cameras.geojson.gz",
        )
        bad.forEach { assertTrue(it, !FlockFiles.servedFromSource(java.net.URL(it), SensorList.PLATES)) }
    }

    @Test
    fun theDownloadIsCappedAndAListWithoutAUrlHasNoHost() {
        // About 2x the published 37.7 MB list; download() stops reading past this.
        assertTrue(FlockFiles.MAX_BYTES >= SensorList.PLATES.expectedBytes)
        assertTrue(FlockFiles.MAX_BYTES <= 3 * SensorList.PLATES.expectedBytes)
        assertEquals("", FlockFiles.sourceHost(SensorList.OTHER))
    }
}
