package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Scrubber v1 (P1-F2, ADR-0017): field-level, before a capture leaves the agent's machine for the private
 * corpus. Every replacement is keyed by a secret made fresh per capture and never stored, so the same
 * original maps to the same stand-in within one capture and to nothing outside it.
 *
 * - Addresses (BLE, BSSID, MLD) are remapped. A universal address keeps its OUI and a random BLE address
 *   keeps its two type bits, so vendor rules and the address classifier still replay.
 * - The same 6 bytes inside any payload (ScanRecord or IE, either byte order) get the same stand-in.
 * - BLE local names, SSIDs (text, raw bytes and IE 0) and WPS identity attributes become same-length
 *   stand-ins.
 * - Rotating ids are remapped: Apple Offline Finding keys, FD5A (SmartTag) and FEAA (Eddystone, Find Hub)
 *   payloads. Eddystone TLM telemetry is kept.
 * - Times shift by one secret offset. The path loses its first and last 500 m, along with every record from
 *   those stretches, and is rotated and moved to a synthetic origin. A path under 1 km keeps no fixes.
 * - The header drops the phone model and boot count.
 */
class CaptureScrubber(private val random: java.util.Random = SecureRandom()) {
    private val key = ByteArray(32).also(random::nextBytes)
    private val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
    private val wallShiftMs = (30L + random.nextInt(335)) * DAY_MS * (if (random.nextBoolean()) 1 else -1)
    private val tShiftNs = (1L + random.nextInt(1_000_000)) * 1_000_000_000L
    private val angle = random.nextDouble() * 2 * PI

    /** Original 6-byte address (hex, no separators) to its stand-in. */
    private val addressMap = LinkedHashMap<String, ByteArray>()

    fun scrub(lines: List<String>): List<String> {
        val records = lines.mapNotNull(CaptureRecord::decodeOrNull)
        for (r in records) collectAddresses(r)
        val window = keptWindow(records.filterIsInstance<CaptureRecord.Fix>())
        val center = records.filterIsInstance<CaptureRecord.Fix>().filter { window.keepsFix(it.tNs) }.let(::centroid)
        return records.mapNotNull { r ->
            when (r) {
                is CaptureRecord.Header -> r.copy(
                    model = "scrubbed", bootCount = null, wallMs = r.wallMs + wallShiftMs, elapsedNs = r.elapsedNs?.plus(tShiftNs),
                )
                is CaptureRecord.Stats -> if (window.keeps(r.tNs)) r.copy(tNs = r.tNs + tShiftNs, wallMs = r.wallMs + wallShiftMs) else null
                is CaptureRecord.Ble -> if (window.keeps(r.tNs)) scrubBle(r) else null
                is CaptureRecord.Wifi -> if (window.keeps(r.tNs)) scrubWifi(r) else null
                is CaptureRecord.Fix -> if (window.keepsFix(r.tNs) && center != null) scrubFix(r, center) else null
            }
        }.map(CaptureRecord::encode)
    }

    // ---- addresses ----

    private fun collectAddresses(r: CaptureRecord) {
        when (r) {
            is CaptureRecord.Ble -> mapAddress(r.addr, ble = true, public = r.addrType == 0)
            is CaptureRecord.Wifi -> {
                mapAddress(r.bssid, ble = false, public = false)
                r.mld?.let { mapAddress(it, ble = false, public = false) }
            }
            else -> Unit
        }
    }

    private fun mapAddress(address: String, ble: Boolean, public: Boolean): String {
        val bytes = macBytes(address) ?: return address
        val original = hex(bytes)
        val mapped = addressMap.getOrPut(original) {
            val out = keyed("addr", bytes).copyOf(6)
            when {
                // Universal Wi-Fi or public BLE: keep the OUI.
                (!ble && bytes[0].toInt() and 0x02 == 0) || (ble && public) -> System.arraycopy(bytes, 0, out, 0, 3)
                // Random BLE: keep the two type bits (static 11, resolvable 01, non-resolvable 00).
                ble -> out[0] = ((bytes[0].toInt() and 0xC0) or (out[0].toInt() and 0x3F)).toByte()
                // Local Wi-Fi: keep the local and multicast bits.
                else -> out[0] = ((bytes[0].toInt() and 0x03) or (out[0].toInt() and 0xFC)).toByte()
            }
            out
        }
        return mapped.joinToString(":") { "%02X".format(it) }
    }

    /** Replaces every original address inside [data], forward and reversed, with its stand-in. */
    private fun remapEmbeddedAddresses(data: ByteArray) {
        for ((originalHex, mapped) in addressMap) {
            val original = unhex(originalHex)
            replaceAll(data, original, mapped)
            replaceAll(data, original.reversedArray(), mapped.reversedArray())
        }
    }

    // ---- BLE ----

