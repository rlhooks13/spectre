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
        // P1-B2: "tile" anywhere in a name hit Tattile cameras; 0x00C7 is Quuppa's; FEDD has no capture behind it.
        Entry("fleet-tile", MatchRule(RuleKind.NAME_CONTAINS, text = "Tile"), retiredIn = 99),
        Entry("fleet-tile", MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x00C7), retiredIn = 99),
        Entry("fleet-tile", MatchRule(RuleKind.SERVICE_UUID, text = "FEDD"), retiredIn = 99),
        // P1-B3: a bare Offline Finding record is any Find My device; "Find My device" takes it and routes by type.
        Entry("fleet-airtag", MatchRule(RuleKind.MANUFACTURER_DATA, companyId = 0x004C, dataPrefixHex = "12"), retiredIn = 100),
        // P1-B5a: bare names that hit everyday devices ("Ravenclaw", a "Vigilant" home alarm, an "IBRAHIM" hotspot).
        Entry("fleet-raven", MatchRule(RuleKind.NAME_CONTAINS, text = "RAVEN"), retiredIn = 101),
        Entry("fleet-vigilant", MatchRule(RuleKind.NAME_CONTAINS, text = "Vigilant"), retiredIn = 101),
        Entry("fleet-cradlepoint", MatchRule(RuleKind.NAME_GLOB, text = "IBR*", radio = RadioKind.WIFI), retiredIn = 101),
    )

    /** The Bluetooth base UUID around a 16-bit alias: `0000XXXX-0000-1000-8000-00805F9B34FB`. */
    private val BASE_UUID = Regex("""^0000([0-9A-F]{4})-0000-1000-8000-00805F9B34FB$""")

    /** What a rule matches, ignoring whether it is switched on. A long-form base UUID reads as its 16-bit alias. */
    private fun key(rule: MatchRule): String {
        val text = rule.text.trim().uppercase().let { t ->
            if (rule.kind == RuleKind.SERVICE_UUID || rule.kind == RuleKind.SERVICE_DATA) BASE_UUID.matchEntire(t)?.groupValues?.get(1) ?: t else t
        }
        return listOf(rule.kind.name, text, rule.companyId, rule.dataPrefixHex.uppercase(), rule.radio?.name.orEmpty()).joinToString("|")
    }

    private val keys: Map<String, Set<String>> = ALL.groupBy({ it.fleetId }, { key(it.rule) }).mapValues { it.value.toSet() }

    fun isRetired(fleetId: String, rule: MatchRule): Boolean = keys[fleetId]?.contains(key(rule)) == true

    /** [fleet] without its retired rules (the same instance when it has none). */
    fun strip(fleet: Fleet): Fleet {
        val retired = keys[fleet.id] ?: return fleet
        val kept = fleet.rules.filterNot { key(it) in retired }
        return if (kept.size == fleet.rules.size) fleet else fleet.copy(rules = kept)
    }
}
