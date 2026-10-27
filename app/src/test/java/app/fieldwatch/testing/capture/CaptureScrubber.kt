package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import app.fieldwatch.domain.Geo
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scrubber v1 (P1-F2, ADR-0017): field-level, before a capture leaves the agent's machine for the private
 * corpus. Use [scrubPart]: each call gets a fresh secret that is never stored, so the same original maps to
 * the same stand-in within the part and to nothing outside it. A part never spans a reboot.
 *
 * - Addresses (BLE, BSSID, MLD, the BLE identity address in AD 0x1B) are remapped. A universal address keeps
 *   its OUI and a random BLE address keeps its two type bits, so vendor rules and the address classifier
 *   still replay. The same 6 bytes anywhere in a payload, in either byte order, get the same stand-in.
 * - Names become same-length stand-ins: BLE AD 0x08/0x09/0x30 and URI 0x24, SSIDs (text, raw bytes, IE 0),
 *   and WPS identity attributes (also when split across IEs).
 * - Payloads that carry identities are remapped whole after their header: Wi-Fi P2P and Wi-Fi Display IEs,
 *   Reduced Neighbor Report (with its Short SSID), Multiple BSSID, Mesh ID, Interworking (HESSID), the
 *   Multi-Link element, Cisco's AP-name IE. Rotating and hashed ids are remapped: Apple Offline Finding
 *   (0x12), AirDrop (0x05) and Nearby Action (0x0F) bodies and the Nearby Info (0x10) auth tag, FD5A
 *   (SmartTag) and FEAA (Eddystone, Find Hub) except TLM telemetry.
 * - Times shift by a secret offset with a sub-day part. Altitude shifts by a secret offset. Only reliable
 *   GPS fixes are kept (provider gps, accuracy within 50 m, not last-known). Every fix and record within 500 m
 *   (straight line) of where the path starts or ends is dropped; the rest is rotated and moved to a synthetic
 *   origin. A path that never gets 500 m from both ends keeps no fixes.
 * - The header drops the phone model and boot count.
 */
class CaptureScrubber private constructor(random: java.util.Random) {
    private val key = ByteArray(32).also(random::nextBytes)
    private val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
    private val wallShiftMs = (30L + random.nextInt(335)) * DAY_MS * (if (random.nextBoolean()) 1 else -1) +
        (random.nextDouble() * DAY_MS).toLong()
    private val tShiftNs = (1L + random.nextInt(1_000_000)) * 1_000_000_000L
    private val altShiftM = (random.nextDouble() - 0.5) * 1_000.0
    private val angle = random.nextDouble() * 2 * PI

    /** Original address (6 bytes as a Long) to its stand-in, in both byte orders. */
    private val embedded = HashMap<Long, ByteArray>()
    private val addressMap = HashMap<Long, ByteArray>()
    private val standIns = HashSet<Long>()

    private fun scrub(lines: List<String>, alsoAddresses: Collection<String>): List<String> {
        val records = lines.mapNotNull(CaptureRecord::decodeOrNull)
        require(records.count { it is CaptureRecord.Header } <= 1) { "Scrub one capture part at a time (one boot each)." }
        for (r in records) collectAddresses(r)
        // Other parts' addresses fill in only what this part didn't map itself. Radio unknown: treated as Wi-Fi,
        // which keeps the OUI only for a universal address.
        for (a in alsoAddresses) mapAddress(a, ble = false, public = false)
        val reliable = records.filterIsInstance<CaptureRecord.Fix>().filter(::reliable).sortedBy { it.tNs }
        val window = keptWindow(reliable)
        val kept = reliable.filter { window.keepsFix(it) }
        val center = centroid(kept)
        return records.mapNotNull { r ->
            when (r) {
                is CaptureRecord.Header -> r.copy(
                    model = "scrubbed", bootCount = null, wallMs = r.wallMs + wallShiftMs, elapsedNs = r.elapsedNs?.plus(tShiftNs),
                )
                is CaptureRecord.Stats -> if (window.keeps(r.tNs)) r.copy(tNs = r.tNs + tShiftNs, wallMs = r.wallMs + wallShiftMs) else null
                is CaptureRecord.Ble -> if (window.keeps(r.tNs)) scrubBle(r) else null
                is CaptureRecord.Wifi -> if (window.keeps(r.tNs)) scrubWifi(r) else null
                is CaptureRecord.Fix -> if (r in kept && center != null) scrubFix(r, center) else null
            }
        }.map(CaptureRecord::encode)
    }

