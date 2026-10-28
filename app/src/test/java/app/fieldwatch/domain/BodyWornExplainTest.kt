package app.fieldwatch.domain

import app.fieldwatch.testing.Sightings.sighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1-B12b: Utility Inc holds vehicle APs (by OUI) and BodyWorn cameras (by name); the explanation follows the name. */
class BodyWornExplainTest {
    @Test
    fun aBodyWornCameraReadsAsACamera() {
        val guess = DeviceExplain.guess(sighting("B", name = "BodyWorn-4A1B"), listOf("Utility Inc"))
        assertTrue(guess.headline, guess.headline.contains("BodyWorn body camera"))
        assertEquals(DeviceExplain.Confidence.HIGH, guess.confidence)
    }

    @Test
    fun aUtilityAccessPointDoesNotMentionACamera() {
        val ap = sighting("W", kind = RadioKind.WIFI, name = "UTL-AP-12")
        val guess = DeviceExplain.guess(ap, listOf("Utility Inc"))
        assertFalse(guess.headline, guess.headline.contains("camera"))
    }
}
