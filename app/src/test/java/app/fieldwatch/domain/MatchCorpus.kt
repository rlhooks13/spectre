package app.fieldwatch.domain

import app.fieldwatch.testing.Sightings.mac
import app.fieldwatch.testing.Sightings.sighting

/**
 * The match corpus (P1-A4). Every catalog fix in Phase 1B is checked against it.
 *
 * - [positives]: generated from each built-in signature's rules, one sighting per enabled rule (all rules
 *   in one sighting for an all-rules signature). Each must hit its signature.
 * - [nearMisses]: hand-written confusers only. Each lists the signatures it must not hit. One that hits
 *   today names the PLAN step that fixes it in [NearMiss.knownUntil]; the test fails if it stops hitting,
 *   so the marker comes off in the fix's PR.
 */
object MatchCorpus {
    data class Positive(val fleetId: String, val ruleIndex: Int, val sighting: Sighting)

    data class NearMiss(
        val label: String,
        val sighting: Sighting,
        val mustNotMatch: Set<String>,
        val knownUntil: String? = null,
    )

    /** Signatures that only hit in groups (peers, OUI clusters, sequential MACs) aren't single-sighting cases. */
    fun clustered(fleet: Fleet): Boolean = fleet.minPeers > 0 || fleet.clusterByOui || fleet.sequentialMac

    fun positives(fleets: List<Fleet> = DefaultCatalog.fleets()): List<Positive> = fleets
        .filterNot(::clustered)
        .flatMap { fleet ->
            val rules = fleet.rules.withIndex().filter { it.value.enabled }
            if (!fleet.matchAny) {
                val all = rules.fold(sighting("POS:${fleet.id}:all")) { s, r -> satisfy(r.value, s) }
                listOf(Positive(fleet.id, -1, all))
            } else {
                rules.map { (i, rule) -> Positive(fleet.id, i, satisfy(rule, sighting("POS:${fleet.id}:$i"))) }
            }
        }

    /** Changes [s] so that [rule] alone hits it. */
    fun satisfy(rule: MatchRule, s: Sighting): Sighting {
        val scoped = rule.radio?.let { s.copy(kind = it, randomized = it == RadioKind.BLE) } ?: s
        return when (rule.kind) {
            RuleKind.OUI, RuleKind.MAC_PREFIX ->
                (if (rule.radio == null) scoped.copy(kind = RadioKind.WIFI, randomized = false) else scoped)
                    .copy(mac = mac(rule.text))
            RuleKind.NAME_CONTAINS -> scoped.copy(name = rule.text)
            RuleKind.NAME_GLOB -> scoped.copy(name = rule.text.replace("*", "").replace('?', 'X'))
            RuleKind.SERVICE_UUID -> scoped.copy(serviceUuids = scoped.serviceUuids + rule.text)
            RuleKind.SERVICE_DATA -> scoped.copy(
                facts = scoped.facts.copy(
                    serviceData = scoped.facts.serviceData +
                        ServiceDataRecord(rule.text.ifBlank { "FFF0" }, rule.dataPrefixHex + "00"),
                ),
            )
            RuleKind.MANUFACTURER_ID -> scoped.withMfg(MfgRecord(rule.companyId, "00"))
            RuleKind.MANUFACTURER_DATA ->
                scoped.withMfg(MfgRecord(rule.companyId.takeIf { it != 0 } ?: 0xFFFF, rule.dataPrefixHex + "00"))
            RuleKind.RADIO_KIND -> scoped.copy(kind = rule.radio ?: scoped.kind)
            RuleKind.HIDDEN_SSID -> scoped.copy(kind = RadioKind.WIFI, randomized = false, hiddenSsid = true)
            RuleKind.VENDOR_IE_OUI ->
                scoped.copy(kind = RadioKind.WIFI, randomized = false, vendorIeOuis = scoped.vendorIeOuis + rule.text)
        }
    }

    private fun Sighting.withMfg(record: MfgRecord): Sighting {
        val records = facts.mfgRecords + record
        return copy(
            manufacturerId = records.first().companyId,
            manufacturerDataHex = records.first().dataHex,
            facts = facts.copy(mfgRecords = records),
        )
    }

