package app.fieldwatch.domain

/**
 * What kind of address a BLE radio advertises from (P1-B8). A signature that matched only through a vendor prefix
 * (an OUI, or a MAC prefix of at most 3 bytes) means something only on a stable address: a rotating one's leading
 * bytes are random, so a vendor-prefix "hit" there is chance. Behind [Features.BLE_ADDRESS_CLASSIFIER].
 */
enum class BleAddressClass {
    /** IEEE-assigned (API 35 addressType Public). */
    PUBLIC,

    /** Random static: random, but fixed until power cycle (API 35 Random with top bits 11). */
    STATIC,

    /** Resolvable or non-resolvable private: rotates (API 35 Random or Anonymous otherwise, or a rotating ecosystem). */
    ROTATING,

    /** No address type, but heard on one address without a break for [BleAddress.STABLE_MS]. */
    STABLE,

    /** No evidence either way. */
    UNKNOWN,
}

object BleAddress {
    /** How long one address must be heard without a break, with no address type, to count as stable. */
    const val STABLE_MS = 20 * 60_000L

    /**
     * Service UUIDs of ecosystems whose tags must rotate their address: Samsung SmartTag FD5A, exposure notification
     * FD6F, and IETF DULT FCB2. Google Find Hub is FEAA with a 0x40 or 0x41 frame ([isFindHub]): plain FEAA is
     * Eddystone, which fixed beacons on static addresses send.
     */
    private val ROTATING_UUIDS = setOf("FD5A", "FD6F", "FCB2")

    /** The only classes a vendor-prefix hit is definite on (the step: "a non-PUBLIC/STATIC address shows as possible"). */
    private val DEFINITE = setOf(BleAddressClass.PUBLIC, BleAddressClass.STATIC)

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
        if (longestPresenceMs(device) >= STABLE_MS) return BleAddressClass.STABLE
        return BleAddressClass.UNKNOWN
    }

    /**
     * [fleet], one [device] matched, hit only through a vendor prefix, and the address isn't PUBLIC or STATIC: the hit
     * shows as "possible", with no attention card or alert, until a non-address rule backs it up. STABLE doesn't count
     * as definite: a resolvable address held for 20 minutes (a connected phone, a separated tag) is still random, and
     * reading it as definite would let a chance collision with, say, Axon's OUI stand. Always false while the flag is off.
     */
    fun possibleOnly(
        device: Sighting,
        fleet: Fleet,
        engine: SignatureEngine,
        enabled: Boolean = Features.BLE_ADDRESS_CLASSIFIER,
    ): Boolean {
        if (!enabled || device.kind != RadioKind.BLE) return false
        if (classify(device) in DEFINITE) return false
        return engine.matchedOnlyByAddress(device, fleet)
    }

    private fun rotatingEcosystem(device: Sighting): Boolean {
        val records = device.facts.mfgRecords.ifEmpty {
            val id = device.manufacturerId ?: return@ifEmpty emptyList()
            listOf(MfgRecord(id, device.manufacturerDataHex))
        }
        // Apple's own frames (Continuity, Offline Finding) rotate. The iBeacon layout (02 15) on 0x004C doesn't count:
        // Minew, Kontakt, Estimote and other fixed beacons send it from static addresses.
        if (records.any { it.companyId == 0x004C && !it.dataHex.filter(Char::isLetterOrDigit).uppercase().startsWith("0215") }) return true
        val uuids = (device.serviceUuids + device.facts.serviceData.map { it.uuid }).flatMap(::uuidAliases).toSet()
        if (uuids.any { it in ROTATING_UUIDS }) return true
        return device.facts.serviceData.any(::isFindHub)
    }

    /** Google Find Hub: Eddystone service data FEAA whose frame type (first byte) is 0x40 or 0x41. */
    private fun isFindHub(record: ServiceDataRecord): Boolean {
        if ("FEAA" !in uuidAliases(record.uuid)) return false
        val frame = record.dataHex.filter(Char::isLetterOrDigit).take(2).uppercase()
        return frame == "40" || frame == "41"
    }

    /** The longest stretch heard without a break; with no presence spans, the whole sighting. */
    private fun longestPresenceMs(device: Sighting): Long {
        if (device.presence.isEmpty()) return device.lastSeen - device.firstSeen
        return device.presence.maxOf { (it.end ?: device.lastSeen) - it.start }
    }

    private fun topBits(mac: String): Int? = MacUtil.normalize(mac).substringBefore(":").toIntOrNull(16)?.let { it shr 6 }
}
