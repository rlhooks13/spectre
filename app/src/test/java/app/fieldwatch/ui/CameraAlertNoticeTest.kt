package app.fieldwatch.ui

import app.fieldwatch.domain.EnglishCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CameraAlertNoticeTest {
    @Test
    fun noticeNamesTheRestrictedCountries() {
        val line = EnglishCopy.byKey.getValue("settings.layer.alert.region").text
        for (country in listOf("Germany", "Switzerland", "France")) {
            assertTrue(country, line.contains(country))
        }
    }

    @Test
    fun noticeSitsUnderEveryCameraAlertBlock() {
        val source = repoFile("src/main/java/app/fieldwatch/ui/screen/SettingsScreen.kt").readText()
        val blocks = Regex("(?<!fun )SensorLayerBlock\\(").findAll(source).count()
        assertTrue(blocks > 0)
        assertEquals(blocks, Regex("(?<!fun )CameraAlertNotice\\(\\)").findAll(source).count())
        assertTrue(source.contains("LanguageText.text(\"settings.layer.alert.region\")"))
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
