package app.fieldwatch.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FieldwatchViewModel's `init` launches coroutines that reach other threads before the
 * constructor finishes. A property initialized below `init` can still be null when they read it
 * (1.1.32 crashed on startup when `cameraCacheLock` was declared after `init`).
 */
class ViewModelInitOrderTest {
    @Test
    fun noPropertyIsInitializedAfterInit() {
        val lines = repoFile("src/main/java/app/fieldwatch/ui/FieldwatchViewModel.kt").readLines()
        val start = lines.indexOfFirst { it.startsWith("class FieldwatchViewModel") }
        assertTrue("class not found", start >= 0)
        val init = lines.indexOfFirst(start) { it == "    init {" }
        assertTrue("init block not found", init >= 0)
        val end = lines.indexOfFirst(init) { it == "}" }
        assertTrue("class end not found", end > init)
        val late = (init until end).filter { member.matches(lines[it]) }
            .map { "${it + 1}: ${lines[it].trim()}" }
        assertEquals(emptyList<String>(), late)
    }

    private val member = Regex(
        """^    (?:@Volatile\s+)?(?:(?:private|internal|protected)\s+)?(?:val|var)\s+\w+[^(]*(?:=|\bby\b).*""",
    )

    private fun List<String>.indexOfFirst(from: Int, predicate: (String) -> Boolean): Int {
        for (i in from until size) if (predicate(this[i])) return i
        return -1
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
