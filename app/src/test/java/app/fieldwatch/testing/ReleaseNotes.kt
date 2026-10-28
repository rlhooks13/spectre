package app.fieldwatch.testing

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.Features
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchCorpus
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.matchKey
import java.io.File
import java.lang.reflect.Modifier

/**
 * P0-8d: the release notes' list of every label and verdict change (PLAN release model), generated, not hand-written.
 *
 * - **Signatures:** the stock catalog diff between two frozen snapshots (`catalog/snapshots/v<N>.json`).
 *   - Signatures added or removed, renamed, reclassed, switched on or off.
 *   - Matching mode and peer clustering (min peers, window, OUI clusters, sequential MACs).
 *   - Attention notes; rules added, retired or switched; decodes; notes.
 * - **Verdicts:** every match-corpus case (the real devices and their confusers) whose signatures, their class or
 *   their attention flag differ between the two catalogs. Each case runs as a live group of three, so cluster
 *   signatures can fire too.
 * - **Switches:** the compile-time switches in [Features], where every switch that changes detection lives.
 *
 * Releases chain: each notes file starts at the catalog version the previous one ended at (v97, Fieldwatch 1.1.32's
 * catalog, for the first), so no version's changes go unpublished. The signatures section is reproducible from the
 * frozen snapshots, so ReleaseNotesTest checks every committed file against it.
 */
object ReleaseNotes {
    const val FIRST_CATALOG = 97
    const val BEGIN = "<!-- generated: begin -->"
    const val END = "<!-- generated: end -->"
    private const val CATALOG_END = "<!-- generated: catalog end -->"
    private val HEADER = Regex("""<!-- generated: catalog v(\d+) -> v(\d+) -->""")
    private val VERSION = Regex("""^(\d+)\.(\d+)\.(\d+)\.md$""")

    val notesDir: File get() = TestFiles.repoFile("../docs/release-notes")

    fun snapshot(version: Int): List<Fleet> {
        val file = TestFiles.repoFile("../catalog/snapshots/v$version.json")
        require(file.isFile) { "No catalog snapshot v$version" }
        return SignatureExchange.parse(file.readText(Charsets.UTF_8)).fleets
    }

    /** The whole generated block, catalog [from] to [to]. */
    fun block(from: Int, to: Int, oldFleets: List<Fleet> = snapshot(from), newFleets: List<Fleet> = snapshot(to)): String = buildString {
        append(BEGIN).append('\n')
        append("<!-- generated: catalog v$from -> v$to -->\n")
        append(signatures(from, to, oldFleets, newFleets))
        append(CATALOG_END).append("\n\n")
        append(verdicts(oldFleets, newFleets))
        append('\n')
        append(switches())
        append(END).append('\n')
    }

    /** What a rule matches, ignoring whether it is switched on: the shared key (P1-B6). */
    private fun matchKey(r: MatchRule): String = r.matchKey()

    /** The reproducible part: the catalog diff. */
    fun signatures(from: Int, to: Int, oldFleets: List<Fleet> = snapshot(from), newFleets: List<Fleet> = snapshot(to)): String = buildString {
        val old = oldFleets.associateBy { it.id }
        val new = newFleets.associateBy { it.id }
        append("### Signatures (stock catalog v$from → v$to)\n\n")
        val lines = mutableListOf<String>()
        for (id in (new.keys - old.keys).sorted()) {
            val f = new.getValue(id)
            lines += "- **New:** ${f.name} (${klass(f)})${if (f.rules.isEmpty()) "" else " matches on " + f.rules.joinToString("; ") { rule(it) }}."
        }
        for (id in (old.keys - new.keys).sorted()) {
            lines += "- **Removed:** ${old.getValue(id).name} (${klass(old.getValue(id))})."
        }
        for (id in (old.keys intersect new.keys).sorted()) {
            val changes = changes(old.getValue(id), new.getValue(id))
            if (changes.isNotEmpty()) lines += "- **${new.getValue(id).name}** (${klass(new.getValue(id))}): ${changes.joinToString("; ")}."
        }
        if (lines.isEmpty()) append("No signature changes.\n") else lines.forEach { append(it).append('\n') }
    }

