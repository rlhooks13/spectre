package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RetiredRules
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.testing.ConfigUpgrade
import app.fieldwatch.testing.TestFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B3, catalog v100: AirTags no longer match a bare Offline Finding record (0x004C prefix 12). A new "Find My
 * device" row takes it, and the engine routes it by the status byte's device type. The retired rule stays gone on
 * every path, as Tile's do (CatalogV99LadderTest).
 */
class CatalogV100LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private fun Fleet.hasOfflineFindingRule(): Boolean = rules.any {
        it.kind == RuleKind.MANUFACTURER_DATA && it.companyId == 0x004C && it.dataPrefixHex.equals("12", ignoreCase = true)
    }

    private fun airTags(fleets: List<Fleet>) = fleets.single { it.id == "fleet-airtag" }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    @Test
    fun theStockRowsAreTheNewOnes() {
        val stock = DefaultCatalog.fleets()
        assertFalse(airTags(stock).hasOfflineFindingRule())
        val findMy = stock.single { it.id == "fleet-findmy" }
        assertTrue(findMy.hasOfflineFindingRule())
        assertEquals(SignatureClass.FINDER, findMy.kind)
        assertTrue(findMy.enabled)
    }

    @Test
    fun aFieldwatchConfigGainsFindMyAndLosesTheAirTagRule() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        assertFalse(airTags(cfg.fleets).hasOfflineFindingRule())
        assertEquals(1, cfg.fleets.count { it.id == "fleet-findmy" })
    }

    /** Each retired entry is exactly a rule the v99 row had, so stripping it really removes something. */
    @Test
    fun theRetiredEntryIsTheV99Rule() {
        val v99 = airTags(snapshot(99))
        assertEquals(1, v99.rules.count { RetiredRules.isRetired("fleet-airtag", it) })
        assertFalse(RetiredRules.strip(v99).hasOfflineFindingRule())
    }

    @Test
    fun aV99ConfigWithAirTagsOnGetsFindMyOn() {
        val loaded = ConfigUpgrade.load(v99Config(airTagsEnabled = true))
        assertTrue(loaded.fleets.single { it.id == "fleet-findmy" }.enabled)
        assertFalse(airTags(loaded.fleets).hasOfflineFindingRule())
    }

    /** AirTags' rule labeled these records until v100, so turning AirTags off had turned them off too. */
    @Test
    fun aV99ConfigWithAirTagsOffGetsFindMyOff() {
        val loaded = ConfigUpgrade.load(v99Config(airTagsEnabled = false))
        assertFalse(loaded.fleets.single { it.id == "fleet-findmy" }.enabled)
        assertFalse(airTags(loaded.fleets).enabled)
    }

    /** A config at v100 still holding the old rule, switched off, loses it on load. */
    @Test
    fun aV100ConfigThatStillHasTheRuleLosesItOnLoad() {
        val stale = MatchRule(RuleKind.MANUFACTURER_DATA, companyId = 0x004C, dataPrefixHex = "12", enabled = false)
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(version = 100, fleets = c.fleets.map { if (it.id == "fleet-airtag") it.copy(rules = it.rules + stale) else it })
        }
        assertFalse(airTags(ConfigUpgrade.load(old).fleets).hasOfflineFindingRule())
    }

    @Test
    fun overlaysAndImportsDontRestoreTheOldRule() {
        val v99 = snapshot(99)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v99)
        assertFalse(airTags(overlaid).hasOfflineFindingRule())
        val (onOld, _) = SignatureExchange.overlayStock(v99.map { it.copy(builtIn = true) }, snapshot(100))
        assertFalse(airTags(onOld).hasOfflineFindingRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), listOf(airTags(v99)))
        assertFalse(airTags(merged).hasOfflineFindingRule())
    }

    private fun v99Config(airTagsEnabled: Boolean) = ConfigUpgrade.fieldwatch1132().let { c ->
        c.copy(
            version = 99,
            fleets = c.fleets.filterNot { it.id == "fleet-findmy" }
                .map { if (it.id == "fleet-airtag") it.copy(enabled = airTagsEnabled) else it },
        )
    }
}
