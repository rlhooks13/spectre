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
        // A dev build's commit (P0-8c) stays off the wire.
        assertEquals("Spectre/0.1.0-dev (+https://github.com/rlhooks13/spectre)", Brand.userAgent("0.1.0-dev+949cd0c73d76.dirty"))
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
    fun noBundledPackNamesTheUpstreamApp() {
        val dir = listOf(
            java.io.File("src/main/assets/lang"),
            java.io.File("app/src/main/assets/lang"),
        ).first { it.isDirectory }
        val packs = BundledLanguages.all.map { java.io.File(dir, "${it.tag}.json") }
        assertTrue(packs.all { it.isFile })
        val upstream = Regex("(?i)field\\s?watch")
        val stale = packs.flatMap { pack ->
            val strings = kotlinx.serialization.json.Json.parseToJsonElement(pack.readText(Charsets.UTF_8))
                .let { it as kotlinx.serialization.json.JsonObject }["strings"] as kotlinx.serialization.json.JsonObject
            // Values only: keys keep the legacy spelling. The app id changed in P0-4e, so no exemption is left.
            strings.filter { (_, v) -> upstream.containsMatchIn(v.toString()) }
                .keys.map { "${pack.name}: $it" }
        }
        assertEquals(emptyList<String>(), stale)
    }

    @Test
    fun noEnglishLineNamesTheUpstreamApp() {
        val stale = EnglishCopy.lines.filter { Regex("(?i)field\\s?watch").containsMatchIn(it.text) }
            .map { it.key }
        assertEquals(emptyList<String>(), stale)
    }
}
