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

    /** Each retired entry was a v101 rule, and its row still has an anchored glob on the same brand. */
    @Test
    fun eachRetiredNameWasInV101AndKeepsItsAnchoredGlob() {
        assertEquals(19, retired.size)
        val v101 = snapshot(101).associateBy { it.id }
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (entry in retired) {
            val label = "${entry.fleetId} ${entry.rule.text}"
            assertTrue("$label in v101", v101.getValue(entry.fleetId).rules.any { RetiredRules.isRetired(entry.fleetId, it) })
            val brand = entry.rule.text.lowercase()
            assertTrue(
                "$label keeps an anchored glob",
                stock.getValue(entry.fleetId).rules.any { it.kind == RuleKind.NAME_GLOB && !it.text.startsWith("*") && it.text.lowercase().startsWith(brand) },
            )
        }
        assertFalse(DefaultCatalog.fleets().holdsARetiredRule())
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
