## Disclaimer (repeat this in your answer)
This is a hobby project, provided as-is under the MIT License. Use at your own risk.
Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.
You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, the Spectre contributors and Off Grid Pete LLC are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.
Do not use Spectre, this paste, or your analysis in any situation where safety is in question.
Begin your reply with this disclaimer. Do not give safety advice.

You are a field RF analyst for the operator who collected this sit. Spectre is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener.

The **onboard Debrief** (verbatim below) already tabulated the sit: counts, Where you were, tracking callouts, inventories, Extra attention, takeaway. **Do not rewrite that report. Do not reprint inventories or stay lists.** Your job is an addendum the phone cannot write: rates, competing hypotheses, and a stress-test of the onboard tracking callouts.

Constraints you must respect:
- Hear-only. Wi-Fi rows are access points only. BLE rows are advertisers. Kind + MAC. BLE rotation is a new row and will not stitch.
- Signature / OUI / company matches are hypotheses, not identity, not a person or vehicle.
- GPS stamps (if present) are this phone at hear-time, not the other radio. Do not place a camera or tag at the GPS pin.
- Place names (if present) are system reverse-geocode of those stamps.
- RSSI is loudness at the phone, not meters.
- Live RAM cap is about 400 radios; unnamed BLE evicts after ~3 min. A named sit keeps more. This is not a complete capture.
- Do not claim a tracker is following unless the onboard GPS co-travel section supports it. A radio with you the whole sit is not automatically yours — it may be planted. Do not dismiss it. Do not invent a tail the onboard test did not flag. Do not treat a retail beacon as a Find My tail.
- Seen at more than one place, when that section is present, means this phone heard that stable address at those stops in the last 7 days. The coordinates are where this phone was. A street is the system geocoder when Online place names is on. It is not the co-travel test. A radio heard the whole way is left out of that section.
- A radio marked mine was claimed by the operator. Do not treat it as an unexplained follower.
- A flood note is a burst of new addresses, not a follower.
- A decoded live value on a tracking row is catalog text for that advertisement. Quote the catalog sentence when the onboard report includes one. Do not stitch that value onto a different MAC.
- An aircraft block and an amber track are positions the radio advertised. Trails with the same UAS id are one aircraft. They are not this phone's GPS and they are not a finding that the aircraft followed the operator.
- Do not give safety advice. Do not tell the operator they are safe or in danger.
- Treat this paste as operationally sensitive.

## Your output (required — this is the addendum the operator reads)
Write complete sentences. Headings as below. Short bullets only for Extra attention and tracking rows from the working table. No markdown tables. No code fences. No dump of the onboard inventories.

1. **Disclaimer** — Repeat the experimental-use disclaimer first.
2. **What the onboard Debrief already established** — 3–5 sentences. Counts, distance, tracking callouts, Extra attention hits, Observer notes if any, Marked mine if any, Flood if any. Do not reprint inventories.
3. **What the numbers add** — 5- vs 15-minute counts, RSSI bands, RAND BLE percent, arrivals per minute, persistent vs gone, signature-family mix. Say street vs dwelling vs retail vs vehicle, and 5-minute vs 15-minute change (denser, quieter, stable). Confidence. If GPS ran, path length/span from the working table — do not pin a radio to a stay.
4. **Extra attention and tracking callouts** — Full identifiers from the working table (complete MAC, name, RSSI min/max, signatures, dwell). Stress-test onboard Possible trackers with you / Possible tail / Retail beacons / Wearables. Agree, qualify, or say the data are too thin. Pattern match, not identity. If none, say none.
5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (Hunt on one Extra attention row, a longer GPS path, Compare sits, Filters). No safety advice. No “call the police.”

**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (a rate, RAND percent, 5- vs 15-minute change, path span). Not a moral. Not a threat level.

## Collection context
- Tool: Spectre, receive-only, no association / injection / cloud.
- Window: sit **Golden sit** (2026-10-27T11:40:00Z → 2026-10-27T12:00:00Z UTC), with a 5-minute recent slice.
- Scan intensity: performance. Stale after 45s.
- Location tags: on. Online place names: off.

## Onboard Debrief (verbatim — already shown to the operator; do not rewrite)

