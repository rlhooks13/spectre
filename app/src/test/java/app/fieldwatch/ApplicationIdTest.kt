package app.fieldwatch

import org.junit.Assert.assertEquals
import org.junit.Test

/** P0-4e: Spectre has its own application id, so it installs next to Fieldwatch (`app.fieldwatch`). */
class ApplicationIdTest {
    @Test
    fun applicationIdIsSpectres() {
        // Unit tests run on debug and release; release has no suffix.
        val base = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
        assertEquals("io.github.rlhooks13.spectre", base)
    }
}
