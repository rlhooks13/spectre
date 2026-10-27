package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RadioKind
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
 * P1-B5a, catalog v101: the bare names "RAVEN" and "Vigilant" and the Wi-Fi glob "IBR*" are retired. RAVEN and
 * Vigilant get separator-ended globs; Cradlepoint keeps its model globs. The retired rules stay gone on every path.
 */
class CatalogV101LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val retired = listOf(
        "fleet-raven" to MatchRule(RuleKind.NAME_CONTAINS, text = "RAVEN"),
        "fleet-vigilant" to MatchRule(RuleKind.NAME_CONTAINS, text = "Vigilant"),
        "fleet-cradlepoint" to MatchRule(RuleKind.NAME_GLOB, text = "IBR*", radio = RadioKind.WIFI),
    )

    private fun List<Fleet>.holdsARetiredRule(): Boolean = retired.any { (id, rule) ->
        val fleet = firstOrNull { it.id == id } ?: return@any false
        fleet.rules.any { SignatureExchange.ruleKey(it) == SignatureExchange.ruleKey(rule) }
    }

    private fun snapshot(n: Int) = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v$n.json").readText()).fleets

    @Test
    fun theStockRowsAreTheNewOnes() {
        val stock = DefaultCatalog.fleets()
        assertFalse(stock.holdsARetiredRule())
        val raven = stock.single { it.id == "fleet-raven" }.rules.map { it.text }
        assertTrue(raven.containsAll(listOf("RAVEN-*", "RAVEN_*")))
        val vigilant = stock.single { it.id == "fleet-vigilant" }.rules.map { it.text }
        assertTrue(vigilant.containsAll(listOf("Vigilant Solutions", "Motorola Vigilant", "Vigilant-*", "Vigilant_*")))
        assertTrue(stock.single { it.id == "fleet-cradlepoint" }.rules.any { it.text == "IBR900*" })
    }

    /** Each retired entry is exactly a rule the v100 rows had, so stripping it really removes something. */
    @Test
    fun theRetiredEntriesAreTheV100Rules() {
        val v100 = snapshot(100)
        assertTrue(v100.holdsARetiredRule())
        for ((id, rule) in retired) {
            assertTrue("$id ${rule.text}", RetiredRules.isRetired(id, rule))
            assertTrue("$id ${rule.text} in v100", v100.single { it.id == id }.rules.any { RetiredRules.isRetired(id, it) })
        }
    }

    @Test
    fun aFieldwatchConfigLosesTheRulesAndGetsTheGlobs() {
        val cfg = ConfigUpgrade.load()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        assertFalse(cfg.fleets.holdsARetiredRule())
        assertTrue(cfg.fleets.single { it.id == "fleet-raven" }.rules.any { it.text == "RAVEN-*" })
    }

    /** A config at v101 still holding the old rules, one switched off, loses them on load. */
    @Test
    fun aV101ConfigThatStillHasTheRulesLosesThemOnLoad() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 101,
                fleets = c.fleets.map { fleet ->
                    val back = retired.filter { it.first == fleet.id }.map { it.second.copy(enabled = fleet.id != "fleet-vigilant") }
                    if (back.isEmpty()) fleet else fleet.copy(rules = fleet.rules + back)
                },
            )
        }
        assertFalse(ConfigUpgrade.load(old).fleets.holdsARetiredRule())
    }

    @Test
    fun overlaysAndImportsDontRestoreTheOldRules() {
        val v100 = snapshot(100)
        val (overlaid, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v100)
        assertFalse(overlaid.holdsARetiredRule())
        val (onOld, _) = SignatureExchange.overlayStock(v100.map { it.copy(builtIn = true) }, snapshot(101))
        assertFalse(onOld.holdsARetiredRule())
        val (merged, _) = SignatureExchange.merge(DefaultCatalog.fleets(), v100.filter { f -> retired.any { it.first == f.id } })
        assertFalse(merged.holdsARetiredRule())
    }
}
