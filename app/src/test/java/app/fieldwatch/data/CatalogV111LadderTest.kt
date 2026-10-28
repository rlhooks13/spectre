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
import org.junit.Test

/** P1-B12c, catalog v111: ShotSpotter's and Rhombus's OUIs become Wi-Fi only, carrying a user's "off". */
class CatalogV111LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val ids = listOf("fleet-raven", "fleet-rhombus")

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun v110(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 110, fleets = snapshot(110).map { if (it.id in ids) edit(it) else it })

    private fun Fleet.oui() = rules.single { it.kind == RuleKind.OUI }

    @Test
    fun theOuisBecomeWifiOnly() {
        val fleets = ConfigUpgrade.load(v110()).fleets.associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (id in ids) {
            assertEquals(id, stock.getValue(id), fleets.getValue(id))
            assertEquals(id, RadioKind.WIFI, fleets.getValue(id).oui().radio)
        }
    }

    @Test
    fun anOffCarries() {
        val old = v110 { f -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.OUI) it.copy(enabled = false) else it }) }
        val fleets = ConfigUpgrade.load(old).fleets.associateBy { it.id }
        for (id in ids) assertFalse(id, fleets.getValue(id).oui().enabled)
    }

    /** Only an off on the retired OUI carries: an off on another rule, or on the other row, leaves the Wi-Fi OUI on. */
    @Test
    fun anOffElsewhereDoesNotCarry() {
        val old = v110 { f ->
            when (f.id) {
                "fleet-raven" -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.SERVICE_UUID) it.copy(enabled = false) else it })
                "fleet-rhombus" -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.OUI) it.copy(enabled = false) else it })
                else -> f
            }
        }
        val fleets = ConfigUpgrade.load(old).fleets.associateBy { it.id }
        assertEquals(true, fleets.getValue("fleet-raven").oui().enabled)
        assertFalse(fleets.getValue("fleet-rhombus").oui().enabled)
    }

    /** Rhombus's attention note names the radio now; a note the user wrote stays. */
    @Test
    fun rhombusAttentionNoteChangesOnlyWhereStock() {
        val stock = DefaultCatalog.fleets().single { it.id == "fleet-rhombus" }.attentionNote
        assertEquals(stock, ConfigUpgrade.load(v110()).fleets.single { it.id == "fleet-rhombus" }.attentionNote)
        val mine = v110 { if (it.id == "fleet-rhombus") it.copy(attentionNote = "mine") else it }
        assertEquals("mine", ConfigUpgrade.load(mine).fleets.single { it.id == "fleet-rhombus" }.attentionNote)
    }
}
