package app.fieldwatch.data

import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.isFlock
import app.fieldwatch.domain.plateBrand
import app.fieldwatch.domain.sensorKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * The bundled OpenStreetMap plate-reader snapshot (P0-7e, ADR-0018): `assets/cameras/osm-alpr.jsonl`, built by Gradle
 * from `catalog/osm/`. The meta on one line, then one camera per line, `[type, id, lat5, lon5, tagIndex, [facings]]`.
 * Plain text in the APK (whose zip compresses it to about 2.5 MB); a gzipped stream is read too. © OpenStreetMap contributors, ODbL.
 * The phone makes no request for it.
 */
object OsmSnapshot {
    const val ASSET = "cameras/osm-alpr.jsonl"
    const val FORMAT = "spectre-alpr-1"

    /** Flock Safety's Wikidata id, as the snapshot's manufacturer and brand tags carry it. */
    private const val FLOCK_WIKIDATA = "Q108485435"
    private val TYPES = listOf("node", "way", "relation")
    private val TAG_KEYS = listOf("manufacturer", "manufacturer:wikidata", "brand", "brand:wikidata", "operator")
    private const val MAX_LINE = 4_096

    /** [cameras] and the data's `osmBase` timestamp, for the "Map data as of" line. */
    data class Loaded(val cameras: List<FlockCamera>, val osmBase: String)

    /**
     * Reads the asset, or null when it isn't the expected format, holds a malformed row, or falls outside
     * [FlockApproach.MIN_COUNT]..[FlockApproach.MAX_COUNT]. A short or broken snapshot is never half-loaded.
     */
    fun read(input: InputStream): Loaded? = runCatching {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(2)
        val gzipped = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        val text = if (gzipped) GZIPInputStream(buffered) else buffered
        BufferedReader(InputStreamReader(text, Charsets.UTF_8), 1 shl 16).use { reader ->
            val metaLine = reader.readLine() ?: return null
            val meta = Json.parseToJsonElement(metaLine).jsonObject
            if (meta["format"]?.jsonPrimitive?.content != FORMAT) return null
            val osmBase = meta["osmBase"]?.jsonPrimitive?.content.orEmpty()
            val count = meta["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
            if (count < FlockApproach.MIN_COUNT || count > FlockApproach.MAX_COUNT) return null
            // The tag table must be exactly the five kept tags, in order, or labels would come from the wrong fields.
            val keys = meta["tagKeys"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null
            if (keys != TAG_KEYS) return null
            val tags = meta["tags"]?.jsonArray?.map { row -> row.jsonArray.map { it.jsonPrimitive.content } } ?: return null
            if (tags.any { it.size != TAG_KEYS.size }) return null
            val brands = tags.map { t -> brandOf(t) }
            val out = ArrayList<FlockCamera>(count)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.length > MAX_LINE || out.size == count) return null
                out += row(line, brands) ?: return null
            }
            if (out.size != count) return null
            Loaded(out, osmBase)
        }
    }.getOrNull()

    /** A tag tuple (manufacturer, manufacturer:wikidata, brand, brand:wikidata, operator) as (brand, flock). */
    internal fun brandOf(t: List<String>): Pair<String, Boolean> {
        if (t.size != 5) return "" to false
        val (manufacturer, manufacturerWiki, brand, brandWiki, operator) = t
        // Most mapped cameras name the maker only in manufacturer (ADR-0018 review): use it when brand is blank.
        val name = brand.ifBlank { manufacturer }
        // The operator (an agency) only decides when no maker or brand is named: a "Flock" agency can run other cameras.
        val flock = manufacturerWiki == FLOCK_WIKIDATA || brandWiki == FLOCK_WIKIDATA ||
            isFlock(name, "") || isFlock(manufacturer, "") || (name.isBlank() && isFlock("", operator))
        return plateBrand(name, operator) to flock
    }

    /** One `[type, id, lat5, lon5, tagIndex, [facings]]` row, or null if malformed. */
    internal fun row(line: String, brands: List<Pair<String, Boolean>>): FlockCamera? {
        val open = line.indexOf('[', 1)
        val close = line.lastIndexOf(']', line.length - 2)
        // The facings array must close right before the row does: nothing after it.
        if (!line.startsWith("[") || !line.endsWith("]") || open < 0 || close != line.length - 2) return null
        val head = line.substring(1, open).split(',').map { it.trim() }
        if (head.size != 6 || head[5].isNotEmpty()) return null
        val type = head[0].toIntOrNull() ?: return null
        val id = head[1].toLongOrNull() ?: return null
        val lat5 = head[2].toIntOrNull() ?: return null
        val lon5 = head[3].toIntOrNull() ?: return null
        val tagIndex = head[4].toIntOrNull() ?: return null
        if (type !in TYPES.indices || id <= 0L || lat5 !in -9_000_000..9_000_000 || lon5 !in -18_000_000..18_000_000) return null
        if (tagIndex < -1 || tagIndex >= brands.size) return null
        val inner = line.substring(open + 1, close)
        val facings = if (inner.isBlank()) emptyList() else inner.split(',').map { it.trim().toIntOrNull() ?: return null }
        if (facings.size > FlockApproach.MAX_FACINGS || facings.any { it !in 0..359 }) return null
        val (brand, flock) = if (tagIndex < 0) "" to false else brands[tagIndex]
        return FlockCamera(
            key = sensorKey(id, TYPES[type]),
            lat = lat5 / 1e5,
            lon = lon5 / 1e5,
            facings = facings,
            brand = brand,
            flock = flock,
            kind = SensorKind.PLATE,
        )
    }

    /**
     * Just the meta line's `osmBase`, for the layer note's date, without parsing the cameras. Null when the asset
     * can't be read or isn't the expected format: the layer note then says so.
     */
    fun readBase(input: InputStream): String? = runCatching {
        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 12).use { reader ->
            val meta = Json.parseToJsonElement(reader.readLine() ?: return null).jsonObject
            if (meta["format"]?.jsonPrimitive?.content != FORMAT) return null
            meta["osmBase"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    /** The data date for the layer note: `osmBase`'s day, "2026-10-28". */
    fun dateOf(osmBase: String): String = osmBase.take(10)
}
