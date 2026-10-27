package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Catalog lint (P1-A5). Every violation left in the built-in catalog is listed below with the PLAN step that
 * fixes it or ADR-0015, which accepts it. A new violation fails; so does an entry that no longer violates.
 */
class CatalogLintTest {
    private val fleets = DefaultCatalog.fleets()

    private data class Hit(val fleet: Fleet, val index: Int, val rule: MatchRule) {
        val id: String get() = "${fleet.id}#$index"
    }

    private val rules = fleets.flatMap { f ->
        f.rules.withIndex().filter { it.value.enabled }.map { Hit(f, it.index, it.value) }
    }

    private fun usesCompany(r: MatchRule) =
        (r.kind == RuleKind.MANUFACTURER_ID || r.kind == RuleKind.MANUFACTURER_DATA) && r.companyId != 0

    private fun check(what: String, found: List<String>, allowed: Map<String, String>) {
        assertEquals("New $what", emptyList<String>(), found - allowed.keys)
        assertEquals("Fixed $what: remove the exception", emptyList<String>(), (allowed.keys - found.toSet()).toList())
    }

    @Test
    fun companyIdsAreAssigned() {
        val found = rules.filter { usesCompany(it.rule) && RadioDb.company(it.rule.companyId) == null }.map { it.id }
        check("unassigned company ids", found, UNKNOWN_COMPANY)
    }

    @Test
    fun noBareMegaVendorCompanyIds() {
        val found = rules.filter {
            it.rule.kind == RuleKind.MANUFACTURER_ID && MEGA_VENDOR.containsMatchIn(RadioDb.company(it.rule.companyId).orEmpty())
        }.map { it.id }
        check("bare mega-vendor company-id rules", found, BARE_MEGA_VENDOR)
    }

    @Test
    fun nameContainsHasAtLeastFiveCharacters() {
        val found = rules.filter { it.rule.kind == RuleKind.NAME_CONTAINS && it.rule.text.trim().length < 5 }.map { it.id }
        check("short name-contains rules", found, SHORT_NAME)
    }

    /**
     * Corpus collisions: the full set of signatures each hand-written corpus case hits is pinned, so a rule
     * that starts catching one of them shows up here. A 1B fix updates its cases in the same PR.
     */
    @Test
    fun corpusCasesHitExactlyThePinnedSignatures() {
        val cases = MatchCorpus.targets.map { it.sighting } + MatchCorpus.nearMisses.map { it.sighting } +
            MatchCorpus.randomAddressOuiHits.map { it.sighting }
        val hits = SignatureEngine().match(cases, fleets)
        assertEquals(PINNED_HITS.keys, cases.map { it.key }.toSet())
        for (s in cases) assertEquals(s.key, PINNED_HITS.getValue(s.key), hits.getValue(s.key))
    }

    @Test
    fun exceptionsNameAnAdrOrAnOpenStep() {
        val plan = repoFile("../docs/plan/PLAN.md").readLines()
        val adr = repoFile("../docs/adr/0015-catalog-lint-exceptions.md")
        for ((id, why) in UNKNOWN_COMPANY + BARE_MEGA_VENDOR + SHORT_NAME) {
            if (why == "ADR-0015") {
                assertFalse(id, !adr.exists() || id !in adr.readText())
                continue
            }
            val line = plan.firstOrNull { "**$why " in it || "**$why**" in it }
            assertNotEquals("$id: $why isn't a PLAN step", null, line)
            assertFalse("$id: $why is ticked but the exception is still here", line!!.contains("✅"))
        }
    }

    companion object {
        private val MEGA_VENDOR = Regex("(?i)\\b(apple|samsung|google|microsoft|dji)\\b")

        private val UNKNOWN_COMPANY = mapOf("fleet-huf#0" to "ADR-0015")

        private val BARE_MEGA_VENDOR = mapOf(
            "fleet-smarttag#4" to "P1-B1",
            "fleet-google#0" to "ADR-0015",
            "fleet-microsoft#0" to "ADR-0015",
            "fleet-fitbit#0" to "P1-B10",
            "fleet-dji#0" to "P1-B10",
        )

        private val SHORT_NAME: Map<String, String> = mapOf("fleet-tile#0" to "P1-B2") + listOf(
            "fleet-amazon#1", "fleet-apple-device#10", "fleet-arlo#0", "fleet-seos#5", "fleet-seos#6", "fleet-asus#2",
            "fleet-audi-mmi#1", "fleet-bmw#1", "fleet-bose#3", "fleet-byd#1", "fleet-dji#1", "fleet-dji#3",
            "fleet-eero#0", "fleet-eufy#0", "fleet-fobo#2", "fleet-ford#1", "fleet-hak5-pineapple#3",
            "fleet-hobby-ble#13", "fleet-hp#2", "fleet-jbl#1", "fleet-kevo#3", "fleet-logitech#3", "fleet-mbux#2",
            "fleet-microsoft#2", "fleet-myq#2", "fleet-nuki#5", "fleet-oura#1", "fleet-paxton#3", "fleet-sony#1",
            "fleet-tapo#0", "fleet-tuya#2", "fleet-unifi-ap#2", "fleet-volkswagen#4", "fleet-wyze#1",
        ).associateWith { "P1-B5" }

        private val PINNED_HITS: Map<String, Set<String>> = mapOf(
            "T:airtag" to setOf("fleet-airtag"),
            "T:smarttag" to setOf("fleet-smarttag"),
            "T:tile-feed" to setOf("fleet-tile"),
            "T:tile-067c" to emptySet(),
            "T:axon" to setOf("fleet-axon"),
            "T:flock" to setOf("fleet-flock-cameras"),
            "T:tattile" to setOf("fleet-tattile", "fleet-tile"),
            "T:raven" to setOf("fleet-raven"),
            "T:vigilant" to setOf("fleet-vigilant"),
            "T:cradlepoint" to setOf("fleet-cradlepoint"),
            "NM:galaxy" to setOf("fleet-smarttag"),
            "NM:buds" to setOf("fleet-smarttag"),
            "NM:iphone-of" to setOf("fleet-airtag"),
            "NM:airpods-of" to setOf("fleet-airtag"),
            "NM:iphone" to setOf("fleet-apple-device"),
            "NM:tattile" to setOf("fleet-tattile", "fleet-tile"),
            "NM:quuppa" to setOf("fleet-tile"),
            "NM:raven" to setOf("fleet-raven"),
            "NM:vigilant" to setOf("fleet-vigilant"),
            "NM:ibr" to setOf("fleet-apple-device", "fleet-cradlepoint"),
            "RND:Axon" to setOf("fleet-axon"),
            "RND:LiteOn" to setOf("fleet-liteon-camera-radio"),
            "RND:Arlo" to setOf("fleet-arlo"),
        )

        private fun repoFile(path: String): File {
            val candidates = listOf(File(path), File("app/$path"))
            return candidates.firstOrNull { it.exists() } ?: candidates.last()
        }

        @JvmStatic
        @BeforeClass
        fun loadRadioDb() {
            RadioDb.parse(repoFile("src/main/assets/lookups/radiodb.bin").readBytes())
        }
    }
}
