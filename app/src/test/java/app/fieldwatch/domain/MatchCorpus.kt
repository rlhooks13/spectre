package app.fieldwatch.domain

import app.fieldwatch.testing.Sightings.mac
import app.fieldwatch.testing.Sightings.sighting

/**
 * The match corpus (P1-A4). Every catalog fix in Phase 1B is checked against it.
 *
 * - [positives]: generated from each built-in signature's rules, one sighting per enabled rule (all rules
 *   in one sighting for an all-rules signature). An unscoped OUI or MAC prefix also gets a public BLE
 *   address, since the engine indexes it for BLE too. Each must hit its signature.
 * - [allRulesNegatives]: for an all-rules signature, every rule but one; none may hit.
 * - [targets]: hand-written real devices each confuser's signature exists for. A fix must keep them hitting.
 * - [nearMisses]: hand-written confusers only. Each lists the signatures it must not hit.
 * - [randomAddressOuiHits]: P1-B8's fixtures; matching still hits them by design.
 *
 * A case that is wrong today names the PLAN step that fixes it in `knownUntil`. The test fails when it
 * becomes right, so the marker comes off in that step's PR.
 */
object MatchCorpus {
    data class Positive(val fleetId: String, val ruleIndex: Int, val sighting: Sighting, val variant: String = "") {
        val id: String get() = "$fleetId#$ruleIndex$variant"
    }

    data class NearMiss(
        val label: String,
        val sighting: Sighting,
        val mustNotMatch: Set<String>,
        val knownUntil: String? = null,
        /** Also must not be labeled a tracker (FINDER) through those signatures' names. */
        val checkFinder: Boolean = false,
        /**
         * The tracker label is still wrong until this step, even once the signature no longer matches (a name-token
         * label, P1-B4). The test fails when it becomes right, so the marker comes off in that step's PR.
         */
        val finderKnownUntil: String? = null,
    )

    /** A real device that must hit [fleetId]. With [knownUntil], it misses today and that step adds it. */
    data class Target(val label: String, val sighting: Sighting, val fleetId: String, val knownUntil: String? = null)

    /** No catalog row may use this; SERVICE_DATA positives with no UUID put their payload under it. */
    const val TEST_UUID = "5A3C1E00-7E57-4C0D-9E00-C0FFEE000001"

    /** Signatures that only hit in groups (peers, OUI clusters, sequential MACs) aren't single-sighting cases. */
    fun clustered(fleet: Fleet): Boolean = fleet.minPeers > 0 || fleet.clusterByOui || fleet.sequentialMac

    private fun bothRadios(rule: MatchRule) = rule.radio == null && (rule.kind == RuleKind.OUI || rule.kind == RuleKind.MAC_PREFIX)

    fun positives(fleets: List<Fleet> = DefaultCatalog.fleets()): List<Positive> = fleets
        .filterNot(::clustered)
        .flatMap { fleet ->
            val rules = fleet.rules.withIndex().filter { it.value.enabled }
            if (!fleet.matchAny) {
                val all = rules.fold(sighting("POS:${fleet.id}:all")) { s, r -> satisfy(r.value, s) }
                listOf(Positive(fleet.id, -1, all))
            } else {
                rules.flatMap { (i, rule) ->
                    val wifi = Positive(fleet.id, i, satisfy(rule, sighting("POS:${fleet.id}:$i")))
                    if (!bothRadios(rule)) {
                        listOf(wifi)
                    } else {
                        val ble = sighting("POS:${fleet.id}:$i:ble", mac = mac(rule.text)).let {
                            it.copy(randomized = false, facts = it.facts.copy(addressType = "Public"))
                        }
                        listOf(wifi, Positive(fleet.id, i, ble, ":ble"))
                    }
                }
            }
        }

    /** How many [positives] there must be: a dropped rule or signature changes it. */
    fun expectedPositiveCount(fleets: List<Fleet> = DefaultCatalog.fleets()): Int = fleets
        .filterNot(::clustered)
        .sumOf { fleet ->
            val rules = fleet.rules.filter { it.enabled }
            if (!fleet.matchAny) 1 else rules.size + rules.count(::bothRadios)
        }

