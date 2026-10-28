package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MatchCorpusTest {
    private val fleets = DefaultCatalog.fleets()
    private val byId = fleets.associateBy { it.id }
    private val names = fleets.associate { it.id to it.name }
    private val classes = TrackerMatch.classesOf(fleets)
    private val engine = SignatureEngine()

    /** Against a copy of the signature with only that rule, so a sibling rule can't hide a broken one. */
    private fun hitsAlone(p: MatchCorpus.Positive): Boolean {
        val fleet = byId.getValue(p.fleetId)
        val alone = if (fleet.matchAny) fleet.copy(rules = listOf(fleet.rules[p.ruleIndex])) else fleet
        return p.fleetId in SignatureEngine().match(listOf(p.sighting), listOf(alone)).getValue(p.sighting.key)
    }

    @Test
    fun everyRuleHitsOnItsOwn() {
        val positives = MatchCorpus.positives(fleets)
        assertEquals(MatchCorpus.expectedPositiveCount(fleets), positives.size)
        assertEquals("Rules that don't hit their own data", emptyList<String>(), positives.filterNot(::hitsAlone).map { it.id })
    }

    @Test
    fun anAllRulesSignatureNeedsEveryRule() {
        val negatives = MatchCorpus.allRulesNegatives(fleets)
        assertTrue(negatives.isNotEmpty())
        val hit = negatives.filter { n ->
            n.fleetId in SignatureEngine().match(listOf(n.sighting), listOf(byId.getValue(n.fleetId))).getValue(n.sighting.key)
        }.map { it.id }
        assertEquals("All-rules signatures that hit with a rule missing", emptyList<String>(), hit)
    }

    @Test
    fun everyRuleStillHitsItsSignatureInTheFullCatalog() {
        val positives = MatchCorpus.positives(fleets)
        println("Match corpus: ${positives.size} positives from ${fleets.size} signatures.")
        // A clustered signature needs multi-sighting positives before it can join the corpus.
        assertEquals(0, fleets.count(MatchCorpus::clustered))
        val hits = engine.match(positives.map { it.sighting }, fleets)
        val missed = positives.filter { it.fleetId !in hits.getValue(it.sighting.key) }.map { it.id }
        assertEquals("Rules a post-filter now removes", emptyList<String>(), missed - EXPECTED_MISSES.keys)
        // Every allowlisted miss must still exist and still miss.
        val ids = positives.map { it.id }.toSet()
        assertEquals(emptyList<String>(), EXPECTED_MISSES.keys.filter { it !in ids || it !in missed })
    }

    @Test
    fun realTargetsKeepHitting() {
        val targets = MatchCorpus.targets
        val hits = engine.match(targets.map { it.sighting }, fleets)
        for (t in targets) {
            val hit = t.fleetId in hits.getValue(t.sighting.key)
            if (t.knownUntil == null) {
                assertTrue(t.label, hit)
            } else {
                assertFalse("${t.label}: added? Remove knownUntil = ${t.knownUntil} in that step's PR.", hit)
            }
        }
    }

    @Test
    fun nearMissesStayOffTheirConfusers() {
        val misses = MatchCorpus.nearMisses
        assertEquals(misses.size, misses.map { it.sighting.key }.toSet().size)
        val hits = engine.match(misses.map { it.sighting }, fleets)
        for (miss in misses) {
            val got = hits.getValue(miss.sighting.key)
            val wrong = got intersect miss.mustNotMatch
            val finder = miss.checkFinder &&
                TrackerMatch.kind(miss.sighting.copy(fleetIds = got), names, classes) == TrackerMatch.Kind.FINDER
            if (miss.knownUntil == null) {
                assertEquals(miss.label, emptySet<String>(), wrong)
                if (miss.finderKnownUntil == null) {
                    assertFalse("${miss.label} is labeled a tracker", finder)
                } else {
                    assertTrue("${miss.label} (tracker label): fixed? Remove finderKnownUntil = ${miss.finderKnownUntil} in that step's PR.", finder)
                }
            } else {
                val fix = "fixed? Remove knownUntil = ${miss.knownUntil} in that step's PR."
                assertTrue("${miss.label}: $fix", wrong.isNotEmpty())
                if (miss.checkFinder) assertTrue("${miss.label} (tracker label): $fix", finder)
            }
        }
    }

    /**
     * Matching still hits these by design. With the classifier on (P1-B8b), each reads as "possible": no attention
     * card, no alert. The `knownUntil = "P1-B8"` marker stays until the flag turns on.
     */
    @Test
    fun randomAddressOuiHitsStillMatchUntilTheClassifierLabelsThem() {
        val cases = MatchCorpus.randomAddressOuiHits
        val hits = engine.match(cases.map { it.sighting }, fleets)
        for (case in cases) {
            assertTrue(case.label, hits.getValue(case.sighting.key).containsAll(case.mustNotMatch))
            val matched = case.sighting.copy(fleetIds = hits.getValue(case.sighting.key))
            val read = BleAddress.reading(matched, byId, engine, enabled = true)
            assertTrue("${case.label} reads as possible", read.possibleFleetIds.containsAll(case.mustNotMatch))
            assertTrue("${case.label} has no attention card", read.attentionNotes(fleets).isEmpty())
            // Top two bits 00 or 01: a real random address, as the fixture claims.
            val top = case.sighting.mac.take(2).toInt(16) shr 6
            assertTrue(case.label, top == 0 || top == 1)
        }
    }

    @Test
    fun casesNameRealSignaturesAndOpenSteps() {
        val ids = fleets.map { it.id }.toSet()
        val plan = repoFile("../docs/plan/PLAN.md").readLines()
        val steps = (MatchCorpus.nearMisses + MatchCorpus.randomAddressOuiHits).flatMap {
            listOf(it.label to it.knownUntil, it.label to it.finderKnownUntil)
        } + MatchCorpus.targets.map { it.label to it.knownUntil }
        for (miss in MatchCorpus.nearMisses + MatchCorpus.randomAddressOuiHits) {
            assertTrue(miss.label, ids.containsAll(miss.mustNotMatch))
            if (miss.finderKnownUntil != null) {
                // The finder marker is for a case whose signature is already right and only its label is wrong.
                assertTrue("${miss.label}: finderKnownUntil needs checkFinder", miss.checkFinder)
                assertEquals("${miss.label}: set knownUntil or finderKnownUntil, not both", null, miss.knownUntil)
            }
        }
        for (t in MatchCorpus.targets) assertTrue(t.label, t.fleetId in ids)
        for ((label, step) in steps) {
            if (step == null) continue
            val line = plan.firstOrNull { "**$step " in it || "**$step**" in it }
            assertNotEquals("$label: $step isn't a PLAN step", null, line)
            assertFalse("$label: $step is ticked but its marker is still here", line!!.contains("✅"))
        }
    }

    @Test
    fun theTestUuidIsInNoCatalogRow() {
        val uuid = MatchCorpus.TEST_UUID.uppercase()
        assertFalse(fleets.flatMap { it.rules }.any { it.text.uppercase() == uuid })
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }

    private companion object {
        /** "fleetId#ruleIndex" a post-filter removes by design, with the reason. Empty today. */
        val EXPECTED_MISSES: Map<String, String> = mapOf()
    }
}
