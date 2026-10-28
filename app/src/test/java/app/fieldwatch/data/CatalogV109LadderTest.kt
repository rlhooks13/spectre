package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.testing.ConfigUpgrade
import app.fieldwatch.testing.TestFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B12a, catalog v109: Novatel (Inseego MiFi), Compex (Wi-Fi modules) and AirLink (Sierra Wireless fleet routers)
 * leave law enforcement. Their default watch goes; what the user set stays.
 */
class CatalogV109LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val ids = listOf("fleet-novatel", "fleet-compex", "fleet-airlink")

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    /** A v108 config with the default watches on the three rows (labeled with the stock name, as defaultWatchlist did). */
    private fun v108(edit: (Fleet) -> Fleet = { it }) = ConfigUpgrade.fieldwatch1132().let { c ->
        val stock = snapshot(108)
        c.copy(
            version = 108,
            fleets = stock.map { if (it.id in ids) edit(it) else it },
            watchlist = stock.filter { it.id in ids }.map { WatchTarget(id = "watch-${it.id}", fleetId = it.id, label = it.name) },
        )
    }

    @Test
    fun theRowsTakeTheStockClassNotesAndNoAttention() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val loaded = ConfigUpgrade.load(v108())
        for (id in ids) {
            val row = loaded.fleets.single { it.id == id }
            assertEquals(id, stock.getValue(id), row)
            assertTrue(id, row.kind != SignatureClass.LAW_ENFORCEMENT && row.attentionNote.isBlank())
        }
        assertTrue("the default watches go", loaded.watchlist.none { it.fleetId in ids })
    }

    @Test
    fun whatTheUserSetStays() {
        val old = v108 { if (it.id == "fleet-compex") it.copy(kind = SignatureClass.SURVEILLANCE, notes = "mine", attentionNote = "look") else it }
            .let { c -> c.copy(watchlist = c.watchlist.map { if (it.fleetId == "fleet-novatel") it.copy(vibrate = false) else it }) }
        val loaded = ConfigUpgrade.load(old)
        val compex = loaded.fleets.single { it.id == "fleet-compex" }
        assertEquals(SignatureClass.SURVEILLANCE, compex.kind)
        assertEquals("mine", compex.notes)
        assertEquals("look", compex.attentionNote)
        assertEquals("a row the user keeps an attention note on stays watched", 1, loaded.watchlist.count { it.fleetId == "fleet-compex" })
        assertEquals("a watch the user changed stays", 1, loaded.watchlist.count { it.fleetId == "fleet-novatel" })
    }

    /** Renamed, recolored, notes edited: each part the user set stays, the rest moves, and the default watch still goes. */
    @Test
    fun eachPartMovesOnItsOwn() {
        val old = v108 { if (it.id == "fleet-airlink") it.copy(name = "Sierra router", colorIndex = 2, notes = "mine") else it }
        val loaded = ConfigUpgrade.load(old)
        val airlink = loaded.fleets.single { it.id == "fleet-airlink" }
        assertEquals(SignatureClass.VEHICLE, airlink.kind)
        assertEquals(2, airlink.colorIndex)
        assertEquals("mine", airlink.notes)
        assertTrue(airlink.attentionNote.isBlank())
        assertTrue("the default watch goes after a rename", loaded.watchlist.none { it.fleetId == "fleet-airlink" })
    }

    /** A fresh install doesn't watch them either. */
    @Test
    fun theDefaultWatchlistLeavesThemOut() {
        assertTrue(DefaultCatalog.defaultWatchlist().none { it.fleetId in ids })
    }
}
