package app.fieldwatch.domain

import app.fieldwatch.data.CatalogRemote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiveOnlyCopyTest {
    @Test
    fun permissionCopyDoesNotClaimNothingIsTransmitted() {
        val line = EnglishCopy.byKey.getValue("ui.permissions.receive.only").text
        assertTrue(line.contains("never connects, pairs, or advertises"))
        assertTrue(line.contains("probe and scan requests"))
        val claims = Regex("(?i)nothing is transmitted|transmits nothing|passively watch")
        assertFalse(EnglishCopy.lines.any { claims.containsMatchIn(it.text) })
    }

    @Test
    fun inAppCatalogUpdateStaysOffUntilPhase4() {
        assertFalse(Features.IN_APP_CATALOG_UPDATE)
        assertFalse(CatalogRemote.STOCK_PACK_URL.contains("OffGridPete"))
        assertTrue(CatalogRemote.STOCK_PACK_URL.startsWith("${Brand.REPO_URL.replace("github.com", "raw.githubusercontent.com")}/"))
    }
}
