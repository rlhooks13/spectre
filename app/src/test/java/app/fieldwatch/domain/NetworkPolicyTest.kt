package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    @Test
    fun nothingIsAllowedBeforeSettingsLoad() {
        for (egress in Egress.entries) assertFalse(egress.name, NetworkPolicy.allows(egress, null))
    }

    @Test
    fun onlineLookupGatesMapsAndPlaceNames() {
        val off = AppSettings(onlineLookup = false)
        assertFalse(NetworkPolicy.allows(Egress.MAP_TILES, off))
        assertFalse(NetworkPolicy.allows(Egress.PLACE_NAMES, off))
        val on = AppSettings(onlineLookup = true)
        assertTrue(NetworkPolicy.allows(Egress.MAP_TILES, on))
        assertTrue(NetworkPolicy.allows(Egress.PLACE_NAMES, on))
        // Privacy stops place names (they'd name where the user is), as the callers already did.
        assertFalse(NetworkPolicy.allows(Egress.PLACE_NAMES, on.copy(demoMode = true)))
    }

    @Test
    fun takPublishNeedsTheFeedOnAndPrivacyOffButRetractAlwaysRuns() {
        val feed = AppSettings(takEnabled = true)
        assertTrue(NetworkPolicy.allows(Egress.TAK_PUBLISH, feed))
        assertFalse(NetworkPolicy.allows(Egress.TAK_PUBLISH, feed.copy(takEnabled = false)))
        assertFalse(NetworkPolicy.allows(Egress.TAK_PUBLISH, feed.copy(demoMode = true)))
        assertTrue(NetworkPolicy.allows(Egress.TAK_RETRACT, feed.copy(takEnabled = false, demoMode = true)))
    }

    @Test
    fun catalogUpdateFollowsItsFeatureFlag() {
        assertEquals(Features.IN_APP_CATALOG_UPDATE, NetworkPolicy.allows(Egress.CATALOG_UPDATE, AppSettings()))
    }
}
