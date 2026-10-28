package app.fieldwatch.data

import app.fieldwatch.BuildConfig
import app.fieldwatch.domain.Egress
import app.fieldwatch.domain.NetworkPolicy
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.FlockParseException
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.parseFlock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * One downloaded list. Plate readers and the other sensors are separate files.
 * A published name may end in .gz. The bytes are often plain JSON.
 * A real gzip stream is unpacked. Neither list ships inside the app.
 * A bad parse never replaces a list that already passed the check.
 */
enum class SensorList(
    val url: String,
    val fileName: String,
    val expectedBytes: Long,
    /** Version 1 of the plate file saved Flock only and must be fetched again. */
    val refillFlockOnly: Boolean,
) {
    /**
     * Plate readers. Never downloaded (P0-7c, ADR-0018): no URL, so [FlockFiles.probe] and [FlockFiles.download] fail
     * without opening a connection. The bundled OpenStreetMap snapshot (P0-7e) replaces the file. The entry stays,
     * inert, because formats only grow; [FlockFiles.retirePlatesDownload] deletes a file an older version saved.
     */
    PLATES(
        "",
        "flock-cameras.bin",
        37_748_018L,
        true,
    ),
    /**
     * Speed cameras, red lights, gunshot detectors, and other cameras. Plate readers stay on
     * [PLATES]. No URL while [app.fieldwatch.domain.Features.OTHER_SENSORS_LAYER] is off, so
     * [FlockFiles.probe] and [FlockFiles.download] fail without opening a connection.
     */
    OTHER(
        "",
        "road-sensors.bin",
        616_965L,
        false,
    ),
}

