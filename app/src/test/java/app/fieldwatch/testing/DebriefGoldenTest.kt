package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DebriefReport
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.MatchCorpus
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.Sighting
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: Debrief text for 6 sits, built the way the app builds a sit debrief (FieldwatchViewModel.fieldDebriefDoc):
 * the sit's devices, path and window, place lookup off, signatures from the stock catalog through the real matcher.
 * Each sit is 20 minutes ending at [Golden.NOW]. Devices come from the match corpus with synthetic addresses, and
 * every position is near 0°, 0°.
 */
class DebriefGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val fleets = DefaultCatalog.fleets()
    private val start = Golden.NOW - 20 * 60_000L
    private val window = { name: String -> DebriefWindow(start, Golden.NOW, name) }

    /** A path sampled every minute, [mPerMin] metres north per minute; 0 = standing still (a few metres of jitter). */
    private fun path(mPerMin: Double): List<GpsSample> = (0..20).map { i ->
        val north = if (mPerMin == 0.0) (i % 3) * 3.0 else i * mPerMin
        GpsSample(start + i * 60_000L, 0.001 + north / 111_320.0, 0.002, 0)
    }

    private fun corpus(key: String): Sighting =
        (MatchCorpus.targets.map { it.sighting } + MatchCorpus.nearMisses.map { it.sighting }).first { it.key == key }

    /**
     * [key]'s corpus device on its own [mac], heard from minute [from] to [to] of the sit at the path's fixes,
     * with RSSI [rssi] at each minute (a function, so a device can fade).
     */
    private fun heard(key: String, mac: String, path: List<GpsSample>, from: Int, to: Int, rssi: (Int) -> Int): Sighting {
        val trail = path.subList(from, to + 1).mapIndexed { i, s -> s.copy(rssi = rssi(from + i)) }
        val levels = trail.map { it.rssi }
        val base = corpus(key)
        return base.copy(
            key = "${base.kind.name}:$mac",
            mac = mac,
            rssi = levels.last(),
            rssiMin = levels.min(),
            rssiMax = levels.max(),
            firstSeen = trail.first().at,
            lastSeen = trail.last().at,
            hitCount = trail.size * 6,
            gpsTrail = trail,
            latitude = trail.last().lat,
            longitude = trail.last().lon,
        )
    }

    private fun debrief(
        name: String,
        devices: List<Sighting>,
        path: List<GpsSample>,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        mineKeys: Set<String> = emptySet(),
    ): String {
        val hits = SignatureEngine().match(devices, fleets, now = Golden.NOW)
        val matched = devices.map { it.copy(fleetIds = hits[it.key].orEmpty()) }
        return DebriefReport.document(
            devices = matched,
            fleets = fleets,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = Golden.NOW,
            window = window(name),
            customNames = customNames,
            observerNotes = observerNotes,
            mineKeys = mineKeys,
        ).toPlainText()
    }

    /** Standing still at a café: trackers can't be tested for following; a camera and an AirTag are near. */
    @Test
    fun stationaryCafe() {
        val path = path(0.0)
        Golden.assertText(
            "debrief/1-stationary-cafe.txt",
            debrief(
                "Café stop",
                listOf(
                    heard("T:airtag", "7A:11:22:33:44:01", path, 0, 20) { -58 },
                    heard("T:flock", "B4:1E:52:0A:0B:0C", path, 2, 20) { -66 },
                ),
                path,
            ),
        )
    }

    /** A walk with an AirTag in the bag from the start: loud the whole way, so a possible tracker with you. */
    @Test
    fun tagInBagFromStart() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/2-tag-in-bag-from-start.txt",
            debrief("Walk home", listOf(heard("T:airtag", "7A:11:22:33:44:01", path, 0, 20) { -50 - it % 6 }), path),
        )
    }

    /** A SmartTag that joins six minutes in and stays loud with the path to the end: a possible tail. */
    @Test
    fun lateJoiningTag() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/3-late-joining-tag.txt",
            debrief("Walk to the station", listOf(heard("T:smarttag", "5E:11:22:33:44:02", path, 6, 20) { -64 - it % 5 }), path),
        )
    }

    /** Pass-bys on a walk: a tag that fades as you leave it, and a Tile heard briefly at one spot. Neither is a tail. */
    @Test
    fun passBys() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/4-pass-bys.txt",
            debrief(
                "Market walk",
                listOf(
                    heard("T:smarttag", "5E:11:22:33:44:02", path, 3, 9) { -60 - (it - 3) * 4 },
                    heard("T:tile-feed", "4A:11:22:33:44:06", path, 12, 13) { -70 },
                ),
                path,
            ),
        )
    }

    /**
     * A Galaxy phone in a pocket for the whole walk. Today it is wrongly matched as a Samsung SmartTag, so the
     * Debrief calls it a possible tracker (severity 1). P1-B1 fixes the match and re-blesses this golden.
     */
    @Test
    fun galaxyPhoneInPocket() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/5-galaxy-phone-in-pocket.txt",
            debrief("Errands", listOf(heard("NM:galaxy", "6E:11:22:33:44:03", path, 0, 20) { -45 - it % 4 }), path),
        )
    }

    /**
     * A drive past roadside cameras with your own AirTag in the car, marked mine and named, plus an observer
     * note on one camera. Cameras are heard at one stretch each.
     */
    @Test
    fun driveWithMarkedMineTagPastCameras() {
        val path = path(400.0)
        val mine = heard("T:airtag", "7A:11:22:33:44:01", path, 0, 20) { -52 }
        val camA = heard("T:flock", "B4:1E:52:0A:0B:0C", path, 5, 6) { -70 }
        val camB = heard("T:flock", "B4:1E:52:0A:0B:0D", path, 14, 15) { -68 }
        Golden.assertText(
            "debrief/6-drive-marked-mine-cameras.txt",
            debrief(
                "Drive to work",
                listOf(mine, camA, camB),
                path,
                customNames = mapOf(mine.key to "Car keys"),
                observerNotes = mapOf(camA.key to "Pole at the junction"),
                mineKeys = setOf(mine.key),
            ),
        )
    }
}
