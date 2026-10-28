package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.matchKey
import app.fieldwatch.testing.ConfigUpgrade
import app.fieldwatch.testing.TestFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B11c, catalog v107: AirTags' FD44 and "Find My" name rules move to "Find My device" (the name BLE-only). A rule
 * the user had switched off on AirTags starts off on Find My device; the retired rules stay gone on every path.
 */
class CatalogV107LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val fd44 = MatchRule(RuleKind.SERVICE_UUID, text = "FD44")
    private val findMyName = MatchRule(RuleKind.NAME_CONTAINS, text = "Find My")
    private val findMyBleName = findMyName.copy(radio = RadioKind.BLE)

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun List<Fleet>.row(id: String) = single { it.id == id }

    private fun Fleet.has(rule: MatchRule) = rules.any { it.matchKey() == rule.matchKey() }

    private fun Fleet.rule(rule: MatchRule) = rules.single { it.matchKey() == rule.matchKey() }

    /** A v106 config: its rows are the v106 stock catalog's. */
    private fun v106(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 106, fleets = snapshot(106).map(edit))

    @Test
    fun theRulesMove() {
        val fleets = ConfigUpgrade.load(v106()).fleets
        val airTags = fleets.row("fleet-airtag")
        val findMy = fleets.row("fleet-findmy")
        assertFalse(airTags.has(fd44))
        assertFalse(airTags.has(findMyName))
        assertTrue(findMy.rule(fd44).enabled)
        assertTrue(findMy.rule(findMyBleName).enabled)
        assertFalse("the any-radio name isn't brought back on Find My device", findMy.has(findMyName))
    }

    @Test
    fun anOffOnAirTagsCarriesOver() {
        val old = v106 { f ->
            if (f.id != "fleet-airtag") f else f.copy(rules = f.rules.map { if (it.matchKey() == fd44.matchKey()) it.copy(enabled = false) else it })
        }
        val findMy = ConfigUpgrade.load(old).fleets.row("fleet-findmy")
        assertFalse("FD44 was off on AirTags", findMy.rule(fd44).enabled)
        assertTrue("the name rule was on", findMy.rule(findMyBleName).enabled)
    }

    @Test
    fun anOffOnTheNameRuleCarriesOver() {
        val old = v106 { f ->
            if (f.id != "fleet-airtag") f else f.copy(rules = f.rules.map { if (it.matchKey() == findMyName.matchKey()) it.copy(enabled = false) else it })
        }
        val findMy = ConfigUpgrade.load(old).fleets.row("fleet-findmy")
        assertFalse(findMy.rule(findMyBleName).enabled)
        assertTrue(findMy.rule(fd44).enabled)
    }

    /** AirTags switched off as a row: neither moved rule starts labeling on Find My device. */
    @Test
    fun anAirTagsRowOffKeepsTheMovedRulesOff() {
        val findMy = ConfigUpgrade.load(v106 { if (it.id == "fleet-airtag") it.copy(enabled = false) else it }).fleets.row("fleet-findmy")
        assertFalse(findMy.rule(fd44).enabled)
        assertFalse(findMy.rule(findMyBleName).enabled)
    }

    /**
     * Find My device switched off as a row, AirTags on: the row comes back on so FD44 radios stay labeled, with its own
     * Offline Finding rule off, so what the user silenced stays silent.
     */
    @Test
    fun aFindMyRowOffComesBackWithOnlyTheMovedRules() {
        val findMy = ConfigUpgrade.load(v106 { if (it.id == "fleet-findmy") it.copy(enabled = false) else it }).fleets.row("fleet-findmy")
        assertTrue(findMy.enabled)
        assertTrue(findMy.rule(fd44).enabled)
        assertTrue(findMy.rule(findMyBleName).enabled)
        assertTrue("the Offline Finding rule stays silent", findMy.rules.filter { it.kind == RuleKind.MANUFACTURER_DATA }.none { it.enabled })
    }

    /** Watched or filtered on AirTags only, Find My device is watched and filtered too (FD44 tags alerted as AirTags). */
    @Test
    fun watchesAndFiltersOnAirTagsCoverFindMy() {
        val base = v106()
        val watched = WatchTarget(id = "watch-fleet-airtag", fleetId = "fleet-airtag", label = "Apple AirTags", vibrate = false)
        val old = base.copy(
            watchlist = base.watchlist.filterNot { it.fleetId == "fleet-airtag" || it.fleetId == "fleet-findmy" } + watched,
            filter = base.filter.copy(includeFleetIds = setOf("fleet-airtag"), fleetIds = setOf("fleet-tile")),
        )
        val loaded = ConfigUpgrade.load(old)
        val target = loaded.watchlist.single { it.fleetId == "fleet-findmy" }
        assertFalse("the user's alert settings carry over", target.vibrate)
        assertEquals(setOf("fleet-airtag", "fleet-findmy"), loaded.filter.includeFleetIds)
        assertEquals("a set without AirTags is left alone", setOf("fleet-tile"), loaded.filter.fleetIds)
    }

    @Test
    fun theNoteChangesOnlyWhereStock() {
        assertEquals(DefaultCatalog.fleets().row("fleet-findmy").notes, ConfigUpgrade.load(v106()).fleets.row("fleet-findmy").notes)
        val edited = v106 { if (it.id == "fleet-findmy") it.copy(notes = "mine") else it }
        assertEquals("mine", ConfigUpgrade.load(edited).fleets.row("fleet-findmy").notes)
    }

    /** Importing the v106 rows (an old pack) doesn't put the retired rules back on AirTags. */
    @Test
    fun anOlderPackDoesNotBringThemBack() {
        val incoming = listOf(snapshot(106).row("fleet-airtag"))
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), incoming)
        val airTags = merged.row("fleet-airtag")
        assertFalse(airTags.has(fd44))
        assertFalse(airTags.has(findMyName))
    }
}
