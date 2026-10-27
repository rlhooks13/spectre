package app.fieldwatch.testing.capture

import app.fieldwatch.domain.CaptureRecord
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.forEachAd
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.hex
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.macBytes
import app.fieldwatch.testing.capture.CaptureScrubber.Companion.unhex

/**
 * Leak test (P1-F3): every original address, in either byte order, and every original name must be gone
 * from the scrubbed capture, whether as text or as bytes inside a payload. Run on every capture before it
 * leaves the machine; a non-empty result blocks it (ADR-0017).
 */
object CaptureLeakCheck {
    /** Names shorter than this are too common to search for (a 2-letter name matches everywhere). */
    private const val MIN_NAME = 3

    fun leaks(original: List<String>, scrubbed: List<String>): List<String> {
        val records = original.mapNotNull(CaptureRecord::decodeOrNull)
        val out = scrubbed.joinToString("\n")
        val outUpper = out.uppercase()
        val found = mutableListOf<String>()

        for (address in addresses(records)) {
            val bytes = macBytes(address) ?: continue
            val forward = hex(bytes)
            val reversed = hex(bytes.reversedArray())
            if (address.uppercase() in outUpper || forward in outUpper || reversed in outUpper) found += "address $address"
        }
        for (name in names(records)) {
            val asHex = hex(name.toByteArray(Charsets.UTF_8))
            if (name.lowercase() in out.lowercase() || asHex in outUpper) found += "name \"$name\""
        }
        return found.distinct()
    }

    private fun addresses(records: List<CaptureRecord>): Set<String> = records.flatMap { r ->
        when (r) {
            is CaptureRecord.Ble -> listOf(r.addr)
            is CaptureRecord.Wifi -> listOfNotNull(r.bssid, r.mld)
            else -> emptyList()
        }
    }.filter { it.isNotBlank() }.toSet()

    private fun names(records: List<CaptureRecord>): Set<String> = records.flatMap { r ->
        when (r) {
            is CaptureRecord.Ble -> bleNames(r)
            is CaptureRecord.Wifi -> wifiNames(r)
            else -> emptyList()
        }
    }.filter { it.trim().length >= MIN_NAME }.toSet()

    private fun bleNames(r: CaptureRecord.Ble): List<String> {
        val adv = unhex(r.adv)
        val names = mutableListOf<String>()
        forEachAd(adv) { type, start, len ->
            if (type == 0x08 || type == 0x09) names += String(adv, start, len, Charsets.UTF_8)
        }
        return names
    }

    private fun wifiNames(r: CaptureRecord.Wifi): List<String> {
        val names = mutableListOf<String>()
        val ssid = r.ssidHex?.let { String(unhex(it), Charsets.UTF_8) } ?: r.ssid.removeSurrounding("\"")
        if (ssid.isNotBlank()) names += ssid
        for (ie in r.ies.filter { it.id == 221 }) {
            val data = unhex(ie.hex)
            if (data.size < 4 || hex(data.copyOf(4)) != "0050F204") continue
            var i = 4
            while (i + 4 <= data.size) {
                val type = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                if (i + 4 + len > data.size) break
                // Device name, model name, serial: the text fields that can name a person or a unit.
                if (type == 0x1011 || type == 0x1023 || type == 0x1042) names += String(data, i + 4, len, Charsets.UTF_8)
                i += 4 + len
            }
        }
        return names
    }
}
