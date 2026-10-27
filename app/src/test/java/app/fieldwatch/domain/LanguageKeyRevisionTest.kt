package app.fieldwatch.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LanguageKeyRevisionTest {
    private fun count(revision: Int) = EnglishCopy.lines.count { it.revision == revision }

    @Test
    fun revisionsFollowTheReleasesThatAddedKeys() {
        // Counts read from each Fieldwatch release commit (3ebda52, a2563a3, 73d1006, da086fc, 819365d);
        // revision 6 is Spectre's own, open until its first release.
        assertEquals(listOf(1099, 5, 2, 53, 69, 19), (1..6).map(::count))
        assertEquals(6, EnglishCopy.KEY_REVISION)
        assertEquals(EnglishCopy.KEY_REVISION, EnglishCopy.lines.maxOf { it.revision })
        assertTrue(EnglishCopy.byKey.keys.containsAll(LanguageKeyRevisions.listed))
    }

    // Revision 1 is closed. A new key left out of LanguageKeyRevisions lands here and changes the hash:
    // add it to the table at the newest revision instead (raise NEWEST if a release shipped since).
    @Test
    fun revisionOneIsFrozen() {
        val keys = EnglishCopy.lines.filter { it.revision == 1 }.map { it.key }.sorted()
        val digest = MessageDigest.getInstance("SHA-256").digest(keys.joinToString("\n").toByteArray())
        assertEquals(REVISION_ONE_SHA256, digest.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun templateCarriesTheKeyRevision() {
        val text = LanguagePack.template("0.1.0")
        val revision = Json.parseToJsonElement(text).jsonObject.getValue("keyRevision").jsonPrimitive.int
        assertEquals(EnglishCopy.KEY_REVISION, revision)
        assertEquals(0, ok(text).newSince)
    }

    @Test
    fun newSinceCountsKeysAfterThePacksRevision() {
        assertEquals(count(6), ok(pack(""""keyRevision":5""")).newSince)
        assertEquals(count(5) + count(6), ok(pack(""""keyRevision":4""")).newSince)
        assertEquals(0, ok(pack(""""keyRevision":6""")).newSince)
    }

    @Test
    fun badKeyRevisionDropsTheCountNotThePack() {
        for (raw in listOf("\"2\"", "2.5", "0", "-1", "99999999999", "{}", "[1]")) {
            assertNull(raw, ok(pack(""""keyRevision":$raw,"writtenFor":"1.1.32"""")).newSince)
        }
        // null is the same as absent.
        assertEquals(count(6), ok(pack(""""keyRevision":null,"writtenFor":"1.1.32"""")).newSince)
    }

    @Test
    fun fieldwatchPacksMapTheirVersion() {
        val after = { revision: Int -> (revision + 1..6).sumOf(::count) }
        assertEquals(after(1), ok(pack(""""writtenFor":"1.1.27"""")).newSince)
        assertEquals(after(2), ok(pack(""""writtenFor":"1.1.28"""")).newSince)
        assertEquals(after(3), ok(pack(""""writtenFor":"1.1.29"""")).newSince)
        assertEquals(after(3), ok(pack(""""writtenFor":"1.1.30"""")).newSince)
        assertEquals(after(4), ok(pack(""""writtenFor":"1.1.31"""")).newSince)
        assertEquals(after(5), ok(pack(""""writtenFor":"1.1.32"""")).newSince)
        // Before the map existed every key is new, except the one the pack translates.
        assertEquals(EnglishCopy.lines.size - 1, ok(pack(""""writtenFor":"1.1.21"""")).newSince)
        // Not a Fieldwatch release with this map: a 0.x hand edit, or a later 1.x Spectre.
        assertNull(ok(pack(""""writtenFor":"0.1.0"""")).newSince)
        assertNull(ok(pack(""""writtenFor":"1.1.33"""")).newSince)
        assertNull(ok(pack(""""writtenFor":"1.2.0"""")).newSince)
    }

    private fun pack(fields: String): String =
        """{"format":"fieldwatch-language","formatVersion":1,"language":"Test",$fields,"strings":{"tab.live":"Direct"}}"""

    private fun ok(text: String): LanguagePreview {
        val parsed = LanguagePack.parse(text.toByteArray(Charsets.UTF_8))
        assertTrue(parsed.toString(), parsed is LanguageParse.Ok)
        return (parsed as LanguageParse.Ok).preview
    }

    private companion object {
        // The 1,099 keys of 3ebda52 (Fieldwatch 1.1.27), sorted and joined by "\n".
        const val REVISION_ONE_SHA256 = "ac6118f402537e850bbaf8c4f3066e146b2ff0ec18bbac907becf0cc9676782e"
    }
}
