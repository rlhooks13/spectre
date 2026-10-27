package app.fieldwatch.testing

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.MatchCorpus
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.attentionNotes

/**
 * One synthetic sit for the export goldens (P1-A3): devices from the match corpus's pinned targets and a
 * near-miss, plus one unmatched radio with no position. Each gets its own made-up address, times before
 * [Golden.NOW], readings and a hear point near 0°, 0°. Signatures come from the stock catalog through the
 * real matcher at [Golden.NOW], so a catalog fix shows up as a golden diff with a reason.
 *
 * [build] runs inside a test, after [GoldenRule] has pinned the zone, locale and overlay.
 */
object SitFixture {
    class Sit(
        val fleets: List<Fleet>,
        val devices: List<Sighting>,
        val customNames: Map<String, String>,
        val observerNotes: Map<String, String>,
        val mineKeys: Set<String>,
    ) {
        /** As the app derives it (FieldwatchViewModel.sitExportText): every device with attention notes. */
        val extraKeys: Set<String> get() = devices.filter { it.attentionNotes(fleets).isNotEmpty() }.map { it.key }.toSet()

        fun device(mac: String): Sighting = devices.first { it.mac == mac }

        /** The watchlist rows the app keeps for these names, notes and mine marks (one row per radio). */
        val watchlist: List<app.fieldwatch.domain.WatchTarget>
            get() = (customNames.keys + observerNotes.keys + mineKeys).distinct().map { key ->
                app.fieldwatch.domain.WatchTarget(
                    id = "w-$key", deviceKey = key, label = customNames[key].orEmpty(),
                    observerNotes = observerNotes[key].orEmpty(), mine = key in mineKeys,
                )
            }
    }

    const val FLOCK = "B4:1E:52:0A:0B:0C"
    const val AIRTAG = "7A:11:22:33:44:01"
    const val SMARTTAG = "5E:11:22:33:44:02"
    const val GALAXY = "6E:11:22:33:44:03"
    const val NO_FIX = "02:00:5E:10:00:09"

    fun build(): Sit {
        val fleets = DefaultCatalog.fleets()
        val raw = listOf(
            target("T:flock").at(FLOCK, -4, -55, 0.0012, 0.0021, 40, channel = 6, vendor = "Flock Safety"),
            target("T:airtag").at(AIRTAG, -3, -67, 0.0013, 0.0022, 25),
            target("T:smarttag").at(SMARTTAG, -2, -72, 0.0014, 0.0023, 18),
            // Wrongly matched "Samsung SmartTags" today (a severity-1 false label); P1-B1 clears it as a golden diff.
            nearMiss("NM:galaxy").at(GALAXY, -1, -80, 0.0015, 0.0024, 9),
            // Matches nothing and has no fix: empty signature cells, null lat/lon, and left out of map exports.
            // A non-ASCII name checks UTF-8 through every format.
            target("T:flock").at(NO_FIX, 0, -86, null, null, 2, channel = 11).copy(name = "Café-AP"),
        )
        val devices = matched(raw, fleets)
        val key = { mac: String -> devices.first { it.mac == mac }.key }
        return Sit(
            fleets = fleets,
            devices = devices,
            // Quotes, commas, apostrophes, ampersands, angle brackets and a newline: CSV and XML escaping.
            customNames = mapOf(key(AIRTAG) to "Tom's \"bag\", left & <right>"),
            observerNotes = mapOf(key(FLOCK) to "On the pole by the gate,\nfacing the road"),
            mineKeys = setOf(key(GALAXY)),
        )
    }

    /** A hand-written match-corpus device (target or near-miss) by its corpus key. */
    fun corpus(key: String): Sighting =
        (MatchCorpus.targets.map { it.sighting } + MatchCorpus.nearMisses.map { it.sighting }).first { it.key == key }

    /** [devices] as a saved sit holds them (`SitRadio.from(...).toSighting()`, as SitStore.debriefSource gives them). */
    fun saved(devices: List<Sighting>, fleets: List<Fleet>): List<Sighting> =
        devices.map { app.fieldwatch.domain.SitRadio.from(it, fleets).toSighting() }

    /** A camera report as the app passes one: plate-reader list present, holding [cameras]. */
    fun cameraReport(vararg cameras: app.fieldwatch.domain.FlockCamera): app.fieldwatch.domain.PathCameras.Report {
        val index = app.fieldwatch.domain.FlockIndex()
        if (cameras.isNotEmpty()) index.replace(cameras.toList())
        return app.fieldwatch.domain.PathCameras.Report(plates = true, others = false, near = index::near)
    }

