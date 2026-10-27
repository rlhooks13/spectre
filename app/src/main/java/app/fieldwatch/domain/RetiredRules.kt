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
        // P1-B5b: short "contains" names whose row already has an anchored glob of the same brand on the same radio.
        Entry("fleet-arlo", MatchRule(RuleKind.NAME_CONTAINS, text = "Arlo"), retiredIn = 102),
        Entry("fleet-asus", MatchRule(RuleKind.NAME_CONTAINS, text = "ASUS", radio = RadioKind.WIFI), retiredIn = 102),
        Entry("fleet-dji", MatchRule(RuleKind.NAME_CONTAINS, text = "DJI", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-dji", MatchRule(RuleKind.NAME_CONTAINS, text = "DJI", radio = RadioKind.WIFI), retiredIn = 102),
        Entry("fleet-eero", MatchRule(RuleKind.NAME_CONTAINS, text = "eero", radio = RadioKind.WIFI), retiredIn = 102),
        Entry("fleet-eufy", MatchRule(RuleKind.NAME_CONTAINS, text = "eufy"), retiredIn = 102),
        Entry("fleet-fobo", MatchRule(RuleKind.NAME_CONTAINS, text = "FOBO", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-hak5-pineapple", MatchRule(RuleKind.NAME_CONTAINS, text = "Hak5"), retiredIn = 102),
        Entry("fleet-hp", MatchRule(RuleKind.NAME_CONTAINS, text = "ENVY", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-kevo", MatchRule(RuleKind.NAME_CONTAINS, text = "Kevo", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-mbux", MatchRule(RuleKind.NAME_CONTAINS, text = "MBUX", radio = RadioKind.WIFI), retiredIn = 102),
        Entry("fleet-myq", MatchRule(RuleKind.NAME_CONTAINS, text = "MyQ", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-nuki", MatchRule(RuleKind.NAME_CONTAINS, text = "Nuki", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-paxton", MatchRule(RuleKind.NAME_CONTAINS, text = "Net2", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-seos", MatchRule(RuleKind.NAME_CONTAINS, text = "Yale", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-tapo", MatchRule(RuleKind.NAME_CONTAINS, text = "Tapo"), retiredIn = 102),
        Entry("fleet-tuya", MatchRule(RuleKind.NAME_CONTAINS, text = "TUYA", radio = RadioKind.BLE), retiredIn = 102),
        Entry("fleet-wyze", MatchRule(RuleKind.NAME_CONTAINS, text = "Wyze"), retiredIn = 102),
        // P1-B5: the last short "contains" names become the brand as a whole word at the start (bleWordStart).
        Entry("fleet-amazon", MatchRule(RuleKind.NAME_CONTAINS, text = "Echo", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-audi-mmi", MatchRule(RuleKind.NAME_CONTAINS, text = "Audi", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-bmw", MatchRule(RuleKind.NAME_CONTAINS, text = "BMW", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-bose", MatchRule(RuleKind.NAME_CONTAINS, text = "Bose", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-byd", MatchRule(RuleKind.NAME_CONTAINS, text = "BYD", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-ford", MatchRule(RuleKind.NAME_CONTAINS, text = "Ford", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-hobby-ble", MatchRule(RuleKind.NAME_CONTAINS, text = "BT05", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-jbl", MatchRule(RuleKind.NAME_CONTAINS, text = "JBL", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-logitech", MatchRule(RuleKind.NAME_CONTAINS, text = "Logi", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-microsoft", MatchRule(RuleKind.NAME_CONTAINS, text = "Xbox", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-oura", MatchRule(RuleKind.NAME_CONTAINS, text = "Oura", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-seos", MatchRule(RuleKind.NAME_CONTAINS, text = "Seos", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-sony", MatchRule(RuleKind.NAME_CONTAINS, text = "Sony", radio = RadioKind.BLE), retiredIn = 103),
        Entry("fleet-volkswagen", MatchRule(RuleKind.NAME_CONTAINS, text = "VW", radio = RadioKind.BLE), retiredIn = 103),
        // The UniFi AP row already has the anchored UBNT* (as in P1-B5b).
        Entry("fleet-unifi-ap", MatchRule(RuleKind.NAME_CONTAINS, text = "UBNT", radio = RadioKind.WIFI), retiredIn = 103),
    )

    /** The shared rule key (P1-B6): ignores the switch, reads a long-form base UUID as its 16-bit alias. */
    private fun key(rule: MatchRule): String = rule.matchKey()

    private val keys: Map<String, Set<String>> = ALL.groupBy({ it.fleetId }, { key(it.rule) }).mapValues { it.value.toSet() }

    /** The catalog version that retired [rule] on [fleetId], or null when it isn't retired. */
    fun retiredIn(fleetId: String, rule: MatchRule): Int? {
        val k = key(rule)
        return ALL.firstOrNull { it.fleetId == fleetId && key(it.rule) == k }?.retiredIn
    }

    fun isRetired(fleetId: String, rule: MatchRule): Boolean = keys[fleetId]?.contains(key(rule)) == true

    /** [fleet] without its retired rules (the same instance when it has none). */
    fun strip(fleet: Fleet): Fleet {
        val retired = keys[fleet.id] ?: return fleet
        val kept = fleet.rules.filterNot { key(it) in retired }
        return if (kept.size == fleet.rules.size) fleet else fleet.copy(rules = kept)
    }
}
