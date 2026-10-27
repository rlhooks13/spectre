package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.PersistedConfig
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

    /** Every v97 stock note naming Fieldwatch (found in the v97 snapshot, not a hand list) is today's stock note. */
    @Test
    fun theSixNotesNoLongerNameFieldwatch() {
        val cfg = upgrade()
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val v97 = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v97.json").readText()).fleets
        val named = v97.filter { "Fieldwatch" in it.notes || "Fieldwatch" in it.attentionNote }.map { it.id }
        assertEquals(5, named.size) // six texts on five fleets (hobby-BLE has two)
        for (id in named) {
            assertEquals(id, stock.getValue(id).notes, cfg.fleet(id).notes)
            assertEquals(id, stock.getValue(id).attentionNote, cfg.fleet(id).attentionNote)
        }
        assertTrue(cfg.fleets.none { "Fieldwatch" in it.notes || "Fieldwatch" in it.attentionNote })
    }

    /**
     * A config already at v98 that still has the retired rule (the stock overlay or an import put it back, or a
     * switched-off copy) loses it on load: retired rules go on every load, not only in the ladder step.
     */
    @Test
    fun aV98ConfigThatStillHasTheRuleLosesItOnLoad() {
        val stale = MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0075, enabled = false)
        val cfg = upgrade { old ->
            old.copy(version = 98, fleets = old.fleets.map { if (it.id == "fleet-smarttag") it.copy(rules = it.rules + stale) else it })
        }
        assertFalse(cfg.fleet("fleet-smarttag").hasSamsungId())
    }

    /** Overlaying the v97 stock pack (in-app update or a file) onto a v98 config doesn't bring the rule back. */
    @Test
    fun theV97StockPackOverlaidDoesNotRestoreTheRule() {
        val v97 = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v97.json").readText())
        val (fleets, _) = SignatureExchange.overlayStock(DefaultCatalog.fleets(), v97.fleets)
        assertFalse(fleets.single { it.id == "fleet-smarttag" }.hasSamsungId())
        // And the other way round: a v97 config's own copy isn't kept as a user extra when v98 is overlaid.
        val v98 = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v98.json").readText())
        val (onOld, _) = SignatureExchange.overlayStock(v97.fleets.map { it.copy(builtIn = true) }, v98.fleets)
        assertFalse(onOld.single { it.id == "fleet-smarttag" }.hasSamsungId())
    }

    /** Importing an old signature pack that carries the stock SmartTag row doesn't restore the rule. */
    @Test
    fun importingAnOldPackDoesNotRestoreTheRule() {
        val v97Tag = SignatureExchange.parse(TestFiles.repoFile("../catalog/snapshots/v97.json").readText())
            .fleets.single { it.id == "fleet-smarttag" }
        assertTrue(v97Tag.hasSamsungId())
        val (fleets, _) = SignatureExchange.merge(DefaultCatalog.fleets(), listOf(v97Tag))
        assertFalse(fleets.single { it.id == "fleet-smarttag" }.hasSamsungId())
    }

    /**
     * A user's own SmartTag rule stays; only the 0x0075 rule goes. A bare 0x0075 rule the user added themselves goes
     * too: it is the same false match on every Galaxy phone (RetiredRules).
     */
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