    /** A mapped plate reader on the operator path's middle fix, so the camera-on-path lines are pinned. */
    val mappedFlock = app.fieldwatch.domain.FlockCamera(key = 7L, lat = 0.0013, lon = 0.0022, facings = emptyList(), flock = true)

    /** [devices] with their signatures from the real matcher at [Golden.NOW]. */
    fun matched(devices: List<Sighting>, fleets: List<Fleet>): List<Sighting> {
        val hits = SignatureEngine().match(devices, fleets, now = Golden.NOW)
        return devices.map { it.copy(fleetIds = hits[it.key].orEmpty()) }
    }

    private fun target(key: String): Sighting = corpus(key)
    private fun nearMiss(key: String): Sighting = corpus(key)

    /** [mac] replaces the corpus's shared default; the key becomes the app's `KIND:MAC`. Last seen [minute]s from NOW. */
    private fun Sighting.at(
        mac: String,
        minute: Int,
        rssi: Int,
        lat: Double?,
        lon: Double?,
        hits: Int,
        channel: Int = 0,
        vendor: String? = null,
    ): Sighting {
        val last = Golden.NOW + minute * 60_000L
        return copy(
            key = "${kind.name}:$mac",
            mac = mac,
            rssi = rssi,
            rssiMin = rssi - 9,
            rssiMax = rssi + 4,
            channel = channel,
            frequencyMhz = if (kind == RadioKind.WIFI && channel > 0) 2407 + 5 * channel else 0,
            vendor = vendor,
            firstSeen = last - 10 * 60_000L,
            lastSeen = last,
            hitCount = hits,
            gpsTrail = if (lat != null && lon != null) listOf(GpsSample(last - 30_000L, lat, lon, rssi)) else emptyList(),
            // The operator's fix at the last hear, which live TAK pins use.
            latitude = lat,
            longitude = lon,
        )
    }

    /**
     * A Remote ID drone matched through FFFA (not in [build]'s sit; add it where a test needs one), decoded
     * mid-flight with its pilot's live GPS. The serial is made up. Its times all differ, so goldens show which one
     * each event uses: last hear NOW-5 s, newest flight fix and decode NOW-9 s, pilot fix NOW-12 s.
     */
    fun drone(): Sighting = Sightings.sighting(
        key = "BLE:4E:11:22:33:44:07",
        mac = "4E:11:22:33:44:07",
        serviceUuids = listOf("FFFA"),
    ).copy(
        rssi = -74, rssiMin = -82, rssiMax = -70,
        firstSeen = Golden.NOW - 6 * 60_000L, lastSeen = Golden.NOW - 5_000L, hitCount = 120,
        latitude = 0.0015, longitude = 0.0024,
        gpsTrail = listOf(GpsSample(Golden.NOW - 5_000L, 0.0015, 0.0024, -74)),
        payloadUasId = "TESTSERIAL0000000001",
        payloadLat = 0.0021, payloadLon = 0.0031, payloadAlt = 86.0, payloadHeading = 270.0, payloadSpeed = 6.5,
        payloadAt = Golden.NOW - 9_000L,
        payloadOpLat = 0.0018, payloadOpLon = 0.0029, payloadOpKind = 1, payloadOpAlt = 4.0,
        payloadOpAt = Golden.NOW - 12_000L,
        // The advertised flight so far, ending at the current position.
        payloadTrail = listOf(
            app.fieldwatch.domain.PayloadFix(Golden.NOW - 129_000L, 0.0019, 0.0035, 80.0, 270.0, 6.0),
            app.fieldwatch.domain.PayloadFix(Golden.NOW - 69_000L, 0.0020, 0.0033, 84.0, 270.0, 6.3),
            app.fieldwatch.domain.PayloadFix(Golden.NOW - 9_000L, 0.0021, 0.0031, 86.0, 270.0, 6.5),
        ),
    )

    /** The operator's path across the sit, ending at NOW. */
    val track: List<GpsSample> = listOf(
        GpsSample(at = Golden.NOW - 5 * 60_000L, lat = 0.0011, lon = 0.0020),
        GpsSample(at = Golden.NOW - 150_000L, lat = 0.0013, lon = 0.0022),
        GpsSample(at = Golden.NOW, lat = 0.0015, lon = 0.0024),
    )
}
