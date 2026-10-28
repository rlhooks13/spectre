package app.fieldwatch.domain

/**
 * BLE/Wi-Fi RSSI as this phone received it (dBm). A received signal is −127..−1 (P1-B7). Bluetooth uses 127 for
 * “not available”, and 0 is what an unset or unmeasured reading holds (a Wi-Fi scan with no level, a default):
 * neither is transmit power or a real hear.
 */
object Rssi {
    fun measured(rssi: Int): Boolean = rssi in -127..-1

    /** At least [floor] dBm, as received: an unmeasured reading (0, 127) never passes a loudness bar. */
    fun atLeast(rssi: Int, floor: Int): Boolean = measured(rssi) && rssi >= floor

    /** For "loudest first" ordering: an unmeasured reading ranks below every received one, not above. */
    fun rank(rssi: Int): Int = if (measured(rssi)) rssi else Int.MIN_VALUE

    fun sessionRange(min: Int, max: Int, history: List<RssiSample> = emptyList()): String {
        val vals = ArrayList<Int>(history.size + 2)
        if (measured(min)) vals += min
        if (measured(max)) vals += max
        for (s in history) if (measured(s.rssi)) vals += s.rssi
        if (vals.isEmpty()) return "Not available"
        val lo = vals.min()
        val hi = vals.max()
        return if (lo == hi) "$lo dBm" else "$lo to $hi dBm"
    }

    fun lastMeasured(rssi: Int, history: List<RssiSample>): Int? {
        if (measured(rssi)) return rssi
        return history.asReversed().firstOrNull { measured(it.rssi) }?.rssi
    }
}
