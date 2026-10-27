package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B6: one rule key for "is this the same rule", ignoring whether it is switched on. The stock overlay and the
 * signature import used a key that counted `enabled`, so a user's switched-off stock rule looked like a different
 * rule: the overlay kept it beside a fresh switched-on copy (the "off" lost), and an import could add a duplicate.
 */
class RuleKeyTest {
    private val stockRule = MatchRule(RuleKind.SERVICE_UUID, text = "FE33")

    @Test
    fun theKeyIgnoresEnabledAndCaseAndLongFormUuids() {
        assertEquals(stockRule.matchKey(), stockRule.copy(enabled = false).matchKey())
        assertEquals(stockRule.matchKey(), stockRule.copy(text = " fe33 ").matchKey())
        assertEquals(stockRule.matchKey(), stockRule.copy(text = "0000FE33-0000-1000-8000-00805F9B34FB").matchKey())
        assertNotEquals(stockRule.matchKey(), stockRule.copy(radio = RadioKind.BLE).matchKey())
        assertEquals(SignatureExchange.ruleKey(stockRule), SignatureExchange.ruleKey(stockRule.copy(enabled = false)))
    }

    /** The overlay keeps a user's "off" on a stock rule, and the row has the rule once. */
    @Test
    fun overlayKeepsAnOffStockRuleOffAndDoesNotDuplicateIt() {
        val stock = Fleet(id = "fleet-x", name = "X", builtIn = true, rules = listOf(stockRule, MatchRule(RuleKind.NAME_CONTAINS, text = "Xyzzy")))
        val local = stock.copy(rules = listOf(stockRule.copy(enabled = false), MatchRule(RuleKind.NAME_CONTAINS, text = "Xyzzy")))
        val (out, _) = SignatureExchange.overlayStock(listOf(local), listOf(stock))
        val rules = out.single().rules
        assertEquals(2, rules.size)
        assertFalse("the user's off stays", rules.single { it.kind == RuleKind.SERVICE_UUID }.enabled)
    }

    /** An imported copy of a rule that differs only in its switch adds nothing. */
    @Test
    fun mergeDoesNotDuplicateARuleThatDiffersOnlyInItsSwitch() {
        val local = Fleet(id = "user-1", name = "Mine", rules = listOf(stockRule.copy(enabled = false)))
        val (out, result) = SignatureExchange.merge(listOf(local), listOf(local.copy(rules = listOf(stockRule))))
        assertEquals(1, out.single().rules.size)
        assertFalse(out.single().rules.single().enabled)
        assertEquals(0, result.merged)
    }

    /** A rule switched off or on is still a change worth saving, so the overlay reports it. */
    @Test
    fun theStateKeyCountsTheSwitch() {
        assertNotEquals(SignatureExchange.ruleStateKey(stockRule), SignatureExchange.ruleStateKey(stockRule.copy(enabled = false)))
        assertTrue(SignatureExchange.ruleStateKey(stockRule).startsWith(stockRule.matchKey()))
    }
}
