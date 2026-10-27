package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    // The settings ladder used to force these back on (V19 tagLocation; V20 keepScreenOn and onlineLookup)
    // and to re-enable every built-in signature the user turned off (V17). Those steps are gone.
    @Test
    fun ladderNoLongerForcesSettingsOrSignaturesBackOn() {
        val source = repoFile("src/main/java/app/fieldwatch/data/ConfigStore.kt").readText()
        assertFalse(source.contains("tagLocation = true"))
        assertFalse(source.contains("onlineLookup = true"))
        assertFalse(source.contains("if (fleet.builtIn && !fleet.enabled) fleet.copy(enabled = true)"))
    }

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
