# ADR-0017: Capture scrubber v1 and its leak test

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Raw captures (ADR-0016) hold bystander data: device addresses, Bluetooth names, SSIDs, WPS device names and serials, rotating tracker ids, and the owner's path, which starts and ends at home.
  - PLAN P1-F2 asks for field-level scrubbing, and P1-F3 for a leak test.
  - The risk table names the corpus leaking bystander data. The answer it gives is to scrub, test, keep the corpus in a private repo, and delete raw captures.

## Decision
- **Test-side code:** the scrubber (`CaptureScrubber`) and the leak check (`CaptureLeakCheck`) are test-side code (`app/src/test/.../testing/capture/`, moving to `:core:testing` in Phase 2). They never ship in the app.
- **One secret per capture:** every replacement is keyed by HMAC-SHA256 with a 32-byte secret drawn fresh for each capture and never written down. Within a capture, the same original always gets the same stand-in, so a device stays one device in replay. Across captures, and against the originals, the stand-ins are unlinkable.
- **What changes:**

  | Data | Treatment |
  |---|---|
  | BLE address, BSSID, Wi-Fi 7 MLD | Remapped. A universal address (Wi-Fi with the local bit clear, or public BLE) keeps its OUI. A random BLE address keeps its top two type bits. A local Wi-Fi address keeps its local and multicast bits. |
  | Those addresses inside payloads (ScanRecord, any IE), in either byte order | Replaced with the same stand-in, in the same order. |
  | BLE local names (AD 0x08/0x09), SSIDs (text, raw bytes, IE 0), WPS device name, manufacturer, model name and number, serial, UUID-E/R | Same-length printable stand-ins, so length-dependent decoding still replays. |
  | Apple Offline Finding key, key bits and hint | Remapped; type, length and status stay. |
  | FD5A (SmartTag) service data after its first byte | Remapped. |
  | FEAA (Eddystone, Find Hub) frames | Remapped after the frame byte, except TLM (0x20), which is telemetry and stays. |
  | Times | One secret shift for all `tNs`, and another for all wall times. Order and spacing survive. |
  | Path | Cut by the first and last 500 m (path distance), and every record from those stretches goes with them. The rest is rotated by a secret angle about its centroid and moved to a synthetic origin near 0°, 0°, with bearings rotated to match. Distances survive. |
  | A path under 1 km | Keeps no fixes at all, but keeps its radio records. It could be a home or a workplace. |
  | Header | `model` becomes "scrubbed"; `bootCount` is dropped. |

- **The leak test is mandatory:**
  - `CaptureLeakCheck.leaks(original, scrubbed)` searches the scrubbed text for every original address (colon form, plus hex forward and reversed) and every original name (text, and hex), case-insensitively.
  - Names are BLE local names, SSIDs, and the WPS device name, model name and serial; names shorter than 3 characters are skipped.
  - A capture with any leak doesn't leave the machine. P1-F4's tool enforces this.

## Consequences
- **Name signatures:** detection that depends on names or SSIDs can't replay from a scrubbed capture. Expectations for those (P6-9) are written against the scrubbed data, or come from the pinned corpus (P1-A4) instead.
- **Payload contents:** detection that depends on vendor OUIs, company ids, service UUIDs, payload prefixes, address type bits and timing does replay.
- **Not scrubbed in v1:**
  - Continuity payloads beyond type 0x12, such as Nearby Info auth tags;
  - other vendors' rotating ids;
  - free text in IEs other than WPS;
  - non-UTF-8 names that aren't valid text.

  The leak test catches the identifiers it knows. These gaps go to v2, with the owner's first real captures as test data.
- **Stationary captures** (reference tags next to the phone) keep their radio data without location, which is what the BLE classifier's acceptance check needs.
