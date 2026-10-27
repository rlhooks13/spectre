package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCorpusTest {
    private val fleets = DefaultCatalog.fleets()
    private val engine = SignatureEngine()

    @Test
    fun everyRuleOfEverySignatureHitsItsSignature() {
        val positives = MatchCorpus.positives(fleets)
        val clustered = fleets.count(MatchCorpus::clustered)
        println("Match corpus: ${positives.size} positives from ${fleets.size - clustered} signatures; $clustered clustered skipped.")
        assertTrue(positives.size > 500)
        val hits = engine.match(positives.map { it.sighting }, fleets)
        val missed = positives
            .filter { it.fleetId !in hits.getValue(it.sighting.key) }
            .map { "${it.fleetId}#${it.ruleIndex}" }
            .filterNot { it in EXPECTED_MISSES }
        assertEquals("Rules that no longer hit their own signature", emptyList<String>(), missed)
        // An expected miss that starts hitting must come off the list.
        val stale = EXPECTED_MISSES.keys.filter { id ->
            positives.any { "${it.fleetId}#${it.ruleIndex}" == id && it.fleetId in hits.getValue(it.sighting.key) }
        }
        assertEquals(emptyList<String>(), stale)
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
    fun nearMissesNameRealSignaturesAndSteps() {
        val ids = fleets.map { it.id }.toSet()
        for (miss in MatchCorpus.nearMisses) {
            assertTrue(miss.label, ids.containsAll(miss.mustNotMatch))
            miss.knownUntil?.let { assertTrue(miss.label, Regex("^P\\d-[A-Z]\\d+$").matches(it)) }
        }
    }

    private companion object {
        /** "fleetId#ruleIndex" a post-filter removes by design, with the reason. Keep it short. */
        val EXPECTED_MISSES: Map<String, String> = mapOf()
    }
}
