package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BrandTest {
    @Test
    fun userAgentNamesTheForkAndItsRepo() {
        assertEquals("Spectre/0.1.0 (+https://github.com/rlhooks13/spectre)", Brand.userAgent("0.1.0"))
        assertFalse(Brand.userAgent("0.1.0").contains("OffGridPete"))
    }

    @Test
    fun displayNamesChangeButStableIdsKeepTheirLegacyValues() {
        assertEquals("Spectre-AB23", TakInstall.callsign("", "AB23"))
        assertEquals("FIELDWATCH-SELF", TakInstall.LEGACY_SELF_UID)
        assertEquals("fieldwatch-settings", SettingsPack.FORMAT)
        assertEquals("fieldwatch-signatures", SignaturePack.FORMAT)
        assertEquals("spectre-map-20260101-000000.gpx", GeoExport.suggestedName(GeoExport.Format.GPX, "20260101-000000"))
    }
}
