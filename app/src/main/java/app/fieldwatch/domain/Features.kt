package app.fieldwatch.domain

/**
 * Compile-time switches for features that are off until a later PLAN step turns them on. Every switch that changes
 * detection or what is sent belongs here, as a `const val` Boolean: the release notes list these (P0-8d).
 */
object Features {
    /**
     * "Update stock catalog from GitHub". Off until Phase 4 (P4-6) brings back a signed,
     * NetworkPolicy-gated catalog update; until then the catalog ships inside the APK.
     */
    const val IN_APP_CATALOG_UPDATE = false

    /**
     * The "Other sensors" map layer (speed and red-light cameras, gunshot detectors; OpenStreetMap
     * data under ODbL). Off until P1-E5 ships `scripts/build_sensors.py` and a list this project
     * builds and hosts; it used to download upstream's copy.
     */
    const val OTHER_SENSORS_LAYER = false

    /**
     * P1-B8's BLE address classifier ([BleAddress]): an address-only signature hit on a rotating BLE address reads as
     * "possible", with no attention card or alert. Field-dependent, so off until its acceptance check passes: the
     * heuristic agrees with API 35 addressType over a 24 h capture on the dedicated phone, and the static-tag checks
     * pass (PLAN, feature-flag acceptance).
     */
    const val BLE_ADDRESS_CLASSIFIER = false

    /**
     * P1-B9's late-tail fix in the Debrief's co-travel test ([DebriefReport]): a radio that joined after the sit
     * started is never "with you the whole sit", its coverage counts from when it was first heard, and a tail needs
     * DebriefReport's minimum distance and time of it (shorter is listed as "too soon to tell"). Field-dependent, so
     * off until its acceptance check passes: at least 3 owner-recorded captures with owner-written ground truth,
     * including a no-tail commute and a tag-joins-late walk, and the preconditions in PLAN (P1-B9).
     */
    const val LATE_TAIL_FIX = false
}

/**
 * Settings as the app may act on them: a switch for a feature that is off reads as off. Applied
 * where settings are loaded and updated (ConfigStore), so every reader sees the same value.
 */
fun AppSettings.withFeatureGates(): AppSettings =
    if (sensorLayer && !Features.OTHER_SENSORS_LAYER) copy(sensorLayer = false) else this