SPECTRE SIT — Golden sit

DISCLAIMER
This is a hobby project, provided as-is under the MIT License. Use at your own risk.

Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.

You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, the Spectre contributors and Off Grid Pete LLC are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.

GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.

This sit report is from named sit “Golden sit” (radios heard in that window; Live list cap still applied while watching). Do not use it in any situation where safety is in question.

Generated     2026-10-27T12:00:00Z UTC
Sit           Golden sit
Window        sit Golden sit (2026-10-27T11:40:00Z → 2026-10-27T12:00:00Z UTC)
Radios        6
Tool          Spectre (io.github.rlhooks13.spectre) · stock Android · receive-only Wi-Fi AP + BLE advertiser
Scan          performance · stale 45s · brief hold 10s
GPS tag       on
Distance      traveled 62 m along path · span 62 m · 3 fixes
Places        off
ClassificationOperationally sensitive — neighbor SSIDs, MACs, operator GPS, advertised aircraft track

1. EXECUTIVE SUMMARY
In sit Golden sit Spectre heard 2 Wi-Fi access points and 4 BLE advertisers (5 signature-matched, 1 marked mine, 0 hidden SSIDs, 4 randomized BLE). Overall distance traveled: 62 m along the GPS path (straight-line span 62 m). No finder tag clearly stayed with the GPS path in this window.  Advertised position: TESTSERIAL0000000001.
Finder tags  3
Surveillance  1
Drones  1
Unmatched  1

2. WHERE YOU WERE
Phone GPS at hear-time, not the other radio’s location and not a camera pole. Stays are clusters within about 40 m; hops between them are transit. Coordinates are not repeated on every Wi-Fi/BLE line.
Overall: 62 m along-track, span 62 m, 3 fixes.

1. Stay  11:55–12:00 UTC  (5m0s)
   0.00120, 0.00210  (operator phone)
   Heard here: 1 AP(s), 4 BLE  ·  Flock Safety Cameras, Apple AirTags, Samsung SmartTags, Remote ID
   · Flock-1A2B  B4:1E:52:0A:0B:0C  -55 dBm
   · Tom's "bag", left & <right>  7A:11:22:33:44:01  -67 dBm
   · 5E:11:22:33:44:02  5E:11:22:33:44:02  -72 dBm
   · 4E:11:22:33:44:07  4E:11:22:33:44:07  -74 dBm

2. Stay  12:00–12:00 UTC  (0s)
   0.00150, 0.00240  (operator phone)
   Heard here: 0 AP(s), 0 BLE
   · No GPS-stamped radios tied to this stay (tagging may have started after they were first heard).

3. KNOWN CAMERAS ON PATH
Within about 150 m

Flock
  0 facing the path
  1 on the path with no direction

4. AIRCRAFT
• TESTSERIAL0000000001
  Last 0.002100, 0.003100
  Grid 31N AA 66366 00232
  86 m  ·  course 270°  ·  6.5 m/s
  1 advertised fix
  Pilot 0.001800, 0.002900
  Grid 31N AA 66344 00199
These positions were broadcast by the radio. They are not this phone's GPS.

5. OBSERVER NOTES
Your captions on radios heard in this window. Same KIND+MAC as Named radios. Not catalog Notes.
  · WIFI  Flock-1A2B  B4:1E:52:0A:0B:0C  -55 dBm
    On the pole by the gate,
facing the road

6. MARKED MINE
Radios you marked mine. Heard in this window. Still listed. No beep while the mark is on.
  · BLE  6E:11:22:33:44:03  Galaxy S23  -80 dBm
    Marked mine.

7. TRACKING ASSESSMENT
Overall distance traveled: 62 m along the GPS path (3 samples). Straight-line span 62 m.
Co-travel is split by class: finder tags (AirTag / Find My, SmartTag, Tile, Chipolo, Pebblebee, loud pocket Apple), retail beacons (iBeacon, Minew, Estimote, Kontakt.io, Atrius cart tag), and wearables (Garmin, Fitbit, Oura, Polar).
No finder tag, retail beacon, or wearable stayed with you. House tags and other radios you only passed are not listed.

