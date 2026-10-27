# ADR-0017: Capture scrubber v1 and its leak test

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Raw captures (ADR-0016) hold bystander data: device addresses, Bluetooth names, SSIDs, WPS device names and serials, Wi-Fi Direct names, rotating tracker ids, and the owner's path, which starts and ends at home.
  - PLAN P1-F2 asks for field-level scrubbing, and P1-F3 for a leak test.
  - The risk table names the corpus leaking bystander data. The answer it gives is to scrub, test, keep the corpus in a private repo, and delete raw captures.

## Decision
- **Test-side code:** the scrubber (`CaptureScrubber.scrubPart`) and the leak check (`CaptureLeakCheck`) are test-side code (`app/src/test/.../testing/capture/`, moving to `:core:testing` in Phase 2). They never ship in the app.
- **One secret per part:** each `scrubPart` call draws a fresh 32-byte HMAC-SHA256 secret, never written down, and scrubs one capture part. A part never spans a reboot; a second header is refused.
  - Within a part, the same original always gets the same stand-in, and two originals never share one.
  - The stand-ins themselves can't be linked across parts or back to the originals.
  - **The records can still be linked** through what isn't scrubbed (see Residual risk).
- **What changes:**

  | Data | Treatment |
  |---|---|
  | BLE address, BSSID, Wi-Fi 7 MLD, the BLE identity address in AD 0x1B | Remapped. A universal address (Wi-Fi with the local bit clear, or public BLE) keeps its OUI. A random BLE address keeps its top two type bits. A local Wi-Fi address keeps its local and multicast bits. |
  | Those addresses anywhere inside a ScanRecord or IE, in either byte order | Replaced with the same stand-in, in one pass. Placeholders (all zeros, all ones, `02:00:00:00:00:00`) are left alone in payloads, where they would match padding. |
  | BLE names (AD 0x08, 0x09, 0x30) and URIs (0x24); SSIDs (text, raw bytes, IE 0 from its own bytes); WPS device name, MAC, manufacturer, model name and number, serial, UUID-E/R and vendor extension | Same-length printable stand-ins. WPS attributes split across vendor IEs are walked as one stream. |
  | Wi-Fi P2P and Wi-Fi Display vendor IEs; Reduced Neighbor Report (neighbor BSSIDs and the Short SSID, a CRC of the real SSID); Multiple BSSID (nested SSIDs); Mesh ID; Interworking (HESSID); the Multi-Link element; Cisco's AP-name IE | Remapped whole after their header. |
  | Apple Continuity TLVs, wherever they sit | Offline Finding (0x12) after its status byte, AirDrop (0x05) and Nearby Action (0x0F) bodies (hashes of phone number and Apple ID), and the Nearby Info (0x10) auth tag are remapped. Type and length bytes, and other TLVs, stay. |
  | FD5A (SmartTag) after its first byte; FEAA (Eddystone, Find Hub) after its frame byte, except TLM (0x20) | Remapped. |
  | Times | One secret shift with a sub-day part for wall times (the time of day moves), and another for `tNs`. Order and spacing survive. |
  | Altitude | Shifted by a secret offset; differences survive. |
  | Fixes | Only reliable GPS fixes are kept and used: provider gps, accuracy within 50 m, not last-known. Network fixes can sit hundreds of metres off. |
  | Path | The start and end anchors are the medians of the first and last five reliable fixes. The kept window runs from the last time the track is within 500 m (straight line) of the start anchor to the first time it comes within 500 m of the end anchor, and every record outside it is dropped. Any fix within 500 m of either anchor is dropped, even inside the window. Jitter while standing at home never counts as travel. The rest is rotated by a secret angle about its centroid and moved to a synthetic origin near 0°, 0°. Distances and bearings survive, rotated together. |
  | A path that never gets 500 m from both anchors | Keeps no fixes at all, but keeps its radio records. |
  | Header | `model` becomes "scrubbed"; `bootCount` is dropped. |

- **The leak test is mandatory:**
  - `CaptureLeakCheck.leaks(original, scrubbed)` searches the scrubbed capture's field values (case-insensitive) and its payload bytes (whole-byte positions, including WPS reassembled across IEs) for:
    - every original address (forward and reversed);
    - every original name, compared as bytes: BLE names and URIs, SSIDs and IE 0, WPS device name, model and serial. Names with fewer than 3 visible characters are skipped.
  - It doesn't search the JSON text, so a name like "WiFi" can't match a key.
  - A capture with any leak doesn't leave the machine. P1-F4's tool enforces this.

## Residual risk
- **Route shape:** the kept route keeps its shape and spacing. Map-matching it against street data could find where it was, and the trimmed ends then point near home and work. The private repo (P1-F4) is the control for that. Captures are for the agent and the owner only.
- **Linkable records:**
  - **Kept data:** everything not listed above stays, and can link records across parts or to a place: the full set of Wi-Fi capabilities and other vendor IEs (an AP fingerprint), OUIs, name lengths, and Eddystone TLM counters.
  - **Not yet remapped:** Fast Pair and Microsoft CDP payloads, and Continuity types other than those above.
- **The leak test finds only identifiers its parsers know about.** An identifier in a structure neither the scrubber nor the check parses is not found by either. Not handled in v1:
  - free text in other vendor IEs (Aruba, MikroTik and similar AP names);
  - other vendors' rotating ids;
  - names inside P2P Group Info, which is remapped whole but not searched.

  v2 widens both, using the owner's first real captures as test data, and adds a generic search for printable runs.

## Consequences
- **Not replayable:** detection that depends on names, SSIDs or the remapped payloads can't replay from a scrubbed capture. Expectations for those (P6-9) are written against the scrubbed data, or come from the pinned corpus (P1-A4).
- **Replayable:** detection that depends on OUIs, company ids, service UUIDs, payload prefixes, address type bits and timing still replays.
- **Stationary captures** (reference tags beside the phone) keep their radio data without location, which is what the BLE classifier's acceptance check needs.
