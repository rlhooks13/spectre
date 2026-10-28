package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
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
 * P1-B12b, catalog v110: Reveal Media's BodyWorn names move to Utility Inc as the glob BodyWorn* (BodyWorn is Utility's
 * camera) and RS2-* is retired. Utility is an older row with its own vehicle-AP OUIs, so only what concerns the moved
 * name carries over.
 */
class CatalogV110LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val reveal = "fleet-reveal-media"
    private val utility = "fleet-utility-inc"

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun v109(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 109, fleets = snapshot(109).map(edit), watchlist = emptyList())

    private fun Fleet.bodyWorn() = rules.single { it.text == "BodyWorn*" }

    private fun loadUtility(config: PersistedConfig) = ConfigUpgrade.load(config).fleets.single { it.id == utility }

    @Test
    fun theNameMovesAndTheRowsMatchStock() {
        val fleets = ConfigUpgrade.load(v109()).fleets.associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        assertEquals(stock.getValue(reveal), fleets.getValue(reveal))
        assertEquals(stock.getValue(utility), fleets.getValue(utility))
        assertTrue(fleets.getValue(reveal).rules.none { it.text.startsWith("BodyWorn") || it.text == "RS2-*" })
        assertTrue(fleets.getValue(utility).bodyWorn().enabled)
    }

    @Test
    fun anOffOnRevealCarriesToTheMovedName() {
        val nameOff = v109 { f -> if (f.id != reveal) f else f.copy(rules = f.rules.map { if (it.text == "BodyWorn") it.copy(enabled = false) else it }) }
        assertFalse(loadUtility(nameOff).bodyWorn().enabled)
        assertFalse(loadUtility(v109 { if (it.id == reveal) it.copy(enabled = false) else it }).bodyWorn().enabled)
    }

    /** Before v74 there was no Reveal row and nothing to switch off: the name starts on. */
    @Test
    fun aConfigFromBeforeRevealGetsTheNameOn() {
        val old = ConfigUpgrade.fieldwatch1132().let { c -> c.copy(fleets = c.fleets.filterNot { it.id == reveal }) }
        assertTrue(loadUtility(old).bodyWorn().enabled)
    }

    /** Utility switched off, Reveal on: BodyWorn stays labeled, Utility's vehicle-AP OUIs stay silent. */
    @Test
    fun aUtilityRowOffComesBackWithOnlyTheMovedName() {
        val row = loadUtility(v109 { if (it.id == utility) it.copy(enabled = false) else it })
        assertTrue(row.enabled)
        assertTrue(row.bodyWorn().enabled)
        assertTrue(row.rules.filter { it.text != "BodyWorn*" }.none { it.enabled })
        // With the moved name off too, the row stays off.
        val both = v109 { if (it.id == utility || it.id == reveal) it.copy(enabled = false) else it }
        assertFalse(loadUtility(both).enabled)
    }

    /** A filter that showed Reveal shows Utility; a hide set and the watchlist are the user's. */
    @Test
    fun onlyAShowFilterCarries() {
        val base = v109()
        val shown = ConfigUpgrade.load(base.copy(filter = base.filter.copy(includeFleetIds = setOf(reveal))))
        assertEquals(setOf(reveal, utility), shown.filter.includeFleetIds)
        val hidden = ConfigUpgrade.load(base.copy(filter = base.filter.copy(excludeSignatures = true, fleetIds = setOf(reveal))))
        assertEquals(setOf(reveal), hidden.filter.fleetIds)
        val watched = ConfigUpgrade.load(base.copy(watchlist = listOf(WatchTarget(id = "watch-$reveal", fleetId = reveal, label = "Reveal Media"))))
        assertTrue("a removed Utility watch isn't brought back", watched.watchlist.none { it.fleetId == utility })
    }

    @Test
    fun editedNotesStay() {
        assertEquals("mine", loadUtility(v109 { if (it.id == utility) it.copy(notes = "mine") else it }).notes)
    }
}
