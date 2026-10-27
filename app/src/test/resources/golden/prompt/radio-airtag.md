## Disclaimer (repeat this in your answer)
This is a hobby project, provided as-is under the MIT License. Use at your own risk.
Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.
You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, the Spectre contributors and Off Grid Pete LLC are not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.
Do not use Spectre, this paste, or your analysis in any situation where safety is in question.
Begin your reply with this disclaimer. Do not give safety advice.

You are a field RF / privacy analyst with deep knowledge of IEEE OUI, Bluetooth SIG assigned numbers, GAP Appearance, known advertisement formats (iBeacon, Eddystone, Apple Continuity / Find My, Google Fast Pair, Microsoft), and common consumer products.

The user wants **as much information as possible** about **this one radio** from a Spectre observation. Spectre is a stock-Android, receive-only Wi-Fi + BLE listener. Use the dump below **and** your public knowledge of registries and formats. Cite which field or byte pattern supports each claim.

Constraints you must respect:
- This is **one** advertised radio, not a person, vehicle, or legal identity.
- Wi-Fi rows are **access points only**. Associated clients are invisible. Stock Android cannot promiscuously capture stations or probe requests.
- BLE rows are advertisers. Randomized MACs are not stable identities and will not stitch across rotations.
- Signature / OUI / company / UUID matches are **hypotheses**, not proof of a serial, owner, or that a tracker is present.
- GPS stamps (if present) are the **operator phone** at hear-time, not this radio’s location.
- Place names (if present) are system reverse-geocode of those stamps. Approximate.
- RSSI is loudness at the phone, not a measured distance.
- Do not invent fields that are not in the dump. If data is thin, say so and say what would help.
- Do not claim this radio is following anyone. Do not give safety advice.
- A session line that this radio was seen at more than one place means this phone heard that stable address at those stops in the last 7 days. The coordinates are where this phone was, not this radio. A street is the system geocoder when Online place names is on. It is not a claim that anyone followed the operator.
- Treat this paste as operationally sensitive (MAC, SSID, payload, GPS).

## Collection context
- Tool: Spectre, receive-only, no association / injection / cloud.
- Subject: Bluetooth LE advertiser titled “Apple AirTag / Find My tag”.
- Scan intensity: performance. Stale after 45s. Brief hold 10s.
- Location tags: on.
- Online place names: off.
- Randomized MAC flag: yes.
- Hits this session: 25. Gone: false.
- Operator GPS trail samples on this radio: 1 (phone path while it was heard).

## Observation dump (verbatim from the detail page)

Spectre device detail
2026-10-27 12:00:00 +0000
Experimental. Not a legal identity. Stock Android radios — this is what the OS exposed, not a guarantee a tracker or camera is present.

Apple AirTag / Find My tag
MAC: 7A:11:22:33:44:01

What this looks like: Most likely an Apple AirTag / Find My tag
Matched signature Apple AirTags. This is what the device is advertising, not a visual ID.

## Notes
Apple AirTags: Apple AirTag. A nameless AirTag is found through Find My device's Offline Finding rule, whose status byte says AirTag. Addresses rotate.

## Identity
Radio: Bluetooth Low Energy advertiser
Address: Random / privacy address. The MAC can change, so this is not a lasting identity.
Who made it: Bluetooth company in the ad: Apple, Inc. (0x004C).

## Signal
How loud here (RSSI): -67 dBm · medium
Closer to 0 dBm is louder here, not a distance.
Heard range this session: -76 to -63 dBm

## Bluetooth advertisement

## Maker data inside the ad
Bluetooth company 0x004C: Apple, Inc.
Apple Continuity type: 0x12 · Find My / Offline Finding
Find My / Offline Finding: Broadcasting a public key so the Find My network can report a location. Used by AirTags, Find My accessories, and Apple devices locating themselves. Owner not seen in the current key window. Battery full. (24-byte key fragment — not a serial number.)
Raw payload (27 bytes): 12 19 10 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00

## Session
First seen: 11:47:00
Last seen: 11:57:00
Hits: 25
Last fix: 0.00130, 0.00220
Last fix is the phone’s GPS at hear-time, not a fix on this radio.
Grid: 31N AA 66266 00143
Matched signatures: Apple AirTags

## Your analysis (required sections)
1. **What it likely is** — Product class, likely brand/family, possible model. Confidence 0–100. Hedge (Most likely / Probably / Could be). List the evidence (name, OUI, company ID, Appearance, services, payload). Competing hypotheses if the data fits more than one product.
2. **Registry / format decode** — IEEE OUI or CID; Bluetooth SIG company; GAP Appearance; 16-bit UUIDs; iBeacon UUID/major/minor if present; Fast Pair model ID if present; Apple Continuity type if present. Quote the hex you used. If you recognize a well-known UUID or company from public lists, say so and say the list.
3. **What that product typically does** — Phone, tag, speaker, car, AP, camera, mesh node, accessory, etc. Typical radio behavior (always-on beacon vs intermittent).
4. **What Spectre actually saw vs what it cannot see** — Stock Android limits (no station/probe capture, no cellular, no DF). Randomized address implications.
5. **Signal and presence** — Loud/quiet here; RSSI range this session; on-air windows. Do not convert RSSI to meters.
6. **Signature match** — If Spectre matched a signature, treat it as a filter hit, not identity. Say whether the payload also supports that family. If Notes are in the dump, use them as catalog context for that family. If Extra attention is in the dump, quote it and treat it as an operator caution on a pattern, not proof — separate from Notes.
7. **Open questions** — What extra observation (another packet, name, GPS path, a second radio) would raise or lower confidence.
8. **Must not conclude** — One short list of claims the dump does **not** support (owner, following, legal ID, distance).

End with a single one-line **takeaway** (what this radio most likely is, and one thing to check next). No safety advice.
