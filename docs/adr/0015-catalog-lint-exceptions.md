# ADR-0015: Catalog lint exceptions

- **Status:** Accepted, 2026-10-27
- **Context:**
  - `CatalogLintTest` (P1-A5) bans three things in the built-in catalog:
    - company ids that the Bluetooth SIG tables in `radiodb.bin` don't list;
    - bare `MANUFACTURER_ID` rules for the mega-vendors (Apple, Samsung, Google, Microsoft, DJI), alone in an any-rule signature, because their ids are on almost everything they make;
    - name rules that only need a literal shorter than 5 characters: `NAME_CONTAINS`, or a glob that starts with `*`.
  - PLAN says every remaining violation needs an ADR. This is it. Each violation is either accepted here or assigned to the Phase 1B step that removes it. The test keys each one by its content (signature, text or company id, radio) and keeps the list exact: a new violation fails, and so does a listed one that is gone.

## Decision

**Accepted (stay as they are):**
- **`fleet-huf/0x070A`** (Huf tire sensors and vehicle access): absent from `radiodb.bin`, which was built 2026-08-24. The id comes from upstream's capture notes. Keep it, and recheck it at the next radiodb refresh. If the SIG still doesn't list it, the rule moves to a payload prefix backed by a capture.

**Assigned to a fix:**

| Violation | Fixed by |
|---|---|
| `fleet-smarttag/0x0075`, bare Samsung | P1-B1 |
| `fleet-tile/Tile/ANY` | P1-B2 (BLE-only anchored glob) |
| `fleet-google/0x00E0` and `fleet-microsoft/0x0006`: class PHONE on the vendor's whole id, so a Nest speaker, Pixel Buds, a Surface mouse or an Xbox controller reads as a phone | P1-B10 |
| `fleet-fitbit/0x018E`: the SIG lists 0x018E as Google LLC, so other Google devices on it read as a Fitbit wearable | P1-B10 |
| `fleet-dji/0x08AA`: a DRONE-class row on all of DJI's id, so DJI's non-drone gear (mics, action cameras outside the Osmo row) reads as a drone | P1-B10 |
| 34 short brand names | P1-B5 |

- **P1-B10** (new) moves the four vendor-id rows to payload-prefix rules, or to a class that fits everything on the id, backed by captures.
- **P1-B5** is widened from "RAVEN, Vigilant and IBR\*" to every short name the lint lists. The 34 names are: Echo, iPad, Arlo, Seos, Yale, ASUS, Audi, BMW, Bose, BYD, DJI (BLE and Wi-Fi), eero, eufy, FOBO, Ford, Hak5, BT05, ENVY, JBL, Kevo, Logi, MBUX, Xbox, MyQ, Nuki, Oura, Net2, Sony, Tapo, TUYA, UBNT, VW, Wyze. P1-B5 turns each into an anchored glob or a longer name, or moves it into the Accepted list above with a reason. A glob that starts with `*` counts as "contains", so it doesn't clear the lint.

**Not linted:** a `MANUFACTURER_DATA` rule with a one-byte prefix on a mega-vendor id (for example Apple's Continuity types) is a protocol message type, not a bare id. The over-broad Offline Finding rule `fleet-airtag` 0x004C "12" is P1-B3's fix.

## Consequences
- The catalog can't gain a violation without a reviewer seeing this list change.
- The 1B steps carry their lint cleanup: their PRs remove the matching exception, or the test fails.
- **Where the full list lives:** in `CatalogLintTest`. An Accepted entry must appear, in backticks, under "Accepted" here.
