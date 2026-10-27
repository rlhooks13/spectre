package app.fieldwatch.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The license the app shows must be the repository's LICENSE, upstream notice included. */
class LicenseTextTest {
    @Test
    fun inAppLicenseMatchesTheRepositoryLicense() {
        val file = listOf(File("LICENSE"), File("../LICENSE")).first { it.isFile }
        val lines = file.readText(Charsets.UTF_8).replace("\r\n", "\n").lines()
        assertEquals("MIT License", lines.first().trim())
        val body = lines.drop(1).joinToString("\n")
        assertEquals(normalize(body), normalize(SpectreDisclaimer.LICENSE_BODY))
    }

    @Test
    fun upstreamCopyrightIsKept() {
        assertTrue(SpectreDisclaimer.LICENSE_BODY.startsWith("Copyright (c) 2026 Off Grid Pete LLC\n"))
        assertTrue(SpectreDisclaimer.LICENSE_BODY.contains("Copyright (c) 2026 the Spectre contributors"))
    }

    @Test
    fun anOlderAcceptanceMustAcceptTheSpectreDisclaimerAgain() {
        assertEquals(4, DISCLAIMER_REV)
        assertFalse(AppSettings(disclaimerAccepted = true, disclaimerRev = 3).disclaimerOk())
        assertTrue(AppSettings(disclaimerAccepted = true, disclaimerRev = DISCLAIMER_REV).disclaimerOk())
    }

    /** Paragraphs compared word for word; line wrapping inside a paragraph is ignored. */
    private fun normalize(text: String): List<String> =
        text.trim().split(Regex("\n\\s*\n")).map { it.trim().split(Regex("\\s+")).joinToString(" ") }
}
