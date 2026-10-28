package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1-B8a: the BLE address classifier and the address-only "possible" query, both behind a flag that is off. */
class BleAddressTest {
    private val fleets = DefaultCatalog.fleets()
    private val engine = SignatureEngine()
    private val arlo = fleets.single { it.id == "fleet-arlo" }

    private fun ble(mac: String, type: String? = null, seenMs: Long = 0L) = Sighting(
        key = "BLE:$mac", kind = RadioKind.BLE, mac = mac, name = "", rssi = -60, rssiMin = -70, rssiMax = -50,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
        manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 0L, lastSeen = seenMs,
        hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
        facts = RadioFacts(addressType = type),
    )

    @Test
    fun api35AddressTypeDecides() {
        assertEquals(BleAddressClass.PUBLIC, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Public")))
        assertEquals(BleAddressClass.STATIC, BleAddress.classify(ble("C8:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("08:62:64:4A:1B:2C", "Random")))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(ble("48:62:64:4A:1B:2C", "Anonymous")))
    }

    /** Without addressType: a rotating ecosystem means rotating; 20 minutes on one address means stable; else unknown. */
    @Test
    fun withoutATypeEvidenceDecides() {
        val plain = ble("48:62:64:4A:1B:2C")
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(plain))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(manufacturerId = 0x004C)))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(serviceUuids = listOf("FD5A"))))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(serviceUuids = listOf("0000FD6F-0000-1000-8000-00805F9B34FB"))))
        assertEquals(BleAddressClass.ROTATING, BleAddress.classify(plain.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FCB2", "40"))))))
        assertEquals(BleAddressClass.STABLE, BleAddress.classify(ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS)))
        assertEquals(BleAddressClass.UNKNOWN, BleAddress.classify(ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS - 1)))
    }

    /** The corpus's random-address OUI hits (P1-B8 fixtures): address-only, so "possible" with the flag on. */
    @Test
    fun anAddressOnlyHitOnARotatingAddressIsPossible() {
        for (case in MatchCorpus.randomAddressOuiHits) {
            val fleet = fleets.single { it.id in case.mustNotMatch }
            assertTrue(case.label, engine.matchedOnlyByAddress(case.sighting, fleet))
            assertTrue(case.label, BleAddress.possibleOnly(case.sighting, fleet, engine, enabled = true))
            assertFalse("flag off", BleAddress.possibleOnly(case.sighting, fleet, engine, enabled = false))
        }
    }

    @Test
    fun aStableAddressOrANonAddressRuleMakesItDefinite() {
        val random = ble("48:62:64:4A:1B:2C", "Random")
        assertTrue(BleAddress.possibleOnly(random, arlo, engine, enabled = true))
        assertFalse("public", BleAddress.possibleOnly(random.copy(facts = RadioFacts(addressType = "Public")), arlo, engine, enabled = true))
        assertFalse("seen 20 min, no type", BleAddress.possibleOnly(ble("48:62:64:4A:1B:2C", seenMs = BleAddress.STABLE_MS), arlo, engine, enabled = true))
        // A name rule backs the OUI up: no longer address-only.
        val named = random.copy(name = "Arlo Pro 4")
        assertFalse(engine.matchedOnlyByAddress(named, arlo))
        assertFalse(BleAddress.possibleOnly(named, arlo, engine, enabled = true))
    }

    @Test
    fun theFlagIsOff() {
        assertFalse(Features.BLE_ADDRESS_CLASSIFIER)
        assertFalse(BleAddress.possibleOnly(ble("48:62:64:4A:1B:2C", "Random"), arlo, engine))
    }
}
