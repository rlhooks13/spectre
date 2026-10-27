package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DebriefReport
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.PathCameras
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitRadio
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: Debrief text for 6 sits, built the way the app builds a sit debrief (FieldwatchViewModel.fieldDebriefDoc
 * over SitStore.debriefSource):
 * - Devices go through `SitRadio.from(...).toSighting()`, as a saved sit's do.
 * - The operator path has a fix every 5 seconds, as the recorder keeps a moving path.
 * - Radios that left are gone.
 * - A camera report is always passed.
 * - Place lookup is off.
 * - Signatures come from the stock catalog through the real matcher.
 *
 * Each sit is 20 minutes ending at [Golden.NOW]. Devices come from the match corpus with synthetic addresses,
 * and every position is near 0°, 0°.
 */
class DebriefGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val fleets = DefaultCatalog.fleets()
    private val start = Golden.NOW - 20 * 60_000L

    /** A fix every 5 s, [mPerMin] metres north per minute; 0 = standing still (a few metres of jitter). */
    private fun path(mPerMin: Double): List<GpsSample> = (0..240).map { i ->
        val north = if (mPerMin == 0.0) (i % 3) * 3.0 else i * mPerMin / 12.0
        GpsSample(start + i * 5_000L, 0.001 + north / 111_320.0, 0.002, 0)
    }

    /** The path fix at [minute] (fractional) of the sit. */
    private fun List<GpsSample>.at(minute: Double): GpsSample = this[(minute * 12).toInt().coerceIn(0, size - 1)]

    /**
     * [key]'s corpus device on its own [mac], heard every 30 s from minute [from] to [to] of the sit at the path's
     * fixes, with RSSI [rssi] at each minute. [firstSeen] can be earlier than the sit (a radio already heard when
     * it started). A radio last heard over a minute before the end is gone.
     */
    private fun heard(
        key: String,
        mac: String,
        path: List<GpsSample>,
        from: Double,
        to: Double,
        firstSeen: Long? = null,
        channel: Int = 0,
        name: String? = null,
        rssi: (Double) -> Int,
    ): Sighting {
        val minutes = generateSequence(from) { it + 0.5 }.takeWhile { it <= to }.toList()
        val trail = minutes.map { m -> path.at(m).copy(rssi = rssi(m)) }
        val levels = trail.map { it.rssi }
        val base = SitFixture.corpus(key)
        val last = trail.last().at
        return base.copy(
            key = "${base.kind.name}:$mac",
            mac = mac,
            name = name ?: base.name,
            rssi = levels.last(),
            rssiMin = levels.min(),
            rssiMax = levels.max(),
            channel = channel,
            frequencyMhz = if (base.kind == RadioKind.WIFI && channel > 0) 2407 + 5 * channel else 0,
            firstSeen = firstSeen ?: trail.first().at,
            lastSeen = last,
            gone = Golden.NOW - last > 60_000L,
            hitCount = trail.size * 6,
            gpsTrail = trail,
            latitude = trail.last().lat,
            longitude = trail.last().lon,
        )
    }

    private fun cameras(vararg cameras: FlockCamera): PathCameras.Report = SitFixture.cameraReport(*cameras)

    private fun debrief(
        name: String,
        devices: List<Sighting>,
        path: List<GpsSample>,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        mineKeys: Set<String> = emptySet(),
        cameras: PathCameras.Report = cameras(),
    ): String {
        val saved = SitFixture.saved(SitFixture.matched(devices, fleets), fleets)
        return DebriefReport.document(
            devices = saved,
            fleets = fleets,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = Golden.NOW,
            window = DebriefWindow(start, Golden.NOW, name),
            customNames = customNames,
            observerNotes = observerNotes,
            mineKeys = mineKeys,
            cameras = cameras,
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
                    heard("T:airtag", "7A:11:22:33:44:01", path, 0.0, 20.0, firstSeen = start - 5 * 60_000L) { -58 },
                    heard("T:flock", "B4:1E:52:0A:0B:0C", path, 2.0, 20.0, channel = 6) { -66 },
                ),
                path,
            ),
        )
    }

    /** A walk with an AirTag in the bag, already heard before the sit started and loud the whole way. */
    @Test
    fun tagInBagFromStart() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/2-tag-in-bag-from-start.txt",
            debrief(
                "Walk home",
                listOf(heard("T:airtag", "7A:11:22:33:44:01", path, 0.0, 20.0, firstSeen = start - 3 * 60_000L) { -50 - (it.toInt() % 6) }),
                path,
            ),
        )
    }

    /**
     * Two tags that join six minutes in and stay with the path to the end. The quieter SmartTag is a possible
     * tail. The loud AirTag is called "with you the whole sit" today, because the loud-cover OWN_LIKELY branch
     * doesn't check presence at the start; P1-B9 fixes that and re-blesses this golden.
     */
    @Test
    fun lateJoiningTags() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/3-late-joining-tags.txt",
            debrief(
                "Walk to the station",
                listOf(
                    heard("T:smarttag", "5E:11:22:33:44:02", path, 6.0, 20.0) { -64 - (it.toInt() % 5) },
                    heard("T:airtag", "7A:11:22:33:44:04", path, 6.0, 20.0) { -50 },
                ),
                path,
            ),
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
                    heard("T:smarttag", "5E:11:22:33:44:02", path, 3.0, 9.0) { -60 - ((it - 3) * 4).toInt() },
                    heard("T:tile-feed", "4A:11:22:33:44:06", path, 12.0, 13.0) { -70 },
                ),
                path,
            ),
        )
    }

    /**
     * A Galaxy phone and a SmartTag (on FD5A) in the same pocket for the whole walk. Since P1-B1 the phone matches
     * nothing; until then it was wrongly a SmartTag. The real SmartTag is a finder with you, and still gets the
     * Find My rotation sentence meant for Apple (P1-D7).
     */
    @Test
    fun galaxyPhoneInPocket() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/5-galaxy-phone-in-pocket.txt",
            debrief(
                "Errands",
                listOf(
                    heard("NM:galaxy", "6E:11:22:33:44:03", path, 0.0, 20.0, firstSeen = start - 60 * 60_000L) { -45 - (it.toInt() % 4) },
                    heard("T:smarttag", "5E:11:22:33:44:08", path, 0.0, 20.0, firstSeen = start - 60 * 60_000L) { -48 - (it.toInt() % 3) },
                ),
                path,
            ),
        )
    }

    /**
     * P1-B4: your own iPhone in a pocket for a whole errand (Continuity plus Offline Finding of type "Apple device").
     * It is assessed as a phone with you, never as a finder tag.
     */
    @Test
    fun iphoneInPocket() {
        val path = path(55.0)
        Golden.assertText(
            "debrief/7-iphone-in-pocket.txt",
            debrief(
                "Errands",
                listOf(
                    heard("NM:iphone", "4E:11:22:33:44:09", path, 0.0, 20.0, firstSeen = start - 60 * 60_000L) { -44 - (it.toInt() % 4) },
                ),
                path,
            ),
        )
    }

    /**
     * P1-B4: someone's iPhone fell in behind you four minutes into the walk, stayed loud across most of the path, and
     * dropped away four minutes before the end. A phone, not a finder, but it followed: the report still alerts.
     */
    @Test
    fun iphoneJoinsAndFollows() {
        val path = path(55.0)
        val phone = heard("NM:iphone", "4E:11:22:33:44:0A", path, 4.0, 16.0) { -46 - (it.toInt() % 3) }
        val saved = SitFixture.saved(SitFixture.matched(listOf(phone), fleets), fleets)
        val doc = DebriefReport.document(
            devices = saved,
            fleets = fleets,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = Golden.NOW,
            window = DebriefWindow(start, Golden.NOW, "Walk"),
            cameras = cameras(),
        )
        assertTrue("a following phone raises the tracking alert", doc.trackingAlert)
        Golden.assertText("debrief/8-iphone-joins-and-follows.txt", doc.toPlainText())
        val markdown = DebriefReport.gpsAnalystMarkdown(
            devices = saved,
            fleets = fleets,
            settings = AppSettings(tagLocation = true),
            operatorPath = path,
            now = Golden.NOW,
            window = DebriefWindow(start, Golden.NOW, "Walk"),
        )
        assertTrue(markdown.contains("### Phones with you (Apple phone"))
        assertTrue(markdown.contains("4E:11:22:33:44:0A"))
    }

    /**
     * A drive past two roadside cameras with your own AirTag in the car, marked mine and named, an observer note
     * on one camera, and one mapped plate reader beside the road. Each camera is heard along one stretch.
     */
    @Test
    fun driveWithMarkedMineTagPastCameras() {
        val path = path(400.0)
        val mine = heard("T:airtag", "7A:11:22:33:44:01", path, 0.0, 20.0, firstSeen = start - 10 * 60_000L) { -52 }
        val camA = heard("T:flock", "B4:1E:52:0A:0B:0C", path, 5.0, 6.0, channel = 6) { -70 }
        val camB = heard("T:flock", "B4:1E:52:0A:0B:0D", path, 14.0, 15.0, channel = 11, name = "Flock-3C4D") { -68 }
        val mapped = path.at(10.0).let { FlockCamera(key = 1L, lat = it.lat, lon = it.lon + 0.0002, facings = listOf(180), flock = true) }
        Golden.assertText(
            "debrief/6-drive-marked-mine-cameras.txt",
            debrief(
                "Drive to work",
                listOf(mine, camA, camB),
                path,
                customNames = mapOf(mine.key to "Car keys"),
                observerNotes = mapOf(camA.key to "Pole at the junction"),
                mineKeys = setOf(mine.key),
                cameras = cameras(mapped),
            ),
        )
    }
}
