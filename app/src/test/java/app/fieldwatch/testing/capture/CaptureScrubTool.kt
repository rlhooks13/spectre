package app.fieldwatch.testing.capture

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.SecureRandom

/**
 * P1-F4: scrubs raw capture parts into the private corpus (docs/capture-runbook.md, ADR-0017).
 *
 * `./gradlew testDebugUnitTest --tests '*CaptureScrubTool*' -PscrubIn=<raw dir> -PscrubOut=<corpus captures dir>`
 *
 * Every part is scrubbed with its own secret and leak-checked. If any part leaks, nothing is written and the
 * raw files stay for a scrubber fix. Otherwise the parts go to `<out>/<random id>/part-NNNN.ndjson` (no
 * timestamps in names) and the raw files are deleted. Skipped unless both properties are set.
 */
class CaptureScrubTool {
    @Test
    fun scrubRawPartsIntoTheCorpus() {
        val input = System.getProperty("spectre.scrubIn").orEmpty()
        val output = System.getProperty("spectre.scrubOut").orEmpty()
        assumeTrue("Set -PscrubIn and -PscrubOut to run the scrub tool.", input.isNotBlank() && output.isNotBlank())
        val result = run(File(input), File(output))
        println("Capture scrub: ${result.parts} parts → ${result.dir}")
        assertEquals("Leaks found; nothing written, raw files kept", emptyList<String>(), result.leaks)
    }

    data class Result(val parts: Int, val dir: File?, val leaks: List<String>)

    companion object {
        /** Pure enough to test: no Gradle, no properties. */
        fun run(rawDir: File, corpusDir: File, random: java.util.Random = SecureRandom()): Result {
            val raw = rawDir.listFiles { f -> f.isFile && f.name.endsWith(".ndjson") }.orEmpty()
                .sortedBy { it.name.substringAfterLast('-').removeSuffix(".ndjson") } // by part number
            require(raw.isNotEmpty()) { "No capture parts in $rawDir" }
            require(!corpusDir.canonicalPath.startsWith(rawDir.canonicalPath)) { "Output must be outside the raw folder" }

            val scrubbed = raw.map { part ->
                val lines = part.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
                val out = CaptureScrubber.scrubPart(lines, random)
                val leaks = CaptureLeakCheck.leaks(lines, out).map { "${part.name}: $it" }
                Triple(part, out, leaks)
            }
            val leaks = scrubbed.flatMap { it.third }
            if (leaks.isNotEmpty()) return Result(0, null, leaks)

            val id = ByteArray(6).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val dir = File(corpusDir, id)
            check(dir.mkdirs()) { "Could not create $dir" }
            scrubbed.forEachIndexed { i, (_, out, _) ->
                File(dir, "part-%04d.ndjson".format(i + 1)).writeText(out.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
            }
            // Raw captures are deleted once scrubbed (PLAN P1-F4).
            for ((part, _, _) in scrubbed) check(part.delete()) { "Could not delete raw ${part.name}" }
            return Result(scrubbed.size, dir, emptyList())
        }
    }
}
