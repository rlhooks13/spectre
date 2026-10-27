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
 * P1-B5, catalog v103: the last 15 short "contains" names are retired. Fourteen become the brand as a whole word at
 * the start (`bleWordStart`), and UniFi AP keeps its anchored `UBNT*`. The retired rules stay gone on every path.
 */
class CatalogV103LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val retired = RetiredRules.ALL.filter { it.retiredIn == 103 }

    private fun List<Fleet>.holdsARetiredRule(): Boolean = any { fleet -> fleet.rules.any { RetiredRules.isRetired(fleet.id, it) } }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    /** Each retired name was a v102 rule, and its row has the brand as an anchored glob on the same radio. */
    @Test
    fun eachRetiredNameWasInV102AndKeepsAnAnchoredGlobOnItsRadio() {
        assertEquals(15, retired.size)
        val v102 = snapshot(102).associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (entry in retired) {
            val label = "${entry.fleetId} ${entry.rule.text}"
            assertTrue("$label in v102", v102.getValue(entry.fleetId).rules.any { RetiredRules.isRetired(entry.fleetId, it) })
            val brand = entry.rule.text.lowercase()
            assertTrue(
                "$label keeps an anchored glob on its radio",
                stock.getValue(entry.fleetId).rules.any {
                    it.kind == RuleKind.NAME_GLOB && it.text.lowercase().startsWith(brand) && (it.radio == null || it.radio == entry.rule.radio)
                },
            )
        }
        assertFalse(DefaultCatalog.fleets().holdsARetiredRule())
    }

    @Test
    fun aFieldwatchConfigGetsTheWordStartGlobsAndEqualsStock() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        assertFalse(cfg.fleets.holdsARetiredRule())
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (id in retired.map { it.fleetId }.toSet()) {
            val upgraded = cfg.fleets.single { it.id == id }
            if (!upgraded.builtIn) continue
            // In order: the lint's collision keys use rule indices.
            assertEquals(id, stock.getValue(id).rules.map { SignatureExchange.ruleKey(it) }, upgraded.rules.map { SignatureExchange.ruleKey(it) })
        }
    }

    /** A user who switched a retired name off gets its word-start globs off too; other rows' stay on. */
    @Test
    fun anOffRetiredNameTurnsItsReplacementsOff() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 102,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-ford") return@map fleet
                    val stale = retired.single { it.fleetId == "fleet-ford" }.rule.copy(enabled = false)
                    fleet.copy(rules = fleet.rules.filterNot { RetiredRules.isRetired(fleet.id, it) } + stale)
                },
            )
        }
        val loaded = ConfigUpgrade.load(old).fleets
        val ford = loaded.single { it.id == "fleet-ford" }.rules.filter { it.kind == RuleKind.NAME_GLOB }
        assertEquals(4, ford.size)
        assertTrue(ford.none { it.enabled })
        assertTrue(loaded.single { it.id == "fleet-jbl" }.rules.filter { it.kind == RuleKind.NAME_GLOB }.all { it.enabled })
    }

    /** Only a rule v103 retired carries its "off": Seos's "Yale" (retired in v102) switched off leaves the Seos globs on. */
    @Test
    fun anOlderRetiredRuleOffDoesNotTurnTheV103GlobsOff() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 101,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-seos") return@map fleet
                    val yale = RetiredRules.ALL.single { it.fleetId == "fleet-seos" && it.retiredIn == 102 }.rule.copy(enabled = false)
                    fleet.copy(rules = fleet.rules.filterNot { RetiredRules.isRetired(fleet.id, it) } + yale)
                },
            )
        }
        val seos = ConfigUpgrade.load(old).fleets.single { it.id == "fleet-seos" }.rules
        assertTrue(seos.filter { it.kind == RuleKind.NAME_GLOB && it.text.startsWith("Seos") }.all { it.enabled })
    }

    /** The carry is by kind, text and radio: BMW's Wi-Fi "BMW_*" hotspot rule isn't the BLE "BMW_*" v103 added. */
    @Test
    fun anOffBleNameLeavesTheWifiGlobOfTheSameTextOn() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 102,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-bmw") return@map fleet
                    val stale = retired.single { it.fleetId == "fleet-bmw" }.rule.copy(enabled = false)
                    fleet.copy(rules = fleet.rules.filterNot { RetiredRules.isRetired(fleet.id, it) } + stale)
                },
            )
        }
        val bmw = ConfigUpgrade.load(old).fleets.single { it.id == "fleet-bmw" }.rules
        assertTrue(bmw.single { it.text == "BMW_*" && it.radio == RadioKind.WIFI }.enabled)
        assertFalse(bmw.single { it.text == "BMW_*" && it.radio == RadioKind.BLE }.enabled)
    }

    @Test
    fun overlaysAndImportsDontRestoreTheNames() {
        val v102 = snapshot(102)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v102)
        assertFalse(overlaid.holdsARetiredRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), v102.filter { f -> retired.any { it.fleetId == f.id } })
        assertFalse(merged.holdsARetiredRule())
    }
}
