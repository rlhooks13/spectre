package app.fieldwatch.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Capture v1 (P1-F1, ADR-0016): what the radios and GPS reported, before any decoding, one JSON object per
 * line. Written only by debug builds; read back by the scrubber (P1-F2) and CaptureReplay (P6-9).
 * Times: `tNs` is elapsed-realtime nanoseconds (monotonic, the radio's own stamp where it has one),
 * `wallMs` is the phone's clock when the app received it. Formats only grow (CLAUDE.md).
 */
@Serializable
sealed class CaptureRecord {
    /**
     * First line of each part. [wallMs] and [elapsedNs] were read together, so they map `tNs` to wall time.
     * [bootCount] changes across reboots, when `tNs` restarts. [model] is identifying next to a GPS track,
     * so the scrubber (P1-F2) coarsens or drops it.
     */
    @Serializable
    @SerialName("header")
    data class Header(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val app: String,
        val sdk: Int,
        val model: String,
        val wallMs: Long,
        val elapsedNs: Long? = null,
        val bootCount: Int? = null,
        val part: Int? = null,
    ) : CaptureRecord()

    /** Records the recorder couldn't keep up with since the last stats line. A capture with drops is incomplete. */
    @Serializable
    @SerialName("stats")
    data class Stats(val tNs: Long, val wallMs: Long, val dropped: Long) : CaptureRecord()

    /** One BLE advertisement report. Optional fields are null when Android says "not present". */
    @Serializable
    @SerialName("ble")
    data class Ble(
        val tNs: Long,
        val wallMs: Long,
        val addr: String,
        /** BluetoothDevice.ADDRESS_TYPE_* (API 35+), else null. */
        val addrType: Int? = null,
        val rssi: Int,
        val txPower: Int? = null,
        val primaryPhy: Int,
        val secondaryPhy: Int,
        val sid: Int? = null,
        val connectable: Boolean,
        /** In 1.25 ms units, as Android reports it. */
        val periodicInterval: Int? = null,
        /**
         * The whole ScanRecord, hex. For a legacy report it is advertising data plus scan response, zero-padded
         * to 62 bytes; for an extended one it is the real length.
         */
        val adv: String,
        /** Legacy (true) or extended (false) advertising PDU; null if unknown. */
        val legacy: Boolean? = null,
        /** ScanResult.DATA_* when not complete (e.g. truncated chained data); null when complete. */
        val dataStatus: Int? = null,
    ) : CaptureRecord()

    /** One Wi-Fi scan result, with every information element as received. */
    @Serializable
    @SerialName("wifi")
    data class Wifi(
        val tNs: Long,
        val wallMs: Long,
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val freq: Int,
        val centerFreq0: Int,
        val centerFreq1: Int,
        val channelWidth: Int,
        val standard: Int? = null,
        val capabilities: String,
        val ies: List<Ie> = emptyList(),
        /** The SSID's raw bytes (API 33+), since [ssid] text can't tell non-UTF-8 bytes from literal hex. */
        val ssidHex: String? = null,
        /** Wi-Fi 7 multi-link: the AP MLD address and this link's id (API 34+). The scrubber remaps [mld]. */
        val mld: String? = null,
        val linkId: Int? = null,
    ) : CaptureRecord()

    @Serializable
    data class Ie(val id: Int, val ext: Int? = null, val hex: String)

    /** One location fix, as the provider gave it. [fixTimeMs] is the provider's own UTC time (Location.time). */
    @Serializable
    @SerialName("fix")
    data class Fix(
        val tNs: Long,
        val wallMs: Long,
        val fixTimeMs: Long? = null,
        val provider: String,
        val lat: Double,
        val lon: Double,
        val alt: Double? = null,
        val acc: Float? = null,
        val speed: Float? = null,
        val bearing: Float? = null,
        /** From getLastKnownLocation at start or refresh; the app may use it, so replay must see it. */
        val lastKnown: Boolean? = null,
    ) : CaptureRecord()

    companion object {
        const val FORMAT = "spectre-capture"
        const val VERSION = 1

        private val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun encode(record: CaptureRecord): String = json.encodeToString(serializer(), record)

        fun decode(line: String): CaptureRecord = json.decodeFromString(serializer(), line)

        /**
         * For readers (scrubber, replay): null for a line that doesn't decode, such as the truncated last line
         * of a part whose process died mid-write, or a record type newer than this reader.
         */
        fun decodeOrNull(line: String): CaptureRecord? = runCatching { decode(line) }.getOrNull()

        fun hex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
            }
            return out.toString()
        }

        private const val HEX = "0123456789ABCDEF"
    }
}
