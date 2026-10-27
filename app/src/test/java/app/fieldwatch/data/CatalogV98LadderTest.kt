package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RuleKind
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
 * P1-B1, catalog v98: SmartTag drops its bare Samsung company-id rule (0x0075 is on every Galaxy phone and Buds) and
 * keeps FD5A, and six notes stop naming Fieldwatch. Upgrades go through the real 1.1.32 config (P1-A3's fixture).
 */
class CatalogV98LadderTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }

    @After
    fun forgetSettings() {
        NetworkPolicy.settings = null
    }

    private fun upgrade(edit: (PersistedConfig) -> PersistedConfig = { it }): PersistedConfig {
        val dir = Files.createTempDirectory("v98").toFile()
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

    private fun PersistedConfig.fleet(id: String): Fleet = fleets.single { it.id == id }

    private fun Fleet.hasSamsungId(): Boolean = rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0075 }

    @Test
    fun aFieldwatchConfigLosesTheBareSamsungRuleAndKeepsFd5a() {
        val cfg = upgrade()
        assertEquals(98, cfg.version)
        val tag = cfg.fleet("fleet-smarttag")
        assertFalse(tag.hasSamsungId())
        assertTrue(tag.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "FD5A" })
        assertEquals(DefaultCatalog.fleets().single { it.id == "fleet-smarttag" }, tag)
    }

    @Test
    fun theSixNotesNoLongerNameFieldwatch() {
        val cfg = upgrade()
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        for (id in listOf("fleet-sony", "fleet-gotenna", "fleet-bluetoad", "fleet-hobby-ble", "fleet-cisco")) {
            assertEquals(id, stock.getValue(id).notes, cfg.fleet(id).notes)
            assertEquals(id, stock.getValue(id).attentionNote, cfg.fleet(id).attentionNote)
        }
        assertTrue(cfg.fleets.none { "Fieldwatch" in it.notes || "Fieldwatch" in it.attentionNote })
    }

    /** A user's own SmartTag rule stays; only the stock 0x0075 rule goes. */
    @Test
    fun aUserRuleOnSmartTagStays() {
        val mine = MatchRule(RuleKind.NAME_CONTAINS, text = "My tag")
        val cfg = upgrade { old ->
            old.copy(fleets = old.fleets.map { if (it.id == "fleet-smarttag") it.copy(rules = it.rules + mine) else it })
        }
        val tag = cfg.fleet("fleet-smarttag")
        assertFalse(tag.hasSamsungId())
        assertTrue(mine in tag.rules)
    }

    /** A note the user rewrote is theirs; only the stock wording is replaced. */
    @Test
    fun aRewrittenNoteStays() {
        val cfg = upgrade { old ->
            old.copy(fleets = old.fleets.map { if (it.id == "fleet-sony") it.copy(notes = "My TV.") else it })
        }
        assertEquals("My TV.", cfg.fleet("fleet-sony").notes)
    }
}
