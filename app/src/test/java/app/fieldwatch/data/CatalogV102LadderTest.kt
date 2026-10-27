package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RetiredRules
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
 * P1-B5b, catalog v102: 19 short "contains" names are retired, each from a row that keeps an anchored glob of the same
 * brand. The retired rules stay gone on every path.
 */
class CatalogV102LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val retired = RetiredRules.ALL.filter { it.retiredIn == 102 }

    private fun List<Fleet>.holdsARetiredRule(): Boolean = any { fleet -> fleet.rules.any { RetiredRules.isRetired(fleet.id, it) } }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    /**
     * Each retired entry was a v101 rule, and its row still has an anchored glob on the same brand that applies on
     * every radio the retired rule did (a Wi-Fi glob doesn't cover a BLE name).
     */
    @Test
    fun eachRetiredNameWasInV101AndKeepsAnAnchoredGlobOnItsRadio() {
        assertEquals(18, retired.size)
        val v101 = snapshot(101).associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (entry in retired) {
            val label = "${entry.fleetId} ${entry.rule.text}"
            assertTrue("$label in v101", v101.getValue(entry.fleetId).rules.any { RetiredRules.isRetired(entry.fleetId, it) })
            val brand = entry.rule.text.lowercase()
            val radio = entry.rule.radio
            assertTrue(
                "$label keeps an anchored glob on its radio",
                stock.getValue(entry.fleetId).rules.any {
                    it.kind == RuleKind.NAME_GLOB && !it.text.startsWith("*") && it.text.lowercase().startsWith(brand) &&
                        (it.radio == null || (radio != null && it.radio == radio))
                },
            )
        }
        assertFalse(DefaultCatalog.fleets().holdsARetiredRule())
    }

    /** A user who switched a retired name off gets the globs v102 added beside it off too. */
    @Test
    fun anOffRetiredNameTurnsTheAddedGlobOff() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 101,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-hp") return@map fleet
                    val stale = retired.single { it.fleetId == "fleet-hp" }.rule.copy(enabled = false)
                    fleet.copy(rules = fleet.rules.filterNot { RetiredRules.isRetired(fleet.id, it) } + stale)
                },
            )
        }
        val loaded = ConfigUpgrade.load(old).fleets
        assertFalse(loaded.single { it.id == "fleet-hp" }.rules.single { it.text == "HP ENVY*" }.enabled)
        assertTrue(loaded.single { it.id == "fleet-myq" }.rules.single { it.text == "MyQ" }.enabled)
    }

    @Test
    fun aFieldwatchConfigLosesTheNames() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        assertFalse(cfg.fleets.holdsARetiredRule())
    }

    @Test
    fun overlaysAndImportsDontRestoreTheNames() {
        val v101 = snapshot(101)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v101)
        assertFalse(overlaid.holdsARetiredRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), v101.filter { f -> retired.any { it.fleetId == f.id } })
        assertFalse(merged.holdsARetiredRule())
    }
}
