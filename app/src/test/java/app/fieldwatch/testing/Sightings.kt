package app.fieldwatch.testing

import app.fieldwatch.domain.MfgRecord
import app.fieldwatch.domain.RadioFacts
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting

/** Shared test builders (PLAN P1-A1: they move to `:core:testing` in Phase 2). */
object Sightings {
    /**
     * A sighting with only what matching reads. [key] must be unique within one `SignatureEngine.match` call.
     * The default MAC's OUI is in no catalog row.
     */
    fun sighting(
        key: String,
        kind: RadioKind = RadioKind.BLE,
        mac: String = "AA:BB:CC:00:00:01",
        name: String = "",
        serviceUuids: List<String> = emptyList(),
        mfg: List<MfgRecord> = emptyList(),
        serviceData: List<ServiceDataRecord> = emptyList(),
        vendorIeOuis: List<String> = emptyList(),
        hiddenSsid: Boolean = false,
    ) = Sighting(
        key = key,
        kind = kind,
        mac = mac,
        name = name,
        rssi = -60,
        rssiMin = -60,
        rssiMax = -60,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = kind == RadioKind.BLE,
        hiddenSsid = hiddenSsid,
        serviceUuids = serviceUuids,
        manufacturerId = mfg.firstOrNull()?.companyId,
        manufacturerDataHex = mfg.firstOrNull()?.dataHex.orEmpty(),
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        vendorIeOuis = vendorIeOuis,
        facts = RadioFacts(mfgRecords = mfg, serviceData = serviceData),
    )

    /** "B41E52" or "b4:1e:52" plus [tail] hex, padded to a full MAC. */
    fun mac(prefix: String, tail: String = "000001"): String {
        val hex = (prefix.filter { it.isLetterOrDigit() }.uppercase() + tail).padEnd(12, '0').take(12)
        return hex.chunked(2).joinToString(":")
    }
}
