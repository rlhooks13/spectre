package app.fieldwatch.testing

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.data.SitStore
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.PlaceMemory
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.SitFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * P1-A3: real Fieldwatch 1.1.32 files, decoded by today's code. The fixtures under `fixtures/fieldwatch-1.1.32/`
 * were written by the 1.1.32 code itself (819365d, each store's own Json settings) from synthetic inputs, and become
 * the importer's fixtures. Each is read the way the app reads it: config.json through ConfigStore.load, a sit through
 * SitStore, the share packs through SettingsExchange / SignatureExchange, and places.json with PlaceStore's settings.
 * The goldens hold what the app ends up with.
 */
@RunWith(RobolectricTestRunner::class)
class Fieldwatch1132DecodeTest {
    @get:Rule
    val golden = GoldenRule()

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    private fun fixture(name: String): File = TestFiles.repoFile("src/test/resources/fixtures/fieldwatch-1.1.32/$name")

    /** The fleets that differ from today's stock row with the same id, or that stock doesn't have. */
    private fun changedFromStock(fleets: List<Fleet>): List<Fleet> {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        return fleets.filter { stock[it.id] != it }
    }

    /** config.json through ConfigStore.load: the stored catalog is patched against today's stock rows. */
    @Test
    fun configJson() {
        val file = File(RuntimeEnvironment.getApplication().filesDir, "config.json")
        fixture("config.json").copyTo(file, overwrite = true)
        val store = ConfigStore(file)
        runBlocking { store.load() }
        val cfg = store.config.value
        val out = buildJsonObject {
            put("version", cfg.version)
            put("fleetCount", cfg.fleets.size)
            put("changedFromStock", json.encodeToJsonElement(ListSerializer(Fleet.serializer()), changedFromStock(cfg.fleets)))
            put("disabledIds", JsonArray(cfg.fleets.filter { !it.enabled }.map { JsonPrimitive(it.id) }))
            put("watchlist", json.encodeToJsonElement(cfg.watchlist))
            put("settings", json.encodeToJsonElement(cfg.settings))
            put("filter", json.encodeToJsonElement(cfg.filter))
            put("presets", JsonArray(cfg.presets.map { JsonPrimitive(it.id) }))
            put("hiddenPresetIds", JsonArray(cfg.hiddenPresetIds.sorted().map(::JsonPrimitive)))
            put("arrivalKnownKeys", JsonArray(cfg.arrivalKnownKeys.sorted().map(::JsonPrimitive)))
            put("takInstallTag", cfg.takInstallTag)
        }
        Golden.assertJson("import-1.1.32/config.json", out.toString())
    }

    /** A saved sit through SitStore: the summary it lists, and the file it hands the Debrief and exports. */
    @Test
    fun savedSit() {
        val app = RuntimeEnvironment.getApplication()
        File(app.filesDir, "sits").mkdirs()
        fixture("sits/sit-1132-golden.json").copyTo(File(app.filesDir, "sits/sit-1132-golden.json"), overwrite = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = SitStore(app, scope)
            runBlocking { store.load() }
            val file = store.sitFile("sit-1132-golden")!!
            val out = buildJsonObject {
                put("listed", json.encodeToJsonElement(store.ui.value.closed))
                put("file", json.encodeToJsonElement(SitFile.serializer(), file))
            }
            Golden.assertJson("import-1.1.32/sit.json", out.toString())
        } finally {
            scope.cancel()
        }
    }

    /**
     * places.json with PlaceStore's own Json. PlaceStore.load also prunes against the wall clock, so the test decodes
     * the records directly to keep the golden fixed.
     */
    @Test
    fun placesJson() {
        val placeJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val root = placeJson.parseToJsonElement(fixture("places.json").readText(Charsets.UTF_8)).jsonObject
        assertNull(root["format"]) // 1.1.32 wrote no format key (its default, with encodeDefaults = false)
        val records = placeJson.decodeFromJsonElement(ListSerializer(PlaceMemory.Record.serializer()), root.getValue("records").jsonArray)
        Golden.assertJson("import-1.1.32/places.json", json.encodeToString(ListSerializer(PlaceMemory.Record.serializer()), records))
    }

    /** The settings share pack, imported onto a fresh install's config. */
    @Test
    fun settingsPack() {
        val pack = SettingsExchange.parse(fixture("settings-pack.json").readText(Charsets.UTF_8))
        val (cfg, result) = SettingsExchange.apply(PersistedConfig(), pack)
        assertNull(result.error)
        val out = buildJsonObject {
            put("result", result.summary())
            put("watchlist", json.encodeToJsonElement(cfg.watchlist))
            put("settings", json.encodeToJsonElement(cfg.settings))
            put("hiddenPresetIds", JsonArray(cfg.hiddenPresetIds.sorted().map(::JsonPrimitive)))
        }
        Golden.assertJson("import-1.1.32/settings-pack.json", out.toString())
    }

    /** The signature share pack, merged into today's stock catalog. */
    @Test
    fun signaturePack() {
        val parsed = SignatureExchange.parsePack(fixture("signature-pack.json").readText(Charsets.UTF_8))
        assertEquals(0, parsed.skippedDecode)
        val (merged, result) = SignatureExchange.merge(DefaultCatalog.fleets(), parsed.pack.fleets)
        assertNull(result.error)
        val out = buildJsonObject {
            put("catalogVersion", parsed.pack.catalogVersion)
            put("result", result.summary())
            put("fleetCount", merged.size)
            put("changedFromStock", json.encodeToJsonElement(ListSerializer(Fleet.serializer()), changedFromStock(merged)))
        }
        Golden.assertJson("import-1.1.32/signature-pack.json", out.toString())
    }
}
