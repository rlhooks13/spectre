# ADR-0020: A radio sending FD44 stays a finder

- **Status:** Accepted, 2026-10-28 (P1-B11c).
- **Context:**
  - Research item 2 (`docs/signature-research-2026-10.md`) moves `uuid("FD44")` and `name("Find My")` from AirTags to "Find My device", "where the status byte routes them": 1 AirTag, 2 third-party, 3 AirPods, 0 Apple device.
  - Until catalog v106, FD44 labeled a radio "Apple AirTags", a finder. `dropAirTagsWhenAppleDevice` kept that label even beside Continuity, so every FD44 radio was a finder whatever its status byte said.
  - Routing FD44 radios by the status byte would move type 0 to Apple device (PHONE) and type 3 to Apple audio. Neither is a finder.
  - No Spectre capture shows which radios send FD44. The research says third-party tags and AirPods do. It doesn't say iPhones do.
  - The research also asks whether the PHONE treatment of a separated AirPods record (`12 19`, type 3) under-warns, since Apple itself alerts on separated AirPods moving with you (https://support.apple.com/en-us/119874).

## Decision

- **FD44 labels "Find My device", not AirTags.** The status byte still moves it to AirTags on an AirTag type (1).
- **On any other type, a radio sending FD44 stays on "Find My device", and stays a finder.** That holds for `SignatureEngine.routeOfflineFinding` and for `TrackerMatch.finderHits`. This keeps the finder verdict it had under AirTags (ADR-0019: inherited verdicts stay on). It only changes the label from "Apple AirTags" to "Find My device".
  - Routing type 0 or 3 away would turn finders into phones and buds on unverified product behavior. Missing a tracker is severity 1, and the charter puts field-dependent changes behind an off flag.
- **Type 3 without FD44 is unchanged:** it's Apple audio, not a finder (P1-B3). Whether separated AirPods should read as a finder is a field-dependent change. It needs captures of separated AirPods, and it is a field-capture issue, not part of this step.
- **The name "Find My" is BLE-only now.** A name-only hit is a full finder label, as it was under AirTags. Whether it should be "possible" (like SmartTag's name-only hits, item 3) goes with P1-B8's classifier work, and isn't changed here.

## Consequences

- AirTags only label radios that say AirTag: their name, or their Offline Finding type. Third-party Find My tags read "Find My device".
- A real iPhone or AirPods that sends FD44 still reads as a Find My finder, as it read as an AirTag before. Captures can narrow this later behind a flag.
- The catalog v107 ladder moves what the user set on the two AirTags rules: their switches, the AirTags row switch, watches, filters and presets. Detection that existed under AirTags isn't lost and isn't newly switched on.
