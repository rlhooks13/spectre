package app.fieldwatch.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FieldwatchViewModel's `init` launches coroutines that reach other threads before the
 * constructor finishes. A property declared below `init` can still be null when they read it
 * (1.1.32 crashed on startup when `cameraCacheLock` was declared after `init`).
 *
 * This guards source order only: it reads the file, so it can't see runtime behavior.
 */
class ViewModelInitOrderTest {
    @Test
    fun noPropertyIsDeclaredAfterInit() {
        val lines = repoFile("src/main/java/app/fieldwatch/ui/FieldwatchViewModel.kt").readLines()
        val start = lines.indexOfFirst { it.startsWith("class FieldwatchViewModel") }
        assertTrue("class not found", start >= 0)
        val end = (start + 1 until lines.size).firstOrNull { topLevel.matches(lines[it]) } ?: lines.size
        val inits = (start until end).filter { init.matches(lines[it]) }
        assertEquals("expected exactly one init block", 1, inits.size)
        val first = inits.single()

        val above = (start until first).count { isStoredProperty(lines, it) }
        assertTrue("the member pattern stopped matching ($above properties above init)", above > 20)

        val late = (first until end).filter { isStoredProperty(lines, it) }
            .map { "${it + 1}: ${lines[it].trim()}" }
        assertEquals(emptyList<String>(), late)
    }

    private val init = Regex("""^    init\s*\{.*""")

    private val topLevel = Regex("""^(?:@|(?:(?:private|internal|public|data|enum|sealed|abstract|open)\s+)*(?:class|object|interface|fun|val|var|const)\b).*""")

    private val member = Regex(
        """^    (?:@[\w:.]+(?:\([^)]*\))?\s+)*(?:(?:public|private|internal|protected|override|open|final|lateinit|const)\s+)*(?:val|var)\s+\w+.*""",
    )

    /** A class-level val/var with a backing field; a computed property (`get()`) has none. */
    private fun isStoredProperty(lines: List<String>, i: Int): Boolean {
        if (!member.matches(lines[i])) return false
        if (lines[i].contains(" get()")) return false
        val next = lines.getOrNull(i + 1)?.trim().orEmpty()
        return !next.startsWith("get()") && !next.startsWith("get() =")
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
