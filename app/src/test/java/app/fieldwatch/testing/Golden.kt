package app.fieldwatch.testing

import app.fieldwatch.domain.LanguageText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.rules.ExternalResource
import java.io.File
import java.util.Locale
import java.util.TimeZone

/**
 * Golden files (PLAN P1-A2). They live under `app/src/test/resources/golden/`, which `.gitattributes`
 * keeps `-text`, so they are compared byte for byte on every platform.
 *
 * Re-bless with `UPDATE_GOLDEN=1 ./gradlew testDebugUnitTest` (or `-PupdateGolden`), then justify every
 * changed golden in the PR. A missing golden fails unless re-blessing.
 */
object Golden {
    /** The fixed "now" every golden test passes explicitly: 2026-10-27T12:00:00Z. */
    const val NOW = 1_793_102_400_000L

    private val json = Json { prettyPrint = true }

    /** Exact text, byte for byte (UTF-8). */
    fun assertText(name: String, actual: String) {
        val file = file(name)
        if (update(file, actual)) return
        assertEquals("Golden $name (re-bless with UPDATE_GOLDEN=1 and justify it)", file.readText(Charsets.UTF_8), actual)
    }

    /**
     * JSON compared after parsing, so key order doesn't matter: the JVM's org.json orders keys differently
     * from Android's. The file holds a pretty-printed copy with sorted keys, so a re-bless never reorders it.
     * [GoldenDeviceJsonTest] pins the device's bytes.
     */
    fun assertJson(name: String, actual: String) {
        val parsed = canonical(json.parseToJsonElement(actual))
        val file = file(name)
        if (update(file, json.encodeToString(JsonElement.serializer(), parsed) + "\n")) return
        assertEquals("Golden $name (re-bless with UPDATE_GOLDEN=1 and justify it)", json.parseToJsonElement(file.readText(Charsets.UTF_8)), parsed)
    }

    /** Like [assertJson], but never writes: for a second producer checked against another test's golden. */
    fun assertMatchesJson(name: String, actual: String) {
        val file = file(name)
        if (!file.isFile) fail("Golden $name is missing; its owning test creates it.")
        assertEquals("Golden $name", json.parseToJsonElement(file.readText(Charsets.UTF_8)), json.parseToJsonElement(actual))
    }

    /**
     * True while re-blessing: every assert writes its golden instead of comparing. The build turns
     * `UPDATE_GOLDEN=1` and `-PupdateGolden` into this property (and then never counts the tests up to date).
     */
    val updating: Boolean
        get() = System.getProperty("spectre.updateGolden") == "true"

    private fun canonical(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.entries.sortedBy { it.key }.associate { it.key to canonical(it.value) })
        is JsonArray -> JsonArray(e.map(::canonical))
        else -> e
    }

    private fun update(file: File, content: String): Boolean {
        if (updating) {
            file.parentFile.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            return true
        }
        if (!file.isFile) fail("Golden ${file.name} is missing; create it with UPDATE_GOLDEN=1.")
        return false
    }

    private fun file(name: String): File {
        require(Regex("^[a-z0-9][a-z0-9._/-]*$").matches(name) && ".." !in name) { "Bad golden name: $name" }
        val dir = listOf(File("src/test/resources/golden"), File("app/src/test/resources/golden"))
            .firstOrNull { it.isDirectory } ?: File("src/test/resources/golden")
        return File(dir, name)
    }
}

/**
 * Pins what a golden must not depend on: the default TimeZone (UTC) and Locale (en-US), and the language
 * overlay (English). Restores all three afterwards. Pass `now` explicitly ([Golden.NOW]); never read the clock.
 */
class GoldenRule : ExternalResource() {
    private var zone: TimeZone? = null
    private var locale: Locale? = null

    override fun before() {
        zone = TimeZone.getDefault()
        locale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
        LanguageText.clear()
    }

    override fun after() {
        LanguageText.clear()
        zone?.let(TimeZone::setDefault)
        locale?.let(Locale::setDefault)
    }
}
