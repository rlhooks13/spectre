package app.fieldwatch.testing

import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.LogRadio
import app.fieldwatch.domain.RadioKind

/**
 * Shared export fixtures (PLAN P1-A1: builders live here). Synthetic only: locally administered or
 * documentation addresses, made-up names, and points near 0°, 0°, so no bystander data.
 */
object LogRadios {
    fun radio(
        mac: String,
        kind: RadioKind = RadioKind.BLE,
        name: String = "",
        vendor: String? = null,
        rssi: Int = -60,
        lastSeen: Long = Golden.NOW,
        channel: Int = 0,
        latitude: Double? = 0.001,
        longitude: Double? = 0.002,
    ) = LogRadio(
        kind = kind,
        mac = mac,
        name = name,
        vendor = vendor,
        manufacturerId = null,
        manufacturerDataHex = "",
        serviceUuids = emptyList(),
        vendorIeOuis = emptyList(),
        randomized = kind == RadioKind.BLE,
        hiddenSsid = false,
        rssi = rssi,
        firstSeen = lastSeen - 60_000,
        lastSeen = lastSeen,
        hits = 3,
        channel = channel,
        latitude = latitude,
        longitude = longitude,
    )

    /** Two radios, one with a vendor and channel, plus a short operator path. */
    val sample: List<LogRadio> = listOf(
        radio("02:00:5E:10:00:01", RadioKind.WIFI, name = "Test AP", vendor = "Example Vendor", rssi = -48, channel = 6),
        radio("6A:1B:2C:3D:4E:5F", name = "Tag", rssi = -71, lastSeen = Golden.NOW + 5_000, latitude = 0.0015, longitude = 0.0025),
    )

    val track: List<GpsSample> = listOf(
        GpsSample(at = Golden.NOW - 30_000, lat = 0.0, lon = 0.0),
        GpsSample(at = Golden.NOW, lat = 0.001, lon = 0.002),
    )
}
