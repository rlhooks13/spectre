package app.fieldwatch.domain

/** Compile-time switches for features that are off until a later PLAN step turns them on. */
object Features {
    /**
     * "Update stock catalog from GitHub". Off until Phase 4 (P4-6) brings back a signed,
     * NetworkPolicy-gated catalog update; until then the catalog ships inside the APK.
     */
    const val IN_APP_CATALOG_UPDATE = false
}