    // ---- addresses ----

    private fun collectAddresses(r: CaptureRecord) {
        when (r) {
            is CaptureRecord.Ble -> {
                mapAddress(r.addr, ble = true, public = r.addrType == 0)
                // AD 0x1B LE Bluetooth Device Address: type byte, then the identity address little-endian.
                val adv = unhex(r.adv)
                forEachAd(adv) { type, start, len ->
                    if (type == 0x1B && len >= 7) {
                        val le = adv.copyOfRange(start, start + 6)
                        mapAddress(le.reversedArray().joinToString(":") { "%02X".format(it) }, ble = true, public = adv[start + 6].toInt() == 0)
                    }
                }
            }
            is CaptureRecord.Wifi -> {
                mapAddress(r.bssid, ble = false, public = false)
                r.mld?.let { mapAddress(it, ble = false, public = false) }
            }
            else -> Unit
        }
    }

    private fun mapAddress(address: String, ble: Boolean, public: Boolean): String {
        val bytes = macBytes(address) ?: return address
        val original = long6(bytes, 0)
        val mapped = addressMap.getOrPut(original) {
            var out: ByteArray
            var attempt = 0
            do {
                out = keyed("addr$attempt", bytes).copyOf(6)
                when {
                    // Universal Wi-Fi or public BLE: keep the OUI.
                    (!ble && bytes[0].toInt() and 0x02 == 0) || (ble && public) -> System.arraycopy(bytes, 0, out, 0, 3)
                    // Random BLE: keep the two type bits (static 11, resolvable 01, non-resolvable 00).
                    ble -> out[0] = ((bytes[0].toInt() and 0xC0) or (out[0].toInt() and 0x3F)).toByte()
                    // Local Wi-Fi: keep the local and multicast bits.
                    else -> out[0] = ((bytes[0].toInt() and 0x03) or (out[0].toInt() and 0xFC)).toByte()
                }
                attempt++
            } while (!standIns.add(long6(out, 0))) // two devices never merge
            // Placeholders (all zeros, all ones, Android's 02:00:00:00:00:00) aren't searched for in payloads,
            // where they'd match padding.
            if (original !in DEGENERATE) {
                embedded[original] = out
                embedded[long6(bytes.reversedArray(), 0)] = out.reversedArray()
            }
            out
        }
        return mapped.joinToString(":") { "%02X".format(it) }
    }

    /** One pass over 6-byte windows: each original address, forward or reversed, becomes its stand-in. */
    private fun remapEmbeddedAddresses(data: ByteArray) {
        var i = 0
        while (i + 6 <= data.size) {
            val hit = embedded[long6(data, i)]
            if (hit != null) {
                hit.copyInto(data, i)
                i += 6
            } else {
                i++
            }
        }
    }

    // ---- BLE ----

