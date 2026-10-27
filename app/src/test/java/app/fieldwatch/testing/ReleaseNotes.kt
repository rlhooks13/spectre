package app.fieldwatch.testing

import app.fieldwatch.domain.Features
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchCorpus
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureExchange
import java.io.File
import java.lang.reflect.Modifier

/**
 * P0-8d: the release notes' list of every label and verdict change (PLAN release model), generated, not hand-written.
 *
 * - **Signatures:** the stock catalog diff between two frozen snapshots (`catalog/snapshots/v<N>.json`): signatures
 *   added or removed, renamed, reclassed, switched on or off, attention notes, and rules added or retired.
 * - **Verdicts:** every match-corpus case (the real devices and their confusers) whose signature hits differ between
 *   the two catalogs.
 * - **Switches:** the compile-time feature switches as this build has them.
 *
 * The signatures section is reproducible from the frozen snapshots, so ReleaseNotesTest checks every committed
 * notes file against it. The verdicts and switches describe the build at release time and aren't re-checked later.
 */
object ReleaseNotes {
    const val BEGIN = "<!-- generated: begin -->"
    const val END = "<!-- generated: end -->"
    private val HEADER = Regex("""<!-- generated: catalog v(\d+) -> v(\d+) -->""")

    fun snapshot(version: Int): List<Fleet> =
        SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$version.json").readText(Charsets.UTF_8)).fleets

    /** The whole generated block, catalog [from] to [to]. */
    fun block(from: Int, to: Int): String = buildString {
        append(BEGIN).append('\n')
        append("<!-- generated: catalog v$from -> v$to -->\n")
        append(signatures(from, to))
        append("<!-- generated: catalog end -->\n\n")
        append(verdicts(snapshot(from), snapshot(to)))
        append('\n')
        append(switches())
        append(END).append('\n')
    }

    /** The reproducible part: the catalog diff. */
    fun signatures(from: Int, to: Int): String = buildString {
        val old = snapshot(from).associateBy { it.id }
        val new = snapshot(to).associateBy { it.id }
        append("### Signatures (stock catalog v$from → v$to)\n\n")
        val lines = mutableListOf<String>()
        for (id in (new.keys - old.keys).sorted()) {
            val f = new.getValue(id)
            lines += "- **New:** ${f.name} (${klass(f)})${rulesLine(" matches ", f.rules)}"
        }
        for (id in (old.keys - new.keys).sorted()) {
            lines += "- **Removed:** ${old.getValue(id).name} (${klass(old.getValue(id))})"
        }
        for (id in (old.keys intersect new.keys).sorted()) {
            val a = old.getValue(id)
            val b = new.getValue(id)
            val changes = mutableListOf<String>()
            if (a.name != b.name) changes += "renamed from ${a.name}"
            if (a.kind != b.kind) changes += "class ${klass(a)} → ${klass(b)}"
            if (a.enabled != b.enabled) changes += if (b.enabled) "now on by default" else "now off by default"
            if (a.matchAny != b.matchAny) changes += if (b.matchAny) "now matches on any rule" else "now needs every rule"
            if (a.attentionNote.isBlank() && b.attentionNote.isNotBlank()) changes += "now flagged for extra attention"
            if (a.attentionNote.isNotBlank() && b.attentionNote.isBlank()) changes += "no longer flagged for extra attention"
            if (a.attentionNote.isNotBlank() && b.attentionNote.isNotBlank() && a.attentionNote != b.attentionNote) changes += "attention note reworded"
            val aKeys = a.rules.associateBy { SignatureExchange.ruleKey(it) }
            val bKeys = b.rules.associateBy { SignatureExchange.ruleKey(it) }
            val gone = (aKeys.keys - bKeys.keys).map { aKeys.getValue(it) }
            val added = (bKeys.keys - aKeys.keys).map { bKeys.getValue(it) }
            if (gone.isNotEmpty()) changes += "stopped matching on ${gone.joinToString("; ") { rule(it) }}"
            if (added.isNotEmpty()) changes += "now also matches on ${added.joinToString("; ") { rule(it) }}"
            if (a.decode != b.decode) changes += "decoded fields changed"
            if (a.notes != b.notes) changes += "notes reworded"
            if (changes.isNotEmpty()) lines += "- **${b.name}** (${klass(b)}): ${changes.joinToString("; ")}."
        }
        if (lines.isEmpty()) append("No signature changes.\n") else lines.forEach { append(it).append('\n') }
    }