    private fun changes(a: Fleet, b: Fleet): List<String> {
        val out = mutableListOf<String>()
        if (a.name != b.name) out += "renamed from ${a.name}"
        if (a.kind != b.kind) out += "class ${klass(a)} → ${klass(b)}"
        if (a.enabled != b.enabled) out += if (b.enabled) "now on by default" else "now off by default"
        if (a.matchAny != b.matchAny) out += if (b.matchAny) "now matches on any rule" else "now needs every rule"
        if (a.minPeers != b.minPeers) out += "needs ${b.minPeers} peers (was ${a.minPeers})"
        if (a.peerWindowSec != b.peerWindowSec) out += "peer window ${b.peerWindowSec} s (was ${a.peerWindowSec} s)"
        if (a.clusterByOui != b.clusterByOui) out += if (b.clusterByOui) "now clusters by OUI" else "no longer clusters by OUI"
        if (a.sequentialMac != b.sequentialMac) out += if (b.sequentialMac) "now groups sequential addresses" else "no longer groups sequential addresses"
        if (a.attentionNote.isBlank() && b.attentionNote.isNotBlank()) out += "now flagged for extra attention"
        if (a.attentionNote.isNotBlank() && b.attentionNote.isBlank()) out += "no longer flagged for extra attention"
        if (a.attentionNote.isNotBlank() && b.attentionNote.isNotBlank() && a.attentionNote != b.attentionNote) out += "attention note reworded"
        val aRules = a.rules.associateBy(::matchKey)
        val bRules = b.rules.associateBy(::matchKey)
        val goneAll = (aRules.keys - bRules.keys).map { aRules.getValue(it) }
        val addedAll = (bRules.keys - aRules.keys).map { bRules.getValue(it) }
        // An any-radio rule replaced by exactly one copy of itself on one radio, switched the same, reads as a narrowing,
        // not a removal and an addition. A split across radios, or a switch flip, stays a removal plus additions.
        val narrowed = goneAll.filter { it.radio == null }.mapNotNull { old ->
            val copies = addedAll.filter { it.radio != null && matchKey(it.copy(radio = null)) == matchKey(old) }
            copies.singleOrNull()?.takeIf { it.enabled == old.enabled }
        }
        val narrowedKeys = narrowed.map { matchKey(it.copy(radio = null)) }.toSet()
        val gone = goneAll.filterNot { matchKey(it) in narrowedKeys }
        val added = addedAll - narrowed.toSet()
        val switched = (aRules.keys intersect bRules.keys).filter { aRules.getValue(it).enabled != bRules.getValue(it).enabled }
        if (gone.isNotEmpty()) out += "stopped matching on ${gone.joinToString("; ") { rule(it) }}"
        if (added.isNotEmpty()) out += "now also matches on ${added.joinToString("; ") { rule(it) }}"
        narrowed.groupBy { it.radio!! }.forEach { (radio, rules) ->
            out += "now ${radioLabel(radio)} only: ${rules.joinToString(", ") { rule(it.copy(radio = null)) }}"
        }
        switched.forEach { k -> out += "rule ${rule(bRules.getValue(k))} switched ${if (bRules.getValue(k).enabled) "on" else "off"}" }
        if (a.decode != b.decode) out += "decoded fields changed"
        if (a.notes != b.notes) out += "notes reworded"
        return out
    }

    /** Every match-corpus case whose labels (signature, class, attention) differ between the two catalogs. */
    fun verdicts(old: List<Fleet>, new: List<Fleet>, cases: List<Pair<String, Sighting>> = corpusCases()): String = buildString {
        append("### Verdict changes in the test corpus\n\n")
        val before = labels(cases, old)
        val after = labels(cases, new)
        val lines = cases.mapNotNull { (label, s) ->
            val a = before.getValue(s.key)
            val b = after.getValue(s.key)
            if (a == b) null else "- $label: was ${a.ifEmpty { "no signature" }}, now ${b.ifEmpty { "no signature" }}."
        }
        if (lines.isEmpty()) append("No corpus case changes.\n") else lines.forEach { append(it).append('\n') }
    }

    /** Labels name their group, since a target and a near miss can share a description. */
    private fun corpusCases(): List<Pair<String, Sighting>> =
        MatchCorpus.targets.map { "Target: ${it.label}" to it.sighting } +
            MatchCorpus.nearMisses.map { m ->
                "Near miss: ${m.label}" + (m.finderKnownUntil?.let { " (its signature is right, but it is still read as a tracker until $it)" } ?: "") to m.sighting
            } +
            MatchCorpus.randomAddressOuiHits.map { "Random address: ${it.label}" to it.sighting }

