# Spectre

Spectre is a receive-only Wi-Fi access-point and Bluetooth LE watcher for Android. It lists what the phone can hear, matches it against a signature catalog (trackers, cameras, beacons and more), and writes reports of what was seen. It only listens: no dongle, no account, no backend server, and it works offline in the field.

Spectre is an independent fork of [Fieldwatch](https://github.com/OffGridPete/Fieldwatch) 1.1.32 by Off Grid Pete LLC. It is not made, reviewed or endorsed by Off Grid Pete LLC, and it installs as a separate app next to Fieldwatch.

## Install

Spectre isn't on the Play Store.

- **GitHub Releases.** Download the APK from [Releases](https://github.com/rlhooks13/spectre/releases). Each release lists the APK's SHA-256 and the signing certificate's SHA-256; check both before you install. The user manual and the install card ([`dist/instruction.txt`](dist/instruction.txt)) are release assets too.
- **Obtainium.** Add `https://github.com/rlhooks13/spectre` in [Obtainium](https://github.com/ImranR98/Obtainium) and it installs new releases for you.

There's no release yet; until the first one, build from source.

Android 10+ (API 29). Allow installs from the app you used to open the APK. Play Protect may warn that the app isn't from Play; that's expected for a sideload.

To bring custom signatures and settings over from Fieldwatch, use **Export signatures** and **Export settings** in Fieldwatch, then **Import** in Spectre.

## Build from source

Clone with `git clone -c core.longpaths=true …` on Windows. The debug build installs as its own app (`.debug`), next to a release build.

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

Spectre is provided as-is under the MIT License. Use it at your own risk.

- There's no guarantee that trackers, cameras, tags, access points or any other device will be found, named or reported. Radios that are off, cellular-only, asleep, randomized, quiet, or outside what the phone's OS exposes won't appear. Each phone has its own radios, firmware, scan limits and battery policies, and software can't fix those.
- Pattern matches, GPS co-travel ("Moving with you" / "possible tail"), Debrief text and AI Export output are hypotheses: not identity, not a legal finding, and not a complete RF capture. You're responsible for how you use the app and for following local law.
- Location stamps are this phone at hear-time, not the other radio. There's no Spectre server. Stamps stay on the phone until you share them. Debrief, Share log, AI Export and Share as text can take that path off the phone, and online place names use the system geocoder. How you store, share or publish those files is up to you.
- Don't use it in any situation where safety is in question.

The app shows the full disclaimer on first launch.

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
