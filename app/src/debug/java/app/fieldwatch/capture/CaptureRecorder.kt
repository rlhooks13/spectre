package app.fieldwatch.capture

import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import app.fieldwatch.BuildConfig
import app.fieldwatch.data.CaptureSink
import app.fieldwatch.domain.Brand
import app.fieldwatch.domain.CaptureRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only Capture v1 recorder (ADR-0016). Records only while `files/capture/ENABLED` exists:
 * `adb shell run-as app.fieldwatch.debug touch files/capture/ENABLED` (and `rm` to stop).
 *
 * The radio callbacks run on the main thread, so they only copy fields into a record and queue it. One
 * writer thread encodes, writes, flushes every second, rotates at [PART_BYTES] and keeps at most
 * [TOTAL_BYTES]. A full queue drops the record and counts it in a `stats` line.
 * Raw captures hold bystander data; P1-F2 scrubs them and P1-F4 deletes the raw files.
 */
class CaptureRecorder(context: Context) : CaptureSink {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "capture")
    private val flag = File(dir, "ENABLED")
    private val queue = ArrayBlockingQueue<CaptureRecord>(QUEUE)
    private val dropped = AtomicLong()

    @Volatile private var enabled = false
    @Volatile private var flagCheckedAt = 0L

    // Wi-Fi dedupe and the writer's state are touched on one thread each.
    private val wifiSeen = LinkedHashMap<String, Boolean>()

    init {
        Thread(::writerLoop, "spectre-capture").apply { isDaemon = true }.start()
    }

    /** The flag is a file, so it is read at most every few seconds, not per advertisement. */
    private fun on(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - flagCheckedAt > FLAG_CHECK_MS) {
            flagCheckedAt = now
            enabled = flag.exists()
        }
        return enabled
    }

    private fun offer(record: CaptureRecord) {
        if (!queue.offer(record)) dropped.incrementAndGet()
    }

    override fun ble(result: ScanResult) {
        if (!on()) return
        guard {
            offer(
                CaptureRecord.Ble(
                    tNs = result.timestampNanos,
                    wallMs = System.currentTimeMillis(),
                    addr = result.device?.address.orEmpty(),
                    addrType = if (Build.VERSION.SDK_INT >= 35) runCatching { result.device?.addressType }.getOrNull() else null,
                    rssi = result.rssi,
                    txPower = result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT },
                    primaryPhy = result.primaryPhy,
                    secondaryPhy = result.secondaryPhy,
                    sid = result.advertisingSid.takeIf { it != ScanResult.SID_NOT_PRESENT },
                    connectable = result.isConnectable,
                    periodicInterval = result.periodicAdvertisingInterval
                        .takeIf { it != ScanResult.PERIODIC_INTERVAL_NOT_PRESENT },
                    adv = result.scanRecord?.bytes?.let(CaptureRecord::hex).orEmpty(),
                    legacy = result.isLegacy,
                    dataStatus = result.dataStatus.takeIf { it != ScanResult.DATA_COMPLETE },
                ),
            )
        }
    }

    override fun wifi(results: List<android.net.wifi.ScanResult>) {
        if (!on()) return
        guard {
            val wall = System.currentTimeMillis()
            synchronized(wifiSeen) {
                for (r in results) {
                    // The same cached results come back on every callback and poll; one line per (BSSID, timestamp).
                    if (wifiSeen.put("${r.BSSID}@${r.timestamp}", true) != null) continue
                    if (wifiSeen.size > WIFI_SEEN_MAX) wifiSeen.remove(wifiSeen.keys.first())
                    offer(wifiRecord(r, wall))
                }
            }
        }
    }

    override fun fix(location: Location, lastKnown: Boolean) {
        if (!on()) return
        guard {
            if (!location.latitude.isFinite() || !location.longitude.isFinite()) return@guard
            offer(
                CaptureRecord.Fix(
                    tNs = location.elapsedRealtimeNanos,
                    wallMs = System.currentTimeMillis(),
                    fixTimeMs = location.time,
                    provider = location.provider.orEmpty(),
                    lat = location.latitude,
                    lon = location.longitude,
                    // JSON has no NaN or Infinity; a non-finite reading is recorded as absent.
                    alt = location.altitude.takeIf { location.hasAltitude() && it.isFinite() },
                    acc = location.accuracy.takeIf { location.hasAccuracy() && it.isFinite() },
                    speed = location.speed.takeIf { location.hasSpeed() && it.isFinite() },
                    bearing = location.bearing.takeIf { location.hasBearing() && it.isFinite() },
                    lastKnown = lastKnown.takeIf { it },
                ),
            )
        }
    }

    /** A recorder failure never reaches detection (ADR-0016). */
    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "capture record failed: $t")
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiRecord(r: android.net.wifi.ScanResult, wall: Long) = CaptureRecord.Wifi(
        tNs = r.timestamp * 1_000L,
        wallMs = wall,
        bssid = r.BSSID.orEmpty(),
        ssid = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString().orEmpty() else r.SSID.orEmpty(),
        rssi = r.level,
        freq = r.frequency,
        centerFreq0 = r.centerFreq0,
        centerFreq1 = r.centerFreq1,
        channelWidth = r.channelWidth,
        standard = if (Build.VERSION.SDK_INT >= 30) r.wifiStandard else null,
        capabilities = r.capabilities.orEmpty(),
        ies = if (Build.VERSION.SDK_INT >= 30) ies(r) else emptyList(),
        ssidHex = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.bytes?.let(CaptureRecord::hex) else null,
        mld = if (Build.VERSION.SDK_INT >= 34) r.apMldMacAddress?.toString() else null,
        linkId = if (Build.VERSION.SDK_INT >= 34) r.apMloLinkId.takeIf { it >= 0 } else null,
    )

    @androidx.annotation.RequiresApi(30)
    private fun ies(r: android.net.wifi.ScanResult): List<CaptureRecord.Ie> =
        runCatching { r.informationElements }.getOrDefault(emptyList()).mapNotNull { ie ->
            // One bad element must not cost the others.
            runCatching {
                val buf = ie.bytes.duplicate()
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                CaptureRecord.Ie(ie.id, ie.idExt.takeIf { ie.id == EXTENSION_ID }, CaptureRecord.hex(bytes))
            }.getOrNull()
        }

    // ---- Writer thread only below. ----

    private var out: OutputStream? = null
    private var written = 0L
    private var part = 0
    private var lastFlush = 0L
    private var lastStats = 0L
    private var pausedUntil = 0L

    private fun writerLoop() {
        while (true) {
            val record = try {
                queue.poll(FLUSH_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                return
            }
            val now = SystemClock.elapsedRealtime()
            try {
                if (!enabled) {
                    if (out != null) close()
                    queue.clear()
                    continue
                }
                if (record != null && now >= pausedUntil) write(record)
                val lost = dropped.get()
                if (lost > 0 && now - lastStats > STATS_MS && out != null) {
                    lastStats = now
                    write(CaptureRecord.Stats(SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), dropped.getAndSet(0)))
                }
                if (out != null && now - lastFlush > FLUSH_MS) {
                    out?.flush()
                    lastFlush = now
                }
            } catch (t: Throwable) {
                // Disk full or similar: stop writing for a while instead of churning new files.
                Log.w(TAG, "capture write failed: $t")
                close()
                pausedUntil = now + PAUSE_MS
            }
        }
    }

    private fun write(record: CaptureRecord) {
        // Encode first: a record that can't encode must not open (or close) a part.
        val bytes = try {
            (CaptureRecord.encode(record) + "\n").toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "capture record skipped: $e")
            return
        }
        val stream = out ?: open() ?: return
        stream.write(bytes)
        written += bytes.size
        if (written >= PART_BYTES) close()
    }

    private fun open(): OutputStream? {
        dir.mkdirs()
        if (dir.usableSpace in 1 until MIN_FREE_BYTES) {
            Log.w(TAG, "capture paused: less than ${MIN_FREE_BYTES shr 20} MB free")
            pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS
            return null
        }
        trim()
        part += 1
        val f = File(dir, "capture-${System.currentTimeMillis()}-%04d.ndjson".format(part))
        val stream = FileOutputStream(f).buffered(64 * 1024)
        out = stream
        written = 0L
        val header = CaptureRecord.Header(
            app = BuildConfig.VERSION_NAME,
            sdk = Build.VERSION.SDK_INT,
            model = Build.MODEL,
            wallMs = System.currentTimeMillis(),
            elapsedNs = SystemClock.elapsedRealtimeNanos(),
            bootCount = runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull(),
            part = part,
        )
        val bytes = (CaptureRecord.encode(header) + "\n").toByteArray(Charsets.UTF_8)
        stream.write(bytes)
        written += bytes.size
        return stream
    }

    private fun close() {
        val stream = out ?: return
        out = null
        try {
            stream.flush()
        } catch (e: Exception) {
            Log.w(TAG, "capture flush failed, tail lost: $e")
        }
        runCatching { stream.close() }
    }

    /** Oldest parts go first once the folder passes [TOTAL_BYTES]. */
    private fun trim() {
        val parts = dir.listFiles { f -> f.name.startsWith("capture-") && f.name.endsWith(".ndjson") }
            .orEmpty().sortedBy { it.lastModified() }
        var total = parts.sumOf { it.length() }
        for (p in parts) {
            if (total <= TOTAL_BYTES - PART_BYTES) break
            total -= p.length()
            p.delete()
        }
    }

    private companion object {
        const val TAG = "${Brand.LOG_TAG}Capture"
        const val PART_BYTES = 8L * 1024 * 1024
        const val TOTAL_BYTES = 64L * 1024 * 1024
        const val MIN_FREE_BYTES = 256L * 1024 * 1024
        const val QUEUE = 8_192
        const val FLAG_CHECK_MS = 3_000L
        const val FLUSH_MS = 1_000L
        const val STATS_MS = 10_000L
        const val PAUSE_MS = 3_000L
        const val WIFI_SEEN_MAX = 4_096
        const val EXTENSION_ID = 255
    }
}
