package app.fieldwatch.domain

/**
 * What kind of address a BLE radio advertises from (P1-B8). A signature that matched only through an address rule
 * (OUI or MAC prefix) means something only on a stable address: a rotating one's leading bytes are random, so an
 * OUI "hit" there is chance. Behind [Features.BLE_ADDRESS_CLASSIFIER].
 */
enum class BleAddressClass {
    /** IEEE-assigned (API 35 addressType Public). */
    PUBLIC,

    /** Random static: random, but fixed until power cycle (API 35 Random with top bits 11). */
    STATIC,

    /** Resolvable or non-resolvable private: rotates (API 35 Random, top bits 00 or 01, or an ecosystem that rotates). */
    ROTATING,

    /** No address type, but heard on one address for over [BleAddress.STABLE_MS]: stable in practice. */
    STABLE,

    /** No evidence either way. */
    UNKNOWN,
}

object BleAddress {
    /** How long one address must persist, with no address type, to count as stable. */
    const val STABLE_MS = 20 * 60_000L

    /** Service UUIDs of ecosystems whose BLE addresses always rotate: SmartTag FD5A, exposure notification FD6F, Find Hub FCB2. */
    private val ROTATING_UUIDS = setOf("FD5A", "FD6F", "FCB2")

    /**
     * Recomputed from the sighting as it stands, so every merge that adds an address type, a payload or more time
     * changes the answer with it. Only meaningful for BLE.
     */
    fun classify(device: Sighting): BleAddressClass {
        when (device.facts.addressType?.lowercase()) {
            "public" -> return BleAddressClass.PUBLIC
            "random" -> return if (topBits(device.mac) == 0b11) BleAddressClass.STATIC else BleAddressClass.ROTATING
            "anonymous" -> return BleAddressClass.ROTATING
        }
        if (rotatingEcosystem(device)) return BleAddressClass.ROTATING
        if (device.lastSeen - device.firstSeen >= STABLE_MS) return BleAddressClass.STABLE
        return BleAddressClass.UNKNOWN
    }

    /**
     * [fleet] hit [device] only through an address rule, and the address isn't known to be stable (PUBLIC, STATIC or
     * STABLE): the hit shows as "possible", with no attention card or alert, until a non-address rule backs it up.
     * Always false while the flag is off.
     */
    fun possibleOnly(
        device: Sighting,
        fleet: Fleet,
        engine: SignatureEngine,
        enabled: Boolean = Features.BLE_ADDRESS_CLASSIFIER,
    ): Boolean {
        if (!enabled || device.kind != RadioKind.BLE) return false
        if (classify(device) in setOf(BleAddressClass.PUBLIC, BleAddressClass.STATIC, BleAddressClass.STABLE)) return false
        return engine.matchedOnlyByAddress(device, fleet)
    }

    private fun rotatingEcosystem(device: Sighting): Boolean {
        val apple = device.manufacturerId == 0x004C || device.facts.mfgRecords.any { it.companyId == 0x004C }
        if (apple) return true
        val uuids = device.serviceUuids + device.facts.serviceData.map { it.uuid }
        return uuids.any { u -> shortUuid(u) in ROTATING_UUIDS }
    }

    private fun shortUuid(uuid: String): String {
        val u = uuid.uppercase()
        return if (u.length == 36 && u.startsWith("0000") && u.endsWith("-0000-1000-8000-00805F9B34FB")) u.substring(4, 8) else u
    }

    private fun topBits(mac: String): Int? = MacUtil.normalize(mac).substringBefore(":").toIntOrNull(16)?.let { it shr 6 }
}