    private fun scrubBle(r: CaptureRecord.Ble): CaptureRecord.Ble {
        val adv = unhex(r.adv)
        forEachAd(adv) { type, start, len ->
            when (type) {
                in NAME_AD_TYPES, 0x24 -> pseudonymInto(adv, start, len, "name")
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

    /** Apple: walk the Continuity TLVs and remap the rotating or hashed parts; type and length bytes stay. */
    private fun scrubManufacturer(adv: ByteArray, start: Int, len: Int) {
        if (len < 3) return
        val company = (adv[start].toInt() and 0xFF) or ((adv[start + 1].toInt() and 0xFF) shl 8)
        if (company != 0x004C) return
        val end = start + len
        var i = start + 2
        while (i + 2 <= end) {
            val type = adv[i].toInt() and 0xFF
            val tlvLen = adv[i + 1].toInt() and 0xFF
            val body = i + 2
            val bodyLen = minOf(end, body + tlvLen) - body
            when (type) {
                0x12 -> if (bodyLen > 1) keyedInto(adv, body + 1, bodyLen - 1, "findmy") // after the status byte
                0x05, 0x0F -> if (bodyLen > 0) keyedInto(adv, body, bodyLen, "apple$type") // AirDrop, Nearby Action hashes
                0x10 -> if (bodyLen > 2) keyedInto(adv, body + 2, bodyLen - 2, "apple10") // Nearby Info auth tag
            }
            i = body + tlvLen
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
        val ies = r.ies.map { ie -> ie to unhex(ie.hex) }
        for ((ie, data) in ies) {
            when {
                // IE 0 from its own bytes, so it matches the stand-in whenever it equals the SSID.
                ie.id == 0 && data.any { it.toInt() != 0 } -> pseudonym(data, "ssid").copyInto(data)
                ie.id in WHOLE_BODY_IES -> keyedInto(data, 0, data.size, "ie${ie.id}")
                ie.id == EXTENSION_ID && ie.ext == MULTI_LINK_EXT -> keyedInto(data, 0, data.size, "ml")
                ie.id == 221 && data.size > 4 && hex(data.copyOf(4)) in WHOLE_BODY_VENDOR -> keyedInto(data, 4, data.size - 4, "v")
            }
        }
        // WPS can be split across several vendor IEs; its attributes are walked as one stream.
        forEachWpsAttribute(ies.filter { it.first.id == 221 }.map { it.second }) { type, chunks ->
            if (type in WPS_IDENTITY) {
                val replaced = pseudonym(join(chunks), "wps")
                var at = 0
                for ((data, s, l) in chunks) {
                    replaced.copyInto(data, s, at, at + l)
                    at += l
                }
            }
        }
        for ((_, data) in ies) remapEmbeddedAddresses(data)
        return r.copy(
            tNs = r.tNs + tShiftNs,
            wallMs = r.wallMs + wallShiftMs,
            bssid = mapAddress(r.bssid, ble = false, public = false),
            ssid = if (stand == null) r.ssid else ssidText(r.ssid, stand),
            ssidHex = r.ssidHex?.let { if (stand == null) it else hex(stand) },
            ies = ies.map { (ie, data) -> ie.copy(hex = hex(data)) },
            mld = r.mld?.let { mapAddress(it, ble = false, public = false) },
        )
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

    /** Network, coarse and last-known fixes can sit hundreds of metres off; they never anchor or survive. */
    private fun reliable(f: CaptureRecord.Fix) =
        f.provider == "gps" && f.lastKnown != true && (f.acc == null || f.acc <= MAX_ACC_M)

    private inner class Window(val start: Long, val end: Long, private val anchors: List<Pair<Double, Double>>?) {
        fun keeps(t: Long) = t in start..end
        fun keepsFix(f: CaptureRecord.Fix) = anchors != null && keeps(f.tNs) &&
            anchors.all { (lat, lon) -> Geo.meters(lat, lon, f.lat, f.lon) >= TRIM_M }
    }

    /**
     * Anchors are the medians of the first and last few reliable fixes, so one stray fix can't move them.
     * The window runs from the last time the track is within 500 m of the start to the first time it comes
     * within 500 m of the end, measured in a straight line: jitter at home never counts as travel.
     */
    private fun keptWindow(fixes: List<CaptureRecord.Fix>): Window {
        if (fixes.isEmpty()) return Window(Long.MIN_VALUE, Long.MAX_VALUE, anchors = null)
        val startAnchor = median(fixes.take(ANCHOR_FIXES))
        val endAnchor = median(fixes.takeLast(ANCHOR_FIXES))
        fun near(a: Pair<Double, Double>, f: CaptureRecord.Fix) = Geo.meters(a.first, a.second, f.lat, f.lon) < TRIM_M
        val leftStart = fixes.indexOfLast { near(startAnchor, it) } + 1
        val reachesEnd = fixes.indices.firstOrNull { it >= leftStart && near(endAnchor, fixes[it]) } ?: fixes.size
        // A path that never leaves either end by 500 m could be a home or a workplace: no fixes, radio stays.
        if (leftStart >= reachesEnd) return Window(Long.MIN_VALUE, Long.MAX_VALUE, anchors = null)
        return Window(fixes[leftStart].tNs, fixes[reachesEnd - 1].tNs, anchors = listOf(startAnchor, endAnchor))
    }

    private fun median(fixes: List<CaptureRecord.Fix>): Pair<Double, Double> {
        val lats = fixes.map { it.lat }.sorted()
        val lons = fixes.map { it.lon }.sorted()
        return lats[lats.size / 2] to lons[lons.size / 2]
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
            alt = r.alt?.plus(altShiftM),
            bearing = r.bearing?.let { (((it - Math.toDegrees(angle)) % 360 + 360) % 360).toFloat() },
        )
    }

    // ---- keyed replacement ----

    private fun keyed(purpose: String, original: ByteArray): ByteArray {
        val out = ByteArray(original.size.coerceAtLeast(32))
        var counter = 0
        var filled = 0
        while (filled < out.size) {
            mac.update(purpose.toByteArray())
            mac.update(counter.toByte())
            val block = mac.doFinal(original)
            val n = minOf(block.size, out.size - filled)
            System.arraycopy(block, 0, out, filled, n)
            filled += n
            counter++
        }
        return out
    }

    private fun keyedInto(data: ByteArray, start: Int, len: Int, purpose: String) {
        if (len <= 0) return
        val original = data.copyOfRange(start, start + len)
        keyed(purpose, original).copyInto(data, start, 0, len)
    }

    /** A same-length, printable stand-in for a name, so string-length checks still replay. */
    private fun pseudonym(original: ByteArray, purpose: String): ByteArray {
        val k = keyed(purpose, original)
        return ByteArray(original.size) { i -> LETTERS[(k[i % k.size].toInt() and 0xFF) % LETTERS.length].code.toByte() }
    }

    private fun pseudonymInto(data: ByteArray, start: Int, len: Int, purpose: String) {
        if (len <= 0) return
        pseudonym(data.copyOfRange(start, start + len), purpose).copyInto(data, start)
    }

    companion object {
        /**
         * Scrubs one capture part with a fresh secret. [alsoAddresses] are addresses seen in the capture's
         * other parts, so a copy of one inside this part's payloads is remapped too. [random] is for tests;
         * production uses SecureRandom.
         */
        fun scrubPart(
            lines: List<String>,
            random: java.util.Random = SecureRandom(),
            alsoAddresses: Collection<String> = emptyList(),
        ): List<String> = CaptureScrubber(random).scrub(lines, alsoAddresses)

        private const val DAY_MS = 86_400_000L
        private const val TRIM_M = 500.0
        private const val MAX_ACC_M = 50f
        private const val ANCHOR_FIXES = 5
        private const val EARTH_M = 6_371_000.0
        private const val EXTENSION_ID = 255
        private const val MULTI_LINK_EXT = 107
        private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private const val HEX_CHARS = "0123456789ABCDEFabcdef"

        private val DEGENERATE = setOf(0L, 0xFFFFFFFFFFFFL, 0x020000000000L)

        /** Multiple BSSID (71), Interworking (107), Mesh ID (114), Cisco AP name (133), Reduced Neighbor Report (201). */
        private val WHOLE_BODY_IES = setOf(71, 107, 114, 133, 201)

        /** Wi-Fi P2P (50:6F:9A type 09) and Wi-Fi Display (50:6F:9A type 0A): device names and addresses. */
        private val WHOLE_BODY_VENDOR = setOf("506F9A09", "506F9A0A")

        /** Shortened and complete local name, and Broadcast Name. */
        val NAME_AD_TYPES = setOf(0x08, 0x09, 0x30)

        /** WPS attributes that name or identify the device or its clients (Wi-Fi Simple Configuration). */
        val WPS_IDENTITY = setOf(0x1011, 0x1020, 0x1021, 0x1023, 0x1024, 0x1042, 0x1047, 0x1048, 0x1049)

        /**
         * Calls [visit] with (type, data start, data length) for each AD structure in a ScanRecord. A zero
         * length byte is padding and is stepped over, so a scan response after padded advertising data is
         * still visited. A structure whose length runs past the buffer (truncated data) is visited to the end.
         */
        fun forEachAd(adv: ByteArray, visit: (type: Int, start: Int, len: Int) -> Unit) {
            var i = 0
            while (i + 1 < adv.size) {
                val len = adv[i].toInt() and 0xFF
                if (len == 0) {
                    i++
                    continue
                }
                val start = i + 2
                visit(adv[i + 1].toInt() and 0xFF, start, minOf(len - 1, adv.size - start))
                i += 1 + len
            }
        }

        /**
         * Walks the WPS attributes (2-byte type, 2-byte length) across every WPS vendor IE (00:50:F2 type 4),
         * as one stream. [visit] gets each attribute's value as (ie bytes, start, length) chunks, since one value
         * can straddle two IEs.
         */
        fun forEachWpsAttribute(vendorIes: List<ByteArray>, visit: (type: Int, chunks: List<Triple<ByteArray, Int, Int>>) -> Unit) {
            val parts = vendorIes.filter { it.size >= 4 && hex(it.copyOf(4)) == "0050F204" }
            val stream = ArrayList<Pair<ByteArray, Int>>()
            for (p in parts) for (i in 4 until p.size) stream += p to i
            fun byteAt(n: Int) = stream[n].first[stream[n].second].toInt() and 0xFF
            var n = 0
            while (n + 4 <= stream.size) {
                val type = (byteAt(n) shl 8) or byteAt(n + 1)
                val len = (byteAt(n + 2) shl 8) or byteAt(n + 3)
                val valueStart = n + 4
                val valueEnd = minOf(stream.size, valueStart + len)
                val chunks = ArrayList<Triple<ByteArray, Int, Int>>()
                var k = valueStart
                while (k < valueEnd) {
                    val (data, at) = stream[k]
                    var run = 1
                    while (k + run < valueEnd && stream[k + run].first === data && stream[k + run].second == at + run) run++
                    chunks += Triple(data, at, run)
                    k += run
                }
                visit(type, chunks)
                n = valueStart + len
            }
        }

        fun join(chunks: List<Triple<ByteArray, Int, Int>>): ByteArray =
            chunks.fold(ByteArray(0)) { acc, (d, s, l) -> acc + d.copyOfRange(s, s + l) }

        fun macBytes(address: String): ByteArray? {
            val h = address.filter { it.isLetterOrDigit() }
            return if (h.length == 12 && h.all { it in HEX_CHARS }) unhex(h) else null
        }

        fun long6(b: ByteArray, at: Int): Long {
            var v = 0L
            for (j in 0 until 6) v = (v shl 8) or (b[at + j].toLong() and 0xFF)
            return v
        }

        fun hex(bytes: ByteArray): String = CaptureRecord.hex(bytes)

        fun unhex(s: String): ByteArray {
            val h = s.filter { it.isLetterOrDigit() }
            return ByteArray(h.length / 2) { ((Character.digit(h[it * 2], 16) shl 4) or Character.digit(h[it * 2 + 1], 16)).toByte() }
        }
    }
}
