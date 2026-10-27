package app.fieldwatch.data

import app.fieldwatch.domain.Features
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-7b: the "Other sensors" layer is off, and nothing can download it from upstream. */
class SensorLayerOffTest {
    @Test
    fun otherSensorsHaveNoUrlWhileTheLayerIsOff() {
        assertFalse(Features.OTHER_SENSORS_LAYER)
        assertTrue(SensorList.OTHER.url.isBlank())
        assertFalse(SensorList.entries.any { it.url.contains("OffGridPete") })
    }

    @Test
    fun probeAndDownloadFailWithoutOpeningAConnection() {
        val dir: File = Files.createTempDirectory("sensors").toFile()
        try {
            assertEquals(FlockFiles.Probe.FAILED, FlockFiles.probe(dir, SensorList.OTHER))
            var progress = false
            assertEquals(FlockFiles.Fetch.Failed, FlockFiles.download(dir, SensorList.OTHER) { progress = true })
            assertFalse(progress)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
