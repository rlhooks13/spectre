package app.fieldwatch.testing

import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.DebriefPrompt
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.DeviceDetailPrompt
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.PathCameras
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitDiffPrompt
import app.fieldwatch.domain.SitRadio
import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import org.junit.Rule
import org.junit.Test

/**
 * P1-A3: the 3 AI prompts (sit, one radio, sit compare) and both packs (settings, signatures), built the way
 * FieldwatchViewModel builds them, from [SitFixture]'s sit with place lookup off. The sit and radio prompts are
 * also pinned in Privacy mode, which masks MAC tails, UAS ids and coordinates.
 */
class PromptGoldenTest {
    @get:Rule
    val golden = GoldenRule()

    private val version = "0.0.0-golden"
    private val window = DebriefWindow(Golden.NOW - 20 * 60_000L, Golden.NOW, "Golden sit")

    /** The sit as a saved sit holds it (SitRadio round trip), like DebriefGoldenTest. */
    private fun saved(sit: SitFixture.Sit): List<Sighting> = sit.devices.map { SitRadio.from(it, sit.fleets).toSighting() }

    /** As startAiExport / startDeviceDetailAiExport finish a prompt: mask, then the Privacy-mode banner. */
    private fun shareText(raw: String, devices: List<Sighting>, demo: Boolean): String {
        val masked = MacUtil.redactPrivateText(raw, devices.map { it.mac }, devices.mapNotNull { it.payloadUasId }, demo)
        return if (demo) {
            "Privacy mode: MAC tails are **:**:**. UAS ids and GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
        } else {
            masked
        }
    }

    private fun sitPrompt(demo: Boolean): String {
        val sit = SitFixture.build()
        val devices = saved(sit)
        val raw = DebriefPrompt.build(
            devices = devices,
            fleets = sit.fleets,
            settings = AppSettings(tagLocation = true, demoMode = demo),
            now = Golden.NOW,
            operatorPath = SitFixture.track,
            places = DebriefPlaces.Off,
            window = window,
            customNames = RadioBookmarks.labels(sit.watchlist),
            observerNotes = RadioBookmarks.notes(sit.watchlist),
            bookmarkedKeys = RadioBookmarks.alertDeviceKeys(sit.watchlist),
            mineKeys = RadioBookmarks.mineKeys(sit.watchlist),
            cameras = PathCameras.Report(plates = true, others = false, near = FlockIndex()::near),
        )
        return shareText(raw, devices, demo)
    }

    @Test
    fun sitPrompt() = Golden.assertText("prompt/sit.md", sitPrompt(demo = false))

    @Test
    fun sitPromptInPrivacyMode() = Golden.assertText("prompt/sit-privacy.md", sitPrompt(demo = true))

    private fun radioPrompt(mac: String, demo: Boolean): String {
        val sit = SitFixture.build()
        val device = sit.device(mac)
        val byId = sit.fleets.associateBy { it.id }
        val raw = DeviceDetailPrompt.build(
            device, device.fleetIds.mapNotNull { byId[it]?.name }, AppSettings(tagLocation = true, demoMode = demo), DebriefPlaces.Off,
            now = Golden.NOW,
            attentionNotes = device.attentionNotes(sit.fleets),
            signatureNotes = device.signatureNotes(sit.fleets),
            fleets = sit.fleets,
            mine = sit.watchlist.any { it.deviceKey == device.key && it.mine },
            placeLines = emptyList(),
            demo = demo,
            phoneLat = SitFixture.track.last().lat,
            phoneLon = SitFixture.track.last().lon,
        )
        return shareText(raw, listOf(device), demo)
    }

    @Test
    fun radioPromptCamera() = Golden.assertText("prompt/radio-flock.md", radioPrompt(SitFixture.FLOCK, demo = false))

    @Test
    fun radioPromptTrackerInPrivacyMode() = Golden.assertText("prompt/radio-airtag-privacy.md", radioPrompt(SitFixture.AIRTAG, demo = true))

    /** Sit compare: this sit against an earlier one that had the camera and the AirTag but not the others. */
    @Test
    fun sitComparePrompt() {
        val sit = SitFixture.build()
        val names = RadioBookmarks.labels(sit.watchlist)
        val notes = RadioBookmarks.notes(sit.watchlist)
        val mine = RadioBookmarks.mineKeys(sit.watchlist)
        fun side(name: String, devices: List<Sighting>, shiftMs: Long) = SitDiff.Side(
            name = name,
            ram = false,
            radios = devices.map { d ->
                val row = SitRadio.from(d, sit.fleets)
                SitDiff.fromSitRadio(
                    row.copy(firstSeen = row.firstSeen - shiftMs, lastSeen = row.lastSeen - shiftMs), sit.fleets, names, notes, mineKeys = mine,
                )
            },
            path = SitFixture.track.map { it.copy(at = it.at - shiftMs) },
        )
        val thisSit = side("Golden sit", sit.devices, 0L)
        val earlier = side("Yesterday", listOf(sit.device(SitFixture.FLOCK), sit.device(SitFixture.AIRTAG)), 24 * 3_600_000L)
        Golden.assertText("prompt/sit-compare.md", SitDiffPrompt.build(thisSit, earlier, demoMode = false))
    }

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
     * A signature pack with one user-made signature and one stock signature turned off. The full stock pack is
     * `dist/fieldwatch-signatures-v2.json`, which StockCatalogFileTest already pins.
     */
    @Test
    fun signaturePack() {
        val stock = SitFixture.build().fleets
        val custom = Fleet(
            id = "fleet-user-golden",
            name = "Neighbour's doorbell",
            kind = SignatureClass.CAMERA,
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "Doorbell-4C2A")),
            notes = "Made on this phone.",
        )
        val off = stock.first { it.id == "fleet-tile" }.copy(enabled = false)
        val pack = SignatureExchange.pack(
            // A live config carries the stock catalog's version.
            fleets = listOf(custom, off), catalogVersion = ConfigStore.CATALOG_VERSION, appVersion = version, exportedAt = "2026-10-27T12:00:00Z",
        )
        Golden.assertJson("pack/signatures.json", SignatureExchange.encode(pack))
    }
}