    private fun scrubBle(r: CaptureRecord.Ble): CaptureRecord.Ble {
        val adv = unhex(r.adv)
        forEachAd(adv) { type, start, len ->
            when (type) {
                0x08, 0x09 -> pseudonymInto(adv, start, len, "name")
                0xFF -> scrubManufacturer(adv, start, len)
                0x16 -> scrubServiceData(adv, start, len)
            }
        }
        remapEmbeddedAddresses(adv)
        return r.copy(
            tNs = r.tNs + tShiftNs,
            wallMs = r.wallMs + wallShiftMs,
            addr = mapAddress(r.addr, ble = true, public = r.addrType == 0),
            adv = hex(adv),
        )
    }

    private fun scrubManufacturer(adv: ByteArray, start: Int, len: Int) {
        if (len < 3) return
        val company = (adv[start].toInt() and 0xFF) or ((adv[start + 1].toInt() and 0xFF) shl 8)
        // Apple Offline Finding: 12 <len> <status> <key 22> <key bits> <hint>; keep type, length and status.
        if (company == 0x004C && adv[start + 2].toInt() and 0xFF == 0x12 && len > 5) {
            keyedInto(adv, start + 5, len - 5, "findmy")
        }
    }

    private fun scrubServiceData(adv: ByteArray, start: Int, len: Int) {
        if (len < 3) return
        val uuid = (adv[start].toInt() and 0xFF) or ((adv[start + 1].toInt() and 0xFF) shl 8)
        when (uuid) {
            // SmartTag: keep the first (state) byte, remap the rotating rest.
            0xFD5A -> if (len > 3) keyedInto(adv, start + 3, len - 3, "fd5a")
            // Eddystone / Find Hub: keep the frame type; TLM (0x20) is telemetry and stays.
            0xFEAA -> if (len > 3 && adv[start + 2].toInt() and 0xFF != 0x20) keyedInto(adv, start + 3, len - 3, "feaa")
        }
    }

    // ---- Wi-Fi ----

    private fun scrubWifi(r: CaptureRecord.Wifi): CaptureRecord.Wifi {
        val ssidBytes = r.ssidHex?.let(::unhex) ?: ssidTextBytes(r.ssid)
        val stand = ssidBytes?.takeIf { it.any { b -> b.toInt() != 0 } }?.let { pseudonym(it, "ssid") }
        val ies = r.ies.map { ie ->
            val data = unhex(ie.hex)
            when {
                ie.id == 0 && stand != null && data.contentEquals(ssidBytes) -> stand.copyInto(data)
                ie.id == 221 -> scrubVendorIe(data)
            }
            remapEmbeddedAddresses(data)
            ie.copy(hex = hex(data))
        }
        return r.copy(
            tNs = r.tNs + tShiftNs,
            wallMs = r.wallMs + wallShiftMs,
            bssid = mapAddress(r.bssid, ble = false, public = false),
            ssid = if (stand == null) r.ssid else ssidText(r.ssid, stand),
            ssidHex = r.ssidHex?.let { if (stand == null) it else hex(stand) },
            ies = ies,
            mld = r.mld?.let { mapAddress(it, ble = false, public = false) },
        )
    }

    /** WPS (00:50:F2 type 4): device name, manufacturer, model, serial and UUIDs are replaced. */
    private fun scrubVendorIe(data: ByteArray) {
        if (data.size < 4 || hex(data.copyOf(4)) != "0050F204") return
        var i = 4
        while (i + 4 <= data.size) {
            val type = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            val start = i + 4
            if (start + len > data.size) break
            if (type in WPS_IDENTITY) pseudonymInto(data, start, len, "wps")
            i = start + len
        }
    }

    /** Without ssidHex (API < 33) the text is the plain SSID; with it, the bytes come from ssidHex. */
    private fun ssidTextBytes(ssid: String): ByteArray? = when {
        ssid.isEmpty() -> null
        ssid.length >= 2 && ssid.startsWith('"') && ssid.endsWith('"') -> ssid.substring(1, ssid.length - 1).toByteArray(Charsets.UTF_8)
        else -> ssid.toByteArray(Charsets.UTF_8)
    }

    /**
     * Keeps Android's text form. API 33+ WifiSsid.toString is quoted UTF-8, or bare hex for other bytes;
     * older SSID text is plain. The stand-in is ASCII, so quoted or plain fits every case but bare hex.
     */
    private fun ssidText(original: String, stand: ByteArray): String {
        val text = String(stand, Charsets.US_ASCII)
        return when {
            original.startsWith('"') -> "\"$text\""
            original.length == stand.size * 2 && original.all { it in HEX_CHARS } -> hex(stand)
            else -> text
        }
    }

    // ---- path ----

    private class Window(val start: Long, val end: Long, val fixes: Boolean) {
        fun keeps(t: Long) = t in start..end
        fun keepsFix(t: Long) = fixes && keeps(t)
    }

