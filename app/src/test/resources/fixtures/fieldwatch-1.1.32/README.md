# Fieldwatch 1.1.32 files (importer fixtures)

These files were written by the Fieldwatch 1.1.32 code itself, at upstream commit `819365d` ("Ship Fieldwatch
1.1.32."), from synthetic inputs, with each store's own Json settings. No phone and no capture was involved:
the addresses are made up, the places are near 0°, 0°, and the names are test names. Spectre's
`Fieldwatch1132DecodeTest` reads them the way today's app does, and its goldens are under
`golden/import-1.1.32/`. They are the importer's fixtures (PLAN P1-A3).

| File | Written by 1.1.32's | Notes |
|---|---|---|
| `config.json` | ConfigStore Json (pretty, encodeDefaults) | A short catalog: AirTag and Flock rows, Tile turned off, one signature made on the phone. A real one carries every stock row. |
| `sits/sit-1132-golden.json` | SitStore Json (encodeDefaults) | A 20-minute sit: an AirTag (marked mine on the watchlist), a Flock camera, a three-point path. |
| `places.json` | PlaceStore Json (encodeDefaults = false) | No `format` key: it equals the default, so 1.1.32 never wrote it. |
| `settings-pack.json` | `SettingsExchange.encode` | |
| `signature-pack.json` | `SignatureExchange.encode` | The custom signature and Tile turned off. |

To regenerate: `git worktree add <dir> 819365d`, add the test below as
`app/src/test/java/app/fieldwatch/Write1132FixturesTest.kt` there, run
`./gradlew -p <dir> testDebugUnitTest --tests '*Write1132FixturesTest*'`, and copy `app/build/fixtures-1.1.32/` here.
Never commit the test to Spectre: it only compiles against 1.1.32.

```kotlin
package app.fieldwatch

import app.fieldwatch.data.ConfigStore
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GpsSample
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

class Write1132FixturesTest {
    private val now = 1_793_102_400_000L // 2026-10-27T12:00:00Z
    private val out = File("build/fixtures-1.1.32").apply { mkdirs() }

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

    private val fleets: List<Fleet> = DefaultCatalog.fleets().filter {
        it.id in setOf("fleet-airtag", "fleet-flock-cameras") && it.decode == null
    } + DefaultCatalog.fleets().first { it.id == "fleet-tile" }.copy(enabled = false, decode = null) + custom

    private val watchlist = listOf(
        WatchTarget(id = "w-tag", deviceKey = "BLE:7A:11:22:33:44:01", label = "Bag tag", mine = true),
        WatchTarget(id = "w-cam", deviceKey = "WIFI:B4:1E:52:0A:0B:0C", label = "Gate camera", observerNotes = "On the pole by the gate"),
        WatchTarget(id = "w-flock", fleetId = "fleet-flock-cameras", label = "Flock Safety Cameras"),
    )

    @Test
    fun write() {
        val configJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
        val config = PersistedConfig(
            version = ConfigStore.CATALOG_VERSION, fleets = fleets, watchlist = watchlist,
            settings = AppSettings(tagLocation = true), arrivalKnownKeys = setOf("BLE:7A:11:22:33:44:01"),
            hiddenPresetIds = setOf("preset-hidden-test"), takInstallTag = "AB23",
        )
        File(out, "config.json").writeText(configJson.encodeToString(config))

        val start = now - 20 * 60_000L
        val tag = radio("BLE:7A:11:22:33:44:01", "", RadioKind.BLE, -52, setOf("fleet-airtag"), start, start + 60_000L, listOf(0.0011 to 0.0020))
        val cam = radio("WIFI:B4:1E:52:0A:0B:0C", "Flock-1A2B", RadioKind.WIFI, -66, setOf("fleet-flock-cameras"), start + 120_000L, start + 180_000L, listOf(0.0012 to 0.0021))
        val session = SitSession.start(
            "Walk to the station", start, listOf(tag), fleets, setOf(tag.key, cam.key), setOf("fleet-flock-cameras"), id = "sit-1132-golden",
        )
        listOf(0.0011 to 0.0020, 0.0013 to 0.0022, 0.0015 to 0.0024).forEachIndexed { i, (lat, lon) ->
            session.recordPath(lat, lon, start + i * 600_000L)
        }
        session.ingest(cam, fleets, setOf(tag.key, cam.key), setOf("fleet-flock-cameras"))
        session.ingest(tag.copy(lastSeen = now - 30_000L, rssi = -50, gpsTrail = tag.gpsTrail + GpsSample(now - 30_000L, 0.0015, 0.0024, -50)), fleets, setOf(tag.key), emptySet())
        val sitJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        File(out, "sits").mkdirs()
        File(out, "sits/sit-1132-golden.json").writeText(sitJson.encodeToString(session.end(now, fleets)))

        val placeJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val records = listOf(
            PlaceMemory.Record(
                id = "BLE:7A:11:22:33:44:01", deviceKey = "BLE:7A:11:22:33:44:01",
                places = listOf(
                    PlaceMemory.Place(0.0011, 0.0020, now - 86_400_000L, now - 86_000_000L, 3),
                    PlaceMemory.Place(0.0015, 0.0024, now - 1_200_000L, now - 30_000L, 4, name = "Test Street"),
                ),
                lastHeardAt = now - 30_000L, returnAt = now - 1_200_000L, returnGapMs = 84_800_000L, sitId = "sit-1132-golden", localDay = 20_388L,
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
    }
}
```
