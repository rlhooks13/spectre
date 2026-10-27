package app.fieldwatch.testing

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.MatchCorpus
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.Sighting

/**
 * One synthetic sit for the export goldens (P1-A3): devices from the match corpus's pinned targets and a
 * near-miss, each given times, readings and a hear point near 0°, 0°. Signatures come from the stock catalog
 * through the real matcher, so a catalog fix shows up as a golden diff with a reason.
 */
object SitFixture {
    val fleets: List<Fleet> = DefaultCatalog.fleets()

    private fun target(key: String): Sighting = MatchCorpus.targets.first { it.sighting.key == key }.sighting
    private fun nearMiss(key: String): Sighting = MatchCorpus.nearMisses.first { it.sighting.key == key }.sighting

    /** [mac] replaces the corpus's shared default; the key becomes the app's `KIND:MAC`. */
    private fun Sighting.at(
        mac: String,
        minute: Int,
        rssi: Int,
        lat: Double,
        lon: Double,
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
            gpsTrail = listOf(GpsSample(last - 60_000L, lat, lon, rssi)),
        )
    }

    /** Unmatched devices first, so the ordering and the empty-signature cells are both covered. */
    val devices: List<Sighting> = run {
        val raw = listOf(
            target("T:flock").at("B4:1E:52:0A:0B:0C", 0, -55, 0.0012, 0.0021, 40, channel = 6, vendor = "Flock Safety"),
            target("T:airtag").at("7A:11:22:33:44:01", 2, -67, 0.0013, 0.0022, 25),
            target("T:smarttag").at("5E:11:22:33:44:02", 3, -72, 0.0014, 0.0023, 18),
            nearMiss("NM:galaxy").at("6E:11:22:33:44:03", 4, -80, 0.0015, 0.0024, 9),
        )
        val hits = SignatureEngine().match(raw, fleets, now = Golden.NOW)
        raw.map { it.copy(fleetIds = hits[it.key].orEmpty()) }
    }

    val customNames: Map<String, String> = mapOf(devices[1].key to "Bag tag?")
    val observerNotes: Map<String, String> = mapOf(devices[0].key to "On the pole by the gate")
}