object FlockFiles {
    /** Version 1 saved Flock only. Version 2 and later saved every plate reader. */
    const val ALL_PLATES: Int = 2
    const val MAX_BYTES = 80L * 1024L * 1024L
    /**
     * Version 1 is Flock only. Version 2 is every plate reader.
     * Version 3 adds a type byte after the brand.
     */
    const val FORMAT: Int = 3
    private const val VERSION_FLOCK_ONLY: Short = 1
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte(), 'C'.code.toByte())

    data class Header(val count: Int, val savedAt: Long, val etag: String, val version: Int = FORMAT)

    data class Saved(val header: Header, val cameras: List<FlockCamera>)

    enum class Probe { SAME, CHANGED, FAILED }

    sealed class Fetch {
        data class Saved(val savedAt: Long) : Fetch()
        data object Current : Fetch()
        data object Failed : Fetch()
    }

    fun file(dir: File, list: SensorList): File = File(dir, list.fileName)

    /** The site a list downloads from. Empty when the list has no URL, as the plate-reader list has none (P0-7c). */
    fun sourceHost(list: SensorList): String =
        if (list.url.isBlank()) "" else runCatching { URL(list.url).host.lowercase() }.getOrDefault("")

    /**
     * P0-7c: deletes a plate-reader list an older version downloaded, and its temp files. Idempotent; run at start.
     * Returns how many files it deleted.
     */
    fun retirePlatesDownload(dir: File): Int {
        val name = SensorList.PLATES.fileName
        return listOf(name, "$name.write", "$name.download").count { File(dir, it).let { f -> f.exists() && f.delete() } }
    }

    fun peek(dir: File, list: SensorList): Header? = peekFile(file(dir, list))

    fun camerasFrom(bytes: ByteArray): List<FlockCamera> = parseStream(bytes)

    fun read(dir: File, list: SensorList): Saved? =
        runCatching { readFile(file(dir, list)) }.getOrNull()

    /**
     * Writes [cameras] only when there are at least [FlockApproach.MIN_COUNT].
     * Returns false and leaves any existing file in place otherwise.
     */
    fun install(
        dir: File,
        cameras: List<FlockCamera>,
        etag: String,
        savedAt: Long,
        list: SensorList,
    ): Boolean {
        if (cameras.size < FlockApproach.MIN_COUNT || cameras.size > FlockApproach.MAX_COUNT) return false
        val dest = file(dir, list)
        val tmp = File(dir, "${list.fileName}.write")
        return try {
            write(tmp, cameras, etag, savedAt)
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Parse [bytes] and install. A short list, or a parse error, leaves the saved file alone.
     */
    fun commitParsed(
        dir: File,
        bytes: ByteArray,
        etag: String,
        savedAt: Long,
        list: SensorList,
    ): Boolean {
        val cameras = try {
            parseStream(bytes)
        } catch (_: FlockParseException) {
            return false
        } catch (_: Exception) {
            return false
        }
        if (cameras.size < FlockApproach.MIN_COUNT) return false
        return install(dir, cameras, etag, savedAt, list)
    }

    fun probe(dir: File, list: SensorList): Probe {
        if (list.url.isBlank()) return Probe.FAILED
        val saved = peek(dir, list)?.etag.orEmpty()
        val conn = open(list, "HEAD", ifNoneMatch = null) ?: return Probe.FAILED
        return try {
            val code = conn.responseCode
            if (!servedFromSource(conn.url, list)) return Probe.FAILED
            if (code == 304) return Probe.SAME
            if (code != 200) return Probe.FAILED
            val remote = conn.getHeaderField("ETag").orEmpty()
            if (saved.isNotEmpty() && remote.isNotEmpty() && remote == saved) Probe.SAME else Probe.CHANGED
        } catch (_: Exception) {
            Probe.FAILED
        } finally {
            conn.disconnect()
        }
    }

    fun download(
        dir: File,
        list: SensorList,
        onPercent: (Int) -> Unit,
    ): Fetch {
        if (list.url.isBlank()) return Fetch.Failed
        val header = peek(dir, list)
        // Version 1 of the plate file saved Flock only. Skip If-None-Match so that list is fetched again.
        val savedEtag = header?.etag?.takeIf {
            it.isNotEmpty() && (!list.refillFlockOnly || header.version >= ALL_PLATES)
        }
        val conn = open(list, "GET", savedEtag) ?: return Fetch.Failed
        val tmp = File(dir, "${list.fileName}.download")
        tmp.delete()
        return try {
            val code = conn.responseCode
            if (!servedFromSource(conn.url, list)) return Fetch.Failed
            if (code == 304) return Fetch.Current
            if (code != 200) return Fetch.Failed
            val length = conn.contentLengthLong
            if (length > MAX_BYTES) return Fetch.Failed
            val need = if (length > 0L) length else list.expectedBytes
            val room = dir.usableSpace
            if (room > 0L && room < need + 8L * 1024L * 1024L) return Fetch.Failed
            val total = if (length > 0L) length else list.expectedBytes
            var read = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        if (read > MAX_BYTES) return Fetch.Failed
                        out.write(buf, 0, n)
                        val pct = ((read * 100L) / total).toInt().coerceIn(0, 99)
                        if (pct != lastPct) {
                            lastPct = pct
                            onPercent(pct)
                        }
                    }
                }
            }
            onPercent(100)
            val etag = conn.getHeaderField("ETag").orEmpty().ifEmpty { savedEtag.orEmpty() }
            val savedAt = System.currentTimeMillis()
            val plates = try {
                parseFile(tmp)
            } catch (_: Exception) {
                return Fetch.Failed
            }
            if (plates.size < FlockApproach.MIN_COUNT) return Fetch.Failed
            if (!install(dir, plates, etag, savedAt, list)) return Fetch.Failed
            onPercent(100)
            Fetch.Saved(savedAt)
        } catch (_: Exception) {
            Fetch.Failed
        } finally {
            tmp.delete()
            conn.disconnect()
        }
    }

    /**
     * True when the response came from [list]'s source host (or a subdomain of it) over HTTPS. Redirects are
     * followed, so a list served from another host fails instead of being installed. A list with no URL fails.
     */
    internal fun servedFromSource(finalUrl: URL, list: SensorList): Boolean = servedFromHost(finalUrl, sourceHost(list))

    /** [servedFromSource] against a named [source] host; empty matches nothing. */
    internal fun servedFromHost(finalUrl: URL, source: String): Boolean {
        if (source.isEmpty()) return false
        val host = finalUrl.host.lowercase()
        return finalUrl.protocol == "https" && (host == source || host.endsWith(".$source"))
    }

    /** Null when NetworkPolicy refuses; nothing is sent. */
    private fun open(list: SensorList, method: String, ifNoneMatch: String?): HttpURLConnection? {
        if (!NetworkPolicy.allows(Egress.SENSOR_LIST)) return null
        return (URL(list.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = method
            setRequestProperty("Accept", "application/geo+json, application/json, application/gzip")
            setRequestProperty(
                "User-Agent",
                app.fieldwatch.domain.Brand.userAgent(BuildConfig.VERSION_NAME),
            )
            if (!ifNoneMatch.isNullOrEmpty()) setRequestProperty("If-None-Match", ifNoneMatch)
        }
    }

    private fun parseFile(file: File): List<FlockCamera> =
        bounded { reader(FileInputStream(file)).use { parseFlock(it) } }

    private fun parseStream(bytes: ByteArray): List<FlockCamera> =
        bounded { reader(bytes.inputStream()).use { parseFlock(it) } }

    /**
     * The parser has depth, string, size and count limits; this is the backstop. These Errors would
     * otherwise slip past the callers' `catch (Exception)` and take down the scanning process.
     */
    private inline fun bounded(parse: () -> List<FlockCamera>): List<FlockCamera> =
        try {
            parse()
        } catch (_: StackOverflowError) {
            throw FlockParseException("nested too deep")
        } catch (_: OutOfMemoryError) {
            throw FlockParseException("too large")
        }

    private fun reader(raw: java.io.InputStream): InputStreamReader {
        val input = BufferedInputStream(raw)
        input.mark(4)
        val b0 = input.read()
        val b1 = input.read()
        input.reset()
        val stream = if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(input) else input
        return InputStreamReader(Capped(stream, MAX_UNPACKED_BYTES), Charsets.UTF_8)
    }

    /** Unpacked size limit: the network cap counts gzip bytes, so a small bomb could still unpack huge. */
    const val MAX_UNPACKED_BYTES = 512L * 1024L * 1024L

    /** Fails the read past [limit] bytes; the parse turns that into a failed fetch. */
    private class Capped(input: java.io.InputStream, private val limit: Long) : java.io.FilterInputStream(input) {
        private var seen = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0 && ++seen > limit) throw java.io.IOException("list unpacks past $limit bytes")
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                seen += n
                if (seen > limit) throw java.io.IOException("list unpacks past $limit bytes")
            }
            return n
        }
    }

    private fun peekFile(file: File): Header? {
        if (!file.isFile || file.length() < 20) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return null
                val version = input.readShort()
                if (!knownVersion(version)) return null
                val count = input.readInt()
                if (count < FlockApproach.MIN_COUNT || count > FlockApproach.MAX_COUNT) return null
                val savedAt = input.readLong()
                val etagLen = input.readUnsignedShort()
                if (etagLen > 512) return null
                val etagBytes = ByteArray(etagLen)
                input.readFully(etagBytes)
                Header(count, savedAt, etagBytes.toString(Charsets.UTF_8), version.toInt())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFile(file: File): Saved {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = ByteArray(4)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) error("bad flock file")
            val version = input.readShort()
            if (!knownVersion(version)) error("bad flock version")
            val count = input.readInt()
            if (count < FlockApproach.MIN_COUNT || count > FlockApproach.MAX_COUNT) error("bad flock count")
            val savedAt = input.readLong()
            val etagLen = input.readUnsignedShort()
            if (etagLen > 512) error("bad flock etag")
            val etagBytes = ByteArray(etagLen)
            input.readFully(etagBytes)
            val cameras = ArrayList<FlockCamera>(count)
            repeat(count) {
                val key = input.readLong()
                val lat = input.readInt() / 1_000_000.0
                val lon = input.readInt() / 1_000_000.0
                val versionInt = version.toInt()
                val legacy = versionInt == VERSION_FLOCK_ONLY.toInt()
                val flock = if (legacy) true else input.readUnsignedByte() == 1
                val brand = if (legacy) {
                    "Flock"
                } else {
                    val len = input.readUnsignedByte()
                    if (len > 48) error("bad flock brand")
                    val bytes = ByteArray(len)
                    if (len > 0) input.readFully(bytes)
                    bytes.toString(Charsets.UTF_8)
                }
                val kind = if (versionInt >= FORMAT) {
                    val raw = input.readUnsignedByte()
                    if (raw >= SensorKind.entries.size) error("bad sensor kind")
                    SensorKind.entries[raw]
                } else {
                    SensorKind.PLATE
                }
                val n = input.readUnsignedByte()
                if (n > FlockApproach.MAX_FACINGS) error("bad flock facing")
                val facings = List(n) {
                    val deg = input.readUnsignedShort()
                    if (deg > 359) error("bad flock degree")
                    deg
                }
                cameras.add(FlockCamera(key, lat, lon, facings, brand, flock, kind).forStore())
            }
            return Saved(Header(count, savedAt, etagBytes.toString(Charsets.UTF_8), version.toInt()), cameras)
        }
    }

    private fun knownVersion(version: Short): Boolean = version.toInt() in 1..FORMAT

    private fun write(file: File, cameras: List<FlockCamera>, etag: String, savedAt: Long) {
        val sorted = cameras.map { it.forStore() }.sortedWith(compareBy({ it.lat }, { it.lon }, { it.key }))
        val etagBytes = etag.encodeToByteArray().let { if (it.size > 512) it.copyOf(512) else it }
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.write(MAGIC)
            out.writeShort(FORMAT)
            out.writeInt(sorted.size)
            out.writeLong(savedAt)
            out.writeShort(etagBytes.size)
            out.write(etagBytes)
            for (cam in sorted) {
                out.writeLong(cam.key)
                out.writeInt(micro(cam.lat))
                out.writeInt(micro(cam.lon))
                val faces = cam.facings.take(FlockApproach.MAX_FACINGS)
                val brandBytes = cam.brand.encodeToByteArray().let { if (it.size > 48) it.copyOf(48) else it }
                out.writeByte(if (cam.flock) 1 else 0)
                out.writeByte(brandBytes.size)
                out.write(brandBytes)
                out.writeByte(cam.kind.ordinal)
                out.writeByte(faces.size)
                for (deg in faces) out.writeShort(deg.coerceIn(0, 359))
            }
        }
    }

    private fun micro(deg: Double): Int = kotlin.math.round(deg * 1_000_000.0).toInt()
}
