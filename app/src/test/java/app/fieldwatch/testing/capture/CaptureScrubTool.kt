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
 * - Every part is scrubbed with its own secret, also remapping the capture's other parts' addresses.
 * - Each part is leak-checked against the identifiers of all parts.
 * - If any part leaks, nothing is written and the raw files stay for a scrubber fix.
 * - Otherwise the parts go to `<out>/<random id>/part-NNNN.ndjson` (no timestamps in names) through a
 *   temporary folder renamed into place, and the raw files are deleted.
 *
 * Reports carry counts and part numbers only, never an identifier, since Gradle writes test output to
 * reports the agent reads. Skipped unless both properties are set.
 */
class CaptureScrubTool {
    @Test
    fun scrubRawPartsIntoTheCorpus() {
        val input = System.getProperty("spectre.scrubIn").orEmpty()
        val output = System.getProperty("spectre.scrubOut").orEmpty()
        assumeTrue("Set -PscrubIn and -PscrubOut to run the scrub tool.", input.isNotBlank() && output.isNotBlank())
        val root = System.getProperty("spectre.rootDir").orEmpty().ifBlank { null }?.let(::File)
        val result = run(File(input), File(output), root)
        println("Capture scrub: ${result.parts} parts stored as ${result.dir?.name ?: "nothing"}; ${result.problems.size} problems")
        assertEquals("Problems (counts only; raw files kept)", emptyList<String>(), result.problems)
    }

    data class Result(val parts: Int, val dir: File?, val problems: List<String>)

    private data class Scrubbed(val raw: File, val lines: List<String>, val out: List<String>)

    companion object {
        private val PART_NAME = Regex("^capture-\\d{13}-\\d{4}\\.ndjson$")

        /**
         * No Gradle, no properties, so it can be tested directly. [projectRoot] is the Spectre checkout; neither
         * folder may be inside it.
         */
        fun run(rawDir: File, corpusDir: File, projectRoot: File? = null, random: java.util.Random = SecureRandom()): Result {
            require(rawDir.isAbsolute && corpusDir.isAbsolute) { "Use absolute paths." }
            require(rawDir.isDirectory) { "The raw folder doesn't exist." }
            require(corpusDir.isDirectory) { "The corpus folder must already exist (a clone of spectre-corpus)." }
            val rawPath = rawDir.toPath().toRealPath()
            val outPath = corpusDir.toPath().toRealPath()
            require(!outPath.startsWith(rawPath) && !rawPath.startsWith(outPath)) { "The raw and corpus folders must not contain each other." }
            require(gitWorkTree(rawDir) == null) { "The raw folder must not be inside a git work tree." }
            require(gitWorkTree(corpusDir) != null) { "The corpus folder must be inside the corpus git clone." }
            projectRoot?.toPath()?.toRealPath()?.let { root ->
                require(!rawPath.startsWith(root) && !outPath.startsWith(root)) { "Neither folder may be inside the Spectre checkout." }
            }

            // Only capture parts, as the recorder names them, and no links: a link would be read but not deleted.
            val entries = rawDir.listFiles().orEmpty().filter { it.name != LEAKS_FILE }
            val strays = entries.filter {
                !PART_NAME.matches(it.name) || !java.nio.file.Files.isRegularFile(it.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
            }
            require(strays.isEmpty()) { "The raw folder holds ${strays.size} entries that aren't capture parts." }
            // capture-<13-digit wallMs>-<part>: the full name sorts by start time, then part, across app runs.
            val raw = entries.sortedBy { it.name }
            require(raw.isNotEmpty()) { "No capture parts in the raw folder." }

            val parts = raw.map { it to it.readLines(Charsets.UTF_8).filter(String::isNotBlank) }
            val allLines = parts.flatMap { it.second }
            val allAddresses = CaptureLeakCheck.addressesIn(allLines)
            val scrubbed = parts.map { (file, lines) -> Scrubbed(file, lines, CaptureScrubber.scrubPart(lines, random, allAddresses)) }

            val details = StringBuilder()
            val problems = scrubbed.mapIndexedNotNull { i, s ->
                val others = scrubbed.filter { it !== s }.flatMap { it.lines }
                val leaks = CaptureLeakCheck.find(s.lines, s.out, others)
                if (leaks.isEmpty()) {
                    null
                } else {
                    leaks.forEach { details.append(s.raw.name).append(": ").append(it).append('\n') }
                    val kinds = leaks.groupingBy { it.kind }.eachCount().entries.joinToString(", ") { "${it.value} ${it.key}" }
                    "part ${i + 1}: $kinds"
                }
            }
            if (problems.isNotEmpty()) {
                // The values stay with the raw files and are deleted with them; reports get counts only.
                File(rawDir, LEAKS_FILE).writeText(details.toString(), Charsets.UTF_8)
                return Result(0, null, problems + "details in $LEAKS_FILE in the raw folder")
            }

            val id = CaptureScrubber.hex(ByteArray(6).also(random::nextBytes)).lowercase()
            val tmp = File(corpusDir, ".tmp-$id")
            val dir = File(corpusDir, id)
            check(tmp.mkdirs()) { "Could not create a temporary folder in $corpusDir" }
            try {
                scrubbed.forEachIndexed { i, s ->
                    File(tmp, "part-%04d.ndjson".format(i + 1)).writeText(s.out.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                }
                check(tmp.renameTo(dir)) { "Could not move the scrubbed capture into place" }
            } catch (e: Exception) {
                tmp.deleteRecursively()
                throw e
            }
            // Raw captures are deleted once the scrubbed copy is in place (PLAN P1-F4).
            val left = scrubbed.filterNot { it.raw.delete() }
            File(rawDir, LEAKS_FILE).delete()
            val note = if (left.isEmpty()) emptyList() else listOf("${left.size} raw parts could not be deleted; delete them by hand")
            return Result(scrubbed.size, dir, note)
        }

        const val LEAKS_FILE = "LEAKS.txt"

        private fun gitWorkTree(start: File): File? {
            var dir: File? = start.canonicalFile
            while (dir != null) {
                if (File(dir, ".git").exists()) return dir
                dir = dir.parentFile
            }
            return null
        }
    }
}
