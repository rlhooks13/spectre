package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCorpusTest {
    private val fleets = DefaultCatalog.fleets()
    private val byId = fleets.associateBy { it.id }
    private val engine = SignatureEngine()

    private fun id(p: MatchCorpus.Positive) = "${p.fleetId}#${p.ruleIndex}"

    @Test
    fun everyRuleHitsOnItsOwn() {
        // Against a copy of the signature with only that rule, so a sibling rule can't hide a broken one.
        val missed = MatchCorpus.positives(fleets).filter { p ->
            val fleet = byId.getValue(p.fleetId)
            val alone = if (p.ruleIndex < 0) fleet else fleet.copy(rules = listOf(fleet.rules[p.ruleIndex]))
            p.fleetId !in SignatureEngine().match(listOf(p.sighting), listOf(alone)).getValue(p.sighting.key)
        }.map(::id)
        assertEquals("Rules that don't hit their own data", emptyList<String>(), missed)
    }

    @Test
    fun everyRuleStillHitsItsSignatureInTheFullCatalog() {
        val positives = MatchCorpus.positives(fleets)
        val clustered = fleets.count(MatchCorpus::clustered)
        println("Match corpus: ${positives.size} positives from ${fleets.size - clustered} signatures.")
        // A clustered signature needs multi-sighting positives before it can join the corpus.
        assertEquals(0, clustered)
        assertTrue(positives.size > 500)
        val hits = engine.match(positives.map { it.sighting }, fleets)
        val missed = positives.filter { it.fleetId !in hits.getValue(it.sighting.key) }.map(::id)
        assertEquals("Rules a post-filter now removes", emptyList<String>(), missed - EXPECTED_MISSES.keys)
        // Every allowlisted miss must still exist and still miss.
        val ids = positives.map(::id).toSet()
        assertEquals(emptyList<String>(), EXPECTED_MISSES.keys.filter { it !in ids || it !in missed })
    }

    @Test
    fun nearMissesStayOffTheirConfusers() {
        val misses = MatchCorpus.nearMisses
        assertEquals(misses.size, misses.map { it.sighting.key }.toSet().size)
        val hits = engine.match(misses.map { it.sighting }, fleets)
        for (miss in misses) {
            val wrong = hits.getValue(miss.sighting.key) intersect miss.mustNotMatch
            if (miss.knownUntil == null) {
                assertEquals(miss.label, emptySet<String>(), wrong)
            } else {
                assertTrue(
                    "${miss.label}: fixed? Remove knownUntil = ${miss.knownUntil} in that step's PR.",
                    wrong.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun randomAddressOuiHitsStillMatchUntilTheClassifierLabelsThem() {
        val cases = MatchCorpus.randomAddressOuiHits
        val hits = engine.match(cases.map { it.sighting }, fleets)
        for (case in cases) {
            assertTrue(case.label, hits.getValue(case.sighting.key).containsAll(case.mustNotMatch))
            // Top two bits 00 or 01: a real random address, as the fixture claims.
            val top = case.sighting.mac.take(2).toInt(16) shr 6
            assertTrue(case.label, top == 0 || top == 1)
        }
    }

    @Test
    fun nearMissesNameRealSignaturesAndSteps() {
        val ids = fleets.map { it.id }.toSet()
        for (miss in MatchCorpus.nearMisses + MatchCorpus.randomAddressOuiHits) {
            assertTrue(miss.label, ids.containsAll(miss.mustNotMatch))
            miss.knownUntil?.let { assertTrue(miss.label, Regex("^P\\d-[A-Z]\\d+$").matches(it)) }
        }
    }

    private companion object {
        /** "fleetId#ruleIndex" a post-filter removes by design, with the reason. Empty today. */
        val EXPECTED_MISSES: Map<String, String> = mapOf()
    }
}
