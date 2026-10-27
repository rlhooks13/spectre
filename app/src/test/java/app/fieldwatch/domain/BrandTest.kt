package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandTest {
    @Test
    fun userAgentNamesTheForkAndItsRepo() {
        assertEquals("Spectre/0.1.0 (+https://github.com/rlhooks13/spectre)", Brand.userAgent("0.1.0"))
        assertFalse(Brand.userAgent("0.1.0").contains("OffGridPete"))
    }

    @Test
    fun displayNamesChangeButStableIdsKeepTheirLegacyValues() {
        assertEquals("Spectre-AB23", TakInstall.callsign("", "AB23"))
        assertEquals("FIELDWATCH-SELF", TakInstall.LEGACY_SELF_UID)
        assertEquals("fieldwatch-settings", SettingsPack.FORMAT)
        assertEquals("fieldwatch-signatures", SignaturePack.FORMAT)
        assertEquals("spectre-map-20260101-000000.gpx", GeoExport.suggestedName(GeoExport.Format.GPX, "20260101-000000"))
    }

    @Test
    fun aSitDebriefHeadingIsRecognizedForTheShareSubject() {
        val sit = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(),
            operatorPath = emptyList(),
            now = 40_000L,
            window = DebriefWindow(1_000L, 40_000L, "Porch"),
            watchedFleetIds = emptySet(),
        )
        assertEquals("SPECTRE SIT — Porch", sit.heading)
        assertTrue(DebriefReport.isSitHeading(sit.heading))
        assertFalse(DebriefReport.isSitHeading(LanguageText.text("ui.fieldwatch.field.debrief")))
    }

    @Test
    fun noUpstreamSocialHandleOrLinkShips() {
        assertEquals("https://github.com/rlhooks13/spectre", Brand.REPO_URL)
        val handles = listOf("OffGridPete", "OGridPete", "instagram.com", "x.com/")
        val english = EnglishCopy.lines.filter { line -> handles.any { it in line.text } }.map { it.key }
        assertEquals(emptyList<String>(), english)
        assertFalse(OfficialSentences.AUTHOR_GUIDE_URL.contains("OffGridPete"))
    }

    @Test
    fun noEnglishLineNamesTheUpstreamApp() {
        // Legacy ids such as "(app.fieldwatch)" are lowercase; the display name is not.
        val stale = EnglishCopy.lines.filter { "Fieldwatch" in it.text || "FIELDWATCH" in it.text }
            .map { it.key }
        assertEquals(emptyList<String>(), stale)
    }
}
