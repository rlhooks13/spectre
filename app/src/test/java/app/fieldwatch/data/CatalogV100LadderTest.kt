package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RetiredRules
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.WatchTarget
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

    private fun findMy(fleets: List<Fleet>) = fleets.single { it.id == "fleet-findmy" }

    @Test
    fun aV99ConfigWithTheRuleOnGetsFindMyOn() {
        val loaded = ConfigUpgrade.load(v99Config(ruleEnabled = true))
        assertTrue(findMy(loaded.fleets).rules.all { it.enabled })
        assertFalse(airTags(loaded.fleets).hasOfflineFindingRule())
    }

    /** The engine reads rule switches, not Fleet.enabled; a user who switched AirTags' 0x12 rule off keeps it off. */
    @Test
    fun aV99ConfigWithTheRuleOffGetsFindMysRuleOff() {
        val loaded = ConfigUpgrade.load(v99Config(ruleEnabled = false))
        assertTrue(findMy(loaded.fleets).rules.none { it.enabled })
        assertFalse(airTags(loaded.fleets).hasOfflineFindingRule())
    }

    @Test
    fun watchesAndFiltersOnAirTagsCoverFindMy() {
        val base = v99Config(ruleEnabled = true)
        val watched = WatchTarget(id = "watch-fleet-airtag", fleetId = "fleet-airtag", label = "Apple AirTags", vibrate = false)
        val old = base.copy(
            watchlist = base.watchlist.filterNot { it.fleetId == "fleet-airtag" } + watched,
            filter = base.filter.copy(includeFleetIds = setOf("fleet-airtag", "fleet-tile"), fleetIds = setOf("fleet-tile")),
        )
        val loaded = ConfigUpgrade.load(old)
        val target = loaded.watchlist.single { it.fleetId == "fleet-findmy" }
        assertEquals("Find My device", target.label)
        assertFalse("the user's alert settings carry over", target.vibrate)
        assertEquals(setOf("fleet-airtag", "fleet-tile", "fleet-findmy"), loaded.filter.includeFleetIds)
        assertEquals("a set without AirTags is left alone", setOf("fleet-tile"), loaded.filter.fleetIds)
    }

    @Test
    fun theStockAirTagNoteIsRewordedAndAUsersNoteStays() {
        val stockNote = snapshot(99).single { it.id == "fleet-airtag" }.notes
        val reworded = ConfigUpgrade.load(v99Config(ruleEnabled = true, airTagNotes = stockNote))
        assertEquals(airTags(DefaultCatalog.fleets()).notes, airTags(reworded.fleets).notes)
        val mine = ConfigUpgrade.load(v99Config(ruleEnabled = true, airTagNotes = "My note."))
        assertEquals("My note.", airTags(mine.fleets).notes)
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

    /** A v99 config whose AirTags row is the v99 stock row, with its 0x12 rule switched as given. */
    private fun v99Config(ruleEnabled: Boolean, airTagNotes: String? = null) = ConfigUpgrade.fieldwatch1132().let { c ->
        val v99 = airTags(snapshot(99))
        val row = v99.copy(
            builtIn = true,
            rules = v99.rules.map { if (RetiredRules.isRetired("fleet-airtag", it)) it.copy(enabled = ruleEnabled) else it },
            notes = airTagNotes ?: v99.notes,
        )
        c.copy(
            version = 99,
            fleets = c.fleets.filterNot { it.id == "fleet-findmy" }.map { if (it.id == "fleet-airtag") row else it },
        )
    }
}
