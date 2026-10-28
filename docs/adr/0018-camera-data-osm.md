# ADR-0018: Camera data from a bundled OpenStreetMap snapshot, and "Not on the map yet"

- **Status:** Accepted, 2026-10-28. This records the owner's decision on P0-7c of 2026-10-27: option A (OpenStreetMap), plus a new "Not on the map yet" feature. It supersedes the P0-7c text of ADR-0014's `SENSOR_LIST` row for the plate-reader list.
- **Context:**
  - Fieldwatch 1.1.32 offered the "plate readers" camera layer (`SensorList.PLATES`) as an optional download from dontgetflocked (`data.dontgetflocked.com/cameras.geojson.gz`).
  - Its terms (Open Road Labs LLC) forbid automated or bulk requests to its data servers.
  - The same cameras are mapped in OpenStreetMap as `man_made=surveillance` with `surveillance:type=ALPR`, under the ODbL.
  - The "Other sensors" list (`SensorList.OTHER`, P1-E5) shares the download code in `FlockFiles` and the `Egress.SENSOR_LIST` entry.

## Decision

### 1. Remove the dontgetflocked download (P0-7c, before 0.1.0)

- **What goes:**
  - `SensorList.PLATES.url` becomes blank, so `sourceHost(PLATES)` is empty and its probe and download fail closed.
  - The PLATES download dialog and its Settings wiring.
  - The string `dontgetflocked` from every non-test source.
- **What stays, for OTHER (P1-E5):** the generic download code in `FlockFiles`, `Egress.SENSOR_LIST` and its `EgressInventoryTest` entry.
- **ADR-0014's `SENSOR_LIST` row** now reads: PLATES makes no request (bundled, this ADR); OTHER as before.
- **Never contacted:** Spectre never contacts dontgetflocked, tests included.
- **Tests are ported, not deleted** (charter invariant 2). `PlateListSourceTest` becomes:
  - `PLATES.url` is blank and its source host is empty;
  - no non-test source contains `dontgetflocked`, which also enforces "never contact";
  - turning the plates layer on opens no dialog and makes no request.
- **Formats only grow.** Serialized fields, settings and the `SensorList.PLATES` entry stay, inert.
- **Language keys:**
  - **Retired** (value "Unused", pruned from the bundled packs), because they are PLATES-only and their meaning ends: `settings.layer.source`, `settings.layer.more`, `settings.layer.download.title`, `settings.layer.download.source`.
  - **Kept**, because OTHER shares them: `settings.layer.download.action`, `update`, `downloading`, `processing`, `needs.network`, `current`, `failed.keep`, `failed.first`, `saved`.
  - **Changed meaning:** `ui.cameras.plates.missing` ("list is not on this phone") gets a new key, for "the bundled camera data couldn't be read", and the old key goes "Unused".
- **Old files.** A camera file already downloaded on a phone stops being read. An idempotent startup janitor deletes `flock-cameras.bin` and its `.write` and `.download` temp files.

### 2. Replace it with a bundled OpenStreetMap snapshot (P0-7e, before 0.1.0, after P0-7c)

- **No phone request.** The phone makes no network request for this layer until P4-7, which brings signed updates through `Egress.CATALOG_UPDATE` under `NetworkPolicy`.
- **"At build time" means a data build.** It never runs in a Gradle build or CI. The owner said "at build time"; this is how the agent reads it.
- **The script.** `scripts/build_alpr.py` sets the pattern that P1-E5's `build_sensors.py` will follow: stdlib-only Python, run with `python -I`, one Overpass query per data build, never per phone:
  ```
  [out:json][timeout:300];
  nwr["man_made"="surveillance"]["surveillance:type"="ALPR"];
  out center tags;
  ```
- **Handling the response.**
  - It is downloaded into a new empty directory and treated as data: parsed with size, depth and count limits, never executed.
  - The request sends a User-Agent naming the repository.
  - A 429 or 504 gets at most one retry, after a backoff.
