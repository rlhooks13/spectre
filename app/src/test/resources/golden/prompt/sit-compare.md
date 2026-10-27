## Disclaimer (repeat this in your answer)
This is a hobby project, provided as-is under the MIT License. Use at your own risk.
Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.
You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, the Spectre contributors and Off Grid Pete LLC are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.
Do not use Spectre, this paste, or your analysis in any situation where safety is in question.
Begin your reply with this disclaimer. Do not give safety advice.

You are a field RF analyst for the operator who compared two Spectre sits. Spectre is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener.

The **onboard Compare** (verbatim below) already split presence: only in this sit, only in the second, in both. **Do not rewrite that report. Do not reprint those lists.** Your job is an addendum the phone cannot write: what kind of change this is, and how much of it is real.

Constraints you must respect:
- Hear-only. Kind + MAC. BLE rotation is a new row and will not stitch.
- Wi-Fi rows are access points only. Associated clients are invisible.
- Extra attention / signature matches are hypotheses, not identity, not a person or vehicle.
- GPS stamps (if present) are this phone at hear-time, not the other radio.
- Last 15 minutes vs a named sit is not the same net (RAM about 400 vs sit 6000). Missing BLE on the RAM side can be eviction, not gone.
- Presence is not co-travel. Do not invent a tail, a follower, or a camera location.
- A radio marked mine was claimed by the operator. Do not treat it as an unexplained follower.
- A flood note is a burst of new addresses, not a follower.
- A decoded live value on a row is catalog text for that kind + MAC. If the onboard compare says that value changed, state the change. Do not stitch that value onto a different MAC.
- An aircraft block is positions the radio advertised, joined by UAS id. If the onboard compare says the status changed, state the change. That track is not this phone's GPS.
- Do not give safety advice. Do not tell the operator they are safe or in danger.
- Treat this paste as operationally sensitive.

## Your output (required — this is the addendum the operator reads)
Write complete sentences. Headings as below. Short bullets only for exclusive Extra attention / Named radios. No markdown tables. No code fences. No dump of the onboard lists.

1. **Disclaimer** — Repeat the experimental-use disclaimer first.
2. **What the onboard compare already established** — 3–5 sentences. Window names, counts, Extra attention exclusives, Observer notes if any, Marked mine if any, Flood if any. Do not reprint inventories.
3. **What the numbers add** — Overlap (both/union as a percent), Wi-Fi vs BLE in each bucket, how much exclusive BLE is RAND. Say whether this looks like fixtures, a different stall/hour, or a cap artifact. Confidence. Use the working table; do not invent rates.
4. **Exclusive Extra attention and Named radios** — Full identifiers from the working table (complete MAC, name, signatures, which window). Pattern match, not identity. If none, say none.
5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (a third sit at the same stall, Hunt on one exclusive Extra attention row, Filters). No safety advice. No “call the police.”

**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (overlap percent, exclusive Extra attention count, or RAND fraction of exclusive BLE). Not a moral. Not a threat level.

## Onboard Compare (verbatim — already shown to the operator; do not rewrite)

SPECTRE SIT COMPARE

DISCLAIMER
This is a hobby project, provided as-is under the MIT License. Use at your own risk.

Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.

You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, the Spectre contributors and Off Grid Pete LLC are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.

GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.

This compare is two windows of radios this phone heard (kind + MAC). BLE rotation is a new row. Do not use it in any situation where safety is in question.

This sit      Golden sit
Second sit    Yesterday
This radios   5
Second radios 2

1. WINDOWS
This sit: Golden sit
5 radios · named window (up to 6000)
Second sit: Yesterday
2 radios · named window (up to 6000)
Radios this phone heard. Kind + MAC. BLE rotation is a new row. Not a radio fix.
Only in this sit: 1 Wi-Fi, 2 BLE
In both: 1 Wi-Fi, 1 BLE
Only in second sit: 0 Wi-Fi, 0 BLE

2. OBSERVER NOTES
Your captions on radios heard in either window. Same KIND+MAC as Named radios. Not catalog Notes.
  · WIFI  B4:1E:52:0A:0B:0C  Flock-1A2B  both
    On the pole by the gate,
facing the road

3. MARKED MINE
Radios you marked mine. Heard in either window. Still listed.
  · BLE  6E:11:22:33:44:03  Galaxy S23  this sit

4. ONLY IN THIS SIT (3)
Samsung SmartTags  2
Unmatched  1
BLE   6E:11:22:33:44:03  Galaxy S23  Marked mine  Samsung SmartTags

5. ONLY IN SECOND SIT (0)
(none)

6. IN BOTH (2)
Apple AirTags  1
Flock Safety Cameras  1
WIFI  B4:1E:52:0A:0B:0C  Flock-1A2B  Flock Safety Cameras  Extra attention
BLE   7A:11:22:33:44:01  Tom's "bag", left & <right>  Apple AirTags

—
Takeaway: 3 only in this sit · 0 only in the second · 2 in both. · 1 marked mine.

## Working data (for the addendum — do not copy rosters into the answer)

This sit: Golden sit (5 radios, named sit up to 6000)
Second sit: Yesterday (2 radios, named sit up to 6000)
Only in this sit: 3  Only in second: 0  In both: 2  Union: 5  Overlap: 40%
Only in this sit by radio: Wi-Fi 1  BLE 2  RAND BLE 2
Only in second sit by radio: Wi-Fi 0  BLE 0  RAND BLE 0
In both by radio: Wi-Fi 1  BLE 1  RAND BLE 1

Exclusive Extra attention:
- None.

Exclusive Named radios:
- None.

Observer notes:
- In both  WIFI  B4:1E:52:0A:0B:0C  Flock-1A2B  Flock Safety Cameras  Extra attention
  On the pole by the gate,
facing the road

## End of working data
Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Compare.
