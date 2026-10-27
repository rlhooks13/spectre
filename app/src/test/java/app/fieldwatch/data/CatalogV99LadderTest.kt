package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RadioKind
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
 * P1-B2, catalog v99: Tile matches on its own company id (0x067C) and a BLE-only exact name "Tile". It no longer
 * matches on Quuppa's 0x00C7, on "tile" anywhere in a name ("Tattile"), or on FEDD (no capture confirms it). The
 * retired rules stay gone on every path, as SmartTag's do (CatalogV98LadderTest).
 */
class CatalogV99LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private fun Fleet.hasRetiredTileRule(): Boolean = rules.any {
        (it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x00C7) ||
            (it.kind == RuleKind.NAME_CONTAINS && it.text.equals("Tile", ignoreCase = true)) ||
            it.text.uppercase().let { t -> t == "FEDD" || t.startsWith("0000FEDD-") }
    }

    private fun tile(fleets: List<Fleet>) = fleets.single { it.id == "fleet-tile" }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    @Test
    fun aFieldwatchConfigGetsTodaysTileRulesAndKeepsTileOff() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        val stock = tile(DefaultCatalog.fleets())
        assertEquals(stock.rules.map { SignatureExchange.ruleKey(it) }.toSet(), tile(cfg.fleets).rules.map { SignatureExchange.ruleKey(it) }.toSet())
        assertFalse("the 1.1.32 user turned Tile off; that stays", tile(cfg.fleets).enabled)
    }

    @Test
    fun theStockTileRowIsTheNewOne() {
        val rules = tile(DefaultCatalog.fleets()).rules
        assertTrue(rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x067C })
        assertTrue(rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Tile" && it.radio == RadioKind.BLE })
        assertFalse(tile(DefaultCatalog.fleets()).hasRetiredTileRule())
    }

    /** Each retired entry is exactly a rule the v98 row had, so stripping it really removes something. */
    @Test
    fun theRetiredEntriesAreTheV98Rules() {
        val v98 = tile(snapshot(98))
        val retired = v98.rules.filter { app.fieldwatch.domain.RetiredRules.isRetired("fleet-tile", it) }
        assertEquals(3, retired.size)
        assertFalse(app.fieldwatch.domain.RetiredRules.strip(v98).hasRetiredTileRule())
    }

    /** A config at v99 still holding the old rules (a long-form FEDD, a switched-off 0x00C7) loses them on load. */
    @Test
    fun aV99ConfigThatStillHasTheRulesLosesThemOnLoad() {
        val stale = listOf(
            MatchRule(RuleKind.SERVICE_UUID, text = "0000FEDD-0000-1000-8000-00805F9B34FB"),
            MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x00C7, enabled = false),
            MatchRule(RuleKind.NAME_CONTAINS, text = "Tile"),
        )
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(version = 99, fleets = c.fleets.map { if (it.id == "fleet-tile") it.copy(rules = it.rules + stale) else it })
        }
        assertFalse(tile(ConfigUpgrade.load(old).fleets).hasRetiredTileRule())
    }

    @Test
    fun overlaysAndImportsDontRestoreTheOldRules() {
        val v98 = snapshot(98)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v98)
        assertFalse(tile(overlaid).hasRetiredTileRule())
        val (onOld, _) = SignatureExchange.overlayStock(v98.map { it.copy(builtIn = true) }, snapshot(99))
        assertFalse(tile(onOld).hasRetiredTileRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), listOf(tile(v98)))
        assertFalse(tile(merged).hasRetiredTileRule())
    }
}
