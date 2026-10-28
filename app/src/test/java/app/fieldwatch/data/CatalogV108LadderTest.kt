package app.fieldwatch.data

import app.fieldwatch.domain.DecodeSource
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FleetDecode
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
 * P1-B11d, catalog v108: SmartTag's "contains" names give way to whole BLE names, and the row gains its FD5A decode.
 * An "off" on a retired name carries to the new names; a decode the user wrote stays.
 */
class CatalogV108LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    private fun List<Fleet>.smartTag() = single { it.id == "fleet-smarttag" }

    private fun Fleet.names() = rules.filter { it.kind == RuleKind.NAME_GLOB || it.kind == RuleKind.NAME_CONTAINS }

    private fun v107(edit: (Fleet) -> Fleet = { it }) =
        ConfigUpgrade.fieldwatch1132().copy(version = 107, fleets = snapshot(107).map { if (it.id == "fleet-smarttag") edit(it) else it })

    @Test
    fun theNamesAreWholeAndTheDecodeArrives() {
        val tag = ConfigUpgrade.load(v107()).fleets.smartTag()
        assertEquals(DefaultCatalog.fleets().smartTag(), tag)
        assertEquals(
            setOf("Smart Tag", "Smart Tag2", "Galaxy SmartTag*"),
            tag.names().filter { it.kind == RuleKind.NAME_GLOB && it.radio == RadioKind.BLE && it.enabled }.map { it.text }.toSet(),
        )
        assertEquals(3, tag.names().size)
        assertEquals("FD5A", tag.decode!!.serviceUuid)
    }

    private fun withOff(text: String) = v107 { f -> f.copy(rules = f.rules.map { if (it.text == text) it.copy(enabled = false) else it }) }

    private fun on(tag: Fleet) = tag.names().filter { it.enabled }.map { it.text }.toSet()

    /** Each retired name's "off" reaches only its own replacements. */
    @Test
    fun anOffOnARetiredNameCarriesToItsOwnReplacements() {
        val spaced = ConfigUpgrade.load(withOff("Smart Tag")).fleets.smartTag()
        assertEquals(setOf("Galaxy SmartTag*"), on(spaced))
        assertTrue("FD5A stays on", spaced.rules.single { it.kind == RuleKind.SERVICE_UUID }.enabled)
        assertEquals(setOf("Smart Tag", "Smart Tag2"), on(ConfigUpgrade.load(withOff("Galaxy SmartTag")).fleets.smartTag()))
        assertEquals(setOf("Smart Tag", "Smart Tag2"), on(ConfigUpgrade.load(withOff("SmartTag")).fleets.smartTag()))
    }

    /** A user's copy of the row (not built in) is the user's: no decode is added. */
    @Test
    fun aUserCopyGetsNoDecode() {
        val copy = snapshot(107).smartTag().copy(id = "user-tag", name = "My tags", builtIn = false, decode = null)
        val old = v107().let { it.copy(fleets = it.fleets + copy) }
        assertEquals(null, ConfigUpgrade.load(old).fleets.single { it.id == "user-tag" }.decode)
    }

    @Test
    fun aDecodeTheUserWroteStays() {
        val mine = FleetDecode(source = DecodeSource.SERVICE_DATA, serviceUuid = "FD5A", fields = emptyList())
        assertEquals(mine, ConfigUpgrade.load(v107 { it.copy(decode = mine) }).fleets.smartTag().decode)
    }

    /** Importing the v107 row (an old pack) doesn't put the "contains" names back. */
    @Test
    fun anOlderPackDoesNotBringThemBack() {
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), listOf(snapshot(107).smartTag()))
        assertFalse(merged.smartTag().rules.any { it.kind == RuleKind.NAME_CONTAINS })
    }
}
