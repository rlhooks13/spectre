package app.fieldwatch.domain

import app.fieldwatch.data.CatalogRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiveOnlyCopyTest {
    @Test
    fun permissionCopyDoesNotClaimNothingIsTransmitted() {
        val line = EnglishCopy.byKey.getValue("ui.permissions.receive.only").text
        assertTrue(line.contains("never joins Wi-Fi networks"))
        assertTrue(line.contains("probe and scan requests"))
        assertEquals("Wi-Fi and Bluetooth scan status", EnglishCopy.byKey.getValue("ui.scan.status.channel").text)
        // "A passive observer" in the debrief describes a third party, not this app.
        val claims = Regex("(?i)nothing is transmitted|transmits nothing|\\bpassiv(e|ely)\\b(?! observer)")
        assertEquals(emptyList<String>(), EnglishCopy.lines.filter { claims.containsMatchIn(it.text) }.map { it.key })
    }

    @Test
    fun inAppCatalogUpdateStaysOffUntilPhase4() {
        // P4-6 turns this on only together with a signed-pack check; replace this assert with that test.
        assertFalse(Features.IN_APP_CATALOG_UPDATE)
        assertFalse(EnglishCopy.byKey.getValue("ui.export.shares.catalog.no.update").text.contains("GitHub"))
        assertFalse(CatalogRemote.STOCK_PACK_URL.contains("OffGridPete"))
        assertTrue(CatalogRemote.STOCK_PACK_URL.startsWith("${Brand.REPO_URL.replace("github.com", "raw.githubusercontent.com")}/"))
    }
}
