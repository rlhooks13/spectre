package app.fieldwatch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BrandGuard (PLAN P0-6): the upstream name and owner must not reach anything Spectre ships,
 * except where an allowlist entry says why. It checks values, not identifiers: every line of
 * every text file under `app/src/main`, `app/build.gradle.kts` and `dist/`, case-insensitively.
 * Stable ids (language keys, the Kotlin package, format ids, notification channels,
 * resource names, TAK UIDs) and CamelCase class names are masked first. Comment lines are
 * skipped. LICENSE and NOTICE live at the repo root and aren't scanned.
 *
 * Every allowlist entry must still match something, so an entry dies with the text it allowed;
 * a temporary entry names the step whose PR removes it.
 * Moves to a build-logic task in Phase 2, once build-logic exists.
 */
class BrandGuardTest {
    private class Allow(val path: Regex, val line: Regex, val reason: String, val until: String? = null)

    @Test
    fun nothingShippedNamesTheUpstreamAppOrOwner() {
        val root = repoRoot()
        val files = scannedFiles(root)
        val rels = files.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        listOf("app/src/main/AndroidManifest.xml", "app/src/main/res/values/strings.xml", "dist/instruction.txt", "app/src/main/assets/lang/de.json")
            .forEach { assertTrue("not scanned: $it", it in rels) }
        val used = mutableSetOf<Allow>()
        val hits = files.flatMap { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            file.readLines(Charsets.UTF_8).withIndex().mapNotNull { (i, line) ->
                violation(rel, line, used)?.let { "$rel:${i + 1}: ${line.trim()}" }
            }
        }
        assertEquals(emptyList<String>(), hits)
        val dead = ALLOWED.filterNot { it in used }.map { "${it.line.pattern} (${it.until ?: it.reason})" }
        assertEquals("allowlist entries that no longer match anything; remove them", emptyList<String>(), dead)
    }

    @Test
    fun everyShippedFileIsTextOrAKnownBinary() {
        val root = repoRoot()
        val unknown = (File(root, "app/src/main").walkTopDown() + File(root, "dist").walkTopDown())
            .filter { it.isFile && it.extension !in TEXT && it.extension !in BINARY }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
        assertEquals("add new file types to TEXT (scanned) or BINARY (with a reason)", emptyList<String>(), unknown)
    }

    @Test
    fun theGuardSeesThroughStringSyntax() {
        val rel = "app/src/main/java/app/fieldwatch/X.kt"
        val sink = mutableSetOf<Allow>()
        val caught = listOf(
            """append(""${'"'}<gpx version="1.1" creator="Fieldwatch ${'$'}{xml(v)}""${'"'}")""",
            """val s = "${'$'}{if (pro) "Fieldwatch Pro" else "x"} settings"""",
            """if (c == '"') append("Fieldwatch")""",
            """    Fieldwatch scans Wi-Fi in a raw string line""",
            """val a = "Field Watch"""",
            """val a2 = "FieldWatch"""",
            """val b = "made by OffGridPete"""",
            """val b2 = "Off-Grid Pete"""",
            """val c = "fieldwatch-export-1.csv"""",
        )
        caught.forEach { assertTrue("missed: $it", violation(rel, it, sink) != null) }
        val ignored = listOf(
            """class FieldwatchViewModel(app: Application)""",
            """import app.fieldwatch.domain.Brand""",
            """LanguageText.text("ui.fieldwatch.watchlist")""",
            """const val CHANNEL = "fieldwatch_scan"""",
            """const val FORMAT = "fieldwatch-settings"""",
            """.setSmallIcon(R.drawable.ic_stat_fieldwatch)""",
            """return "FIELDWATCH-GNSS-${'$'}key"""",
            """// Fieldwatch upstream note in a comment""",
        )
        ignored.forEach { assertTrue("false hit: $it", violation(rel, it, sink) == null) }
        assertTrue(sink.isEmpty())
    }

    /** Null when the line is clean or allowed; records which allowlist entry it used. */
    private fun violation(rel: String, line: String, used: MutableSet<Allow>): String? {
        val trimmed = line.trimStart()
        if (rel.endsWith(".kt") || rel.endsWith(".kts")) {
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return null
        }
        val masked = ID_TOKENS.fold(line) { acc, token -> token.replace(acc, "") }
        if (!UPSTREAM.containsMatchIn(masked)) return null
        val allow = ALLOWED.firstOrNull { it.path.matches(rel) && it.line.containsMatchIn(line) } ?: return line
        used += allow
        return null
    }

