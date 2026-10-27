# ADR-0015: Catalog lint exceptions

- **Status:** Accepted, 2026-10-27
- **Context:**
  - `CatalogLintTest` (P1-A5) bans three things in the built-in catalog:
    - company ids that the Bluetooth SIG tables in `radiodb.bin` don't list;
    - bare `MANUFACTURER_ID` rules for the mega-vendors (Apple, Samsung, Google, Microsoft, DJI), whose ids are on almost everything they make;
    - `NAME_CONTAINS` rules shorter than 5 characters, which catch unrelated names.
  - PLAN says every remaining violation needs an ADR. This is it. Each violation is either accepted here or assigned to the Phase 1B step that removes it. The test keeps the list exact: a new violation fails, and so does a listed one that is gone.

## Decision

**Accepted (stay as they are):**
- **`fleet-huf#0`, company id 0x070A (Huf, tire sensors and vehicle access):** absent from `radiodb.bin`, which was built 2026-08-24. The id comes from upstream's capture notes. Keep it, and recheck it at the next radiodb refresh. If the SIG still doesn't list it, the rule moves to a payload prefix backed by a capture.
- **`fleet-google#0`, Google 0x00E0, and `fleet-microsoft#0`, Microsoft 0x0006:** these rows mean "a device from this vendor" (class PHONE, notes "Pixel phone or Chromecast" and "Windows PC, Surface, or Xbox"). The company id is the signature, and the class carries no attention. A Google or Microsoft accessory labeled "Google" or "Microsoft Device" is still true.

**Assigned to a fix:**

| Violation | Fixed by |
|---|---|
| `fleet-smarttag#4`, bare Samsung 0x0075 | P1-B1 |
| `fleet-tile#0`, name "Tile" | P1-B2 (BLE-only glob) |
| `fleet-fitbit#0`, 0x018E, which the SIG lists as Google LLC: other Google devices on that id would read as a Fitbit wearable | P1-B10 (new): a payload prefix backed by a capture |
| `fleet-dji#0`, bare DJI 0x08AA on a DRONE-class row: DJI's non-drone gear (mics, action cameras not covered by the Osmo row) would read as a drone | P1-B10 (new): a payload prefix backed by a capture |
| 34 short brand names | P1-B5, widened from "RAVEN, Vigilant and IBR\*" to every name-contains rule under 5 characters |

The 34 short brand names are mostly scoped to BLE or Wi-Fi: Echo, iPad, Arlo, Seos, Yale, ASUS, Audi, BMW, Bose, BYD, DJI ×2, eero, eufy, FOBO, Ford, Hak5, BT05, ENVY, JBL, Kevo, Logi, MBUX, Xbox, MyQ, Nuki, Oura, Net2, Sony, Tapo, TUYA, UBNT, VW, Wyze. P1-B5 turns each into an anchored glob or a longer name, or moves it into this ADR with a reason.

## Consequences
- The catalog can't gain a new violation without a reviewer seeing this list change.
- The 1B steps carry their lint cleanup: their PRs remove the matching exception, or the test fails.
- **Where the full list lives:** in `CatalogLintTest`. This ADR names the accepted ones (`fleet-huf#0`, `fleet-google#0`, `fleet-microsoft#0`), and the test checks they're mentioned here.
