package app.fieldwatch.domain

import app.fieldwatch.testing.TestFiles.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A key whose meaning changed keeps its name with the English "Unused" (CLAUDE.md, language keys). Nothing shows it
 * again, but a short translation of it still loads, and a long one is counted as ignored. This keeps such lines out of
 * the bundled packs. `-PpruneLanguagePacks` removes them, one line each, leaving every other byte as it was.
 */
class BundledPackRetiredKeysTest {
    private val retired = EnglishCopy.lines.filter { it.text == "Unused" }.map { it.key }.toSet()

    @Test
    fun bundledPacksCarryNoRetiredKeys() {
        val dir = repoFile("src/main/assets/lang")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        val found = files.associate { file -> file.name to stringKeys(file.readText(Charsets.UTF_8)).filter { it in retired } }
            .filterValues { it.isNotEmpty() }
        if (System.getProperty("spectre.pruneLanguagePacks") == "true") {
            for (name in found.keys) {
                val file = File(dir, name)
                file.writeText(prune(file.readText(Charsets.UTF_8), retired), Charsets.UTF_8)
            }
            return
        }
        assertEquals("Retired keys in bundled packs; run ./gradlew testDebugUnitTest -PpruneLanguagePacks", emptyMap<String, List<String>>(), found)
    }

    @Test
    fun pruneDropsMiddleLastAndOnlyEntriesAndKeepsTheRest() {
        val pack = "{\n  \"strings\": {\n    \"a.one\": \"1\",\n    \"b.two\": \"2\",\n    \"c.three\": \"3\"\n  }\n}"
        assertEquals("{\n  \"strings\": {\n    \"a.one\": \"1\",\n    \"c.three\": \"3\"\n  }\n}", prune(pack, setOf("b.two")))
        assertEquals("{\n  \"strings\": {\n    \"a.one\": \"1\",\n    \"b.two\": \"2\"\n  }\n}", prune(pack, setOf("c.three")))
        assertEquals("{\n  \"strings\": {\n  }\n}", prune(pack, setOf("a.one", "b.two", "c.three")))
        for (keys in listOf(setOf("b.two"), setOf("c.three"), setOf("a.one", "b.two", "c.three"))) {
            assertEquals(setOf("a.one", "b.two", "c.three") - keys, stringKeys(prune(pack, keys)).toSet())
        }
        // No final newline, and a CRLF line: both stay as they were.
        val crlf = "{\n  \"strings\": {\n    \"a.one\": \"1\",\r\n    \"b.two\": \"2\"\n  }\n}"
        assertEquals("{\n  \"strings\": {\n    \"a.one\": \"1\"\r\n  }\n}", prune(crlf, setOf("b.two")))
        assertEquals(crlf, prune(crlf, setOf("x.none")))
    }

    @Test
    fun theCurrentPacksParseAfterPruning() {
        val dir = repoFile("src/main/assets/lang")
        for (file in dir.listFiles { f -> f.extension == "json" }.orEmpty()) {
            val pruned = prune(file.readText(Charsets.UTF_8), retired)
            assertTrue(file.name, LanguagePack.parse(pruned.toByteArray(Charsets.UTF_8)) is LanguageParse.Ok)
        }
    }

    private fun stringKeys(json: String): List<String> =
        Json.parseToJsonElement(json).jsonObject["strings"]?.jsonObject?.keys?.toList().orEmpty()

    private val entry = Regex("""^\s*"([^"]+)"\s*:""")

    /**
     * [text] without the one-line entries for [keys]. Splits and joins on "\n" only, so a CR, a missing final newline
     * and every other line stay byte for byte. Removing the last entry takes the comma off the one before it.
     */
    private fun prune(text: String, keys: Set<String>): String {
        val kept = ArrayList<String>()
        for (line in text.split("\n")) {
            val key = entry.find(line)?.groupValues?.get(1)
            if (key != null && key in keys) {
                if (!line.trimEnd().endsWith(",")) {
                    val prev = kept.indexOfLast { entry.containsMatchIn(it) }
                    if (prev >= 0 && prev == kept.size - 1) {
                        val p = kept[prev]
                        val cr = p.endsWith("\r")
                        kept[prev] = p.removeSuffix("\r").trimEnd().removeSuffix(",") + if (cr) "\r" else ""
                    }
                }
                continue
            }
            kept += line
        }
        return kept.joinToString("\n")
    }
}
