package app.fieldwatch.domain

import app.fieldwatch.testing.Sightings.sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-B11c (ADR-0020): FD44, Apple's Find My accessory service, labels "Find My device", never AirTags on its own, and
 * a radio that sends it is a finder whatever its Offline Finding status byte says, as it was under AirTags.
 */
class FindMyAccessoryServiceTest {
    private val fleets = DefaultCatalog.fleets()
    private val names = fleets.associate { it.id to it.name }
    private val classes = TrackerMatch.classesOf(fleets)

    private fun offlineFinding(status: Int) = MfgRecord(0x004C, "1219" + "%02X".format(status) + "00".repeat(24))

    private fun matched(s: Sighting): Sighting = s.copy(fleetIds = SignatureEngine().match(listOf(s), fleets).getValue(s.key))

    private fun fd44(key: String, status: Int?) = sighting(key, serviceUuids = listOf("FD44")).let { s ->
        if (status == null) s else s.copy(facts = s.facts.copy(mfgRecords = listOf(offlineFinding(status))))
    }

    @Test
    fun fd44IsAFinderOnEveryStatusType() {
        for ((key, status) in listOf("none" to null, "device" to 0x00, "tag" to 0x20, "airpods" to 0x30)) {
            val s = matched(fd44(key, status))
            assertEquals(key, setOf(SignatureEngine.FIND_MY), s.fleetIds)
            assertEquals(key, TrackerMatch.Kind.FINDER, TrackerMatch.kind(s, names, classes))
        }
    }

    @Test
    fun anAirTagStatusStillSaysAirTag() {
        val s = matched(fd44("airtag", 0x10))
        assertEquals(setOf("fleet-airtag"), s.fleetIds)
        assertEquals(TrackerMatch.Kind.FINDER, TrackerMatch.kind(s, names, classes))
    }

    /** Without FD44, AirPods' record still routes to Apple audio and isn't a finder (P1-B3, unchanged). */
    @Test
    fun airPodsWithoutFd44AreUnchanged() {
        val s = matched(sighting("plain").let { it.copy(facts = it.facts.copy(mfgRecords = listOf(offlineFinding(0x30)))) })
        assertTrue("fleet-apple-audio" in s.fleetIds)
        assertFalse(SignatureEngine.FIND_MY in s.fleetIds)
    }

    @Test
    fun theServiceIsReadFromServiceDataAndTheLongForm() {
        assertTrue(TrackerMatch.sendsFindMyAccessoryService(sighting("sd", serviceData = listOf(ServiceDataRecord("FD44", "00")))))
        assertTrue(TrackerMatch.sendsFindMyAccessoryService(sighting("long", serviceUuids = listOf("0000FD44-0000-1000-8000-00805F9B34FB"))))
        assertFalse(TrackerMatch.sendsFindMyAccessoryService(sighting("other", serviceUuids = listOf("FD5A"))))
    }
}
