# ADR-0016: Capture v1 is a debug-only raw recorder

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Field-dependent logic stays flagged off until owner-recorded captures pass `CaptureReplay` (PLAN Decision policy, P6-9). P1-F1 asks for what a capture holds: full ScanRecord and IE bytes, addressType, `elapsedRealtimeNanos`, RSSI, TX power, PHY, connectable, `ScanResult.timestamp`, and GPS.
  - The existing log (`LogStore`) can't serve. It samples Sightings (first hit, then every 25th), cuts BLE payloads to 80 bytes, keeps no Wi-Fi IE bytes and no radio timestamps, and stamps rows with the wall clock.
  - The scan pipeline drops data too: a 512-slot inbound channel with DROP_OLDEST, a 350 ms Wi-Fi throttle, and `rawHex` cut to 512 bytes.
  - Raw captures hold bystander data (addresses, names, SSIDs, locations), so they must never reach users' phones by accident.

## Decision
- **Format** (`domain/CaptureFormat.kt`, in main so P1-F2 and P6-9 can read it): newline-delimited JSON with one record per line and a `type` of `header`, `ble`, `wifi` or `fix`.
  - **Times:** `tNs` is elapsed-realtime nanoseconds, from the radio's own stamp where it has one (`ScanResult.timestampNanos`, Wi-Fi `timestamp`×1000, `Location.elapsedRealtimeNanos`). `wallMs` is the phone's clock.
  - **Raw values:**
    - BLE keeps the whole ScanRecord as hex, and the raw ints for PHY, address type, SID and periodic interval.
    - Wi-Fi keeps every IE (`id`, `ext`, hex) and the raw width and standard ints.
    - Fixes keep the provider and whatever the `Location` has.
  - **Absent fields** are omitted. The format only grows; readers ignore unknown keys.
- **Hooks** in main (`data/Capture.kt`): `Capture.sink?.ble/wifi/fix(...)` sits in `BleRadio.emit`, in `WifiRadio.emitResults` (before the throttle), and in `FieldwatchApp.onProviderFix` (before `acceptFix` filters the fix). `sink` is null unless something sets it.
- **The recorder is debug-only:**
  - `CaptureRecorder` and `CaptureInstaller` (a non-exported ContentProvider that sets the sink at process start) live in `app/src/debug/`. Release and staging builds don't compile them; their dex holds neither class.
  - It records only while `files/capture/ENABLED` exists. The flag is re-read every 3 s.
  - It writes `files/capture/capture-<wallMs>.ndjson`, 8 MB per part and 64 MB at most, oldest parts deleted first.
  - Wi-Fi results are deduped by (BSSID, timestamp), because Android re-reports cached results on every callback.
- **Not in v1:**
  - the scrubber (P1-F2) and its leak test (P1-F3);
  - storage in the private corpus repo and deletion of raw files (P1-F4);
  - an in-app start/stop or export, which comes once the owner records ground-truth captures.

## Consequences
- **Start and stop:** recording is started and stopped over adb on a debug build: `adb shell run-as app.fieldwatch.debug touch files/capture/ENABLED`, and `rm` to stop.
- **Raw data:** raw capture files stay on the phone until P1-F2 and F4 exist. The agent deletes any raw capture it makes during testing.
- **Cost:** the hooks cost one null check per result in release builds.
- **No test for the Android-to-record mapping yet:** the recorder needs Android types, so its unit test waits for Robolectric (P1-A1). The format has JVM tests, and a test pins the recorder to the debug source set.
