package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageKeyRevisionTest {
    // Keys Spectre added in Phase 0. A pack written for Fieldwatch 1.1.27–1.1.32 never had them.
    private val phase0 = setOf(
        "ui.permissions.receive.only",
        "ui.scan.status.channel",
        "ui.export.shares.catalog.no.update",
        "settings.layer.download.source",
        "settings.layer.source",
        "settings.layer.alert.region",
    )

    @Test
    fun phase0KeysAreRevisionTwoAndTheRestOne() {
        assertEquals(2, EnglishCopy.KEY_REVISION)
        for (line in EnglishCopy.lines) {
            assertEquals(line.key, if (line.key in phase0) 2 else 1, line.revision)
        }
    }

    @Test
    fun templateCarriesTheKeyRevision() {
        val text = LanguagePack.template("0.1.0")
        assertTrue(text.contains("\"keyRevision\": ${EnglishCopy.KEY_REVISION}"))
        assertEquals(0, ok(text).newSince)
    }

    @Test
    fun newSinceCountsKeysAfterThePacksRevision() {
        val rev1 = ok(pack(""""keyRevision":1,"writtenFor":"0.1.0""""))
        assertEquals(phase0.size, rev1.newSince)
        val rev2 = ok(pack(""""keyRevision":2,"writtenFor":"0.1.0""""))
        assertEquals(0, rev2.newSince)
    }

    @Test
    fun legacyPacksMapTheirFieldwatchVersion() {
        // Fieldwatch 1.1.27 or later had every revision-1 key.
        assertEquals(phase0.size, ok(pack(""""writtenFor":"1.1.32"""")).newSince)
        assertEquals(phase0.size, ok(pack(""""writtenFor":"1.1.27"""")).newSince)
        // Before 1.1.27 the map didn't exist yet, so every key is new except the one translated.
        assertEquals(EnglishCopy.lines.size - 1, ok(pack(""""writtenFor":"1.1.21"""")).newSince)
        // A 0.x writtenFor without a keyRevision is a hand edit; the count is unknown.
        assertNull(ok(pack(""""writtenFor":"0.1.0"""")).newSince)
    }

    private fun pack(fields: String): String =
        """{"format":"fieldwatch-language","formatVersion":1,"language":"Test",$fields,"strings":{"tab.live":"Direct"}}"""

    private fun ok(text: String): LanguagePreview {
        val parsed = LanguagePack.parse(text.toByteArray(Charsets.UTF_8))
        assertTrue(parsed.toString(), parsed is LanguageParse.Ok)
        return (parsed as LanguageParse.Ok).preview
    }
}
