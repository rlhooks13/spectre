# ADR-0018: Camera data from a bundled OpenStreetMap snapshot, and "Not on the map yet"

- **Status:** Accepted, 2026-10-28. This records the owner's decision on P0-7c of 2026-10-27: option A (OpenStreetMap), plus a new "Not on the map yet" feature.
- **Context:**
  - Fieldwatch 1.1.32 offered the "plate readers" camera layer as an optional download from dontgetflocked (`data.dontgetflocked.com/cameras.geojson.gz`, in `FlockFiles` / `SensorList.PLATES`).
  - Its terms (Open Road Labs LLC) forbid automated or bulk requests to its data servers. P0-7c was parked because the operator and terms couldn't be confirmed.
  - The same cameras are mapped in OpenStreetMap as `man_made=surveillance` with `surveillance:type=ALPR`, under the ODbL.

## Decision

### 1. Remove the dontgetflocked download (before 0.1.0)

- **What goes:** the code path (probe, download, install), the URL, and the download dialog.
- **Never contacted:** Spectre never contacts dontgetflocked, tests included. No test uses its host or a recorded response from it.
- **Formats only grow.**
  - Each retired language key keeps the value "Unused".
  - Serialized fields and enum values stay. That includes the `SensorList` entry and settings fields; they become inert, not deleted.
  - A camera file already downloaded on a phone stops being read. It is deleted on upgrade.
- **`NetworkPolicy` and `EgressInventoryTest`** lose the endpoint.

### 2. Replace it with a bundled OpenStreetMap snapshot (before 0.1.0)

- **The phone makes no network request for this layer.**
- **Query.** `scripts/build_alpr.py` reuses the P1-E5 `build_sensors.py` approach. It runs one Overpass query per data build, never per phone and never in CI or a Gradle build:
  ```
  [out:json][timeout:300];
  nwr["man_made"="surveillance"]["surveillance:type"="ALPR"];
  out center tags;
  ```
- **Handling the download.**
  - The response is downloaded into a new empty directory and treated as data: parsed with size, depth and count limits, never executed.
  - The request sends a User-Agent naming the repository.
  - The script is stdlib-only Python, run with `python -I`.
- **Coverage:** worldwide. Every node, and every way through its center, with both tags. No region cut: the layer is small, and a traveler shouldn't lose it at a border.
- **Kept per camera:**
  - the OSM element type and id (for attribution and de-duplication);
  - position, rounded to 1e-5° (about 1 m);
  - facing, from `direction` or `camera:direction` (degrees, or one of the 16 compass points; a list split on `;`);
  - `manufacturer` and `manufacturer:wikidata`, interned into a table.
  - Nothing else: no operator, names, notes or free text.
- **Format:** `app/src/main/assets/cameras/osm-alpr.json.gz`, gzipped JSON:
  ```
  {"format":"spectre-alpr-1",
   "osmBase":"<Overpass osm_base timestamp>",
   "querySha256":"<hash of the query text>",
   "count":N,
   "manufacturers":[["Flock Safety","Q108485435"], ...],
   "cameras":[[type, id, lat5, lon5, manufacturerIndex|-1, [facings...]], ...]}
  ```
  `type` is 0 for a node and 1 for a way. `lat5` and `lon5` are integers, in degrees × 1e5.
- **Where it lives.** The snapshot is committed in the repo, with its source data under `catalog/osm/`, so builds are reproducible and offline. Gradle only copies it into assets.
- **Refresh:** before a release, at most monthly. Each refresh is its own PR, and the release notes give its `osmBase` and the count change.
- **Size budget:** at most 4 MiB compressed and 400,000 cameras. The script and a unit test both fail over either limit, rather than truncating. (Expected today: tens of thousands of cameras, about 1–2 MiB.)
- **Reading it:** the app parses it with the existing bounded JSON limits into `FlockCamera`. `flock = true` when the manufacturer is Flock Safety, by name or wikidata.

### 3. License and credit

- **License file.** The snapshot is a derivative database under the Open Database License 1.0. `LICENSE-ODbL.txt` ships at the repo root and in the APK (`assets/licenses/`), and `catalog/osm/README` states the data's license. The app code stays MIT.
- **Credit text.** "© OpenStreetMap contributors" appears:
  - in the UI: the camera layer, its note, and About;
  - in the Debrief PDF;
  - in every export that includes camera data. Exports stay English.

### 4. Updates in Phase 4

- Snapshot updates ship as **signed data releases on the catalog channel** (P4-4 signing, P4-5 distribution), never as per-phone Overpass queries.
- Until then, the snapshot only changes with an app release.

### 5. "Not on the map yet" (after P1-B, behind a flag that defaults off)

- **Strong Flock radio hit.** Only these count:
  - a Wi-Fi SSID matching `Flock-*`;
  - the OUI `B4:1E:52` on a universally administered Wi-Fi address;
  - the OUI `B4:1E:52` on a BLE address the P1-B8 classifier calls PUBLIC or STATIC.
- **Never strong** (invariant 1): an OUI-only match on a random, rotating or unknown BLE address. It stays "possible" (P1-B8) and is never labeled unmapped.
- **Unmapped camera.** A strong hit is "not on the map yet" when no mapped ALPR camera from the snapshot lies within **100 m** of the radio's loudest hear point. That point is the phone's current fix (P1-C1) at its strongest received level, and only fixes with an accuracy of 30 m or better count.
  - 100 m allows for the phone's fix not being the camera's position, and for a camera's Wi-Fi being heard from across a junction.
  - It is a starting value. The flag's acceptance captures tune it, and any change is recorded here.
- **Export.** It's user-initiated only: a GeoJSON Point and a GPX waypoint through the share sheet, for the user to submit to OpenStreetMap or DeFlock themselves.
  - The file holds the estimated point, its accuracy and the hear time.
  - It suggests the tags `man_made=surveillance`, `surveillance:type=ALPR` and `manufacturer=Flock Safety`.
  - It says the point is estimated from the phone's position at the strongest signal, not surveyed.
  - It leaves out the radio's MAC and SSID.
  - Nothing uploads automatically. Any network action goes through `NetworkPolicy` and the user's choice.
- **Flag:** `Features.UNMAPPED_CAMERA = false`. It can't turn on before `Features.BLE_ADDRESS_CLASSIFIER`.
- **Acceptance (owner captures):**
  - a drive past at least 3 mapped Flock cameras, with none labeled unmapped;
  - at least 1 known unmapped camera, which is labeled;
  - a no-camera drive, with no label.

## Consequences

- The camera layer works offline from first launch, with no third-party server and no terms to accept.
- **Coverage is what OSM contributors have mapped.** Unmapped cameras are exactly what part 5 helps users add.
- **The data ages between app releases** until Phase 4's signed data releases.
- **The repo carries ODbL data** in its own directory with its own license. The APK grows by the snapshot size, capped at 4 MiB.
- **The agent runs the Overpass query** when refreshing the snapshot. If the distro can't reach Overpass, the agent opens a `needs-human` issue naming the rule. It never works around the denial.
