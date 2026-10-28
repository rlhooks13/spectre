package app.fieldwatch.data

import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.sensorKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * P0-7e (ADR-0018): the committed OpenStreetMap plate-reader snapshot (`catalog/osm/`) stays within its limits:
 * at least [FlockApproach.MIN_COUNT] and at most 400,000 cameras, at most 4 MiB gzipped, every row well formed, and
 * sorted by key with no duplicates.
 */
class OsmSnapshotTest {
    private val dir = listOf(File("catalog/osm"), File("../catalog/osm")).first { it.isDirectory }

    @Test
    fun theSnapshotIsWellFormedAndWithinItsLimits() {
        val meta = Json.parseToJsonElement(File(dir, "alpr-meta.json").readText()).jsonObject
        assertEquals("spectre-alpr-1", meta.getValue("format").jsonPrimitive.content)
        assertTrue(meta.getValue("osmBase").jsonPrimitive.content.isNotBlank())
        val tagCount = meta.getValue("tags").jsonArray.size
        val lines = File(dir, "alpr.jsonl").readLines().filter { it.isNotBlank() }
        val count = meta.getValue("count").jsonPrimitive.int
        assertEquals(count, lines.size)
        assertTrue("$count cameras", count >= FlockApproach.MIN_COUNT && count <= FlockApproach.MAX_COUNT)
        // The meta's query hash matches the committed query, and every tag row has one field per tag key.
        val query = File(dir, "query.overpassql").readBytes()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(query).joinToString("") { "%02x".format(it) }
        assertEquals(sha, meta.getValue("querySha256").jsonPrimitive.content)
        // Only the five tags ADR-0018 keeps, each a clipped, trimmed string.
        val keys = meta.getValue("tagKeys").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("manufacturer", "manufacturer:wikidata", "brand", "brand:wikidata", "operator"), keys)
        meta.getValue("tags").jsonArray.forEach { row ->
            val fields = row.jsonArray.map { it.jsonPrimitive }
            assertEquals(row.toString(), keys.size, fields.size)
            assertTrue(row.toString(), fields.all { it.isString && it.content.length <= 80 && it.content == it.content.trim() })
        }
        var lastKey = Long.MIN_VALUE
        val types = listOf("node", "way", "relation")
        for (line in lines) {
            val row = Json.parseToJsonElement(line).jsonArray
            assertEquals(line, 6, row.size)
            assertTrue("numbers, not strings: $line", (0..4).none { row[it].jsonPrimitive.isString })
            val type = row[0].jsonPrimitive.int
            assertTrue(line, type in 0..2)
            val id = row[1].jsonPrimitive.long
            assertTrue(line, id > 0)
            assertTrue(line, row[2].jsonPrimitive.int in -9_000_000..9_000_000)
            assertTrue(line, row[3].jsonPrimitive.int in -18_000_000..18_000_000)
            assertTrue(line, row[4].jsonPrimitive.int in -1 until tagCount)
            val facings = row[5].jsonArray.map { it.jsonPrimitive.int }
            assertTrue(line, facings.size <= FlockApproach.MAX_FACINGS && facings.all { it in 0..359 })
            assertEquals(line, facings.size, facings.toSet().size)
            val key = sensorKey(id, types[type])
            assertTrue("sorted by key, no duplicates: $line", key > lastKey)
            lastKey = key
        }
        // The APK asset layout (P0-7e2): the meta on one line, then the rows.
        val packed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use {
                it.write((meta.toString() + "\n").toByteArray())
                it.write(File(dir, "alpr.jsonl").readBytes())
            }
        }.size()
        assertTrue("$packed bytes gzipped", packed <= 4 * 1024 * 1024)
    }

    /** The app's asset layout, as Gradle builds it (P0-7e2): the meta on one line, then the rows. */
    private fun assetBytes(rows: String = File(dir, "alpr.jsonl").readText()): ByteArray {
        val meta = File(dir, "alpr-meta.json").readLines().joinToString("") { it.trim() }
        return (meta + "\n" + rows).toByteArray()
    }

    /** P0-7e2: the app reads every camera, as a plate reader, and labels Flock from manufacturer when brand is blank. */
    @Test
    fun theAppReadsTheWholeSnapshot() {
        val loaded = OsmSnapshot.read(assetBytes().inputStream())!!
        val count = Json.parseToJsonElement(File(dir, "alpr-meta.json").readText()).jsonObject.getValue("count").jsonPrimitive.int
        assertEquals(count, loaded.cameras.size)
        assertTrue(loaded.osmBase.isNotBlank())
        assertTrue(loaded.cameras.all { it.kind == app.fieldwatch.domain.SensorKind.PLATE })
        // About 71% of mapped cameras name Flock only in manufacturer (ADR-0018 review); they must stay Flock.
        val flock = loaded.cameras.count { it.flock }
        assertTrue("$flock of $count are Flock", flock > count / 2)
        // A gzipped stream reads the same.
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(assetBytes()) } }.toByteArray()
        assertEquals(count, OsmSnapshot.read(zipped.inputStream())!!.cameras.size)
    }

    @Test
    fun brandComesFromManufacturerWhenBrandIsBlank() {
        assertEquals("Flock Safety" to true, OsmSnapshot.brandOf(listOf("Flock Safety", "", "", "", "")))
        assertEquals("Flock Safety" to true, OsmSnapshot.brandOf(listOf("", "", "Flock Safety", "Q108485435", "")))
        assertEquals(true, OsmSnapshot.brandOf(listOf("Flock Group Inc.", "", "", "", "")).second)
        assertEquals(true, OsmSnapshot.brandOf(listOf("", "Q108485435", "", "", "")).second)
        assertEquals("Motorola Solutions" to false, OsmSnapshot.brandOf(listOf("Motorola Solutions", "", "", "", "City Police")))
        assertEquals("" to false, OsmSnapshot.brandOf(listOf("x")))
    }

    /** A malformed row, a short snapshot, or the wrong format reads as nothing, never as half a list. */
    @Test
    fun aBrokenSnapshotIsNotHalfLoaded() {
        val rows = File(dir, "alpr.jsonl").readText()
        assertEquals(null, OsmSnapshot.read(assetBytes(rows.replaceFirst("]]\n", "]\n")).inputStream()))
        assertEquals(null, OsmSnapshot.read(assetBytes(rows.lines().take(500).joinToString("\n")).inputStream()))
        assertEquals(null, OsmSnapshot.read(assetBytes(rows.replaceFirst(Regex("^\\[\\d"), "[7")).inputStream()))
        assertEquals(null, OsmSnapshot.read("{\"format\":\"other\"}\n".toByteArray().inputStream()))
        assertEquals(null, OsmSnapshot.read(ByteArray(0).inputStream()))
        // A tag table in another order would read labels from the wrong fields: refused.
        val meta = File(dir, "alpr-meta.json").readLines().joinToString("") { it.trim() }
            .replace("\"manufacturer\",\"manufacturer:wikidata\"", "\"manufacturer:wikidata\",\"manufacturer\"")
            .replace("\"manufacturer\", \"manufacturer:wikidata\"", "\"manufacturer:wikidata\", \"manufacturer\"")
        assertEquals(null, OsmSnapshot.read((meta + "\n" + rows).toByteArray().inputStream()))
        // Junk after a row's facings is refused.
        val brands = listOf("" to false)
        assertEquals(null, OsmSnapshot.row("[0,1,1,1,-1,[5]x]", brands))
        assertTrue(OsmSnapshot.row("[0, 1, 1, 1, -1, [5]]", brands) != null)
    }

    @Test
    fun anAgencyNamedFlockDoesNotMakeAnotherMakersCameraFlock() {
        assertEquals(false, OsmSnapshot.brandOf(listOf("Motorola Solutions", "", "", "", "Little Flock Police Department")).second)
        assertEquals(true, OsmSnapshot.brandOf(listOf("", "", "", "", "Flock Safety")).second)
    }

    /** The layer note's date comes from the meta line alone; an unreadable asset gives null, which the note shows. */
    @Test
    fun theDateComesFromTheMetaLineAlone() {
        val base = Json.parseToJsonElement(File(dir, "alpr-meta.json").readText()).jsonObject.getValue("osmBase").jsonPrimitive.content
        assertEquals(base, OsmSnapshot.readBase(assetBytes().inputStream()))
        assertEquals(base.take(10), OsmSnapshot.dateOf(base))
        assertEquals(null, OsmSnapshot.readBase("{\"format\":\"other\",\"osmBase\":\"x\"}\n".toByteArray().inputStream()))
        assertEquals(null, OsmSnapshot.readBase(ByteArray(0).inputStream()))
    }

    /** The license notice and the snapshot's README ship with it (ADR-0018 §3). */
    @Test
    fun theLicenseShipsWithTheData() {
        val root = dir.parentFile.parentFile
        val notice = File(root, "LICENSE-ODbL.txt").readText()
        assertTrue(notice.contains("© OpenStreetMap contributors"))
        assertTrue(notice.contains("opendatacommons.org/licenses/odbl/1-0"))
        assertTrue(File(dir, "README.md").isFile)
        assertTrue(File(root, "NOTICE").readText().contains("catalog/osm/"))
    }
}