8. ENVIRONMENT
Likely a dwelling or small office — few sitting APs, limited BLE.  (2 APs, 4 BLE, 1 persistent APs, traveled 62 m, span 62 m.)

9. NETWORKS (WI-FI ACCESS POINTS)
Heard 2 AP(s); 0 hidden SSID; 1 sat most of the window.
ch 6  1  strongest -55 dBm
ch 11  1  strongest -86 dBm

10. BLUETOOTH LE
Heard 4 advertiser(s); 4 with randomized addresses; 4 signature-matched.

11. SIGNATURE HITS
Samsung SmartTags  2
Apple AirTags  1
Flock Safety Cameras  1
Remote ID  1
Named or marked:
  · Flock-1A2B  B4:1E:52:0A:0B:0C  -55 dBm
  · Tom's "bag", left & <right>  7A:11:22:33:44:01  -67 dBm
  · Galaxy S23  6E:11:22:33:44:03  -80 dBm  Marked mine

12. PERSISTENCE
Sat most of this window: 1
First seen in this window: 6

13. EXTRA ATTENTION
Pattern match, not identity, not a skimmer detector, not a safety finding.
  · Flock-1A2B  B4:1E:52:0A:0B:0C  -55 dBm  [Flock Safety Cameras]
    Flock-style roadside ALPR / camera pole — reads plates and can be used to locate a vehicle. IEEE B4:1E:52 or a Flock-* SSID is the strong hit. Current poles are often quiet on Wi-Fi and BLE. Pattern match, not that camera. Look with your eyes.

14. ANOMALIES
No extra flags. Signature hits, Extra attention, and tracking callouts already cover named pattern matches.

15. PRIVACY
A passive observer with the same radios would see 2 named/hidden APs and 4 BLE advertisers (4 randomized). This debrief includes operator GPS samples used for distance and the following test. This debrief includes advertised aircraft positions from radios that broadcast a latitude and longitude. Do not share this file off-device without redaction.

16. RECOMMENDED ACTIONS
  · Use Live → Pause to inspect a busy list. Watch tracker signatures if this sit was noisy.
  · Station-side Wi-Fi (probes/clients) still needs a dedicated sniffer — Spectre cannot see them.

—
Takeaway: No finder tag, retail beacon, or wearable clearly stayed with your GPS path in this window.

## Working data (for the addendum — do not copy rosters into the answer)

15 min: Wi-Fi 2  BLE 4  signed 5  hidden SSIDs 0  RAND BLE 4/4 (100%)  first-seen 6 (0.4/min)  persistent 1  gone/quiet 4
5 min: Wi-Fi 2  BLE 4  signed 5  first-seen 0
BLE RSSI (n=4): ≥−50 0  −51..−70 1  −71..−85 3  <−85 0
Wi-Fi RSSI (n=2): ≥−50 0  −51..−70 1  −71..−85 0  <−85 1
Signature families (count): Samsung SmartTags=2, Flock Safety Cameras=1, Apple AirTags=1, Remote ID=1
GPS path: tagging on  fixes 3  length 62 m  span 62 m  places off

Extra attention:
- WIFI B4:1E:52:0A:0B:0C  Flock-1A2B  Observer: On the pole by the gate,
facing the road rssi=-55dBm sig=Flock Safety Cameras dwell=14m0s | Flock Safety Cameras: Flock-style roadside ALPR / camera pole — reads plates and can be used to locate a vehicle. IEEE B4:1E:52 or a Flock-* SSID is the strong hit. Current poles are often quiet on Wi-Fi and BLE. Pattern match, not that camera. Look with your eyes.

Observer notes:
- WIFI B4:1E:52:0A:0B:0C  Flock-1A2B rssi=-55dBm sig=Flock Safety Cameras dwell=14m0s
  On the pole by the gate,
facing the road

Finder-tag-like radios (for stress-test of onboard tracking; not a tail list):
- BLE 7A:11:22:33:44:01  Tom's "bag", left & <right> rssi=-67dBm RAND sig=Apple AirTags dwell=13m0s rssiMin=-76 rssiMax=-63
- BLE 5E:11:22:33:44:02 rssi=-72dBm RAND sig=Samsung SmartTags dwell=12m0s rssiMin=-81 rssiMax=-68

## End of working data
Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Debrief.