    private fun scannedFiles(root: File): List<File> {
        val main = File(root, "app/src/main").walkTopDown()
        val dist = File(root, "dist").walkTopDown()
        return (main + dist + sequenceOf(File(root, "app/build.gradle.kts")))
            .filter { it.isFile && it.extension in TEXT }
            .filterNot { file -> SKIPPED.any { it.matches(file.relativeTo(root).invariantSeparatorsPath) } }
            .sortedBy { it.path }
            .toList()
    }

    private fun repoRoot(): File =
        listOf(File("."), File("..")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").isFile }

    private companion object {
        val UPSTREAM = Regex("(?i)field[\\s_-]?watch|off[\\s-]?grid[\\s-]?pete|o[\\s-]?grid[\\s-]?pete")

        val TEXT = setOf("kt", "kts", "xml", "txt", "json", "md")

        /** Not scanned: images and third-party data tables (IEEE/SIG numbers, OSM sensors). */
        val BINARY = setOf("png", "webp", "bin", "gz")

        val SKIPPED = listOf(
            Regex("""dist/CHANGELOG\.md"""), // upstream history, kept as written
            Regex("""dist/fieldwatch-signatures(-v2)?\.json"""), // legacy catalog packs; P4-1 replaces them
        )

        /** Stable ids that keep the legacy spelling on purpose. Masked before matching, in this order. */
        val ID_TOKENS = listOf(
            Regex(""""[a-z0-9]+(?:\.[a-z0-9]+)+""""), // language keys (before the app id, which they can contain)
            Regex("""app\.fieldwatch\b"""), // the Kotlin package and namespace (the application id changed in P0-4e)
            Regex("""\bfieldwatch_[a-z0-9_]+"""), // notification channel ids
            Regex("""\b[a-z0-9_]+_fieldwatch\b"""), // resource names, e.g. ic_stat_fieldwatch
            Regex("""\bfieldwatch-(?:settings|signatures|signature-text|sit|places|language|sensors|class|%03d)\b"""), // format ids, file names, the TTS utterance id
            Regex("""startsWith\("fieldwatch-"\)"""), // internal log files
            Regex("""\bFIELDWATCH-"""), // TAK UIDs: peers match and delete markers by them
            Regex("""\bFieldwatch[A-Z]\w*"""), // class names
            Regex("""\bTheme\.Fieldwatch\b"""), // style name
        )

        val ALLOWED = listOf(
            // Permanent.
            Allow(Regex(""".*/domain/SpectreDisclaimer\.kt|dist/instruction\.txt"""), Regex("""Copyright \(c\) 2026 Off Grid Pete LLC(\\n"|$)"""),
                "MIT requires keeping upstream's copyright notice"),
            Allow(Regex(""".*/domain/SpectreDisclaimer\.kt"""), Regex("""the Spectre contributors and Off Grid Pete LLC are not liable"""),
                "liability disclaimer names upstream (owner-reviewed, P0-4d)"),
            Allow(Regex(""".*/domain/ChromeLines\.kt"""), Regex(""""Copyright \(c\) 2026 Off Grid Pete LLC\. Copyright \(c\) 2026 the Spectre contributors\. MIT License\.""""),
                "the About attribution (ui.copyright.spectre)"),
            Allow(Regex(""".*/domain/(Settings|Signature)Exchange\.kt"""), Regex("""exported from \$\{Brand\.NAME\} or Fieldwatch"""),
                "import hint: files exported from upstream still import"),
            Allow(Regex(""".*/data/ConfigStore\.kt"""), Regex("""\.replace\("Spectre", "Fieldwatch"\)"""),
                "the v98 ladder step (P1-B1) recognizes the stock notes' pre-v98 wording, so it only replaces untouched notes"),
            Allow(Regex(""".*/domain/SitTakPackage\.kt"""), Regex("""ifBlank \{ "fieldwatch" }"""),
                "folder name inside the TAK package zip; kept as an id (P0-4b)"),
            Allow(Regex("""dist/instruction\.txt"""), Regex("""fork of Fieldwatch 1\.1\.32|endorsed by Off Grid Pete LLC|next to Fieldwatch;|settings in Fieldwatch and Import"""),
                "fork statement and migration notes name upstream on purpose"),
            Allow(Regex("""dist/fieldwatch-sensors\.txt"""), Regex("""built by upstream Fieldwatch"""),
                "data credit to upstream"),
        )
    }
}
