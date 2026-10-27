package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * P1-B4: a matched fleet's class decides FINDER, not words in its name, and a carried Apple phone, Mac or AirPods is
 * [TrackerMatch.Kind.PHONE], never a finder.
 */
class TrackerMatchTest {
    private val fleets = DefaultCatalog.fleets()
    private val names = fleets.associate { it.id to it.name }
    private val classes = fleets.associate { it.id to it.kind }

    private fun kind(device: Sighting) = TrackerMatch.kind(device, names, classes)

    @Test
    fun aLoudCarriedIphoneIsAPhone() {
        val iphone = apple("1005031C0F2A7B", fleetIds = setOf("fleet-apple-device"))
        assertEquals(TrackerMatch.Kind.PHONE, kind(iphone))
        assertEquals("Apple BLE (phone / Continuity)", TrackerMatch.label(iphone, names, classes))
    }

    /** P1-B3 routes an Offline Finding record of type "Apple device" to Apple Device; that radio isn't a finder. */
    @Test
    fun anIphoneSendingOfflineFindingIsAPhone() {
        val iphone = apple("121900" + "00".repeat(24), fleetIds = setOf("fleet-apple-device"))
        assertEquals(TrackerMatch.Kind.PHONE, kind(iphone))
    }

    @Test
    fun aQuietAppleDeviceIsNothing() {
        val far = apple("1005031C0F2A7B", fleetIds = setOf("fleet-apple-device")).copy(rssiMax = -80, rssiMin = -90)
        assertEquals(null, kind(far))
    }

    @Test
    fun aFinderClassIsAFinderWhateverItsName() {
        val bike = apple("", fleetIds = setOf("bike")).copy(manufacturerId = null)
        val custom = mapOf("bike" to "Bike") to mapOf("bike" to SignatureClass.FINDER)
        assertEquals(TrackerMatch.Kind.FINDER, TrackerMatch.kind(bike, custom.first, custom.second))
        assertEquals("Bike", TrackerMatch.label(bike, custom.first, custom.second))
    }

    @Test
    fun aFinderWordInANonFinderNameIsNotAFinder() {
        val camera = apple("", fleetIds = setOf("fleet-tattile")).copy(manufacturerId = null, rssiMax = -80, rssiMin = -90)
        assertNotEquals(TrackerMatch.Kind.FINDER, kind(camera))
    }

    @Test
    fun anAirTagIsAFinder() {
        assertEquals(TrackerMatch.Kind.FINDER, kind(apple("121910" + "00".repeat(24), fleetIds = setOf("fleet-airtag"))))
    }

    /** With no signature at all (the Find My device rule switched off), a tag record still reads as a finder. */
    @Test
    fun anUnlabeledOfflineFindingTagIsAFinderButAnUnlabeledIphoneIsNot() {
        assertEquals(TrackerMatch.Kind.FINDER, kind(apple("121910" + "00".repeat(24), fleetIds = emptySet())))
        assertEquals(TrackerMatch.Kind.FINDER, kind(apple("1219", fleetIds = emptySet())))
        assertEquals(TrackerMatch.Kind.PHONE, kind(apple("121900" + "00".repeat(24), fleetIds = emptySet())))
        assertEquals(TrackerMatch.Kind.PHONE, kind(apple("121930" + "00".repeat(24), fleetIds = emptySet())))
    }

    private fun apple(hex: String, fleetIds: Set<String>) = Sighting(
        key = "BLE:4A:11:22:33:44:55",
        kind = RadioKind.BLE,
        mac = "4A:11:22:33:44:55",
        name = "",
        rssi = -45,
        rssiMin = -50,
        rssiMax = -40,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = 0x004C,
        manufacturerDataHex = hex,
        rawHex = "",
        extras = "",
        firstSeen = 0L,
        lastSeen = 0L,
        hitCount = 1,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