    /** Each case as a live group of three heard at NOW on consecutive addresses; the label of its first member. */
    private fun labels(cases: List<Pair<String, Sighting>>, fleets: List<Fleet>): Map<String, String> {
        val byId = fleets.associateBy { it.id }
        return cases.associate { (_, s) ->
            val group = (1..3).map { n ->
                s.copy(key = "${s.key}#$n", mac = s.mac.substring(0, 15) + "%02X".format(n), lastSeen = Golden.NOW, firstSeen = Golden.NOW - 30_000L)
            }
            val ids = SignatureEngine().match(group, fleets, now = Golden.NOW).getValue(group.first().key)
            s.key to ids.mapNotNull { byId[it] }.sortedBy { it.name }.joinToString(", ") { f ->
                "${f.name} (${klass(f)}${if (f.attentionNote.isNotBlank()) ", extra attention" else ""})"
            }
        }
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

    private fun radioLabel(radio: RadioKind): String = when (radio) {
        RadioKind.WIFI -> "Wi-Fi"
        RadioKind.BLE -> "BLE"
    }

    private fun rule(r: MatchRule): String {
        val radio = r.radio?.let { " (${it.name})" }.orEmpty()
        val off = if (r.enabled) "" else " (switched off)"
        val company = if (r.companyId == 0) "any company" else "Bluetooth company id 0x" + r.companyId.toString(16).uppercase().padStart(4, '0')
        val prefix = if (r.dataPrefixHex.isBlank()) "" else " data starting " + r.dataPrefixHex.uppercase()
        return when (r.kind) {
            RuleKind.MANUFACTURER_ID -> company
            RuleKind.MANUFACTURER_DATA -> company + prefix
            else -> r.kind.name.lowercase().replace('_', ' ') + (if (r.text.isBlank()) "" else " \"${r.text}\"") + prefix
        } + radio + off
    }

    /** Notes files in [dir], oldest release first (by X.Y.Z, numerically). */
    fun notesFiles(dir: File = notesDir): List<File> =
        dir.listFiles().orEmpty().filter { VERSION.matches(it.name) }.sortedWith(
            compareBy({ VERSION.find(it.name)!!.groupValues[1].toInt() }, { VERSION.find(it.name)!!.groupValues[2].toInt() }, { VERSION.find(it.name)!!.groupValues[3].toInt() }),
        )

    /**
     * Writes or refreshes the generated block in `<dir>/<version>.md`, up to the current catalog. It starts where the
     * previous release's notes ended (FIRST_CATALOG for the first). Hand-written text around the block is kept; a
     * hand-written file without a block gets one appended; a new file gets a heading and a summary line.
     */
    fun write(version: String, dir: File = notesDir, to: Int = ConfigStore.CATALOG_VERSION): File {
        require(Regex("""^\d+\.\d+\.\d+$""").matches(version)) { "Version is X.Y.Z" }
        val file = File(dir, "$version.md")
        val previous = notesFiles(dir).filter { it.name != file.name }.lastOrNull { older(it.name, file.name) }
        val from = previous?.let { declaredRange(it.readText(Charsets.UTF_8))?.second } ?: FIRST_CATALOG
        require(from <= to) { "The previous notes (${previous?.name}) end at v$from, after the current catalog v$to" }
        val generated = block(from, to)
        val text = when {
            !file.isFile -> "# Spectre $version\n\nSummary: (hand-written)\n\n## What changed in detection\n\n$generated"
            else -> {
                val old = file.readText(Charsets.UTF_8)
                if (BEGIN in old && END in old) {
                    old.substringBefore(BEGIN) + generated + old.substringAfter(END).removePrefix("\n")
                } else {
                    old.trimEnd('\n') + "\n\n## What changed in detection\n\n" + generated
                }
            }
        }
        dir.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    private fun older(a: String, b: String): Boolean {
        fun parts(n: String) = VERSION.find(n)!!.groupValues.drop(1).map { it.toInt() }
        val x = parts(a)
        val y = parts(b)
        return (0..2).firstOrNull { x[it] != y[it] }?.let { x[it] < y[it] } ?: false
    }

    /** The catalog range a notes file's block declares, or null if it has none. */
    fun declaredRange(text: String): Pair<Int, Int>? =
        HEADER.find(text)?.groupValues?.let { it[1].toInt() to it[2].toInt() }

    /** The catalog part of a notes file's block, as committed. */
    fun committedSignatures(text: String): String =
        text.substringAfter(HEADER.find(text)!!.value + "\n").substringBefore(CATALOG_END)

    /**
     * Problems with the notes files in [dir]: a file without a block, a signatures section that isn't its range's
     * diff, a gap or overlap between consecutive releases, or a range past the current catalog.
     */
    fun problems(dir: File = notesDir, current: Int = ConfigStore.CATALOG_VERSION): List<String> {
        val out = mutableListOf<String>()
        var expectFrom = FIRST_CATALOG
        for (file in notesFiles(dir)) {
            val text = file.readText(Charsets.UTF_8)
            val range = declaredRange(text)
            if (range == null) {
                out += "${file.name}: no generated block (P0-8d)"
                continue
            }
            val (from, to) = range
            if (from != expectFrom) out += "${file.name}: starts at v$from, but the previous release ended at v$expectFrom"
            if (to > current) out += "${file.name}: ends at v$to, after the current catalog v$current"
            if (from <= to && to <= current && signatures(from, to) != committedSignatures(text)) {
                out += "${file.name}: its signature changes aren't the v$from → v$to diff"
            }
            expectFrom = to
        }
        return out
    }
}
