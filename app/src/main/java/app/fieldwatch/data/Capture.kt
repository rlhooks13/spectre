package app.fieldwatch.data

import android.location.Location

/**
 * Hook for Capture v1 (P1-F1, ADR-0016). The radios and the location listener hand raw results here before
 * anything is decoded or dropped. [sink] stays null unless the debug build's `CaptureInstaller` sets it, so
 * release and staging builds record nothing and ship no recorder.
 */
object Capture {
    @Volatile
    var sink: CaptureSink? = null

    /** Hands a result to the sink, if any. A sink failure is swallowed so it can never cost a real observation. */
    inline fun record(block: (CaptureSink) -> Unit) {
        val s = sink ?: return
        try {
            block(s)
        } catch (_: Throwable) {
        }
    }
}

interface CaptureSink {
    fun ble(result: android.bluetooth.le.ScanResult)

    fun wifi(results: List<android.net.wifi.ScanResult>)

    /** [lastKnown]: the fix came from getLastKnownLocation at start or refresh, not from a listener. */
    fun fix(location: Location, lastKnown: Boolean = false)
}