    /** For each all-rules signature, one sighting per rule that satisfies every other rule. */
    fun allRulesNegatives(fleets: List<Fleet> = DefaultCatalog.fleets()): List<Positive> = fleets
        .filter { !it.matchAny && !clustered(it) }
        .flatMap { fleet ->
            val rules = fleet.rules.withIndex().filter { it.value.enabled }
            rules.map { (skip, _) ->
                val others = rules.filter { it.index != skip }
                Positive(fleet.id, skip, others.fold(sighting("NEG:${fleet.id}:$skip")) { s, r -> satisfy(r.value, s) })
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
                        ServiceDataRecord(rule.text.ifBlank { TEST_UUID }, rule.dataPrefixHex + "00"),
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

    private fun apple(vararg hex: String) = hex.map { MfgRecord(0x004C, it) }

    /**
     * An Offline Finding record: type 0x12, length 0x19 (25 bytes) = status, 22-byte key, key bits, hint.
     * Status bits 4-5 carry the device type as AirGuard decodes it: 0x00 Apple device (iPhone, Mac),
     * 0x10 AirTag, 0x20 Find My accessory, 0x30 AirPods. This follows AirGuard's decoder; no Spectre capture
     * confirms it yet (a field-capture request in PROGRESS.md asks for one).
     */
    private fun offlineFinding(status: Int) = "1219" + "%02X".format(status) + "00".repeat(24)

    /** The near-owner form: type 0x12, length 0x02 = status, one more byte. Same status bits. */
    private fun offlineFindingNearOwner(status: Int) = "1202" + "%02X".format(status) + "00"

    /** The real devices behind each confuser's signature. Pinned by hand, so a rule fix can't drop them. */
    val targets: List<Target> = listOf(
        Target("AirTag, Offline Finding status 0x10", sighting("T:airtag", mfg = apple(offlineFinding(0x10))), "fleet-airtag"),
        // An AirTag's other status bits (battery level in bits 6-7) don't change its type.
        Target("AirTag with low battery, status 0xD0", sighting("T:airtag-lowbatt", mfg = apple(offlineFinding(0xD0))), "fleet-airtag"),
        // A third-party Find My accessory (type 0x20) is still a finder, under its own name.
        Target("Find My accessory, status 0x20", sighting("T:findmy-accessory", mfg = apple(offlineFinding(0x20))), "fleet-findmy"),
        // A record too short to read the status byte: type unknown, still a finder.
        Target("Offline Finding record too short to read", sighting("T:findmy-short", mfg = apple("1219")), "fleet-findmy"),
        Target("AirTag near its owner, short Offline Finding form", sighting("T:airtag-near-owner", mfg = apple(offlineFindingNearOwner(0x10))), "fleet-airtag"),
        Target(
            "SmartTag on FD5A",
            sighting("T:smarttag", serviceUuids = listOf("FD5A"), serviceData = listOf(ServiceDataRecord("FD5A", "1000"))),
            "fleet-smarttag",
        ),
        // Since P1-B1, FD5A is SmartTag's only non-name signal, so pin the form where it comes as service data alone
        // (no service-UUID list). A tag still in setup (Easy Setup on 0x0075 only, not yet registered) can't track
        // anyone and is no longer labeled.
        Target(
            "SmartTag with FD5A in service data only",
            sighting("T:smarttag-svcdata", serviceData = listOf(ServiceDataRecord("FD5A", "1000"))),
            "fleet-smarttag",
        ),
        Target("Tile on FEED", sighting("T:tile-feed", serviceUuids = listOf("FEED")), "fleet-tile"),
        Target(
            "Tile on its own company id 0x067C",
            sighting("T:tile-067c", mfg = listOf(MfgRecord(0x067C, "0100"))),
            "fleet-tile",
        ),
        Target("Tile advertising its name over BLE", sighting("T:tile-name", name = "Tile"), "fleet-tile"),
        Target("Tile with its name in capitals", sighting("T:tile-caps", name = "TILE"), "fleet-tile"),
        // Since P1-B2 the name must be exactly Tile; a "Tile Mate" is found through its service UUID instead.
        // (A "Tile Mate" advertising no FEED, FEEC or 0x067C at all is no longer labeled, by design.)
        Target("Tile Mate with FEED", sighting("T:tile-mate", name = "Tile Mate", serviceUuids = listOf("FEED")), "fleet-tile"),
        Target(
            "Axon Body camera, BLE on its public OUI",
            sighting("T:axon", mac = mac("00:25:DF", "4A1B2C")).let {
                it.copy(randomized = false, facts = it.facts.copy(addressType = "Public"))
            },
            "fleet-axon",
        ),
        Target(
            "Flock camera Wi-Fi BSSID",
            sighting("T:flock", kind = RadioKind.WIFI, mac = mac("B4:1E:52", "0A0B0C"), name = "Flock-1A2B"),
            "fleet-flock-cameras",
        ),
        Target("Tattile camera name", sighting("T:tattile", name = "Tattile Vega"), "fleet-tattile"),
        Target("ShotSpotter sensor name", sighting("T:raven", name = "ShotSpotter"), "fleet-raven"),
        Target("Motorola Vigilant name", sighting("T:vigilant", name = "Motorola Vigilant"), "fleet-vigilant"),
        Target(
            "Cradlepoint IBR900 router",
            sighting("T:cradlepoint", kind = RadioKind.WIFI, mac = "AA:BB:CC:00:00:0A", name = "IBR900-1A2"),
            "fleet-cradlepoint",
        ),
    )

    val nearMisses: List<NearMiss> = listOf(
        // Samsung's company id is on every Galaxy phone and Buds, not only SmartTags.
        NearMiss(
            "Galaxy phone advertising Samsung 0x0075",
            sighting("NM:galaxy", name = "Galaxy S23", mfg = listOf(MfgRecord(0x0075, "0100020001"))),
            setOf("fleet-smarttag"), checkFinder = true,
        ),
        NearMiss(
            "Galaxy Buds advertising Samsung 0x0075",
            sighting("NM:buds", name = "Galaxy Buds2 Pro", mfg = listOf(MfgRecord(0x0075, "42098102"))),
            setOf("fleet-smarttag"), checkFinder = true,
        ),
        // Offline Finding (0x12) is sent by separated or powered-off iPhones and AirPods too.
        NearMiss(
            "Separated iPhone, Offline Finding only",
            sighting("NM:iphone-of", mfg = apple(offlineFinding(0x00))),
            // An Apple device (P1-B3), and not a finder: TrackerMatch goes by the matched class (P1-B4).
            setOf("fleet-airtag", "fleet-findmy"), checkFinder = true,
        ),
        NearMiss(
            "Powered-off AirPods, Offline Finding only",
            sighting("NM:airpods-of", mfg = apple(offlineFinding(0x30))),
            // Apple audio (P1-B3), and not a finder (P1-B4).
            setOf("fleet-airtag", "fleet-findmy"), checkFinder = true,
        ),
        // A phone carried by the user sends Continuity nearby-info next to Offline Finding. The Continuity
        // record is what keeps it off AirTag today (dropAirTagsWhenAppleDevice), so this exercises that filter.
        NearMiss(
            "Companion iPhone (Continuity 0x10 with Offline Finding)",
            sighting("NM:iphone", mfg = apple("1005031C0F2A7B", offlineFinding(0x00))),
            setOf("fleet-airtag", "fleet-findmy"),
        ),
        // The same phone with an Offline Finding record too short to read its type: Continuity still says Apple product.
        NearMiss(
            "Companion iPhone with an unreadable Offline Finding record",
            sighting("NM:iphone-short-of", mfg = apple("1005031C0F2A7B", "1219")),
            setOf("fleet-airtag", "fleet-findmy"),
        ),
        // "Tattile" (plate-reader maker) contains "tile".
        NearMiss(
            "Tattile camera name",
            sighting("NM:tattile", name = "Tattile Vega"),
            // No longer matches Tile (P1-B2), and "tile" in the name no longer reads as a tracker (P1-B4).
            setOf("fleet-tile"), checkFinder = true,
        ),
        // Quuppa positioning tags share company id 0x00C7 with old Tile rows.
        NearMiss(
            "Quuppa tag on 0x00C7",
            sighting("NM:quuppa", mfg = listOf(MfgRecord(0x00C7, "01210000"))),
            setOf("fleet-tile"), checkFinder = true,
        ),
        // Tile's name rule is BLE-only: a Wi-Fi network someone named "Tile" isn't a tracker.
        NearMiss(
            "Wi-Fi network named Tile",
            sighting("NM:tile-ssid", kind = RadioKind.WIFI, mac = "AA:BB:CC:00:00:0B", name = "Tile"),
            setOf("fleet-tile"), checkFinder = true,
        ),
        // Bare vendor ids (ADR-0015). Unnamed, so only the company id can match.
        NearMiss(
            "Unnamed DJI accessory on 0x08AA (a mic or action camera, not a drone)",
            sighting("NM:dji", mfg = listOf(MfgRecord(0x08AA, "0100"))),
            setOf("fleet-dji"), knownUntil = "P1-B10",
        ),
        NearMiss(
            "Unnamed non-Fitbit Google LLC device on 0x018E",
            sighting("NM:google-018e", mfg = listOf(MfgRecord(0x018E, "0100"))),
            setOf("fleet-fitbit"), knownUntil = "P1-B10",
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
    )

    /**
     * Random BLE addresses whose first bytes collide with a camera vendor's OUI. Matching hits them by design:
     * P1-B8 (flagged) keeps the hit but shows an OUI-only hit on a random address as "possible", with no
     * attention card or alert. So these are P1-B8's fixtures, and today's test asserts they still hit.
     * A random address's top two bits are 00 (non-resolvable) or 01 (resolvable), or 11 for a static random
     * address. Flock's B4:1E:52 starts 10, which is reserved, so no random address collides with it.
     */
    val randomAddressOuiHits: List<NearMiss> = listOf(
        randomOn("Axon", "00:25:DF", "fleet-axon"), // 00: non-resolvable
        randomOn("LiteOn", "70:C9:4E", "fleet-liteon-camera-radio"), // 01: resolvable
        randomOn("Arlo", "48:62:64", "fleet-arlo"), // 01: resolvable
    )

    private fun randomOn(vendor: String, oui: String, fleet: String) = NearMiss(
        "Random BLE address on $vendor's OUI",
        // randomized as production computes it (MacUtil reads the Wi-Fi local bit, so false here); the
        // random-address signal P1-B8 reads is addressType.
        sighting("RND:$vendor", mac = mac(oui, "4A1B2C")).let {
            it.copy(randomized = MacUtil.isRandomized(it.mac), facts = it.facts.copy(addressType = "Random"))
        },
        setOf(fleet),
        knownUntil = "P1-B8",
    )
}
