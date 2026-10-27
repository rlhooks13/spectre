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
        assertTrue(english.contains("{host}"))
        val shown = LanguageText.text("settings.layer.download.source", mapOf("host" to FlockFiles.sourceHost(SensorList.PLATES)))
        assertTrue(shown, shown.contains("from data.dontgetflocked.com."))
        assertTrue(shown, shown.contains("Spectre does not run that site; check it for its terms"))
        assertTrue(shown, shown.contains("crowdsourced and not complete"))
    }

    @Test
    fun theDownloadIsCappedAndAListWithoutAUrlHasNoHost() {
        // About 2x the published 37.7 MB list; download() stops reading past this.
        assertTrue(FlockFiles.MAX_BYTES >= SensorList.PLATES.expectedBytes)
        assertTrue(FlockFiles.MAX_BYTES <= 3 * SensorList.PLATES.expectedBytes)
        assertEquals("", FlockFiles.sourceHost(SensorList.OTHER))
    }
}
