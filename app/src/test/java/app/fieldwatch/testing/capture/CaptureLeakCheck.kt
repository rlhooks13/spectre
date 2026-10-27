package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.NAME_AD_TYPES
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.forEachAd
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.forEachWpsAttribute
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.join
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.macBytes
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.unhex

/**
 * Leak test (P1-F3, ADR-0017): every original address, in either byte order, and every original name must be
 * gone from the scrubbed capture. It searches field values and whole-byte positions in payloads (including a
 * WPS element reassembled across IEs), not the JSON text, so a name like "WiFi" doesn't match the
 * `"type":"wifi"` key. Names are compared as bytes, so non-UTF-8 names count too. It finds the identifiers
 * these parsers know about; a non-empty result blocks the capture.
 */
object CaptureLeakCheck {
    /** Names shorter than this are too common to search for. */
    private const val MIN_NAME = 3

    /** WPS text attributes that can name a person or a unit: device name, model name, serial. */
    private val WPS_TEXT = setOf(0x1011, 0x1023, 0x1042)

    fun leaks(original: List<String>, scrubbed: List<String>): List<String> {
        val before = original.mapNotNull(CaptureRecord::decodeOrNull)
        val after = scrubbed.mapNotNull(CaptureRecord::decodeOrNull)
        val texts = after.flatMap(::textFields).map { it.lowercase() }
        val payloads = after.flatMap(::payloads)
        val found = mutableListOf<String>()

        for (address in addresses(before)) {
            val bytes = macBytes(address) ?: continue
            val canonical = bytes.joinToString(":") { "%02x".format(it) }
            if (texts.any { canonical in it } ||
                payloads.any { contains(it, bytes) || contains(it, bytes.reversedArray()) }
            ) {
                found += "address $address"
            }
        }
        for (name in names(before)) {
            val text = String(name, Charsets.UTF_8)
            val asText = !text.contains('�') && text.lowercase().let { t -> texts.any { t in it } }
            if (asText || payloads.any { contains(it, name) }) found += "name \"$text\""
        }
        return found.distinct()
    }

    private fun textFields(r: CaptureRecord): List<String> = when (r) {
        is CaptureRecord.Header -> listOf(r.app, r.model)
        is CaptureRecord.Ble -> listOf(r.addr)
        is CaptureRecord.Wifi -> listOfNotNull(r.bssid, r.ssid, r.mld, r.capabilities)
        is CaptureRecord.Fix -> listOf(r.provider)
        is CaptureRecord.Stats -> emptyList()
    }

    private fun payloads(r: CaptureRecord): List<ByteArray> = when (r) {
        is CaptureRecord.Ble -> listOf(unhex(r.adv))
        is CaptureRecord.Wifi -> {
            val ies = r.ies.map { unhex(it.hex) }
            val wps = ArrayList<ByteArray>()
            forEachWpsAttribute(r.ies.filter { it.id == 221 }.map { unhex(it.hex) }) { _, chunks -> wps += join(chunks) }
            ies + wps + listOfNotNull(r.ssidHex?.let(::unhex))
        }
        else -> emptyList()
    }

    private fun addresses(records: List<CaptureRecord>): Set<String> = records.flatMap { r ->
        when (r) {
            is CaptureRecord.Ble -> listOf(r.addr)
            is CaptureRecord.Wifi -> listOfNotNull(r.bssid, r.mld)
            else -> emptyList()
        }
    }.filter { it.isNotBlank() }.map { it.uppercase() }.toSet()

    private fun names(records: List<CaptureRecord>): List<ByteArray> = records.flatMap { r ->
        when (r) {
            is CaptureRecord.Ble -> bleNames(r)
            is CaptureRecord.Wifi -> wifiNames(r)
            else -> emptyList()
        }
    }.filter { n -> n.count { it.toInt() != 0 && it.toInt() != 0x20 } >= MIN_NAME }
        .distinctBy { CaptureScrubber.hex(it) }

    private fun bleNames(r: CaptureRecord.Ble): List<ByteArray> {
        val adv = unhex(r.adv)
        val names = mutableListOf<ByteArray>()
        forEachAd(adv) { type, start, len -> if ((type in NAME_AD_TYPES || type == 0x24) && len > 0) names += adv.copyOfRange(start, start + len) }
        return names
    }

    private fun wifiNames(r: CaptureRecord.Wifi): List<ByteArray> {
        val names = mutableListOf<ByteArray>()
        val ssid = r.ssidHex?.let(::unhex) ?: r.ssid.removeSurrounding("\"").toByteArray(Charsets.UTF_8)
        names += ssid
        r.ies.filter { it.id == 0 }.forEach { names += unhex(it.hex) }
        forEachWpsAttribute(r.ies.filter { it.id == 221 }.map { unhex(it.hex) }) { type, chunks ->
            if (type in WPS_TEXT) names += join(chunks)
        }
        return names
    }

    /** Whole-byte search: [needle] at any byte offset of [hay]. */
    private fun contains(hay: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > hay.size) return false
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
