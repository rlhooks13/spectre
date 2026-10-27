package app.fieldwatch.testing

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.DebriefPrompt
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.DeviceDetailPrompt
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.PathCameras
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitDiffPrompt
import app.fieldwatch.domain.SitRadio
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: the 3 AI prompts (sit, one radio, sit compare) and both packs (settings, signatures), built the way
 * FieldwatchViewModel builds them. The sit is [SitFixture]'s plus its Remote ID drone and a mapped plate reader,
 * with place lookup off (`onlineLookup = false`). Each prompt is also pinned in Privacy mode, and those outputs are
 * checked to hold no fixture address, UAS id or coordinate.
 *
 * Not covered here: floods, GNSS marks and place records (the "seen at more than one place" block) are passed
 * empty. The mask-and-banner step is copied from the ViewModel's three AI exports, where it lives today.
 */
class PromptGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val version = "0.0.0-golden"
    private val window = DebriefWindow(Golden.NOW - 20 * 60_000L, Golden.NOW, "Golden sit")

    private fun settings(demo: Boolean) = AppSettings(tagLocation = true, onlineLookup = false, demoMode = demo)

    /** The sit plus the drone, as a saved sit holds them. */
    private fun devices(sit: SitFixture.Sit): List<Sighting> =
        SitFixture.saved(sit.devices + SitFixture.matched(listOf(SitFixture.drone()), sit.fleets), sit.fleets)

    /** As startAiExport / startDeviceDetailAiExport finish a prompt: mask, then the Privacy-mode banner. */
    private fun shareText(raw: String, devices: List<Sighting>, demo: Boolean): String {
        val masked = MacUtil.redactPrivateText(raw, devices.map { it.mac }, devices.mapNotNull { it.payloadUasId }, demo)
        return if (demo) {
            "Privacy mode: MAC tails are **:**:**. UAS ids and GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
        } else {
            masked
        }
    }

    /** A Privacy-mode prompt holds no fixture address tail, UAS id or coordinate. */
    private fun assertMasked(text: String, devices: List<Sighting>) {
        devices.forEach { d ->
            assertFalse("${d.mac} in privacy text", text.contains(d.mac))
            assertFalse("${d.mac} as hex in privacy text", text.contains(d.mac.replace(":", "")))
            d.payloadUasId?.let { assertFalse("$it in privacy text", text.contains(it)) }
        }
        assertFalse("a coordinate in privacy text", Regex("""\b0\.00[0-9]{2,}""").containsMatchIn(text))
    }

    private fun sitPrompt(demo: Boolean): String {
        val sit = SitFixture.build()
        val devices = devices(sit)
        val raw = DebriefPrompt.build(
            devices = devices,
            fleets = sit.fleets,
            settings = settings(demo),
            now = Golden.NOW,
            operatorPath = SitFixture.track,
            places = DebriefPlaces.Off,
            window = window,
            customNames = RadioBookmarks.labels(sit.watchlist),
            observerNotes = RadioBookmarks.notes(sit.watchlist),
            bookmarkedKeys = RadioBookmarks.alertDeviceKeys(sit.watchlist),
            mineKeys = RadioBookmarks.mineKeys(sit.watchlist),
            floods = emptyList(),
            gnss = emptyList(),
            placeRecords = emptyList(),
            cameras = SitFixture.cameraReport(SitFixture.mappedFlock),
        )
        return shareText(raw, devices, demo).also { if (demo) assertMasked(it, devices) }
    }

    @Test
    fun sitPrompt() = Golden.assertText("prompt/sit.md", sitPrompt(demo = false))

    @Test
    fun sitPromptInPrivacyMode() = Golden.assertText("prompt/sit-privacy.md", sitPrompt(demo = true))

    private fun radioPrompt(device: (SitFixture.Sit) -> Sighting, demo: Boolean): String {
        val sit = SitFixture.build()
        val d = device(sit)
        val byId = sit.fleets.associateBy { it.id }
        val raw = DeviceDetailPrompt.build(
            d, d.fleetIds.map { id -> byId[id]?.name ?: id }, settings(demo), DebriefPlaces.Off,
            now = Golden.NOW,
            attentionNotes = d.attentionNotes(sit.fleets),
            signatureNotes = d.signatureNotes(sit.fleets),
            fleets = sit.fleets,
            mine = d.key in RadioBookmarks.mineKeys(sit.watchlist),
            placeLines = emptyList(),
            demo = demo,
            phoneLat = SitFixture.track.last().lat,
            phoneLon = SitFixture.track.last().lon,
        )
        return shareText(raw, listOf(d), demo).also { if (demo) assertMasked(it, listOf(d)) }
    }

    @Test
    fun radioPromptCamera() = Golden.assertText("prompt/radio-flock.md", radioPrompt({ it.device(SitFixture.FLOCK) }, demo = false))

    @Test
    fun radioPromptCameraInPrivacyMode() =
        Golden.assertText("prompt/radio-flock-privacy.md", radioPrompt({ it.device(SitFixture.FLOCK) }, demo = true))

    @Test
    fun radioPromptTracker() = Golden.assertText("prompt/radio-airtag.md", radioPrompt({ it.device(SitFixture.AIRTAG) }, demo = false))

    @Test
    fun radioPromptTrackerInPrivacyMode() =
        Golden.assertText("prompt/radio-airtag-privacy.md", radioPrompt({ it.device(SitFixture.AIRTAG) }, demo = true))

    @Test
    fun radioPromptDroneInPrivacyMode() = Golden.assertText(
        "prompt/radio-drone-privacy.md",
        radioPrompt({ SitFixture.matched(listOf(SitFixture.drone()), it.fleets).single() }, demo = true),
    )

    /**
     * Sit compare, with sides built as compareSides() builds saved sits: this sit (with the drone) against an earlier
     * one that had only the camera and the AirTag.
     */
    private fun comparePrompt(demo: Boolean): String {
        val sit = SitFixture.build()
        val names = RadioBookmarks.labels(sit.watchlist)
        val notes = RadioBookmarks.notes(sit.watchlist)
        val bookmarked = RadioBookmarks.alertDeviceKeys(sit.watchlist)
        val mine = RadioBookmarks.mineKeys(sit.watchlist)
        fun side(name: String, devices: List<Sighting>, shiftMs: Long) = SitDiff.Side(
            name = name,
            ram = false,
            radios = devices.map { d ->
                val row = SitRadio.from(d, sit.fleets)
                SitDiff.fromSitRadio(
                    row.copy(firstSeen = row.firstSeen - shiftMs, lastSeen = row.lastSeen - shiftMs),
                    sit.fleets, names, notes, bookmarked, mine,
                )
            },
            path = SitFixture.track.map { it.copy(at = it.at - shiftMs) },
            floods = emptyList(),
            gnss = emptyList(),
        )
        val all = sit.devices + SitFixture.matched(listOf(SitFixture.drone()), sit.fleets)
        val thisSit = side("Golden sit", all, 0L)
        val earlier = side("Yesterday", listOf(sit.device(SitFixture.FLOCK), sit.device(SitFixture.AIRTAG)), 24 * 3_600_000L)
        return SitDiffPrompt.build(thisSit, earlier, demoMode = demo).also { if (demo) assertMasked(it, all) }
    }

    @Test
    fun sitComparePrompt() = Golden.assertText("prompt/sit-compare.md", comparePrompt(demo = false))

    @Test
    fun sitComparePromptInPrivacyMode() = Golden.assertText("prompt/sit-compare-privacy.md", comparePrompt(demo = true))

    /** The settings pack as the app exports it: the default config, plus the sit's watchlist rows. */
    @Test
    fun settingsPack() {
        val cfg = PersistedConfig()
        val pack = SettingsExchange.pack(
            settings = cfg.settings, filter = cfg.filter, presets = cfg.presets, watchlist = SitFixture.build().watchlist,
            hiddenPresetIds = cfg.hiddenPresetIds, appVersion = version, exportedAt = "2026-10-27T12:00:00Z",
        )
        Golden.assertJson("pack/settings.json", SettingsExchange.encode(pack))
    }

    /**
     * The signature pack's envelope and the rows a user changes: one signature made on the phone, and one stock
     * signature turned off. The app exports every fleet in the config. The stock rows' content isn't repeated here:
     * DefaultCatalog is the source, which StockCatalogFileTest and DefaultCatalogTest cover, and the encoding is
     * this same SignatureExchange.encode.
     */
    @Test
    fun signaturePack() {
        val stock = SitFixture.build().fleets
        val custom = Fleet(
            id = "fleet-user-golden",
            name = "Test doorbell",
            kind = SignatureClass.CAMERA,
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "Doorbell-TEST")),
            notes = "Made on this phone.",
        )
        val off = stock.first { it.id == "fleet-tile" }.copy(enabled = false)
        val pack = SignatureExchange.pack(
            // A fixed version, so a catalog bump doesn't re-bless this; the live value is ConfigStore.CATALOG_VERSION.
            fleets = listOf(custom, off), catalogVersion = 97, appVersion = version, exportedAt = "2026-10-27T12:00:00Z",
        )
        Golden.assertJson("pack/signatures.json", SignatureExchange.encode(pack))
    }
}
