package app.fieldwatch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BrandGuard (PLAN P0-6): the upstream name and owner must not reach anything Spectre ships,
 * except where an allowlist entry below says why. It checks values, not keys or identifiers:
 * string literals in app sources, resource and manifest text, text assets, and `dist/`.
 * Language-pack values are checked by BrandTest. The lowercase legacy ids (`app.fieldwatch`,
 * `fieldwatch-settings`, …) don't match, on purpose.
 *
 * Moves to a build-logic task in Phase 2, once build-logic exists.
 */
class BrandGuardTest {
    private class Allow(val path: Regex, val line: Regex, val reason: String)

    private val upstream = Regex("Fieldwatch|FIELDWATCH|OffGridPete|OGridPete|Off Grid Pete")

    /** Files not scanned at all. */
    private val skipped = listOf(
        Regex("""app/src/main/assets/lang/.*"""), // values checked by BrandTest
        Regex("""dist/CHANGELOG\.md"""), // upstream history, kept as written
        Regex("""dist/.*\.json"""), // legacy-format stock catalog packs; replaced in Phase 4
    )

    private val allowed = listOf(
        // Permanent.
        Allow(Regex(""".*/domain/(TakPublish|SitTakPackage)\.kt"""), Regex(""""FIELDWATCH-"""),
            "TAK UIDs are stable ids: peers match and delete markers by them"),
        Allow(Regex(""".*/domain/(Settings|Signature)Exchange\.kt"""), Regex("""exported from \$\{Brand\.NAME\} or Fieldwatch"""),
            "import hint: files exported from upstream still import"),
        Allow(Regex(""".*"""), Regex("""Copyright \(c\) 2026 Off Grid Pete LLC"""),
            "MIT requires keeping upstream's copyright notice"),
        Allow(Regex("""app/src/main/(AndroidManifest\.xml|res/values/themes\.xml)"""), Regex("""\.FieldwatchApp"|Theme\.Fieldwatch"""),
            "class and style identifiers, not shown to users; renamed with the package moves"),
        Allow(Regex(""".*/domain/(Fieldwatch|Spectre)Disclaimer\.kt"""), Regex("""Off Grid Pete LLC (is|are) not liable|Spectre contributors and Off Grid Pete LLC"""),
            "liability disclaimer names upstream (owner-reviewed, P0-4d)"),
        Allow(Regex("""dist/instruction\.txt"""), Regex("""fork of Fieldwatch 1\.1\.32|endorsed by Off Grid Pete LLC|next to Fieldwatch|settings in Fieldwatch and Import|upstream Fieldwatch's"""),
            "fork statement and migration notes name upstream on purpose"),
        Allow(Regex("""dist/fieldwatch-sensors\.txt"""), Regex("""built by upstream Fieldwatch"""),
            "data credit to upstream"),
        // Temporary: each names the step that removes it.
        Allow(Regex(""".*/data/(CatalogRemote|FlockFiles)\.kt"""), Regex("""raw\.githubusercontent\.com/OffGridPete/Fieldwatch"""),
            "TEMPORARY until P0-7 turns off or replaces the upstream catalog and sensor downloads"),
        Allow(Regex(""".*/domain/FieldwatchDisclaimer\.kt"""), Regex("""Do not use Fieldwatch"""),
            "TEMPORARY until P0-4d (PR #10) renames the disclaimer"),
        Allow(Regex(""".*/domain/ChromeLines\.kt"""), Regex(""""ui\.off\.grid\.pete\.llc""""),
            "TEMPORARY until P0-4d (PR #10) retires this key"),
        Allow(Regex(""".*/domain/LanguageCopy\.kt"""), Regex("""certified by Off Grid Pete LLC"""),
            "TEMPORARY until P0-4d (PR #10) rewords NOT_REVIEWED"),
        Allow(Regex(""".*/domain/DefaultCatalog\.kt"""), Regex("""^\s*(notes|attentionNote) = """),
            "TEMPORARY: catalog notes change only through the catalog checklist (version bump, ladder step, snapshot)"),
    )

    private val stringLiteral = Regex(""""(?:[^"\\]|\\.)*"""")

    @Test
    fun nothingShippedNamesTheUpstreamAppOrOwner() {
        val root = repoRoot()
        val files = shippedFiles(root)
        assertTrue("found only ${files.size} files to scan", files.size > 100)
        val hits = files.flatMap { file ->
            val rel = file.relativeTo(root).invariantSeparatorsPath
            if (skipped.any { it.matches(rel) }) return@flatMap emptyList()
            file.readLines(Charsets.UTF_8).withIndex().mapNotNull { (i, line) ->
                val text = if (rel.endsWith(".kt")) stringLiteral.findAll(line).joinToString(" ") { it.value } else line
                if (!upstream.containsMatchIn(text)) return@mapNotNull null
                if (allowed.any { it.path.matches(rel) && it.line.containsMatchIn(line) }) return@mapNotNull null
                "$rel:${i + 1}: ${line.trim()}"
            }
        }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun theGuardCatchesTheUpstreamName() {
        val line = """        val label = "Fieldwatch settings""""
        assertTrue(upstream.containsMatchIn(stringLiteral.findAll(line).joinToString(" ") { it.value }))
        val identifier = """class FieldwatchViewModel(app: Application)"""
        assertTrue(stringLiteral.findAll(identifier).none())
    }

    private fun shippedFiles(root: File): List<File> {
        val main = File(root, "app/src/main")
        val text = setOf("kt", "xml", "txt", "json", "md")
        val sources = main.walkTopDown().filter { it.isFile && it.extension in text }
        val dist = File(root, "dist").walkTopDown().filter { it.isFile && it.extension in text }
        return (sources + dist).sortedBy { it.path }.toList()
    }

    private fun repoRoot(): File =
        listOf(File("."), File("..")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").isFile }
}
