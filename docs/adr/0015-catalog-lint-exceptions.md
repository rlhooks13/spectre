# ADR-0015: Catalog lint exceptions

- **Status:** Accepted, 2026-10-27
- **Context:**
  - `CatalogLintTest` (P1-A5) bans four things in the built-in catalog:
    - company ids that the Bluetooth SIG tables in `radiodb.bin` don't list, or that are malformed (a `MANUFACTURER_ID` of 0, or beyond 16 bits);
    - bare `MANUFACTURER_ID` rules on the mega-vendors' ids, alone in an any-rule signature: Apple 0x004C, Samsung 0x0075, Google 0x00E0 and 0x018E, Microsoft 0x0006, DJI 0x08AA. These ids are on almost everything those vendors make.
    - name rules that only need a literal shorter than 5 characters: `NAME_CONTAINS`, or a glob that starts with `*`;
    - generated corpus positives that hit another signature (corpus collisions).
  - PLAN says every remaining violation needs an ADR. This is it. Each violation is either accepted here or assigned to the Phase 1B step that removes it. The test keys each one by its content and keeps the list exact: a new violation fails, and so does a listed one that is gone.

## Decision

**Accepted (stay as they are).** These are corpus collisions where one real device legitimately carries both labels:
- `fleet-target-atrius#0` → iBeacon: Atrius beacons are iBeacons.
- `fleet-honeywell-home#4` and `fleet-honeywell-home#5` → Amazon: the Amazon Smart Thermostat is a Honeywell-made Amazon product.
- `fleet-marauder#3` → hobby BLE: "ESP32 Marauder" runs on an ESP32 board.
- `fleet-inseego#3` and `fleet-novatel#0`, each hitting the other: Inseego is the former Novatel Wireless, and the hotspots carry both names.
- `fleet-unifi-ap#0`, `fleet-unifi-ap#1`, `fleet-unifi-ap#3` and `fleet-unifi-ap#4` → UniFi: the AP row is a narrower view of the UniFi row.

**Anchored short brand prefixes (P1-B5b, catalog v102).** Eighteen short "contains" rules, on seventeen rows (DJI has a BLE and a Wi-Fi one), sat beside an anchored glob of the same brand on the same radio: `Arlo*`, `ASUS*`, `DJI*`, `eero*`, `eufy*`, `FOBO*`, `Hak5*`, `ENVY*`, `Kevo*`, `MBUX*`, `MyQ-*`, `Nuki*`, `Net2*`, `Yale*`, `Tapo*`, `TUYA*`, `Wyze*`. P1-B5b retired the "contains" rule and kept the glob. HP also gained `HP ENVY*` and MyQ the exact name `MyQ`, for forms the glob misses.
- The step's rule asks for a prefix literal of 5+ characters or one that ends in a separator. These short brand prefixes stay anyway, as accepted: each device's default name starts with its brand.
- Anchoring rules out the mid-word hits that made "contains" wrong ("Tattile", "Car Audio").
- What it doesn't rule out is a name that starts with the brand. Some are first names ("Arlo's iPhone", "Kevon-iPhone"), and the corpus pins them as known misses. P1-B5d tightens them, with a separator or the device's default-name shape, once captures show it.
- Audi stayed in P1-B5: its anchored globs are Wi-Fi only, so its BLE name had nothing to fall back on.

**Assigned to a fix:**

| Violation | Fixed by |
|---|---|
| `fleet-smarttag/0x0075`, bare Samsung | P1-B1 (done: retired in catalog v98) |
| `fleet-tile/Tile/ANY`, and the Tattile → Tile collisions | P1-B2 (done in catalog v99: BLE-only anchored glob) |
| 16 short brand names (18 more done as P1-B5b) | P1-B5 |
| Anchored short brand globs that match a first name | P1-B5d |
| `fleet-google/0x00E0`, `fleet-microsoft/0x0006`, `fleet-fitbit/0x018E`, `fleet-dji/0x08AA`, `fleet-huf/0x070A`, and the LiteOn → TP-Link collision | P1-B10 |

**What's wrong with each P1-B10 row today:**
- **Google and Microsoft** are PHONE class on the vendor's whole id. A Nest speaker, Pixel Buds, a Surface mouse, an Xbox controller, or a third-party Swift Pair mouse (Logitech, Razer) reads as a phone.
- **Fitbit** is on 0x018E, which the SIG lists as Google LLC. Its name puts any 0x018E device into `TrackerMatch`'s WEARABLE kind, which feeds the follow assessment and Debrief's "watch joined" copy.
- **DJI** is DRONE class on DJI's whole id. A DJI mic or action camera reads as a drone and is published to TAK as an airborne UAV.
- **Huf 0x070A** sits in a gap of the SIG's assigned range (between 0x0709 and 0x070B), so it was never published or was withdrawn. Its only source is upstream's capture notes.
- **The LiteOn camera-radio row's `vendorIe("00:0A:EB")`** is TP-Link's OUI, so a TP-Link router's own vendor IE reads as a LiteOn camera radio.
- **Corpus:** the DJI and Fitbit cases are in the corpus as near-misses with `knownUntil = "P1-B10"`.

**What the steps do:**
- **P1-B10** (new) moves these to payload-prefix rules or a class that fits everything on the id, backed by captures. If no capture arrives by the 0.1.0 phase gate, it drops the bare id and keeps the row's names and service UUIDs.
- **P1-B5** is widened from "RAVEN, Vigilant and IBR\*" (done as P1-B5a, catalog v101) to every short name the lint lists (18 of them done as P1-B5b, catalog v102; 16 left): Echo, iPad, Arlo, Seos, Yale, ASUS, Audi, BMW, Bose, BYD, DJI (BLE and Wi-Fi), eero, eufy, FOBO, Ford, Hak5, BT05, ENVY, JBL, Kevo, Logi, MBUX, Xbox, MyQ, Nuki, Oura, Net2, Sony, Tapo, TUYA, UBNT, VW, Wyze.
  - It turns each into a longer name, or a prefix glob whose literal is at least 5 characters or ends in a separator (`-`, `_`, space). A glob that starts with `*` counts as "contains".
  - Or it moves a name into the Accepted list above, with a reason.

**Reviewer checks, not lint:**
- **Short prefix globs:** `lit*` with a literal under 5 characters, such as the `IBR*` that P1-B5a retired in catalog v101. Fixed-length patterns like AT&T's `ATT???????` are fine.
- **Subsidiary ids used bare:** Beats 0x00CC (Apple), Nest Labs 0x01B5 (Google), Harman 0x0057 (Samsung).
- **One-byte `MANUFACTURER_DATA` prefixes on a mega-vendor id:** Apple's Continuity types are protocol message types. Offline Finding 0x004C "12" left AirTags in P1-B3 (catalog v100). It now sits on purpose on `fleet-findmy`, a type-unknown finder that the engine routes by the status byte (`SignatureEngine.routeOfflineFinding`). It stays one byte rather than "1219" so the near-owner form (`12 02 <status>`) still matches.

## Consequences
- The catalog can't gain a violation or a new cross-signature overlap without a reviewer seeing this list change.
- The 1B steps carry their lint cleanup: their PRs remove the matching exception, or the test fails.
- **Where the full list lives:** in `CatalogLintTest`. An Accepted entry must appear, in backticks, under "Accepted" here.
