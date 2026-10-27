package app.fieldwatch.domain

import app.fieldwatch.testing.Golden
import app.fieldwatch.testing.GoldenRule
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: signature matching goldens over the match corpus and the stock catalog. They replace the hit sets
 * CatalogLintTest pinned by hand (P1-A5). Each hand-written case shows what it must (or must not) hit, the step
 * that fixes it if it's wrong today, and its full hit set with each signature's class and attention flag, so a
 * 1B fix shows the hit and its marker change in one diff.
 *
 * Generated positives that also hit other signatures stay in CatalogLintTest's allowlist, which ties each one
 * to a step or ADR.
 */
class MatchGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val fleets = DefaultCatalog.fleets()
    private val byId = fleets.associateBy { it.id }

    private class Case(val group: String, val label: String, val sighting: Sighting, val expect: String)

    private val cases: List<Case> =
        MatchCorpus.targets.map {
            Case("target", it.label, it.sighting, "expect ${it.fleetId}" + (it.knownUntil?.let { s -> ", missed until $s" } ?: ""))
        } + MatchCorpus.nearMisses.map {
            Case(
                "near-miss", it.label, it.sighting,
                "must not ${it.mustNotMatch.sorted().joinToString(", ")}" +
                    (if (it.checkFinder) " (nor be a finder)" else "") + (it.knownUntil?.let { s -> ", wrong until $s" } ?: "") +
                    (it.finderKnownUntil?.let { s -> ", still labeled a tracker until $s" } ?: ""),
            )
        } + MatchCorpus.randomAddressOuiHits.map {
            Case("random-oui", it.label, it.sighting, "hits by design until P1-B8 classifies the address")
        }

    private fun describe(ids: Set<String>): String = ids.sorted().joinToString("; ") { id ->
        val f = byId.getValue(id)
        "$id [${f.kind.name}${if (f.attentionNote.isNotBlank()) ", attention" else ""}]"
    }.ifEmpty { "(none)" }

    private fun render(hits: (Case) -> Set<String>): String = cases.joinToString("\n", postfix = "\n") { c ->
        "${c.sighting.key}\t${c.group}\t${c.label}\n  ${c.expect}\n  -> ${describe(hits(c))}"
    }

    /** Each case alone, heard long before `now`: the per-device rules. */
    @Test
    fun corpusCases() {
        val hits = SignatureEngine().match(cases.map { it.sighting }, fleets, now = Golden.NOW)
        Golden.assertText("match/corpus.txt", render { hits.getValue(it.sighting.key) })
    }

    /**
     * Each case as a live group of three, heard at `now` on consecutive addresses, so cluster signatures
     * (min peers, OUI clusters, sequential MACs) can fire too. Each group is matched on its own.
     */
    @Test
    fun corpusCasesInALiveGroup() {
        val engine = SignatureEngine()
        val firstHits = cases.associate { c ->
            val group = (1..3).map { n ->
                val mac = c.sighting.mac.substring(0, 15) + "%02X".format(n)
                c.sighting.copy(key = "${c.sighting.key}#$n", mac = mac, lastSeen = Golden.NOW, firstSeen = Golden.NOW - 30_000L)
            }
            c.sighting.key to engine.match(group, fleets, now = Golden.NOW).getValue(group.first().key)
        }
        Golden.assertText("match/corpus-live-group.txt", render { firstHits.getValue(it.sighting.key) })
    }
}