- **The script fails, writing nothing, when:**
  - the response has a `remark` (Overpass reports a timeout or out-of-memory that way, with HTTP 200 and truncated results);
  - the count is under `FlockApproach.MIN_COUNT`;
  - the count drops more than 10% from the committed snapshot, unless the refresh PR records an override with a reason.
- **Coverage:** worldwide. Every node, every way, and every relation through its center, with both tags. No region cut: a traveler shouldn't lose the layer at a border.
- **Kept per camera:**
  - the OSM element type and id. The key is the existing `sensorKey(osmId, osmType)`, so keys stay stable.
  - position, rounded to 1e-5° (about 1 m);
  - facings, from `direction` or `camera:direction` (degrees or compass points, split on `;`), within 0–359 and at most `FlockApproach.MAX_FACINGS` (4);
  - `manufacturer`, `manufacturer:wikidata`, `brand`, `brand:wikidata` and `operator`, interned into a table. They feed the existing `isFlock` and `plateBrand`, so brand detection doesn't narrow.
  - Nothing else: no names, notes or other free text.
- **In the repo**, under `catalog/osm/`:
  - `alpr.jsonl`: one camera per line, sorted by key and uncompressed, so a refresh diffs well and the count change is reviewable;
  - `alpr-meta.json`: `format: "spectre-alpr-1"`, `osmBase` (Overpass's data timestamp), the query's SHA-256, the count, and the interned tag table;
  - `query.overpassql`;
  - a README stating the data's license.
  - The raw Overpass response is deleted after conversion.
- **In the APK.** Gradle writes the two files, the meta on one line then the rows, as `assets/cameras/osm-alpr.jsonl`. It ships plain: P0-7e2 found that a `.gz` asset came out of the build renamed, and the APK's zip compresses this one to about 2.5 MB. `LICENSE-ODbL.txt` goes to `assets/licenses/`. Each camera is `[type, id, lat5, lon5, tagIndex|-1, [facings…]]`:
  - `type`: 0 node, 1 way, 2 relation;
  - `lat5` and `lon5`: integer degrees × 1e5.
- **Size budget:** at most 4 MiB compressed in the APK and 400,000 cameras. The script and a unit test both fail over either limit rather than truncating, and the test also requires at least `MIN_COUNT`. (Expected today: tens of thousands of cameras, about 1–2 MiB.)
- **Reading it.** The app parses it once, off the main thread, with the existing bounded JSON limits, into `FlockCamera`s of `SensorKind.PLATE`.
- **Refresh.** Before a release, at most monthly, each in its own PR. The release notes give `osmBase` and the count change. A refresh PR is data, so it is exempt from the ~600-line PR size rule; the owner is asked to add that to CLAUDE.md.

### 3. License and credit

- **The license.** The snapshot is a derivative database under the Open Database License 1.0.
  - `LICENSE-ODbL.txt` ships at the repo root and in the APK (`assets/licenses/`). Per ODbL §4.6, it says where the database is published: `catalog/osm/` in this repository, with the query and the script.
  - `NOTICE` names the ODbL paths. The app code stays MIT.
- **The credit** is the existing fixed English string, not a translated key: "© OpenStreetMap contributors · openstreetmap.org/copyright". It links to `openstreetmap.org/copyright` where links are possible. It appears:
  - in the UI: the camera layer's note and About;
  - in the Debrief PDF;
  - in every export that includes camera data. Exports stay English.
- **The data date.** The layer note gains one new key (revision `NEWEST`), "Map data as of {date}", built from `osmBase`.

### 4. Updates in Phase 4 (P4-7)

- Snapshot updates ship as **signed data releases on the catalog channel** (P4-4 signing, P4-5 distribution), never as per-phone Overpass queries.
- Until then, the snapshot only changes with an app release.

### 5. "Not on the map yet" (P1-B15, after P1-B, behind a flag that defaults off)

- **Strong Flock radio hit.** Only these count:
  - a Wi-Fi SSID matching `Flock-*`;
  - the OUI `B4:1E:52` on a universally administered Wi-Fi address (local bit clear);
  - the OUI `B4:1E:52` on a BLE address that is **PUBLIC**: API 35 addressType Public, or the classifier's PUBLIC.
- **Why not "static" for BLE.** The owner said "public or static". A BLE random static address has its top two bits set to 11. `0xB4` is `10…`, the reserved random sub-type, so a real `B4:1E:52` BLE address can only be public. "Stable by persistence" (P1-B8, before API 35) is not public. A classifier STATIC or STABLE on such an address is never strong, and a test pins that.
- **Never strong** (invariant 1): an OUI-only match on a random, rotating, unknown or stable BLE address. It stays "possible" and is never labeled.
- **Unmapped camera.** A strong hit is "not on the map yet" only when all of these hold:
  - the snapshot loaded with at least `MIN_COUNT` cameras. A missing or short snapshot labels nothing.
  - its peak level was at least −75 dBm. This is a starting value, tuned by the captures.
  - its loudest hear point is a current phone fix (P1-C1), taken within 5 s of the peak, with accuracy of 30 m or better.
  - no snapshot camera lies within **100 m** of that point. That allows for the phone's fix not being the camera's position, and for a camera's Wi-Fi being heard across a junction. The captures tune it, and any change is recorded here.
- **Wording and where it shows.**
  - The label reads "Not in Spectre's map data (OpenStreetMap, as of {date})", never "unmapped" as a fact.
  - It tells the user to look at the pole and check the current map before submitting.
  - It shows on the radio's detail screen, its map dot and its Debrief line. It is never an alert, notification or attention card on its own.
- **Export.** User-initiated only, and unavailable in Privacy mode (`demoMode`).
  - It's a GeoJSON Point and a GPX waypoint through the share sheet (P1-E3 `ShareHelper`), for the user to submit to OpenStreetMap or DeFlock themselves.
  - It holds the estimated point, its accuracy and the date only (for `survey:date`), not a time.
  - It suggests the tags `man_made=surveillance`, `surveillance:type=ALPR`, `manufacturer=Flock Safety`, `surveillance=public` and `surveillance:zone=traffic`.
  - It says the point is estimated from the phone's position at the strongest signal, not surveyed.
  - It leaves out the radio's MAC and SSID.
  - Nothing uploads automatically. Any network action goes through `NetworkPolicy` and the user's choice.
- **Flag.** `Features.UNMAPPED_CAMERA = false`. A unit test enforces `!UNMAPPED_CAMERA || BLE_ADDRESS_CLASSIFIER`.
- **Acceptance** (charter Decision policy): at least 3 owner-recorded captures with owner-written ground truth pass in `CaptureReplay`. They must include:
  - at least 3 strong hits actually heard near mapped cameras, none labeled;
  - at least 1 known unmapped camera, labeled;
  - a no-camera drive, with no label.
- **Replay needs a scrubber extension.** The ADR-0017 scrubber replaces SSIDs and moves the path, so replay needs two additions:
  - SSIDs keep a `Flock-` prefix class marker;
  - the snapshot cameras inside the capture window go through the same secret rotation and offset as the path.
  - This lands with P1-B15, or the flag stays off.

## Consequences

- The camera layer works offline from first launch, with no third-party server and no terms to accept.
- **Coverage is what OSM contributors have mapped.** Unmapped cameras are exactly what part 5 helps users add, after checking with their own eyes.
- **The data ages between app releases** until P4-7's signed data releases. The label names its data date.
- **The repo carries ODbL data** in `catalog/osm/` with its own license. The APK grows by the snapshot, capped at 4 MiB.
- **The agent runs the Overpass query** when refreshing the snapshot. If the distro can't reach Overpass, the agent opens a `needs-human` issue naming the rule. It never works around the denial.
