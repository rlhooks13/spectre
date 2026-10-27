package app.fieldwatch.domain

/**
 * The key revision that added each English key (ADR-0013). A pack's `keyRevision` says which revision its
 * template had; the confirm dialog counts untranslated keys added after it.
 *
 * Revision 1 is the map Fieldwatch 1.1.27 introduced, and every key not listed here. The revisions after it
 * follow each release that added keys, read from that release's commit. A new key goes in the newest
 * revision, which the first key-adding PR after a release creates by raising [NEWEST]. A key left out of
 * this table falls to revision 1, and `LanguageKeyRevisionTest` fails because revision 1 is frozen.
 */
object LanguageKeyRevisions {
    const val NEWEST = 6

    /** Fieldwatch release that shipped each revision, for packs that predate `keyRevision`. */
    val UPSTREAM_RELEASES: List<Pair<List<Int>, Int>> = listOf(
        listOf(1, 1, 27) to 1,
        listOf(1, 1, 28) to 2,
        listOf(1, 1, 29) to 3, // 1.1.30 added no keys
        listOf(1, 1, 31) to 4,
        listOf(1, 1, 32) to 5,
    )

    private val added: Map<String, Int> = buildMap {
        // 2: Fieldwatch 1.1.28.
        for (key in listOf(
            "display.live.path", "display.recenter", "ui.move.path", "ui.radios.here", "ui.waiting.gps.fix",
        )) put(key, 2)
        // 3: Fieldwatch 1.1.29.
        for (key in listOf("crash.closed", "crash.send")) put(key, 3)
        // 4: Fieldwatch 1.1.31.
        for (key in listOf(
            "hunt.heat.empty", "hunt.heat.moderate", "hunt.heat.poor", "hunt.heat.powerful", "hunt.heat.strong",
            "map.close.heading", "map.close.north", "map.esri.outdoor", "map.esri.satellite", "map.esri.streets",
            "map.key.denied.body", "map.key.denied.title", "map.key.failed.body", "map.key.failed.title",
            "map.key.hint", "map.key.label", "map.key.mark", "map.key.missing.body", "map.key.missing.title",
            "map.key.rejected.body", "map.key.rejected.title", "map.maptiler.outdoor", "map.maptiler.satellite",
            "map.maptiler.streets", "map.north.up", "map.openstreetmap", "map.position.gps", "map.rotate.travel",
            "map.stadia.satellite", "map.stadia.streets", "map.stadia.terrain", "map.thunderforest.outdoors",
            "map.thunderforest.streets", "map.thunderforest.transport", "map.usgs.imagery", "map.usgs.topo",
            "settings.map.about", "settings.map.choice", "settings.map.needs.online", "settings.map.position",
            "settings.map.position.about", "settings.topic.location.maps", "settings.topic.maps",
            "ui.aircraft.position", "ui.grid", "ui.range.bearing", "ui.rssi.band.unavailable", "ui.rssi.medium",
            "ui.rssi.not.available", "ui.rssi.strong", "ui.rssi.very.strong", "ui.rssi.very.weak", "ui.rssi.weak",
        )) put(key, 4)
        // 5: Fieldwatch 1.1.32.
        for (key in listOf(
            "alert.camera.ahead", "alert.camera.looking", "alert.flock.ahead", "alert.flock.looking",
            "alert.gun.ahead", "alert.plate.ahead", "alert.plate.looking", "alert.plate.unknown.ahead",
            "alert.plate.unknown.looking", "alert.red.ahead", "alert.red.looking", "alert.speed.ahead",
            "alert.speed.looking", "hunt.no.gps.walk", "map.faa.not.for.navigation", "map.faa.vfr",
            "map.united.states", "map.usgs.hydro", "map.usgs.imagerytopo", "map.usgs.relief", "map.zoom.in",
            "settings.layer.alert", "settings.layer.alert.camera", "settings.layer.alert.gun",
            "settings.layer.alert.plate", "settings.layer.alert.red", "settings.layer.alert.speed",
            "settings.layer.alerts", "settings.layer.channel", "settings.layer.channel.body", "settings.layer.current",
            "settings.layer.download.action", "settings.layer.download.body", "settings.layer.download.title",
            "settings.layer.downloading", "settings.layer.failed.first", "settings.layer.failed.keep",
            "settings.layer.flock", "settings.layer.heading", "settings.layer.more", "settings.layer.needs.network",
            "settings.layer.note", "settings.layer.other", "settings.layer.other.download.body",
            "settings.layer.other.download.title", "settings.layer.other.note", "settings.layer.processing",
            "settings.layer.saved", "settings.layer.update", "settings.map.clear", "settings.map.clear.body",
            "settings.map.overlays", "ui.cameras.along.line", "ui.cameras.along.note", "ui.cameras.along.path",
            "ui.cameras.facing.count", "ui.cameras.kind.flock", "ui.cameras.kind.other", "ui.cameras.kind.plate",
            "ui.cameras.kind.red", "ui.cameras.kind.speed", "ui.cameras.lists.missing", "ui.cameras.near.count",
            "ui.cameras.no.path", "ui.cameras.none.along", "ui.cameras.on.path", "ui.cameras.other.missing",
            "ui.cameras.plates.missing", "ui.cameras.within",
        )) put(key, 5)
        // 6: Spectre Phase 0.
        for (key in listOf(
            "ui.permissions.receive.only", "ui.scan.status.channel", "ui.export.shares.catalog.no.update",
            "settings.layer.download.source", "settings.layer.source", "settings.layer.alert.region",
            "ui.copyright.spectre", "ui.footer.operationally.sensitive",
        )) put(key, 6)
    }

    fun of(key: String): Int = added[key] ?: 1

    /** Every key listed above, for the test that keeps this table in step with the English map. */
    val listed: Set<String> get() = added.keys
}
