package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RetiredRules
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.testing.ConfigUpgrade
import app.fieldwatch.testing.TestFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B5a, catalog v101: the bare names "RAVEN" and "Vigilant" and the Wi-Fi glob "IBR*" are retired. RAVEN and
 * Vigilant get the exact name and separator-ended globs (Raven also its Wi-Fi Direct shape), and Cradlepoint gets the
 * two models only "IBR*" covered. The retired rules stay gone on every path, and an "off" carries to the replacements.
 */
class CatalogV101LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val retired = RetiredRules.ALL.filter { it.retiredIn == 101 }

    private fun List<Fleet>.holdsARetiredRule(): Boolean = any { fleet -> fleet.rules.any { RetiredRules.isRetired(fleet.id, it) } }

    private fun List<Fleet>.rulesOf(id: String) = single { it.id == id }.rules

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    @Test
    fun theRetiredEntriesAreTheV100Rules() {
        assertEquals(setOf("fleet-raven", "fleet-vigilant", "fleet-cradlepoint"), retired.map { it.fleetId }.toSet())
        val v100 = snapshot(100)
        for (entry in retired) {
            assertTrue("${entry.fleetId} ${entry.rule.text} in v100", v100.rulesOf(entry.fleetId).any { RetiredRules.isRetired(entry.fleetId, it) })
        }
        assertFalse(DefaultCatalog.fleets().holdsARetiredRule())
    }

    @Test
    fun aFieldwatchConfigLosesTheRulesAndGetsTheReplacements() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        assertFalse(cfg.fleets.holdsARetiredRule())
        val raven = cfg.fleets.rulesOf("fleet-raven")
        assertTrue(raven.any { it.kind == RuleKind.NAME_GLOB && it.text == "DIRECT-??-Raven-*" && it.radio == RadioKind.WIFI })
        assertTrue(raven.any { it.text == "RAVEN-*" && it.enabled })
        assertTrue(cfg.fleets.rulesOf("fleet-cradlepoint").any { it.text == "IBR1150*" })
        // Upgraded rows equal stock: the replacements sit where patchBuiltIn appends them.
        for (id in listOf("fleet-raven", "fleet-vigilant", "fleet-cradlepoint")) {
            assertEquals(id, DefaultCatalog.fleets().rulesOf(id).map { SignatureExchange.ruleKey(it) }, cfg.fleets.rulesOf(id).map { SignatureExchange.ruleKey(it) })
        }
    }

    /** A user who switched the old name rule off gets its replacements off; other rows' replacements stay on. */
    @Test
    fun anOffRetiredRuleTurnsItsReplacementsOff() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 100,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-raven") return@map fleet
                    val stale = retired.single { it.fleetId == "fleet-raven" }.rule.copy(enabled = false)
                    fleet.copy(rules = fleet.rules.filterNot { RetiredRules.isRetired(fleet.id, it) } + stale)
                },
            )
        }
        val loaded = ConfigUpgrade.load(old).fleets
        val names = loaded.rulesOf("fleet-raven").filter { it.kind == RuleKind.NAME_GLOB }
        assertEquals(4, names.size)
        assertTrue(names.none { it.enabled })
        assertTrue("UUIDs stay on", loaded.rulesOf("fleet-raven").any { it.kind == RuleKind.SERVICE_UUID && it.enabled })
        assertTrue(loaded.rulesOf("fleet-vigilant").filter { it.kind == RuleKind.NAME_GLOB }.all { it.enabled })
    }

    /** A config at v101 still holding the old rules, one switched off, loses them on load. */
    @Test
    fun aV101ConfigThatStillHasTheRulesLosesThemOnLoad() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 101,
                fleets = c.fleets.map { fleet ->
                    val back = retired.filter { it.fleetId == fleet.id }.map { it.rule.copy(enabled = fleet.id != "fleet-vigilant") }
                    if (back.isEmpty()) fleet else fleet.copy(rules = fleet.rules + back)
                },
            )
        }
        assertFalse(ConfigUpgrade.load(old).fleets.holdsARetiredRule())
    }

    @Test
    fun overlaysAndImportsDontRestoreTheOldRules() {
        val v100 = snapshot(100)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v100)
        assertFalse(overlaid.holdsARetiredRule())
        val (onOld, _) = SignatureExchange.overlayStock(v100.map { it.copy(builtIn = true) }, snapshot(101))
        assertFalse(onOld.holdsARetiredRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), v100.filter { f -> retired.any { it.fleetId == f.id } })
        assertFalse(merged.holdsARetiredRule())
    }
}
