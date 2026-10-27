package app.fieldwatch.ui

import app.fieldwatch.domain.EnglishCopy
import app.fieldwatch.domain.LanguagePack
import app.fieldwatch.domain.LanguageParse
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
    fun bundledPacksForThoseCountriesTranslateIt() {
        // German, French and Italian cover the three countries' languages (ADR-0012).
        val expected = mapOf(
            "de" to listOf("Deutschland", "Schweiz", "Frankreich"),
            "fr" to listOf("Allemagne", "Suisse", "France"),
            "it" to listOf("Germania", "Svizzera", "Francia"),
        )
        for ((tag, countries) in expected) {
            val bytes = repoFile("src/main/assets/lang/$tag.json").readBytes()
            val preview = (LanguagePack.parse(bytes) as LanguageParse.Ok).preview
            val line = preview.accepted["settings.layer.alert.region"]
            assertTrue(tag, line != null && countries.all { line.contains(it) })
        }
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
