package app.fieldwatch.domain

import app.fieldwatch.testing.Golden
import app.fieldwatch.testing.GoldenRule
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: signature matching goldens over the match corpus and the stock catalog. They replace the hit sets
 * CatalogLintTest pinned by hand (P1-A5). A rule change shows up as a reviewable diff: every hand-written case's
 * full hit set with each signature's class and whether it carries an attention note, and every generated
 * positive that also hits another signature.
 */
class MatchGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val fleets = DefaultCatalog.fleets()
    private val byId = fleets.associateBy { it.id }

    private fun describe(id: String): String {
        val f = byId.getValue(id)
        return "$id [${f.kind.name}${if (f.attentionNote.isNotBlank()) ", attention" else ""}]"
    }

    @Test
    fun corpusCases() {
        val cases = MatchCorpus.targets.map { "target" to (it.label to it.sighting) } +
            MatchCorpus.nearMisses.map { "near-miss" to (it.label to it.sighting) } +
            MatchCorpus.randomAddressOuiHits.map { "random-oui" to (it.label to it.sighting) }
        val hits = SignatureEngine().match(cases.map { it.second.second }, fleets, now = Golden.NOW)
        val lines = cases.map { (group, case) ->
            val (label, s) = case
            val found = hits.getValue(s.key).sorted().joinToString("; ") { describe(it) }.ifEmpty { "(none)" }
            "${s.key}\t$group\t$label\n  -> $found"
        }
        Golden.assertText("match/corpus.txt", lines.joinToString("\n", postfix = "\n"))
    }

    @Test
    fun generatedPositivesThatHitOtherSignatures() {
        val positives = MatchCorpus.positives(fleets)
        val hits = SignatureEngine().match(positives.map { it.sighting }, fleets, now = Golden.NOW)
        val lines = positives.mapNotNull { p ->
            val others = (hits.getValue(p.sighting.key) - p.fleetId).sorted()
            if (others.isEmpty()) null else "${p.id} -> ${others.joinToString(", ")}"
        }
        val missed = positives.count { p -> p.fleetId !in hits.getValue(p.sighting.key) }
        val header = "${positives.size} generated positives, $missed miss their own signature, ${lines.size} also hit others"
        Golden.assertText("match/positive-collisions.txt", (listOf(header) + lines).joinToString("\n", postfix = "\n"))
    }
}
