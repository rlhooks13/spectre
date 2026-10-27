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
        // A MANUFACTURER_ID left at 0 matches Ericsson; ids are 16-bit. MANUFACTURER_DATA uses 0 as "any company".
        val malformed = rows.filter {
            (it.rule.kind == RuleKind.MANUFACTURER_ID && it.rule.companyId == 0) || it.rule.companyId !in 0..0xFFFF
        }.map { it.companyKey }
        assertEquals("Malformed company ids", emptyList<String>(), malformed)
        val found = rows.filter { usesCompany(it.rule) && RadioDb.company(it.rule.companyId) == null }.map { it.companyKey }
        check("unassigned company ids", found, UNKNOWN_COMPANY)
    }

    @Test
    fun megaVendorIdsAreWhoWeThink() {
        for ((id, owner) in MEGA_VENDOR) assertTrue("0x%04X".format(id), RadioDb.company(id).orEmpty().contains(owner, ignoreCase = true))
    }

    @Test
    fun noBareMegaVendorCompanyIds() {
        // Bare: alone in an any-rule signature. In an all-rules signature other rules narrow it.
        val found = rows.filter {
            it.fleet.matchAny && it.rule.kind == RuleKind.MANUFACTURER_ID && it.rule.companyId in MEGA_VENDOR
        }.map { it.companyKey }
        check("bare mega-vendor company-id rules", found, BARE_MEGA_VENDOR)
    }

    /**
     * P1-B6: no stock row holds a rule twice under the shared rule key. Name matching ignores case, so "Eufy*" and
     * "eufy*" are one rule; a saved config keeps one copy (the v104 ladder step), and stock must match it.
     */
    @Test
    fun noStockRowHoldsARuleTwice() {
        val twice = fleets.flatMap { f ->
            f.rules.groupBy { it.matchKey() }.filterValues { it.size > 1 }.values.map { "${f.id}: ${it.joinToString { r -> r.text }}" }
        }
        assertEquals("Stock rules held twice", emptyList<String>(), twice)
    }

    @Test
    fun nameContainsHasAtLeastFiveCharacters() {
        val found = rows.filter { r -> containsLiteral(r.rule)?.let { it.length < 5 } == true }.map { it.nameKey }
        check("short name-contains rules", found, SHORT_NAME)
    }

    /**
     * Corpus collisions. The generated positives' hits on other signatures must each name a step or ADR, so a
     * rule that starts catching more fails here. Each hand-written case's full hit set is a golden
     * (`MatchGoldenTest`, `golden/match/corpus.txt`); a 1B fix re-blesses it in the same PR.
     */
    @Test
    fun generatedPositivesHitNoNewSignatures() {
        val positives = MatchCorpus.positives(fleets)
        val hits = SignatureEngine().match(positives.map { it.sighting }, fleets, now = app.fieldwatch.testing.Golden.NOW)
        val found = positives.mapNotNull { p ->
            val others = (hits.getValue(p.sighting.key) - p.fleetId).sorted()
            if (others.isEmpty()) null else "${p.id} -> ${others.joinToString(",")}"
        }
        check("cross-signature hits from the generated positives", found, ALLOWED_COLLISIONS)
    }

    @Test
    fun exceptionsNameAnAdrOrAnOpenStep() {
        val plan = repoFile("../docs/plan/PLAN.md").readLines()
        val adr = repoFile("../docs/adr/0015-catalog-lint-exceptions.md").readText()
        val accepted = adr.substringAfter("**Accepted").substringBefore("**Assigned")
        for ((key, why) in UNKNOWN_COMPANY + BARE_MEGA_VENDOR + SHORT_NAME + ALLOWED_COLLISIONS) {
            if (why == "ADR-0015") {
                assertTrue("$key must be in ADR-0015's Accepted list", "`${key.substringBefore(" ->")}`" in accepted)
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

        /**
         * The mega-vendors' ids, pinned (a SIG rename can't drop one) and checked against the SIG name.
         * Subsidiary ids (Beats 0x00CC, Nest 0x01B5, Harman 0x0057) are a reviewer check, per ADR-0015.
         */
        private val MEGA_VENDOR = mapOf(
            0x004C to "Apple", 0x0075 to "Samsung", 0x00E0 to "Google", 0x018E to "Google",
            0x0006 to "Microsoft", 0x08AA to "DJI",
        )

        private val UNKNOWN_COMPANY = mapOf("fleet-huf/0x070A" to "P1-B10")

        private val BARE_MEGA_VENDOR = mapOf(
            "fleet-google/0x00E0" to "P1-B10",
            "fleet-microsoft/0x0006" to "P1-B10",
            "fleet-fitbit/0x018E" to "P1-B10",
            "fleet-dji/0x08AA" to "P1-B10",
        )

        /** Short "contains" names kept on purpose (P1-B5): Apple's default name puts "iPad" last ("Jane's iPad"). */
        private val SHORT_NAME: Map<String, String> = mapOf("fleet-apple-device/iPad/ANY" to "ADR-0015")

        /** Generated positive → the other signatures it hits, with ADR-0015 or the fixing step. */
        private val ALLOWED_COLLISIONS = mapOf(
            "fleet-target-atrius#0 -> fleet-ibeacon" to "ADR-0015",
            "fleet-honeywell-home#4 -> fleet-amazon" to "ADR-0015",
            "fleet-honeywell-home#5 -> fleet-amazon" to "ADR-0015",
            "fleet-marauder#3 -> fleet-hobby-ble" to "ADR-0015",
            "fleet-inseego#3 -> fleet-novatel" to "ADR-0015",
            "fleet-novatel#0 -> fleet-inseego" to "ADR-0015",
            "fleet-unifi-ap#0 -> fleet-unifi" to "ADR-0015",
            "fleet-unifi-ap#1 -> fleet-unifi" to "ADR-0015",
            "fleet-unifi-ap#2 -> fleet-unifi" to "ADR-0015",
            "fleet-unifi-ap#3 -> fleet-unifi" to "ADR-0015",
            "fleet-liteon-camera-radio#25 -> fleet-tplink" to "P1-B10",
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
