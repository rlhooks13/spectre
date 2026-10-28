package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RadioKind
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
 * P1-B12d, catalog v113: the LiteOn row becomes "LiteOn Wi-Fi module" (OTHER), its static-range OUIs Wi-Fi only, and
 * vendor IE 00:80:19 is retired. Each part the user set stays.
 */
class CatalogV113LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val id = "fleet-liteon-camera-radio"

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun v112(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 112, fleets = snapshot(112).map { if (it.id == id) edit(it) else it })

    private fun load(c: app.fieldwatch.domain.PersistedConfig) = ConfigUpgrade.load(c).fleets.single { it.id == id }

    private fun Fleet.oui(text: String) = rules.single { it.kind == RuleKind.OUI && it.text == text }

    @Test
    fun theRowMatchesStock() {
        val row = load(v112())
        assertEquals(DefaultCatalog.fleets().single { it.id == id }, row)
        assertEquals(SignatureClass.OTHER, row.kind)
        assertEquals(RadioKind.WIFI, row.oui("F4:6A:DD").radio)
        assertFalse(row.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text == "00:80:19" })
    }

    /** An off on one retired OUI carries to its own Wi-Fi replacement only; an off on the vendor IE carries nowhere. */
    @Test
    fun anOffCarriesPerOui() {
        val old = v112 { f ->
            f.copy(rules = f.rules.map { r ->
                val off = (r.kind == RuleKind.OUI && r.text == "D0:39:57") || r.kind == RuleKind.VENDOR_IE_OUI && r.text == "00:80:19"
                if (off) r.copy(enabled = false) else r
            })
        }
        val row = load(old)
        assertFalse(row.oui("D0:39:57").enabled)
        val others = listOf("D8:F3:BC", "C0:35:32", "E0:0A:F6", "E4:AA:EA", "F4:6A:DD", "F8:A2:D6", "E8:D0:FC")
        for (oui in others) assertTrue(oui, row.oui(oui).enabled)
        assertTrue(row.rules.single { it.kind == RuleKind.VENDOR_IE_OUI && it.text == "00:0A:EB" }.enabled)
    }

    /** Research item 13: Verkada Pass phones advertise these to unlock doors, so no stock row may hold them. */
    @Test
    fun noRowHoldsVerkadaMemberUuids() {
        val member = setOf("FD3A", "FD3B", "FC2C")
        val holders = DefaultCatalog.fleets().filter { f -> f.rules.any { it.text.uppercase() in member } }.map { it.id }
        assertEquals(emptyList<String>(), holders)
    }

    @Test
    fun aWatchWithTheOldNameTakesTheNewOne() {
        val old = v112().copy(watchlist = listOf(app.fieldwatch.domain.WatchTarget(id = "w", fleetId = id, label = "LiteOn camera radio")))
        assertEquals("LiteOn Wi-Fi module", ConfigUpgrade.load(old).watchlist.single { it.fleetId == id }.label)
    }

    @Test
    fun whatTheUserSetStays() {
        val row = load(v112 { it.copy(name = "My modules", kind = SignatureClass.HOME, notes = "mine") })
        assertEquals("My modules", row.name)
        assertEquals(SignatureClass.HOME, row.kind)
        assertEquals("mine", row.notes)
    }
}
