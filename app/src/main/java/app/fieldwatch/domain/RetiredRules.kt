package app.fieldwatch.domain

/**
 * Stock rules a catalog version retired. The documented list is `catalog/retired.json` (CLAUDE.md catalog checklist,
 * step 5); RetiredRulesFileTest keeps the two identical.
 *
 * A retired rule is removed from a built-in fleet on every config load, and never comes back through a signature
 * import or a stock-catalog overlay, so no path (an old pack, a file, a config that skipped a ladder step) restores
 * a label the catalog took away. A user's identical rule on that fleet goes too: it is the same false match.
 * P1-B6 extends this to the generic `ruleKey` handling.
 */
object RetiredRules {
    data class Entry(val fleetId: String, val rule: MatchRule, val retiredIn: Int)

    val ALL: List<Entry> = listOf(
        // P1-B1: Samsung's company id is on every Galaxy phone and Buds.
        Entry("fleet-smarttag", MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0075), retiredIn = 98),
    )

    /** What a rule matches, ignoring whether it is switched on. */
    private fun key(rule: MatchRule): String =
        listOf(rule.kind.name, rule.text.uppercase(), rule.companyId, rule.dataPrefixHex.uppercase(), rule.radio?.name.orEmpty())
            .joinToString("|")

    private val keys: Map<String, Set<String>> = ALL.groupBy({ it.fleetId }, { key(it.rule) }).mapValues { it.value.toSet() }

    fun isRetired(fleetId: String, rule: MatchRule): Boolean = keys[fleetId]?.contains(key(rule)) == true

    /** [fleet] without its retired rules (the same instance when it has none). */
    fun strip(fleet: Fleet): Fleet {
        val retired = keys[fleet.id] ?: return fleet
        val kept = fleet.rules.filterNot { key(it) in retired }
        return if (kept.size == fleet.rules.size) fleet else fleet.copy(rules = kept)
    }
}
