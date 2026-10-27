# Fieldwatch 1.1.32 files (importer fixtures)

These files were written by the Fieldwatch 1.1.32 code itself, at upstream commit `819365d` ("Ship Fieldwatch
1.1.32."), from synthetic inputs, with each store's own Json settings. No phone and no capture was involved:
the addresses are made up, the places are near 0°, 0°, and the names are test names. Spectre's
`Fieldwatch1132DecodeTest` reads them the way today's app does, and its goldens are under
`golden/import-1.1.32/`. They are the importer's fixtures (PLAN P1-A3).

| File | Written by 1.1.32's | What's in it |
|---|---|---|
| `config.json` | ConfigStore Json (pretty, encodeDefaults) | The whole stock catalog with its decode maps, Tile turned off, one signature made on the phone, a user filter preset (OR logic, two classes), a watchlist (a named mine radio, a camera with a note, a signature watch), a hidden stock chip and an install tag. |
| `sits/sit-1132-golden.json` | SitStore Json (encodeDefaults) | A 20-minute sit: an AirTag with a live-decode chip, a Flock camera, a three-point path, a pairing flood and a GNSS mark. |
| `places.json` | PlaceStore Json (encodeDefaults = false) | One radio at two places. No `format` key: it equals the default, so 1.1.32 never wrote it. `localDay` is derived from the time, as PlaceStore does. |
| `settings-pack.json` | `SettingsExchange.encode` | The config's settings, filter, preset, watchlist and hidden chip. |
| `signature-pack.json` | `SignatureExchange.encode` | The custom signature and the stock Tile row (decode included), turned off. |

To regenerate: `git worktree add <dir> 819365d`, add the test below as
`app/src/test/java/app/fieldwatch/Write1132FixturesTest.kt` there, and run
`./gradlew -p <dir> testDebugUnitTest --tests '*Write1132FixturesTest*'`. It writes straight into this folder (`OUT`).
Then remove the worktree. Never commit the test to Spectre: it only compiles against 1.1.32.

```kotlin
package app.fieldwatch

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FloodBurst
import app.fieldwatch.domain.GnssMark
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.LiveDecodeChip
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.PlaceMemory
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SitSession
import app.fieldwatch.domain.WatchTarget
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

class Write1132FixturesTest {
    private val now = 1_793_102_400_000L // 2026-10-27T12:00:00Z
    private val out = File(System.getProperty("fixturesOut") ?: OUT).apply { mkdirs() }

    private fun radio(key: String, name: String, kind: RadioKind, rssi: Int, fleetIds: Set<String>, first: Long, last: Long, at: List<Pair<Double, Double>>) =
        Sighting(
            key = key, kind = kind, mac = key.substringAfter(':'), name = name, rssi = rssi, rssiMin = rssi - 8, rssiMax = rssi + 3,
            channel = if (kind == RadioKind.WIFI) 6 else 0, frequencyMhz = if (kind == RadioKind.WIFI) 2437 else 0, vendor = null,
            randomized = kind == RadioKind.BLE, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
            manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = first, lastSeen = last, hitCount = 12,
            fleetIds = fleetIds, rssiHistory = emptyList(), presence = emptyList(),
        ).copy(
            latitude = at.last().first, longitude = at.last().second,
            gpsTrail = at.mapIndexed { i, (lat, lon) -> GpsSample(first + i * 60_000L, lat, lon, rssi) },
        )

    private val custom = Fleet(
        id = "fleet-user-1132", name = "Test doorbell", kind = SignatureClass.CAMERA,
        rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "Doorbell-TEST")), notes = "Made on this phone.",
    )

    private val fleets: List<Fleet> = DefaultCatalog.fleets().map { if (it.id == "fleet-tile") it.copy(enabled = false) else it } + custom

    private val watchlist = listOf(
        WatchTarget(id = "w-tag", deviceKey = "BLE:7A:11:22:33:44:01", label = "Bag tag", mine = true),
        WatchTarget(id = "w-cam", deviceKey = "WIFI:B4:1E:52:0A:0B:0C", label = "Gate camera", observerNotes = "On the pole by the gate"),
        WatchTarget(id = "w-flock", fleetId = "fleet-flock-cameras", label = "Flock Safety Cameras"),
    )

    private val preset = FilterPreset(
        id = "user-finders", name = "Finders, either radio",
        filter = FilterState(logic = FilterLogic.OR, useClassFilter = true, classes = setOf(SignatureClass.FINDER, SignatureClass.BEACON)),
    )

    @Test
    fun write() {
        val configJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        val config = PersistedConfig(
            version = ConfigStore.CATALOG_VERSION, fleets = fleets, presets = listOf(preset), watchlist = watchlist,
            settings = AppSettings(tagLocation = true), arrivalKnownKeys = setOf("BLE:7A:11:22:33:44:01"),
            hiddenPresetIds = setOf("wifi"), takInstallTag = "AB23",
        )
        File(out, "config.json").writeText(configJson.encodeToString(config))

        val start = now - 20 * 60_000L
        val tag = radio("BLE:7A:11:22:33:44:01", "", RadioKind.BLE, -52, setOf("fleet-airtag"), start, start + 60_000L, listOf(0.0011 to 0.0020))
            .copy(liveDecode = listOf(LiveDecodeChip("separated", emphasis = true, note = "Away from its owner.")))
        val cam = radio("WIFI:B4:1E:52:0A:0B:0C", "Flock-1A2B", RadioKind.WIFI, -66, setOf("fleet-flock-cameras"), start + 120_000L, start + 180_000L, listOf(0.0012 to 0.0021))
        val session = SitSession.start(
            "Walk to the station", start, listOf(tag), fleets, setOf(tag.key, cam.key), setOf("fleet-flock-cameras"), id = "sit-1132-golden",
        )
        listOf(0.0011 to 0.0020, 0.0013 to 0.0022, 0.0015 to 0.0024).forEachIndexed { i, (lat, lon) ->
            session.recordPath(lat, lon, start + i * 600_000L)
        }
        session.ingest(cam, fleets, setOf(tag.key, cam.key), setOf("fleet-flock-cameras"))
        session.ingest(tag.copy(lastSeen = now - 30_000L, rssi = -50, gpsTrail = tag.gpsTrail + GpsSample(now - 30_000L, 0.0015, 0.0024, -50)), fleets, setOf(tag.key), emptySet())
        session.noteFloods(listOf(FloodBurst(at = start + 300_000L, popupCount = 14, nameCount = 6, families = listOf("Apple proximity pairing"), medianRssi = -58, keys = listOf("BLE:6A:00:00:00:00:01"))))
        session.noteGnss(listOf(GnssMark(at = start + 420_000L, endedAt = start + 480_000L, kind = "jamming", confidence = "Low", detail = "Signal-to-noise dropped on every constellation")))
        val sitJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        File(out, "sits").mkdirs()
        File(out, "sits/sit-1132-golden.json").writeText(sitJson.encodeToString(session.end(now, fleets)))

        val placeJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val lastHeard = now - 30_000L
        val records = listOf(
            PlaceMemory.Record(
                id = "BLE:7A:11:22:33:44:01", deviceKey = "BLE:7A:11:22:33:44:01",
                places = listOf(
                    PlaceMemory.Place(0.0011, 0.0020, now - 86_400_000L, now - 86_000_000L, 3),
                    PlaceMemory.Place(0.0015, 0.0024, now - 1_200_000L, lastHeard, 4, name = "Test Street"),
                ),
                lastHeardAt = lastHeard, returnAt = now - 1_200_000L, returnGapMs = 84_800_000L, sitId = "sit-1132-golden",
                localDay = PlaceMemory.localDate(lastHeard, ZoneOffset.UTC).toEpochDay(),
            ),
        )
        val places = buildJsonObject { put("records", placeJson.encodeToJsonElement(ListSerializer(PlaceMemory.Record.serializer()), records)) }
        File(out, "places.json").writeText(placeJson.encodeToString(places))

        File(out, "settings-pack.json").writeText(
            SettingsExchange.encode(
                SettingsExchange.pack(config.settings, config.filter, config.presets, watchlist, config.hiddenPresetIds, "1.1.32", "2026-10-27T12:00:00Z"),
            ),
        )
        File(out, "signature-pack.json").writeText(
            SignatureExchange.encode(SignatureExchange.pack(listOf(custom, fleets.first { it.id == "fleet-tile" }), 97, "1.1.32", "2026-10-27T12:00:00Z")),
        )
        check(Instant.ofEpochMilli(lastHeard).atOffset(ZoneOffset.UTC).toLocalDate().toEpochDay() == 20_753L)
    }

    companion object {
        const val OUT = "/home/spectre/spectre/app/src/test/resources/fixtures/fieldwatch-1.1.32"
    }
}
```