    private fun apple(hex: String) = listOf(MfgRecord(0x004C, hex))

    val nearMisses: List<NearMiss> = listOf(
        // Samsung's company id is on every Galaxy phone and Buds, not only SmartTags.
        NearMiss(
            "Galaxy phone advertising Samsung 0x0075",
            sighting("NM:galaxy", name = "Galaxy S23", mfg = listOf(MfgRecord(0x0075, "0100020001"))),
            setOf("fleet-smarttag"), knownUntil = "P1-B1",
        ),
        NearMiss(
            "Galaxy Buds advertising Samsung 0x0075",
            sighting("NM:buds", name = "Galaxy Buds2 Pro", mfg = listOf(MfgRecord(0x0075, "42098102"))),
            setOf("fleet-smarttag"), knownUntil = "P1-B1",
        ),
        // Offline Finding (0x12) is sent by separated or powered-off iPhones and AirPods too.
        NearMiss(
            "Separated iPhone, Offline Finding only",
            sighting("NM:iphone-of", mfg = apple("1219000000000000000000000000000000000000000000000000")),
            setOf("fleet-airtag"), knownUntil = "P1-B3",
        ),
        NearMiss(
            "Powered-off AirPods, Offline Finding only",
            sighting("NM:airpods-of", mfg = apple("1219100000000000000000000000000000000000000000000000")),
            setOf("fleet-airtag"), knownUntil = "P1-B3",
        ),
        // A phone carried by the user: Continuity nearby-info, no Find My.
        NearMiss(
            "Companion iPhone (Continuity 0x10)",
            sighting("NM:iphone", mfg = apple("1005031C0F2A7B")),
            setOf("fleet-airtag"),
        ),
        // "Tattile" (plate-reader maker) contains "tile".
        NearMiss(
            "Tattile camera name",
            sighting("NM:tattile", name = "Tattile Vega"),
            setOf("fleet-tile"), knownUntil = "P1-B2",
        ),
        // Quuppa positioning tags share company id 0x00C7 with old Tile rows.
        NearMiss(
            "Quuppa tag on 0x00C7",
            sighting("NM:quuppa", mfg = listOf(MfgRecord(0x00C7, "01210000"))),
            setOf("fleet-tile"), knownUntil = "P1-B2",
        ),
        // Bare "contains" names catch everyday devices.
        NearMiss(
            "Speaker named Ravenclaw",
            sighting("NM:raven", name = "RAVENCLAW SPEAKER"),
            setOf("fleet-raven"), knownUntil = "P1-B5",
        ),
        NearMiss(
            "Home alarm named Vigilant",
            sighting("NM:vigilant", name = "Vigilant Home Alarm"),
            setOf("fleet-vigilant"), knownUntil = "P1-B5",
        ),
        NearMiss(
            "Phone hotspot named Ibrahim",
            sighting("NM:ibr", kind = RadioKind.WIFI, mac = "AA:BB:CC:00:00:09", name = "IBRAHIM-iPhone"),
            setOf("fleet-cradlepoint"), knownUntil = "P1-B5",
        ),
        // Random BLE addresses are random bytes; any of them can start with a camera vendor's OUI.
        NearMiss(
            "Random BLE address on Axon's OUI",
            sighting("NM:rand-axon", mac = mac("00:25:DF", "4A1B2C")),
            setOf("fleet-axon"), knownUntil = "P1-B8",
        ),
        NearMiss(
            "Random BLE address on Flock's OUI",
            sighting("NM:rand-flock", mac = mac("B4:1E:52", "4A1B2C")),
            setOf("fleet-flock-cameras"), knownUntil = "P1-B8",
        ),
        NearMiss(
            "Random BLE address on a LiteOn OUI",
            sighting("NM:rand-liteon", mac = mac("70:C9:4E", "4A1B2C")),
            setOf("fleet-liteon-camera-radio"), knownUntil = "P1-B8",
        ),
        NearMiss(
            "Random BLE address on Arlo's OUI",
            sighting("NM:rand-arlo", mac = mac("48:62:64", "4A1B2C")),
            setOf("fleet-arlo"), knownUntil = "P1-B8",
        ),
    )
}
