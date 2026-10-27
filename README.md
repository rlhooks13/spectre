# Spectre

Spectre is a receive-only Wi-Fi access-point and Bluetooth LE watcher for Android. It lists what the phone can hear, matches it against a signature catalog (trackers, cameras, beacons and more), and writes reports of what was seen. It's receive-only by default: it never connects, pairs or advertises, though the Wi-Fi and BLE scans themselves can send the probe and scan requests Android makes when it scans. No dongle, no account, no backend server, and it works offline in the field.

Spectre is an independent fork of [Fieldwatch](https://github.com/OffGridPete/Fieldwatch) 1.1.32 by Off Grid Pete LLC. It is not made, reviewed or endorsed by Off Grid Pete LLC. Spectre will get its own application id before its first release, so that release installs as a separate app next to Fieldwatch. Until then, a build from this repo uses Fieldwatch's id (`app.fieldwatch`) and can't be installed on a phone that already has Fieldwatch; the debug build (`app.fieldwatch.debug`) can.

## Install

Spectre isn't on the Play Store.

- **GitHub Releases.** Releases will be published at [Releases](https://github.com/rlhooks13/spectre/releases), each listing the APK's SHA-256 and the signing certificate's SHA-256; check both before you install. The install card is [`dist/instruction.txt`](dist/instruction.txt).
- **Obtainium.** Add `https://github.com/rlhooks13/spectre` in [Obtainium](https://github.com/ImranR98/Obtainium) and it will install new releases for you.

There's no release yet; until the first one, build from source. The user manual is being rewritten for Spectre and will ship as a release asset.

Android 10+ (API 29). Allow installs from the app you used to open the APK. Play Protect may warn that the app isn't from Play; that's expected for a sideload.

To bring custom signatures and settings over from Fieldwatch, use **Export signatures** and **Export settings** in Fieldwatch, then **Import** in Spectre.

## Build from source

Clone with `git clone -c core.longpaths=true …` on Windows. The debug build installs as its own app (`.debug`), next to a release or staging build.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

| Path | What it is |
|---|---|
| `dist/fieldwatch-signatures.json` | Stock catalog for 1.1.11 catalog updates (catalog 77; legacy format) |
| `dist/fieldwatch-signatures-v2.json` | Stock catalog for 1.1.12+ catalog updates |
| `dist/fieldwatch-sensors.geojson.gz` | Other-sensors list. OpenStreetMap contributors, ODbL 1.0. See `dist/fieldwatch-sensors.txt` |
| `dist/instruction.txt` | Install card: permissions, first launch |
| [`CHANGELOG.md`](CHANGELOG.md) | What's new in each build |
| `docs/plan/` | How this fork is developed |

## Safety and disclaimer

Spectre is provided as-is under the MIT License, without warranty of any kind. Use it at your own risk. The app shows the full disclaimer, including the limitation of liability, on first launch; by using the software you accept it and the MIT License.

- There's no guarantee that trackers, cameras, tags, access points or any other device will be found, named or reported. Radios that are off, cellular-only, asleep, randomized, quiet, or outside what the phone's OS exposes won't appear. Each phone has its own radios, firmware, scan limits and battery policies, and software can't fix those.
- Pattern matches, GPS co-travel ("Moving with you" / "possible tail"), Debrief text and AI Export output are hypotheses: not identity, not a legal finding, and not a complete RF capture. You're responsible for how you use the app and for following local law.
- Location stamps are this phone at hear-time, not the other radio. There's no Spectre server. Stamps stay on the phone until you share them. Logs keep full coordinates even when Privacy mode masks the screen and sit reports. Sharing a Debrief, a sit compare, an AI Export (sit or one radio), a radio-detail Share as text, or a log can take that path off the phone. A TAK/CoT feed, when you turn it on, sends markers onto the LAN. Online place names use the system geocoder (often the phone maker's or Google's network), and online map tiles, the sensor list and the catalog update fetch over the network when you use them. How you store, share or publish those files is up to you.
- Don't use it in any situation where safety is in question.

## What Spectre is not

- Not Wi-Fi clients, probe-only stations, or 802.11 monitor mode
- Not Bluetooth Classic inquiry (HC-05 / HC-06 won't appear)
- Not cellular
- Not direction finding

## Copyright and license

Copyright (c) 2026 Off Grid Pete LLC
Copyright (c) 2026 the Spectre contributors

Spectre is licensed under the [MIT License](LICENSE). AndroidX, Kotlin and related libraries remain Apache-2.0. The IEEE and Bluetooth SIG assigned-number tables in `radiodb.bin` are subject to those organizations' terms. See [NOTICE](NOTICE).

Report problems in [Issues](https://github.com/rlhooks13/spectre/issues).
