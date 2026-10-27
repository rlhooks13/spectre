package app.fieldwatch.capture

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.fieldwatch.BuildConfig
import app.fieldwatch.data.CaptureSink
import app.fieldwatch.domain.Brand
import app.fieldwatch.domain.CaptureRecord
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * Debug-only Capture v1 recorder (ADR-0016). Records only while `files/capture/ENABLED` exists:
 * `adb shell run-as app.fieldwatch.debug touch files/capture/ENABLED` (and `rm` to stop).
 * Writes `files/capture/capture-<wallMs>.ndjson`, rotating at [PART_BYTES] and keeping at most [TOTAL_BYTES].
 * Raw captures hold bystander data; P1-F2 scrubs them and P1-F4 deletes the raw files.
 */
class CaptureRecorder(context: Context) : CaptureSink {
    private val dir = File(context.filesDir, "capture")
    private val flag = File(dir, "ENABLED")
    private val lock = Any()
    private var out: BufferedWriter? = null
    private var file: File? = null
    private var written = 0L
    private var enabledAt = 0L
    private var enabled = false
    private val wifiSeen = LinkedHashMap<String, Boolean>()

    /** The flag is a file, so it is read at most every few seconds, not per advertisement. */
    private fun on(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - enabledAt > FLAG_CHECK_MS) {
            enabledAt = now
            enabled = flag.exists()
            if (!enabled) close()
        }
        return enabled
    }

    override fun ble(result: ScanResult) {
        synchronized(lock) {
            if (!on()) return
            write(
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
                ),
            )
        }
    }

    override fun wifi(results: List<android.net.wifi.ScanResult>) {
        synchronized(lock) {
            if (!on()) return
            val wall = System.currentTimeMillis()
            for (r in results) {
                // The same cached results come back on every callback and poll; one line per (BSSID, timestamp).
                val key = "${r.BSSID}@${r.timestamp}"
                if (wifiSeen.put(key, true) != null) continue
                if (wifiSeen.size > WIFI_SEEN_MAX) wifiSeen.remove(wifiSeen.keys.first())
                write(
                    CaptureRecord.Wifi(
                        tNs = r.timestamp * 1_000L,
                        wallMs = wall,
                        bssid = r.BSSID.orEmpty(),
                        ssid = ssidOf(r),
                        rssi = r.level,
                        freq = r.frequency,
                        centerFreq0 = r.centerFreq0,
                        centerFreq1 = r.centerFreq1,
                        channelWidth = r.channelWidth,
                        standard = if (Build.VERSION.SDK_INT >= 30) r.wifiStandard else null,
                        capabilities = r.capabilities.orEmpty(),
                        ies = if (Build.VERSION.SDK_INT >= 30) ies(r) else emptyList(),
                    ),
                )
            }
        }
    }

    override fun fix(location: Location) {
        synchronized(lock) {
            if (!on()) return
            write(
                CaptureRecord.Fix(
                    tNs = location.elapsedRealtimeNanos,
                    wallMs = location.time,
                    provider = location.provider.orEmpty(),
                    lat = location.latitude,
                    lon = location.longitude,
                    alt = location.altitude.takeIf { location.hasAltitude() },
                    acc = location.accuracy.takeIf { location.hasAccuracy() },
                    speed = location.speed.takeIf { location.hasSpeed() },
                    bearing = location.bearing.takeIf { location.hasBearing() },
                ),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun ssidOf(r: android.net.wifi.ScanResult): String =
        if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty() else r.SSID.orEmpty()

    @androidx.annotation.RequiresApi(30)
    private fun ies(r: android.net.wifi.ScanResult): List<CaptureRecord.Ie> =
        runCatching {
            r.informationElements.map { ie ->
                val buf = ie.bytes.duplicate()
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                CaptureRecord.Ie(ie.id, ie.idExt.takeIf { ie.id == EXTENSION_ID }, CaptureRecord.hex(bytes))
            }
        }.getOrDefault(emptyList())

    private fun write(record: CaptureRecord) {
        try {
            val w = out ?: open()
            val line = CaptureRecord.encode(record)
            w.write(line)
            w.write("\n")
            written += line.length + 1L
            if (written >= PART_BYTES) close()
        } catch (e: Exception) {
            Log.w(TAG, "capture write failed: $e")
            close()
        }
    }

    private fun open(): BufferedWriter {
        dir.mkdirs()
        trim()
        val f = File(dir, "capture-${System.currentTimeMillis()}.ndjson")
        val w = BufferedWriter(OutputStreamWriter(FileOutputStream(f), Charsets.UTF_8), 64 * 1024)
        file = f
        out = w
        written = 0L
        wifiSeen.clear()
        val header = CaptureRecord.Header(
            app = BuildConfig.VERSION_NAME, sdk = Build.VERSION.SDK_INT, model = Build.MODEL, wallMs = System.currentTimeMillis(),
        )
        val line = CaptureRecord.encode(header)
        w.write(line)
        w.write("\n")
        written += line.length + 1L
        return w
    }

    private fun close() {
        runCatching { out?.flush() }
        runCatching { out?.close() }
        out = null
        file = null
    }

    /** Oldest parts go first once the folder passes [TOTAL_BYTES]. */
    private fun trim() {
        val parts = dir.listFiles { f -> f.name.startsWith("capture-") && f.name.endsWith(".ndjson") }
            .orEmpty().sortedBy { it.name }
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
        const val FLAG_CHECK_MS = 3_000L
        const val WIFI_SEEN_MAX = 4_096
        const val EXTENSION_ID = 255
    }
}