    private fun keptWindow(fixes: List<CaptureRecord.Fix>): Window {
        val sorted = fixes.sortedBy { it.tNs }
        if (sorted.isEmpty()) return Window(Long.MIN_VALUE, Long.MAX_VALUE, fixes = false)
        val along = DoubleArray(sorted.size)
        for (i in 1 until sorted.size) along[i] = along[i - 1] + meters(sorted[i - 1], sorted[i])
        val total = along.last()
        // A short path could be a home or a workplace; keep no fixes, but keep the radio data.
        if (total < 2 * TRIM_M) return Window(Long.MIN_VALUE, Long.MAX_VALUE, fixes = false)
        val first = sorted.indices.first { along[it] >= TRIM_M }
        val last = sorted.indices.last { total - along[it] >= TRIM_M }
        return Window(sorted[first].tNs, sorted[last].tNs, fixes = true)
    }

    private fun centroid(fixes: List<CaptureRecord.Fix>): Pair<Double, Double>? =
        if (fixes.isEmpty()) null else fixes.sumOf { it.lat } / fixes.size to fixes.sumOf { it.lon } / fixes.size

    private fun scrubFix(r: CaptureRecord.Fix, center: Pair<Double, Double>): CaptureRecord.Fix {
        val (lat0, lon0) = center
        val x = Math.toRadians(r.lon - lon0) * cos(Math.toRadians(lat0)) * EARTH_M
        val y = Math.toRadians(r.lat - lat0) * EARTH_M
        val xr = x * cos(angle) - y * sin(angle)
        val yr = x * sin(angle) + y * cos(angle)
        return r.copy(
            tNs = r.tNs + tShiftNs,
            wallMs = r.wallMs + wallShiftMs,
            fixTimeMs = r.fixTimeMs?.plus(wallShiftMs),
            lat = Math.toDegrees(yr / EARTH_M),
            lon = Math.toDegrees(xr / EARTH_M),
            bearing = r.bearing?.let { (((it - Math.toDegrees(angle)) % 360 + 360) % 360).toFloat() },
        )
    }

    private fun meters(a: CaptureRecord.Fix, b: CaptureRecord.Fix): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
        return sqrt(dLat * dLat + dLon * dLon) * EARTH_M
    }

    // ---- keyed replacement ----

    private fun keyed(purpose: String, original: ByteArray): ByteArray {
        val out = ByteArray(original.size.coerceAtLeast(32))
        var counter = 0
        var filled = 0
        while (filled < out.size) {
            val block = synchronized(mac) {
                mac.update(purpose.toByteArray())
                mac.update(counter.toByte())
                mac.doFinal(original)
            }
            val n = minOf(block.size, out.size - filled)
            System.arraycopy(block, 0, out, filled, n)
            filled += n
            counter++
        }
        return out
    }

    private fun keyedInto(data: ByteArray, start: Int, len: Int, purpose: String) {
        val original = data.copyOfRange(start, start + len)
        keyed(purpose, original).copyInto(data, start, 0, len)
    }

    /** A same-length, printable stand-in for a name, so string-length checks still replay. */
    private fun pseudonym(original: ByteArray, purpose: String): ByteArray {
        val k = keyed(purpose, original)
        return ByteArray(original.size) { i -> LETTERS[(k[i % k.size].toInt() and 0xFF) % LETTERS.length].code.toByte() }
    }

    private fun pseudonymInto(data: ByteArray, start: Int, len: Int, purpose: String) {
        pseudonym(data.copyOfRange(start, start + len), purpose).copyInto(data, start)
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val TRIM_M = 500.0
        private const val EARTH_M = 6_371_000.0
        private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private const val HEX_CHARS = "0123456789ABCDEFabcdef"

        /** WPS attributes that name or identify the device (Wi-Fi Simple Configuration). */
        private val WPS_IDENTITY = setOf(0x1011, 0x1021, 0x1023, 0x1024, 0x1042, 0x1047, 0x1048)

        /** Calls [visit] with (type, data start, data length) for each AD structure in a ScanRecord. */
        fun forEachAd(adv: ByteArray, visit: (type: Int, start: Int, len: Int) -> Unit) {
            var i = 0
            while (i < adv.size) {
                val len = adv[i].toInt() and 0xFF
                if (len == 0 || i + 1 + len > adv.size) break
                visit(adv[i + 1].toInt() and 0xFF, i + 2, len - 1)
                i += 1 + len
            }
        }

        fun macBytes(address: String): ByteArray? {
            val h = address.filter { it.isLetterOrDigit() }
            return if (h.length == 12 && h.all { it in HEX_CHARS }) unhex(h) else null
        }

        fun hex(bytes: ByteArray): String = CaptureRecord.hex(bytes)

        fun unhex(s: String): ByteArray {
            val h = s.filter { it.isLetterOrDigit() }
            return ByteArray(h.length / 2) { ((Character.digit(h[it * 2], 16) shl 4) or Character.digit(h[it * 2 + 1], 16)).toByte() }
        }

        private fun replaceAll(data: ByteArray, find: ByteArray, with: ByteArray) {
            if (find.isEmpty() || find.size > data.size) return
            var i = 0
            while (i <= data.size - find.size) {
                var match = true
                for (j in find.indices) if (data[i + j] != find[j]) { match = false; break }
                if (match) {
                    with.copyInto(data, i)
                    i += find.size
                } else {
                    i++
                }
            }
        }
    }
}
