package app.fieldwatch.data

import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.testing.ConfigUpgrade
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B6, catalog v104: one rule key that ignores the switch. A row an older overlay or import left holding a rule twice
 * (once on, once off) keeps one copy, off if either was. The stock catalog doesn't change.
 */
class CatalogV104LadderTest {
    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private val fe33 = MatchRule(RuleKind.SERVICE_UUID, text = "FE33")

    @Test
    fun aDuplicateRuleIsKeptOnceAndOffWins() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 103,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-chipolo") return@map fleet
                    // As the old overlay left it: the user's "off" copy beside a fresh "on" copy of the same text.
                    fleet.copy(
                        rules = fleet.rules.filterNot { SignatureExchange.ruleKey(it) == SignatureExchange.ruleKey(fe33) } +
                            fe33.copy(enabled = false) + fe33,
                    )
                },
            )
        }
        val chipolo = ConfigUpgrade.load(old).fleets.single { it.id == "fleet-chipolo" }.rules
        val copies = chipolo.filter { SignatureExchange.ruleKey(it) == SignatureExchange.ruleKey(fe33) }
        assertEquals(1, copies.size)
        assertFalse(copies.single().enabled)
        assertEquals(chipolo.size, chipolo.map { SignatureExchange.ruleKey(it) }.toSet().size)
    }

    /** A user who switched off one case variant of a stock rule ("Eufy*") still matched on the other; that stays. */
    @Test
    fun aCaseVariantOffKeepsTheRuleOn() {
        val old = ConfigUpgrade.fieldwatch1132().let { c ->
            c.copy(
                version = 103,
                fleets = c.fleets.map { fleet ->
                    if (fleet.id != "fleet-eufy") return@map fleet
                    fleet.copy(rules = fleet.rules + MatchRule(RuleKind.NAME_GLOB, text = "Eufy*", enabled = false))
                },
            )
        }
        val eufy = ConfigUpgrade.load(old).fleets.single { it.id == "fleet-eufy" }.rules
        val copies = eufy.filter { it.kind == RuleKind.NAME_GLOB && it.text.equals("eufy*", ignoreCase = true) }
        assertEquals(1, copies.size)
        assertTrue(copies.single().enabled)
    }

    /** With no duplicates, the v104 step changes nothing but the version. */
    @Test
    fun aConfigWithoutDuplicatesIsUnchangedApartFromTheVersion() {
        val clean = ConfigUpgrade.load()
        assertTrue(clean.fleets.all { f -> f.rules.size == f.rules.map { SignatureExchange.ruleKey(it) }.toSet().size })
        val replayed = ConfigUpgrade.load(clean.copy(version = 103))
        assertEquals(ConfigStore.CATALOG_VERSION, replayed.version)
        assertEquals(clean.fleets, replayed.fleets)
    }
}
