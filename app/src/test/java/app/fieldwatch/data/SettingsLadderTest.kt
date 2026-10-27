package app.fieldwatch.data

import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** An upgrade keeps the user's choices (P1-A7, ADR-0014): the old ladder forced these back on. */
class SettingsLadderTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    @Test
    fun anOldConfigKeepsNetworkAndScreenChoicesOff() {
        val dir = Files.createTempDirectory("ladder").toFile()
        val file = File(dir, "config.json")
        val seeded = runBlocking { ConfigStore(file).also { it.load() }.config.value }
        val old = seeded.copy(
            version = 16,
            settings = seeded.settings.copy(onlineLookup = false, tagLocation = false, keepScreenOn = false),
        )
        file.writeText(json.encodeToString(PersistedConfig.serializer(), old))

        val store = ConfigStore(file)
        runBlocking { store.load() }
        val settings = store.config.value.settings
        assertFalse(settings.onlineLookup)
        assertFalse(settings.tagLocation)
        assertFalse(settings.keepScreenOn)
        assertEquals(ConfigStore.CATALOG_VERSION, store.config.value.version)
        // The policy sees exactly what the store loaded.
        assertSame(settings, NetworkPolicy.settings)
        dir.deleteRecursively()
    }
}
