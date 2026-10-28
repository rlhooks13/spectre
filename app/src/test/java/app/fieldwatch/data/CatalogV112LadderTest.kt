package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
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

/** P1-B12f, catalog v112: Uniview's, Hatch's and Arlo's static-random-range OUIs become Wi-Fi only, carrying an "off". */
class CatalogV112LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val moved = mapOf("fleet-uniview" to "C4:79:05", "fleet-hatch" to "C8:FA:9C", "fleet-arlo" to "FC:9C:98")

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun v111(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 111, fleets = snapshot(111).map { if (it.id in moved) edit(it) else it })

    private fun Fleet.ouiOf(text: String) = rules.single { it.kind == RuleKind.OUI && it.text == text }

    @Test
    fun theOuisBecomeWifiOnlyAndTheRowsMatchStock() {
        val fleets = ConfigUpgrade.load(v111()).fleets.associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for ((id, oui) in moved) {
            assertEquals(id, stock.getValue(id), fleets.getValue(id))
            assertEquals(id, RadioKind.WIFI, fleets.getValue(id).ouiOf(oui).radio)
            assertTrue(id, fleets.getValue(id).ouiOf(oui).enabled)
        }
    }

    @Test
    fun anOffCarriesOnlyFromTheRetiredOui() {
        val old = v111 { f -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.OUI && it.text == moved[f.id]) it.copy(enabled = false) else it }) }
        val fleets = ConfigUpgrade.load(old).fleets.associateBy { it.id }
        for ((id, oui) in moved) assertFalse(id, fleets.getValue(id).ouiOf(oui).enabled)
    }

    /** An off on another rule of the row (Hatch's company id, Arlo's other OUI) leaves the Wi-Fi OUI on. */
    @Test
    fun anOffElsewhereDoesNotCarry() {
        val old = v111 { f ->
            f.copy(rules = f.rules.map { r ->
                val other = (f.id == "fleet-hatch" && r.kind == RuleKind.MANUFACTURER_ID) || (f.id == "fleet-arlo" && r.text == "48:62:64")
                if (other) r.copy(enabled = false) else r
            })
        }
        val fleets = ConfigUpgrade.load(old).fleets.associateBy { it.id }
        for ((id, oui) in moved) assertTrue(id, fleets.getValue(id).ouiOf(oui).enabled)
        assertFalse(fleets.getValue("fleet-arlo").ouiOf("48:62:64").enabled)
    }
}
