package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1-B8a: the BLE address classifier and the vendor-prefix-only "possible" query, both behind a flag that is off. */
class BleAddressTest {
    private val fleets = DefaultCatalog.fleets()
    private val engine = SignatureEngine()
    private val arlo = fleets.single { it.id == "fleet-arlo" }
    private val axon = fleets.single { it.id == "fleet-axon" }

    private fun ble(mac: String, type: String? = null, seenMs: Long = 0L, fleetIds: Set<String> = emptySet()) = Sighting(
        key = "BLE:$mac", kind = RadioKind.BLE, mac = mac, name = "", rssi = -60, rssiMin = -70, rssiMax = -50,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
        manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 0L, lastSeen = seenMs,
        hitCount = 1, fleetIds = fleetIds, rssiHistory = emptyList(), presence = emptyList(),
        facts = RadioFacts(addressType = type),
    )

    @Test
    fun api35AddressTypeDecides() {
        assertEquals(BleAddressClass.PUBLIC, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Public")))
        assertEquals(BleAddressClass.STATIC, BleAddress.classify(ble("C8:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("08:62:64:4A:1B:2C", "Random")))
        // Top bits 10 are reserved for random addresses: not static.
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("88:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Anonymous")))
        // "Unknown" from the stack is no type: the heuristic decides.
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Unknown")))
    }

    /** Without a type: a rotating ecosystem means rotating; 20 minutes heard without a break means stable. */
    @Test
    fun withoutATypeEvidenceDecides() {
        val plain = ble("48:62:64:4A:1B:2C")
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(plain))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(manufacturerId = 0x004C, manufacturerDataHex = "1005031C0F2A7B")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(serviceUuids = listOf("FD5A"))))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(serviceUuids = listOf("0000FD6F-0000-1000-8000-00805F9B34FB"))))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FCB2", "01"))))))
        // Google Find Hub: FEAA frames 0x40 and 0x41. Plain Eddystone (0x10, URL) is a fixed beacon.
        for (frame in listOf("40", "41")) {
            assertEquals(frame, BleAddressClass.ROTATING, BleAddress.classify(plain.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", frame + "00"))))))
        }
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(plain.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "1000"))))))
        assertEquals(BleAddressClass.STABLE, BleAddress.classify(ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS)))
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS - 1)))
    }

    /** An Apple Continuity radio stays rotating however long it's heard; an iBeacon on 0x004C (a fixed beacon) can be stable. */
    @Test
    fun appleRotatesButAnIbeaconCanBeStable() {
        val long = ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS * 3)
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(long.copy(manufacturerId = 0x004C, manufacturerDataHex = "1005031C0F2A7B")))
        assertEquals(BleAddressClass.STABLE, BleAddress.classify(long.copy(manufacturerId = 0x004C, manufacturerDataHex = "0215" + "00".repeat(21))))
    }

    /** Stable means heard without a break: two hears 21 minutes apart are not 21 minutes of presence. */
    @Test
    fun stableNeedsUnbrokenPresence() {
        val gappy = ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS + 60_000).copy(
            presence = listOf(PresenceSpan(0L, 30_000L), PresenceSpan(BleAddress.STABLE_MS + 30_000, null)),
        )
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(gappy))
        val unbroken = gappy.copy(presence = listOf(PresenceSpan(0L, null)))
        assertEquals(BleAddressClass.STABLE, BleAddress.classify(unbroken))
    }

    /** The corpus's random-address OUI hits (P1-B8 fixtures): vendor-prefix only, so "possible" with the flag on. */
    @Test
    fun aVendorPrefixHitOnARotatingAddressIsPossible() {
        for (case in MatchCorpus.randomAddressOuiHits) {
            val fleet = fleets.single { it.id in case.mustNotMatch }
            val matched = case.sighting.copy(fleetIds = setOf(fleet.id))
            assertTrue(case.label, engine.matchedOnlyByAddress(matched, fleet))
            assertTrue(case.label, BleAddress.possibleOnly(matched, fleet, engine, enabled = true))
            assertFalse("flag off", BleAddress.possibleOnly(matched, fleet, engine, enabled = false))
        }
    }

    @Test
    fun aDefiniteAddressOrANonAddressRuleMakesItDefinite() {
        val random = ble("48:62:64:4A:1B:2C", "Random", fleetIds = setOf("fleet-arlo"))
        assertTrue(BleAddress.possibleOnly(random, arlo, engine, enabled = true))
        assertFalse("public", BleAddress.possibleOnly(random.copy(facts = RadioFacts(addressType = "Public")), arlo, engine, enabled = true))
        assertFalse("static", BleAddress.possibleOnly(random.copy(mac = "C8:62:64:4A:1B:2C"), arlo, engine, enabled = true))
        // A name rule backs the OUI up: no longer vendor-prefix only.
        val named = random.copy(name = "Arlo Pro 4")
        assertFalse(engine.matchedOnlyByAddress(named, arlo))
        assertFalse(BleAddress.possibleOnly(named, arlo, engine, enabled = true))
        // A fleet the device didn't match is never "possible".
        assertFalse(engine.matchedOnlyByAddress(random.copy(fleetIds = emptySet()), arlo))
    }

    /** STABLE isn't definite: a random address on Axon's OUI held 20 minutes on a pre-API-35 phone stays "possible". */
    @Test
    fun aStableButUntypedAddressStaysPossible() {
        val axonOui = ble("00:25:DF:4A:1B:2C", seenMs = BleAddress.STABLE_MS * 2, fleetIds = setOf("fleet-axon"))
        assertEquals(BleAddressClass.STABLE, BleAddress.classify(axonOui))
        assertTrue(BleAddress.possibleOnly(axonOui, axon, engine, enabled = true))
    }

    /** A full address a user saved from one radio names that radio; it isn't a chance vendor-prefix collision. */
    @Test
    fun aFullMacRuleBacksTheHitUp() {
        val device = ble("48:62:64:4A:1B:2C", "Random")
        val mine = engine.suggestFleet(device).let { it.copy(rules = it.rules.filter { r -> r.kind == RuleKind.MAC_PREFIX }) }
        val matched = device.copy(fleetIds = setOf(mine.id))
        assertFalse(engine.matchedOnlyByAddress(matched, mine))
        assertFalse(BleAddress.possibleOnly(matched, mine, engine, enabled = true))
    }

    /** An all-rules signature made only of vendor-prefix rules (and a radio kind) is vendor-prefix only too. */
    @Test
    fun anAllRulesOuiSignatureCounts() {
        val fleet = Fleet(id = "user-oui", name = "OUI only", matchAny = false, rules = listOf(
            MatchRule(RuleKind.RADIO_KIND, radio = RadioKind.BLE), MatchRule(RuleKind.OUI, text = "48:62:64"),
        ))
        assertTrue(engine.matchedOnlyByAddress(ble("48:62:64:4A:1B:2C", "Random", fleetIds = setOf("user-oui")), fleet))
    }

    /** A known address type isn't replaced by a later "Unknown" from the stack. */
    @Test
    fun aKnownAddressTypeSurvivesALaterUnknown() {
        assertEquals("Random", RadioFacts(addressType = "Random").merge(RadioFacts(addressType = "Unknown")).addressType)
        assertEquals("Public", RadioFacts(addressType = "Unknown").merge(RadioFacts(addressType = "Public")).addressType)
        assertEquals("Random", RadioFacts(addressType = "Random").merge(RadioFacts()).addressType)
    }

    /** P1-B8b: after a match, a vendor-prefix-only hit on a rotating address is "possible", and it gets no OUI vendor. */
    @Test
    fun aReadingMarksPossibleMatchesAndDropsTheVendorOnARotatingAddress() {
        val byId = fleets.associateBy { it.id }
        val random = ble("48:62:64:4A:1B:2C", "Random", fleetIds = setOf("fleet-arlo")).copy(vendor = "Arlo Technologies")
        val read = BleAddress.reading(random, byId, engine, enabled = true)
        assertEquals(setOf("fleet-arlo"), read.possibleFleetIds)
        assertEquals(null, read.vendor)
        // A public address keeps both: the hit is definite and the OUI is the vendor.
        val public = random.copy(facts = RadioFacts(addressType = "Public"))
        val definite = BleAddress.reading(public, byId, engine, enabled = true)
        assertTrue(definite.possibleFleetIds.isEmpty())
        assertEquals("Arlo Technologies", definite.vendor)
        // A static random address is definite too, so it keeps its vendor, matching its definite hit.
        val static = random.copy(mac = "C8:62:64:4A:1B:2C")
        val staticRead = BleAddress.reading(static, byId, engine, enabled = true)
        assertTrue(staticRead.possibleFleetIds.isEmpty())
        assertEquals("Arlo Technologies", staticRead.vendor)
        // Flag off: unchanged, and a possible set left from when it was on is cleared.
        assertTrue(BleAddress.reading(random, byId, engine, enabled = false) === random)
        assertTrue(BleAddress.reading(read, byId, engine, enabled = false).possibleFleetIds.isEmpty())
    }

    /** The store's per-advertisement vendor skip: a rotating address loses its OUI vendor whether or not anything matched. */
    @Test
    fun noVendorOnARotatingAddressAtTheSource() {
        val rotating = ble("48:62:64:4A:1B:2C", "Random").copy(vendor = "Arlo Technologies")
        assertEquals(null, BleAddress.withoutRotatingVendor(rotating, enabled = true).vendor)
        val static = rotating.copy(mac = "C8:62:64:4A:1B:2C")
        assertTrue(BleAddress.withoutRotatingVendor(static, enabled = true) === static)
        val public = rotating.copy(facts = RadioFacts(addressType = "Public"))
        assertTrue(BleAddress.withoutRotatingVendor(public, enabled = true) === public)
        assertTrue(BleAddress.withoutRotatingVendor(rotating, enabled = false) === rotating)
    }

    /** A "possible" match gets no attention card and doesn't raise a signature watch. */
    @Test
    fun aPossibleMatchHasNoAttentionCardAndNoAlert() {
        val axonHit = ble("00:25:DF:4A:1B:2C", "Random", fleetIds = setOf("fleet-axon"))
        assertTrue(axonHit.attentionNotes(fleets).isNotEmpty())
        val possible = axonHit.copy(possibleFleetIds = setOf("fleet-axon"))
        assertTrue(possible.attentionNotes(fleets).isEmpty())
        val watch = WatchTarget(id = "watch-fleet-axon", fleetId = "fleet-axon", label = "Axon")
        assertTrue(app.fieldwatch.alert.shouldRaiseWatch(watch, axonHit, emptySet()))
        assertFalse(app.fieldwatch.alert.shouldRaiseWatch(watch, possible, emptySet()))
        assertFalse(TakPublish.hasAttention(possible, fleets))
    }

    @Test
    fun theFlagIsOff() {
        assertFalse(Features.BLE_ADDRESS_CLASSIFIER)
        assertFalse(BleAddress.possibleOnly(ble("48:62:64:4A:1B:2C", "Random", fleetIds = setOf("fleet-arlo")), arlo, engine))
    }
}
