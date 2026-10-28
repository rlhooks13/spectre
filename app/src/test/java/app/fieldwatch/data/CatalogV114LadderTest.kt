package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
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
 * P1-B13a, catalog v114: the glasses row matches Meta's company id only with a glasses product id; the bare ids move to
 * a new Meta device row (no attention). A bare id the user switched off stays off.
 */
class CatalogV114LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val glasses = "fleet-meta-glasses"
    private val device = "fleet-meta-device"

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun v113(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 113, fleets = snapshot(113).map { if (it.id == glasses) edit(it) else it })

    private fun load(c: PersistedConfig) = ConfigUpgrade.load(c).fleets.associateBy { it.id }

    @Test
    fun theRowsMatchStock() {
        val fleets = load(v113())
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        assertEquals(stock.getValue(glasses), fleets.getValue(glasses))
        assertEquals(stock.getValue(device), fleets.getValue(device))
        assertTrue(fleets.getValue(device).attentionNote.isBlank())
        assertFalse(fleets.getValue(glasses).rules.any { it.kind == RuleKind.MANUFACTURER_ID && (it.companyId == 0x01AB || it.companyId == 0x058E) })
        assertFalse(fleets.getValue(glasses).rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "FEB8" })
        assertTrue(fleets.getValue(device).rules.single { it.kind == RuleKind.SERVICE_UUID && it.text == "FEB8" }.enabled)
    }

    @Test
    fun anOffOnFeb8Carries() {
        val old = v113 { f -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.SERVICE_UUID && it.text == "FEB8") it.copy(enabled = false) else it }) }
        assertFalse(load(old).getValue(device).rules.single { it.kind == RuleKind.SERVICE_UUID && it.text == "FEB8" }.enabled)
    }

    @Test
    fun anOffOnABareIdCarries() {
        val old = v113 { f -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x01AB) it.copy(enabled = false) else it }) }
        val fleets = load(old)
        val meta = fleets.getValue(device).rules.associateBy { it.companyId }
        assertFalse(meta.getValue(0x01AB).enabled)
        assertTrue(meta.getValue(0x058E).enabled)
        val prefixes = fleets.getValue(glasses).rules.filter { it.kind == RuleKind.MANUFACTURER_DATA && it.companyId == 0x01AB }
        assertEquals(7, prefixes.size)
        assertTrue(prefixes.none { it.enabled })
        // An off on 0x058E only leaves the glasses prefixes on.
        val only058e = load(v113 { f -> f.copy(rules = f.rules.map { if (it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x058E) it.copy(enabled = false) else it }) })
        assertFalse(only058e.getValue(device).rules.associateBy { it.companyId }.getValue(0x058E).enabled)
        assertTrue(only058e.getValue(glasses).rules.filter { it.kind == RuleKind.MANUFACTURER_DATA }.all { it.enabled })
    }

    /** The glasses row switched off: Meta device starts off. A filter naming the glasses row names Meta device too. */
    @Test
    fun theRowSwitchAndFiltersCarry() {
        assertFalse(load(v113 { it.copy(enabled = false) }).getValue(device).enabled)
        val base = v113()
        val loaded = ConfigUpgrade.load(base.copy(filter = base.filter.copy(includeFleetIds = setOf(glasses))))
        assertEquals(setOf(glasses, device), loaded.filter.includeFleetIds)
    }

    @Test
    fun editedNotesStay() {
        val fleets = load(v113 { it.copy(notes = "mine", attentionNote = "look") })
        assertEquals("mine", fleets.getValue(glasses).notes)
        assertEquals("look", fleets.getValue(glasses).attentionNote)
    }

    @Test
    fun theMetaDeviceRowIsNotWatchedByDefault() {
        assertTrue(DefaultCatalog.defaultWatchlist().none { it.fleetId == device })
    }
}
