package app.fieldwatch.domain

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Catalog lint (P1-A5). Every violation left in the built-in catalog is listed below with the PLAN step that
 * fixes it or ADR-0015, which accepts it. A new violation fails; so does an entry that no longer violates.
 * Entries are keyed by the rule's content, not its position, so editing other rules doesn't move them.
 */
class CatalogLintTest {
    private data class Row(val fleet: Fleet, val rule: MatchRule) {
        val nameKey: String get() = "${fleet.id}/${rule.text}/${rule.radio ?: "ANY"}"
        val companyKey: String get() = "${fleet.id}/0x%04X".format(rule.companyId)
    }

    private fun usesCompany(r: MatchRule) =
        (r.kind == RuleKind.MANUFACTURER_ID || r.kind == RuleKind.MANUFACTURER_DATA) && r.companyId != 0

    /** The literal a name rule needs, or null when it is anchored (a glob that doesn't start with `*`). */
    private fun containsLiteral(r: MatchRule): String? = when {
        r.kind == RuleKind.NAME_CONTAINS -> r.text.trim()
        r.kind == RuleKind.NAME_GLOB && r.text.startsWith("*") ->
            r.text.split('*', '?').maxByOrNull { it.length }.orEmpty()
        else -> null
    }

    private fun check(what: String, found: List<String>, allowed: Map<String, String>) {
        assertEquals("New $what", emptyList<String>(), found - allowed.keys)
        assertEquals("Fixed $what: remove the exception", emptyList<String>(), (allowed.keys - found.toSet()).toList())
    }

    @Test
    fun companyIdsAreAssigned() {
        val found = rows.filter { usesCompany(it.rule) && RadioDb.company(it.rule.companyId) == null }.map { it.companyKey }
        check("unassigned company ids", found, UNKNOWN_COMPANY)
    }

    @Test
    fun noBareMegaVendorCompanyIds() {
        // Bare: alone in an any-rule signature. In an all-rules signature other rules narrow it.
        val found = rows.filter {
            it.fleet.matchAny && it.rule.kind == RuleKind.MANUFACTURER_ID &&
                MEGA_VENDOR.containsMatchIn(RadioDb.company(it.rule.companyId).orEmpty())
        }.map { it.companyKey }
        check("bare mega-vendor company-id rules", found, BARE_MEGA_VENDOR)
    }

    @Test
    fun nameContainsHasAtLeastFiveCharacters() {
        val found = rows.filter { r -> containsLiteral(r.rule)?.let { it.length < 5 } == true }.map { it.nameKey }
        check("short name-contains rules", found, SHORT_NAME)
    }

    /**
     * Corpus collisions. Each hand-written corpus case has its full hit set pinned, and the generated
     * positives' hits on other signatures are counted, so a rule that starts catching more shows up here.
     * A 1B fix updates the pins in the same PR. A golden replaces this once P1-A2 lands.
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
    fun generatedPositivesHitNoNewSignatures() {
        val positives = MatchCorpus.positives(fleets)
        val hits = SignatureEngine().match(positives.map { it.sighting }, fleets)
        val crossHits = positives.sumOf { p -> (hits.getValue(p.sighting.key) - p.fleetId).size }
        assertEquals("Cross-signature hits from the generated positives", PINNED_CROSS_HITS, crossHits)
    }

    @Test
    fun exceptionsNameAnAdrOrAnOpenStep() {
        val plan = repoFile("../docs/plan/PLAN.md").readLines()
        val adr = repoFile("../docs/adr/0015-catalog-lint-exceptions.md").readText()
        val accepted = adr.substringAfter("**Accepted").substringBefore("**Assigned")
        for ((key, why) in UNKNOWN_COMPANY + BARE_MEGA_VENDOR + SHORT_NAME) {
            if (why == "ADR-0015") {
                assertTrue("$key must be in ADR-0015's Accepted list", "`$key`" in accepted)
                continue
            }
            val line = plan.firstOrNull { "**$why " in it || "**$why**" in it }
            assertNotEquals("$key: $why isn't a PLAN step", null, line)
            assertFalse("$key: $why is ticked but the exception is still here", line!!.contains("✅"))
        }
    }

    companion object {
        private val fleets = DefaultCatalog.fleets()
        private val rows = fleets.flatMap { f -> f.rules.filter { it.enabled }.map { Row(f, it) } }

        private val MEGA_VENDOR = Regex("(?i)\\b(apple|samsung|google|microsoft|dji)\\b")

        private val UNKNOWN_COMPANY = mapOf("fleet-huf/0x070A" to "ADR-0015")

        private val BARE_MEGA_VENDOR = mapOf(
            "fleet-smarttag/0x0075" to "P1-B1",
            "fleet-google/0x00E0" to "P1-B10",
            "fleet-microsoft/0x0006" to "P1-B10",
            "fleet-fitbit/0x018E" to "P1-B10",
            "fleet-dji/0x08AA" to "P1-B10",
        )

        private val SHORT_NAME: Map<String, String> = mapOf("fleet-tile/Tile/ANY" to "P1-B2") + listOf(
            "fleet-amazon/Echo/BLE", "fleet-apple-device/iPad/ANY", "fleet-arlo/Arlo/ANY", "fleet-seos/Seos/BLE",
            "fleet-seos/Yale/BLE", "fleet-asus/ASUS/WIFI", "fleet-audi-mmi/Audi/BLE", "fleet-bmw/BMW/BLE",
            "fleet-bose/Bose/BLE", "fleet-byd/BYD/BLE", "fleet-dji/DJI/BLE", "fleet-dji/DJI/WIFI",
            "fleet-eero/eero/WIFI", "fleet-eufy/eufy/ANY", "fleet-fobo/FOBO/BLE", "fleet-ford/Ford/BLE",
            "fleet-hak5-pineapple/Hak5/ANY", "fleet-hobby-ble/BT05/BLE", "fleet-hp/ENVY/BLE", "fleet-jbl/JBL/BLE",
            "fleet-kevo/Kevo/BLE", "fleet-logitech/Logi/BLE", "fleet-mbux/MBUX/WIFI", "fleet-microsoft/Xbox/BLE",
            "fleet-myq/MyQ/BLE", "fleet-nuki/Nuki/BLE", "fleet-oura/Oura/BLE", "fleet-paxton/Net2/BLE",
            "fleet-sony/Sony/BLE", "fleet-tapo/Tapo/ANY", "fleet-tuya/TUYA/BLE", "fleet-unifi-ap/UBNT/WIFI",
            "fleet-volkswagen/VW/BLE", "fleet-wyze/Wyze/ANY",
        ).associateWith { "P1-B5" }

        /** Today's count; a fix that removes overlaps lowers it, a new over-broad rule raises it. */
        private const val PINNED_CROSS_HITS = 13

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
            RadioDb.initFrom(repoFile("src/main/assets/lookups/radiodb.bin").readBytes())
        }

        /** RadioDb is a process-wide object; leave it unloaded for the test classes that run next. */
        @JvmStatic
        @AfterClass
        fun unloadRadioDb() {
            RadioDb.resetForTest()
        }
    }
}
