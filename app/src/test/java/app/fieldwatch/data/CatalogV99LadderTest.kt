package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.testing.TestFiles
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * P1-B2, catalog v99: Tile matches on its own company id (0x067C) and a BLE-only exact name "Tile". It no longer
 * matches on Quuppa's 0x00C7, on "tile" anywhere in a name ("Tattile"), or on FEDD (no capture confirms it).
 */
class CatalogV99LadderTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private fun upgrade(edit: (PersistedConfig) -> PersistedConfig = { it }): PersistedConfig {
        val dir = Files.createTempDirectory("v99").toFile()
        try {
            val file = File(dir, "config.json")
            val old = json.decodeFromString(PersistedConfig.serializer(), TestFiles.repoFile("src/test/resources/fixtures/fieldwatch-1.1.32/config.json").readText())
            file.writeText(json.encodeToString(PersistedConfig.serializer(), edit(old)))
            val store = ConfigStore(file)
            runBlocking { store.load() }
            return store.config.value
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aFieldwatchConfigGetsTodaysTileRulesAndKeepsTileOff() {
        val cfg = upgrade()
        assertEquals(ConfigStore.CATALOG_VERSION, cfg.version)
        val tile = cfg.fleets.single { it.id == "fleet-tile" }
        val stock = DefaultCatalog.fleets().single { it.id == "fleet-tile" }
        assertEquals(stock.rules.map { SignatureExchange.ruleKey(it) }.toSet(), tile.rules.map { SignatureExchange.ruleKey(it) }.toSet())
        assertFalse("the 1.1.32 user turned Tile off; that stays", tile.enabled)
    }

    @Test
    fun theStockTileRowIsTheNewOne() {
        val rules = DefaultCatalog.fleets().single { it.id == "fleet-tile" }.rules
        assertTrue(rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x067C })
        assertTrue(rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Tile" && it.radio == RadioKind.BLE })
        assertFalse(rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x00C7 })
        assertFalse(rules.any { it.kind == RuleKind.NAME_CONTAINS })
        assertFalse(rules.any { it.text.equals("FEDD", ignoreCase = true) })
    }
}
