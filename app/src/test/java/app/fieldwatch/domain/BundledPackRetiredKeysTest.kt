package app.fieldwatch.domain

import app.fieldwatch.testing.TestFiles.repoFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A key whose meaning changed keeps its name with the English "Unused" (CLAUDE.md, language keys), so a bundled pack's
 * translation of it is ignored on load. This keeps the packs free of those lines. `-PpruneLanguagePacks` removes them,
 * one line each, leaving every other byte as it was.
 */
class BundledPackRetiredKeysTest {
    private val retired = EnglishCopy.lines.filter { it.text == "Unused" }.map { it.key }.toSet()
    private val entry = Regex("""^\s*"([^"]+)":""")

    @Test
    fun bundledPacksCarryNoRetiredKeys() {
        val dir = repoFile("src/main/assets/lang")
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        val found = files.associate { file -> file.name to retiredKeysIn(file.readLines(Charsets.UTF_8)) }
            .filterValues { it.isNotEmpty() }
        if (System.getProperty("spectre.pruneLanguagePacks") == "true") {
            for (name in found.keys) prune(File(dir, name))
            return
        }
        assertEquals("Retired keys in bundled packs; run ./gradlew testDebugUnitTest -PpruneLanguagePacks", emptyMap<String, List<String>>(), found)
    }

    private fun retiredKeysIn(lines: List<String>): List<String> =
        lines.mapNotNull { entry.find(it)?.groupValues?.get(1) }.filter { it in retired }

    private fun prune(file: File) {
        val kept = ArrayList<String>()
        for (line in file.readLines(Charsets.UTF_8)) {
            val key = entry.find(line)?.groupValues?.get(1)
            if (key != null && key in retired) {
                // The last entry of the strings object has no comma; its predecessor then becomes last.
                if (!line.trimEnd().endsWith(",") && kept.isNotEmpty()) kept[kept.size - 1] = kept.last().trimEnd().removeSuffix(",")
                continue
            }
            kept += line
        }
        file.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
