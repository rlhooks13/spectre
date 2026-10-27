package app.fieldwatch.testing

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.PersistedConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

/** Ladder tests: the real Fieldwatch 1.1.32 config (P1-A3's fixture), optionally edited, loaded by today's ConfigStore. */
object ConfigUpgrade {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun fieldwatch1132(): PersistedConfig =
        json.decodeFromString(PersistedConfig.serializer(), TestFiles.repoFile("src/test/resources/fixtures/fieldwatch-1.1.32/config.json").readText())

    /** [old] written as a config file and loaded the way the app loads it. Callers clear NetworkPolicy.settings after. */
    fun load(old: PersistedConfig = fieldwatch1132()): PersistedConfig {
        val dir = Files.createTempDirectory("ladder").toFile()
        try {
            val file = File(dir, "config.json")
            file.writeText(json.encodeToString(PersistedConfig.serializer(), old))
            val store = ConfigStore(file)
            runBlocking { store.load() }
            return store.config.value
        } finally {
            dir.deleteRecursively()
        }
    }
}
