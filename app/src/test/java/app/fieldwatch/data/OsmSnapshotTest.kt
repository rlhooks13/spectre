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