    /** Every match-corpus case whose hits differ between the two catalogs. */
    fun verdicts(old: List<Fleet>, new: List<Fleet>): String = buildString {
        append("### Verdict changes in the test corpus\n\n")
        val cases = MatchCorpus.targets.map { it.label to it.sighting } + MatchCorpus.nearMisses.map { it.label to it.sighting } +
            MatchCorpus.randomAddressOuiHits.map { it.label to it.sighting }
        val engine = SignatureEngine()
        val before = engine.match(cases.map { it.second }, old, now = Golden.NOW)
        val after = SignatureEngine().match(cases.map { it.second }, new, now = Golden.NOW)
        fun names(fleets: List<Fleet>, ids: Set<String>): String {
            val byId = fleets.associateBy { it.id }
            return ids.mapNotNull { byId[it] }.sortedBy { it.name }.joinToString(", ") { "${it.name} (${klass(it)})" }.ifEmpty { "no signature" }
        }
        val lines = cases.mapNotNull { (label, s) ->
            val a = before.getValue(s.key)
            val b = after.getValue(s.key)
            if (a == b) null else "- $label: was ${names(old, a)}, now ${names(new, b)}."
        }
        if (lines.isEmpty()) append("No corpus case changes.\n") else lines.forEach { append(it).append('\n') }
    }

    /** The compile-time switches in [Features], as this build has them. */
    fun switches(): String = buildString {
        append("### Feature switches\n\n| Switch | State |\n|---|---|\n")
        Features::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == java.lang.Boolean.TYPE }
            .sortedBy { it.name }
            .forEach { f ->
                f.isAccessible = true
                append("| `").append(f.name).append("` | ").append(if (f.getBoolean(null)) "on" else "off").append(" |\n")
            }
    }

    private fun klass(f: Fleet): String = f.kind.name.lowercase().replace('_', ' ')

    private fun rule(r: MatchRule): String {
        val radio = r.radio?.let { " (${it.name})" }.orEmpty()
        val off = if (r.enabled) "" else " (switched off)"
        return when (r.kind) {
            RuleKind.MANUFACTURER_ID -> "Bluetooth company id 0x%04X".format(r.companyId)
            RuleKind.MANUFACTURER_DATA -> "Bluetooth company id 0x%04X data starting ${r.dataPrefixHex}".format(r.companyId)
            else -> "${r.kind.name.lowercase().replace('_', ' ')} \"${r.text}\""
        } + radio + off
    }

    private fun rulesLine(prefix: String, rules: List<MatchRule>): String =
        if (rules.isEmpty()) "" else prefix + rules.joinToString("; ") { rule(it) }

    /**
     * Writes or refreshes the generated block in `docs/release-notes/<version>.md`, keeping any hand-written text
     * around it. A new file gets a heading and a line for the hand-written summary.
     */
    fun write(version: String, from: Int, to: Int): File {
        require(Regex("""^\d+\.\d+\.\d+$""").matches(version)) { "Version is X.Y.Z" }
        val file = TestFiles.repoFile("../docs/release-notes").resolve("$version.md")
        val generated = block(from, to)
        val text = if (file.isFile) {
            val old = file.readText(Charsets.UTF_8)
            check(BEGIN in old && END in old) { "${file.name} has no generated block to refresh" }
            old.substringBefore(BEGIN) + generated + old.substringAfter(END).removePrefix("\n")
        } else {
            "# Spectre $version\n\nSummary: (hand-written)\n\n## What changed in detection\n\n$generated"
        }
        file.parentFile.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    /** The catalog range a notes file's block declares, or null if it has none. */
    fun declaredRange(text: String): Pair<Int, Int>? =
        HEADER.find(text)?.groupValues?.let { it[1].toInt() to it[2].toInt() }

    /** The catalog part of a notes file's block, as committed. */
    fun committedSignatures(text: String): String =
        text.substringAfter(HEADER.find(text)!!.value + "\n").substringBefore("<!-- generated: catalog end -->")
}
