package app.fieldwatch.domain

/** Every kind of traffic the app sends. `EgressInventoryTest` keeps the code to these five places. */
enum class Egress {
    /** Map tiles for the Live, Hunt and Reports maps and PDFs (`PathTiles`). */
    MAP_TILES,

    /** Reverse geocoding through the system Geocoder (`PlaceLookup`). */
    PLACE_NAMES,

    /** The stock signature pack from GitHub (`CatalogRemote`). */
    CATALOG_UPDATE,

    /** A sensor list the user confirmed in Settings (`FlockFiles`). */
    SENSOR_LIST,

    /** New Cursor-on-Target packets to the TAK destination (`TakPublisher`). */
    TAK_PUBLISH,

    /** Tombstones that withdraw spots already published, when the feed or Privacy turns off. */
    TAK_RETRACT,
}

/**
 * The one check before any network call (P1-A7). Each egress function asks it right before it connects,
 * with the settings as they are now, so a screen's stale "online" value or a queued job can't send.
 * Until [ConfigStore][app.fieldwatch.data.ConfigStore] has loaded settings, nothing is allowed.
 */
object NetworkPolicy {
    @Volatile
    var settings: AppSettings? = null

    fun allows(egress: Egress): Boolean = allows(egress, settings)

    fun allows(egress: Egress, settings: AppSettings?): Boolean {
        if (settings == null) return false
        return when (egress) {
            Egress.MAP_TILES -> settings.onlineLookup
            Egress.PLACE_NAMES -> settings.onlineLookup && !settings.demoMode
            Egress.CATALOG_UPDATE -> Features.IN_APP_CATALOG_UPDATE
            // The user confirms each download in a dialog that names the source; that is the consent.
            Egress.SENSOR_LIST -> true
            Egress.TAK_PUBLISH -> settings.takEnabled && !settings.demoMode
            // Withdrawing what was already sent protects the user, so it never waits on the switch that
            // just turned off. It only targets spots the feed published.
            Egress.TAK_RETRACT -> true
        }
    }
}
