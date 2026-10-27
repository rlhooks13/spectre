# Spectre: master engineering plan (fork of Fieldwatch 1.1.32)

## Context

Fieldwatch 1.1.32 (`app.fieldwatch`) is a solo-built, receive-only Wi-Fi AP and BLE watcher: about 61K lines of Kotlin in one `:app` module, with 766 passing JVM tests. An evaluation in this session found four kinds of problems:

- **Detection bugs that cause false labels.**
  - The SmartTag rule labels every Galaxy device a finder.
  - The Tile rule uses Quuppa's company ID.
  - The AirTag rule labels separated iPhones and AirPods as AirTags.
  - BLE random-address detection uses the Wi-Fi rule.
  - Location tags go stale.
- **Data-loss paths.**
  - The log ring index resets on launch.
  - Sits are written non-atomically.
  - A corrupt config is silently reset to defaults.
- **Privacy gaps.**
  - Tiles and the geocoder are on by default.
  - The privacy gate is checked only in the UI.
  - Old migrations force online lookups back on.
- **Structure.**
  - A 4,100-line god ViewModel and a service locator.
  - About 24K lines with no tests.
  - The catalog lives inside a 2 MB config file.

The user is forking the app as **Spectre**. This plan is the basis for all future development. An autonomous Claude Code agent executes it.

The plan was drafted from a design pass and then checked by two independent reviewer subagents: one for engineering and sequencing, one for autonomy, safety and correctness. All of their blocking and major findings are folded in below.

### Binding decisions (from the user)

| Topic | Decision |
|---|---|
| Identity | Independent fork named **Spectre**; app id `io.github.rlhooks13.spectre`; own signing key; MIT and NOTICE obligations kept |
| Upstream | **Fully independent**: own code and own catalog. Upstream is reference only, never merged |
| Distribution | GitHub Releases + Obtainium only |
| Feature boundary | Receive-only by default. **Optional active features** (Bluetooth Classic, Wi-Fi RTT, Wi-Fi Aware) are off by default and labeled as transmitting. No external hardware |
| Privacy | **First-run choice** for online maps and geocoding, plus offline map packs |
| Depth | **Multi-module Gradle + Jetpack**: Room, DataStore, Hilt, WorkManager, type-safe Navigation |
| Operation | **Fully autonomous, releases included** (no human approval step). One long `/loop` session, back to back, in a dedicated Linux VM on this PC as a non-sudo user. A usage watcher pauses work at 90% and resumes it after the reset |
| Isolation | The **`spectre` WSL2 distro**. On GitHub the agent acts as **`rlhooks13`** (owner decision) through a fine-grained token limited to the two Spectre repos. A **dedicated test phone** with no personal accounts. The **shared Claude account**, with connectors disabled and checked every cycle |
| Guardrails | **Protected control files** change only through PRs whose commits carry the owner's SSH signature (the required `Protected paths` check). Everything else merges autonomously |

### Consequences
- **No in-place upgrade from official Fieldwatch** (new app id). Users migrate by importing a `fieldwatch-settings` v1 settings pack and `fieldwatch-signatures` / `spectre-signatures` signature packs, and can review old JSONL logs through `LogReplay`. The fork's own 0.1.x files need the Phase 3 importer.
- **Upstream code merges are impossible** after the module split. That is accepted.
- **The "Spectre" name is a recorded risk.** Upstream dropped it because another app already uses it, and upstream's NOTICE says Fieldwatch continues "Spectre 1.2.14".
  - The *display name* is one `Brand` object, so renaming it takes one PR.
  - Internal ids are permanent: applicationId, format ids, `spectre://`, packages, secret names.
- **The agent shares the user's usage quota.** The watcher threshold also protects the user's own usage.

## Charter

The binding decisions, invariants, trust rule, hard stops, protected paths and decision policy live in **`docs/plan/CHARTER.md`**. That file is protected and wins over this plan. They aren't repeated here, so the two can't drift apart.

## Facts that shape the plan (verified in code)

### Catalog and config
- **The upgrade ladder is not dead code.** `ConfigStore.load` (:56-86) builds config from stock only when the file is missing. After that, fleets change only through the `patchBuiltIn` steps (:176-1218). Those steps only *add* rules. The tail of `patchBuiltIn` resets darkTheme, scan controls and presets on every load.
- **The V19/V20 steps force `tagLocation` and `onlineLookup` back on** (:371-379). `PersistedConfig.version` defaults to 1 (Models :1401), so any config without a version would re-run them.
- **Imports re-add rules that were removed.** `SignatureExchange.merge` (:146-150) adds any incoming rule that is missing by `ruleKey`, so importing an official pack would bring back 0x0075 on SmartTag and 0x00C7 on Tile. `parsePack` doesn't check `formatVersion` and decodes with `ignoreUnknownKeys`.
- **Rules have no ids.** User extras on stock signatures are content-matched (`SignatureExchange` :249-251). The 6,975 rules include 6,081 OUI rules.
- **In-app catalog updates come straight from `main`.** `CatalogRemote` fetches `main/dist/…-v2.json` with no signature and no size cap.

### Detection
- **AirTag:** `mfgData(0x004C,"12")` with matchAny (`DefaultCatalog.kt:404`). The Apple-device drop (`SignatureEngine.kt:319-327`) only works when Continuity is also heard.
- **SmartTag:** `mfg(0x0075)` (:422).
- **Tile:** `mfg(0x00C7)` (Quuppa) plus `uuid("FEDD")`, whose owner needs checking (:516-522).
- **Apple devices count as trackers.** `Geo.kt:842` `isCarriedApple` makes them FINDER.
- **OUI rules ignore address type.** OUI rules apply to every BLE address (`SignatureEngine` :174-176, :475-480). For example, Axon (`DefaultCatalog.kt:2734`) and Flock (:344) can match random addresses.
- **Debrief verdicts.** The second OWN_LIKELY branch (`DebriefReport.kt` :843) doesn't check `presentAtStart`. The current copy warns about a device that is "with you the whole sit — yours or planted" (`ChromeLines.kt:869`).

### Privacy
- **The privacy gate is UI-only.** Five ViewModel tile calls hard-code `onlineLookup = true` (VM :1123, :1216, :1387, :1525, :1646). Only Compose callers check it. `PlaceLookup` checks connectivity only.
- **"Privacy mode" is `demoMode`**, which only masks text.
- **The tile fetcher conflicts with OSM's policy:** `Semaphore(4)` plus look-ahead prefetch.

### Code structure
- **Domain is nearly pure.** The exceptions:
  - `LanguageText` reads Compose snapshot state on each of its 1,433 call sites. The comment explains that a root-level revision isn't enough.
  - `RadioDb` needs a Context (its `parse` is pure).
  - `DeviceDetailText` imports `radio.BleAdParser`.
  - GeoExport, SitExport, LogReplay and LogStore use platform `org.json`. It orders keys differently from the JVM `org.json` used in tests.
- **Language constraints.**
  - `EnglishCopy` requires `addedIn == "1.1.27"` (LanguageCopy :107), so a key with any other value crashes the app at startup.
  - `newSince` compares app versions.
  - 29 English keys contain "fieldwatch". Keys must not change.
  - The packs mention the brand 2,172 times, about 50 of them inflected.
- **Coupling.**
  - FieldwatchApp (1,010 lines) also owns location, Hunt sensors, arrivals and the operator path.
  - ScanService, GnssMonitor and ScanStatusScreen cast to it.
  - Alerter and ScanService reference `MainActivity` and `R`.
  - `FieldwatchUi` has 71 fields; 7 screens take `(FieldwatchUi, FieldwatchViewModel)`.
- **R8 and builds.** Keep rules are package-based (`-keep class app.fieldwatch.**`). An unsigned release APK won't install, so R8 can't be smoke-tested locally today.
- **Repo.** There's no `.gitattributes`, `core.autocrlf=true`, and 244 files are checked out CRLF. `.git` is about 220 MB with an APK and two PDFs. `origin` is OffGridPete, and `gh` isn't installed.

## Key decisions
- **Fix first, ship 0.1.0, then restructure.** Phase 1 fixes either survive the split (AtomicFiles, NetworkPolicy, the fixes themselves) or are needed for 0.1.x correctness. Work that Phase 4 would delete is skipped: the in-app GitHub catalog update is **disabled until Phase 4**, so the `overlayStock` fix isn't needed.
- **0.1.x catalog changes ship inside the APK through the ladder.**
  - Every catalog PR bumps `CATALOG_VERSION` and adds a ladder step.
  - The ladder learns *removal* through `catalog/retired.json`.
  - A stock snapshot per version is committed in `catalog/snapshots/v<N>.json` for the importer's three-way diff.
- **Rebrand once, in Phase 0.**
  - User-visible text and the applicationId only.
  - Language **keys never change**.
  - Packages change as files move into modules.
- **Language versioning.** `addedIn` becomes an integer key revision. `newSince` uses it instead of the app version.
- **Version numbers.**
  - SemVer `0.x` up to `1.0.0` at the end of Phase 8.
  - `versionCode = X·1,000,000 + Y·1,000 + Z`; CI checks that Y and Z are at most 999 and that each tag is the next version.
  - **A pre-release and the final release are the same binary**, promoted with `gh release edit`. Untagged builds are `X.Y.Z-dev+<sha>`, built with checkout `fetch-depth: 0`.
- **Build types.**
  - `debug`: debuggable, `.debug` suffix.
  - `staging`: minified, `.staging` suffix, signed with a committed, non-secret test keystore, for R8 smoke tests on the device.
  - `release`: minified, signed only by the CI sign job.
- **Format ids.**
  - 0.1.x keeps writing v1 `fieldwatch-settings` / `fieldwatch-signatures` with an identical schema. So the Tile fix uses the existing `NAME_GLOB` with radio=BLE, not a new rule kind.
  - Catalog v2 gets the id `io.github.rlhooks13.spectre/catalog`.
  - Legacy ids are accepted on import forever.
- **Catalog source of truth** becomes one reviewed JSON file per signature (Phase 4). The DSL and the ladder are deleted once the importer no longer needs them, at 1.0.
- **NOTICE.** Keep upstream's NOTICE, including the IEEE, Bluetooth SIG and Fast Pair exclusions, and append: "Spectre is an independent fork of Fieldwatch 1.1.32 by Off Grid Pete LLC (formerly named Spectre through 1.2.14). It is not made, reviewed or endorsed by Off Grid Pete LLC." The copyright holder for new work is "the Spectre contributors".

## Target architecture

**Build setup**
- Convention plugins in `build-logic/`: `spectre.jvm.library`, `spectre.android.library`, `spectre.android.compose`, `spectre.android.feature`, `spectre.hilt`, `spectre.room`, `spectre.android.application`, `spectre.quality`.
- Version catalog: `gradle/libs.versions.toml`.

| Module | Kind | Contents |
|---|---|---|
| `:core:common` | JVM | Clock, `AtomicFiles` (tmp + fsync + rename + `.bak`), Hex, `Brand`, `AppInfo`, CrashReport |
| `:core:testing` | JVM | builders, FakeClock, MainDispatcherRule, Golden (parsed-JSON compare), capture loader + scrubber |
| `:core:i18n` | JVM | LanguageText with an `observe` hook (Compose installs it), LanguagePack, ChromeLines, LanguageCopy, SpectreDisclaimer |
| `:core:domain` | JVM | the rest of `domain/` including the **catalog v2 model**, DeviceStore, pure parsers, `shouldRaiseWatch`; RadioDb parses bytes |
| `:core:catalog` | JVM | v2 compiler, lint, ECDSA verify (→ domain) |
| `:core:database`, `:core:datastore` | Android | Room, typed DataStore |
| `:core:network` | Android | **egress chokepoint**: one HTTP client, size caps, `NetworkPolicy`, UA (kept small on purpose) |
| `:core:data` | Android | repositories, catalog overlay, legacy importer, LogStore, GnssRawFiles, FlockFiles, PlaceLookup, RadioDbLoader, WorkManager jobs |
| `:core:maps` | Android | PathTiles, MBTiles, map keys on Keystore |
| `:core:radio` | Android | BleRadio, WifiRadio, GnssMonitor, Permissions, the BleAdParser adapter, LocationSource, HuntSensors, TxGate |
| `:core:scan` | Android | ScanService, ScanPipeline (the single tick), Alerter, TakPublisher + transports |
| `:core:export` | Android | ShareHelper, SAF save, janitor, DebriefPdf |
| `:core:designsystem`, `:core:ui` | Android | theme, components, glyphs / MapChrome, SitPathCanvas, HuntHeatMap, the language observe hook |
| `:feature:{onboarding, live, filters, detail, hunt, signatures, reports, places, settings}` | Android | screens + `@HiltViewModel`s |
| `:app` | Android | `SpectreApp`, MainActivity, NavHost, deep links, crash gate, notification intents and icons (provided to core through interfaces) |

**Dependency rules** (enforced by a build-logic check)
- Features → core only, never another feature.
- `:core:domain` → `:core:i18n` and `:core:common` only.
- `:core:scan` → radio, data, network.
- Only `:app` sees everything.

## Phases

Sizes are S, M or L. Every phase ends with a gate (see Execution model). "Agent-runnable" means the agent can check it alone on the dedicated phone.

**Step ids and ticks.**
- Every step has an id: `P<phase>-<n>` (for example `P0-3`, `P4-2`), or `P1-<group><n>` in Phase 1 (for example `P1-B1`). PR titles and branches use it.
- A finished step starts with ✅, added in the PR that completes it. A step without ✅ is open.
- Within a phase, steps run in the listed order unless the text says otherwise. Phase 1: 1A lands before 1B–1E, and 1F is independent. "Verify" and "Exit" lines are phase gates, not steps.

### Phase 0: Fork foundation (M; no public release)
1. ✅ **Agent scaffolding.**
   - `CLAUDE.md`: commands, localization and enum rules, the golden workflow, the adb checklist, the restart runbook.
   - `docs/plan/CHARTER.md` and `PLAN.md`, with step ids such as `P1-B3` used in PR titles. `PLAN.md` is agent-editable; `CHARTER.md` is protected.
   - `docs/plan/PROGRESS.md`, `review-prompts/`, `approved-dependencies.md` (seeded with this plan's libraries), `test-baseline.txt`.
   - ADR-0001 (identity), ADR-0002 (versioning), ADR-0003 (autonomy and review).
2. ✅ **Repo hygiene.**
   - `.gitattributes`: `* text=auto eol=lf`, goldens and binaries `-text`.
   - `core.longpaths`. It's a per-clone setting: the README clone line carries it, and the owner sets it on the Windows signing checkout.
   - Scripts write `encoding="utf-8", newline="\n"`.
   - Delete the committed APK and PDFs and gitignore them.
3. **Build.**
   - Version catalog.
   - The `debug` / `staging` / `release` build types.
   - Signing only in CI.
   - `dependenciesInfo { includeInApk = false }`.
   - Gradle dependency verification, which writes the protected `verification-metadata.xml`.
   - Pin `buildToolsVersion = "35.0.0"` and set `android.builder.sdkDownload=false`, so AGP never fetches SDK packages on its own (it downloaded Build-Tools 34 during the P0-1 test run).
4. **Identity.**
   - applicationId, `Brand`, and rebranded `strings.xml`, English copy values, and the literals: TAK callsign, GPX creator, export names, log prefix, UA.
   - Remove upstream's social links. Rename the disclaimer to `SpectreDisclaimer` and bump `disclaimerRev`.
   - Split into sub-steps so each PR stays reviewable:
     - ✅ **P0-4a** `Brand` plus the non-copy literals: TAK callsign, GPX/KML/GeoJSON/WiGLE creator, export file names and titles, log tags, User-Agent, crash and report headers. Stable identifiers stay: format ids, TAK UIDs (`FIELDWATCH-…`), notification channel ids, internal log file names.
     - ✅ **P0-4b** English copy values (`ChromeLines`, `LanguageCopy`) and `strings.xml`. Keys never change. Also the hard-coded English copy outside the language map: `PlaceLookup.kt` (online-lookup line) and the `GnssInterference.kt` explanations. The `SitTakPackage` zip folder fallback `"fieldwatch"` stays as an id.
     - ✅ **P0-4c** Remove upstream's social links.
     - **P0-4d** (protected) `SpectreDisclaimer` and the `disclaimerRev` bump. The same owner-reviewed PR also settles the other legal and attribution copy that names Off Grid Pete LLC:
       - the in-app copyright line `ui.copyright.c.2026.off` (MIT requires keeping upstream's notice; add the Spectre contributors);
       - the debrief PDF footer `ui.off.grid.pete.llc`;
       - `OfficialSentences.NOT_REVIEWED` on the language screen.
     - **P0-4e** applicationId `io.github.rlhooks13.spectre`. The charter's hard stops forbid the agent from changing the applicationId, so this waits for the owner's explicit go-ahead in the session.
     - Catalog `notes` that mention Fieldwatch go through the catalog change checklist, or onto BrandGuard's allowlist (P0-6).
     - ✅ **P0-4f** README and docs rebrand: `README.md`, `docs/language-pack-requirements.md`, the instruction cards (`dist/` and `docs/instruction.txt`), and the other user-facing docs that still name Fieldwatch or present Off Grid Pete LLC as the publisher. Upstream's history and attribution stay.
     - ✅ **P0-4g** Language-pack file cap follows the English map (ADR-0011). The Phase 0 copy pushed the worst-case pack past the cap's test margin. Must land before P0-4d.
     - ✅ **P0-4h** Language keys carry an integer revision (ADR-0013), pulled forward from Phase 1 "Language versioning" because P0-8c changes the versionName. `addedIn` becomes `revision`, from a table with one revision per release that added keys. Templates write `keyRevision`, and "new since" counts by revision.
5. ✅ **Language packs.**
   - `scripts/rebrand_packs.py` changes **values only** and writes a per-language list of inflected forms.
   - The agent fixes those forms; the localization reviewer checks them.
   - Until the translated disclaimer is reviewed in the protected disclaimer PR, the English disclaimer is shown.
6. ✅ **`BrandGuardTest`** (a build-logic task). It checks values, not keys. Allowlisted: LICENSE/NOTICE, legacy format ids, the About attribution.
   - Until build-logic exists (Phase 2, blocked on approved build plugins), it's a JVM unit test (`app/src/test/.../BrandGuardTest.kt`) so CI enforces it now. It moves to build-logic in P2-1.
   - Temporary allowlist entries name the step that removes them (P0-4d, P0-7, P1-B1). Remove each entry in that step's PR; the test fails on any entry that no longer matches.
   - P1-B1, the first catalog-version PR, also rewords the six catalog notes that still say "Fieldwatch".
7. **Copy and legal fixes.**
   - The "Nothing is transmitted" line (`ChromeLines.kt:556`) becomes accurate copy about receive-only scanning.
   - Disable the in-app GitHub catalog update until Phase 4.
   - Sensor list: ship `scripts/build_sensors.py` (Overpass, ODbL license file, "© OpenStreetMap contributors" in the UI and exports), or turn the layer off.
   - dontgetflocked: show its operator and terms before download, cap the size, validate the schema. The agent handles only counts and hashes from it.
   - Add a region notice for camera alerts (DE/CH/FR restrictions).
   - Split into sub-steps:
     - ✅ **P0-7a** Receive-only permission copy (new key `ui.permissions.receive.only`; the old key stays, "Unused") and the in-app catalog update off (`Features.IN_APP_CATALOG_UPDATE`; its URL now points at this repo).
     - ✅ **P0-7b** Sensor list: build script with ODbL credit, or the layer off; stop downloading from upstream. Done as "layer off" (`Features.OTHER_SENSORS_LAYER`, blank URL); P1-E5 brings it back with the build script.
     - **P0-7c** dontgetflocked: operator and terms before download, size cap, schema validation. **Partial (stays open):**
       - Done: the dialog and the layer note name the source host, say Spectre doesn't run it, point to its terms and credit OpenStreetMap (ODbL). The download must come from that host over HTTPS (redirects elsewhere fail). Parse limits cover depth, string length, unpacked size and duplicates.
       - Open: the operator's name and the terms themselves (needs-human). Then strict GeoJSON type checks, an atomic install, and download tests against a local server.
     - ✅ **P0-7d** Region notice for camera alerts.
8. **CI.**
   - The agent's token has no Workflows permission, so it can't push `.github/workflows/`. The agent drafts workflow files in `docs/setup/workflows/` (a protected path). After reviewing the PR, the owner copies them into `.github/workflows/`.
   - Unit tests, lint, unsigned `assembleRelease`, `assembleStaging`, wrapper validation, a test-count guard summed across modules.
   - The two-job release workflow (see Release model). The release notes list the APK's SHA-256 and the signing certificate's SHA-256, which the README and install card promise.
   - CI uploads the staging APK as its artifact; debug is debuggable and unminified (P0-3).
   - Split into sub-steps (both are protected PRs the owner signs and copies into `.github/workflows/`):
     - **P0-8a** CI workflow draft (`docs/setup/workflows/android.yml`): wrapper validation, unit tests, lint, the test-count guard, unsigned `assembleRelease`, `assembleStaging` uploaded as the artifact. Actions pinned by SHA.
     - **P0-8b** Release workflow draft (`docs/setup/workflows/release.yml`): the two-job release.
     - ✅ **P0-8c** Version from the git tag (ADR-0002). `vX.Y.Z` and `dryrun/v0.0.Z` tags give the version, and untagged builds give `X.Y.Z-dev+<sha>`. Needs P0-4h.
     - **P0-8d** Release-notes generator: every label and verdict change, from the catalog diff and the flag states. Depends on the per-version catalog snapshots (`catalog/snapshots/`), which the first catalog PR (P1-B1) adds, so it lands after P1-B1 and before the 0.1.0 tag. Notes are hand-written until it lands.

- **Verify:** install the staging and debug builds next to official Fieldwatch. Check scanning, an alert, an export share and the TAK callsign.
- **Exit:**
  - 766 tests green and BrandGuard green.
  - A staging APK passes the on-device smoke test.
  - A dry-run tag produces a signed draft in a scratch tag namespace.

### Phase 1: Safety net + correctness fixes → 0.1.0 (L)

**1A. Safety net** (lands before any fix)
- **P1-A1 Test libraries:** (waits for PR #6, dependency verification, so new dependencies land in its metadata instead of breaking it; P1-A2 needs Robolectric and waits too) coroutines-test, Turbine, Robolectric. Shared builders go in `app/src/test/.../testing/` (moved to `:core:testing` first in Phase 2).
- **P1-A2 Golden rule:** goldens live under a `golden/` directory (for example `app/src/test/resources/golden/`), so `.gitattributes` keeps them `-text`.
  - Pins TimeZone and Locale, resets the LanguageText overlay, and passes `now` explicitly.
  - JSON goldens are compared **after parsing**, plus one Robolectric test that pins the device's byte order.
  - One localized golden.
- **P1-A3 Goldens:**
  - signature matching
  - Debrief text for 6 sits, including late-joining and tag-in-bag-from-start cases
  - every export format, CoT, the 3 AI prompts, and both packs
  - decoding of real 1.1.32 files, which become the importer's fixtures
- ✅ **P1-A4 Match corpus** (`MatchCorpus`, `MatchCorpusTest`). Near-misses that still hit name the 1B step that fixes them (`knownUntil`); that step's PR removes the marker.
  - Positives are **generated automatically** from each signature's rules.
  - Hand-written near-misses cover the confusers only:
    - Galaxy phone and Buds (0x0075)
    - separated or powered-off iPhones and AirPods (0x12)
    - a companion iPhone
    - "Tattile"
    - a Quuppa 0x00C7 tag
    - RAVEN, Vigilant, IBR\*
    - random BLE addresses that collide with BLE-applicable OUIs (Axon, Flock, LiteOn/Arlo)
- ✅ **P1-A5 `CatalogLintTest`** (ADR-0015). Corpus collisions are checked by pinning each hand-written case's full hit set; a golden replaces the pin once P1-A2 lands.
  - Company ids must exist in radiodb (expose `RadioDb.parse` as `internal`).
  - Bare mega-vendor MANUFACTURER_ID rules are banned (Apple, Samsung, Google, Microsoft, DJI).
  - NAME_CONTAINS needs at least 5 characters.
  - Corpus collisions are checked.
  - Every remaining violation needs an ADR. Whether a company name fits a signature is a reviewer check, not a lint rule.
- **P1-A6 `LanguageKeyTest`** (build-logic): every literal key exists.
- ✅ **P1-A7 `NetworkPolicy` + `EgressInventoryTest`** (ADR-0014).
  - One check inside `PathTiles` fetch, `PlaceLookup.reverse`, `CatalogRemote.fetch`, `FlockFiles.open` and `TakPublisher`.
  - Remove the 5 hard-coded `onlineLookup = true` calls.
  - Delete the V17/V19/V20 steps that force settings back on.
- ✅ **P1-A8 Clear the lint baseline** (`app/lint-baseline.xml`, added in P0-8a). Fix the 9 inherited errors and delete the file. Done on `main` before #19 merged, so #19 drops its baseline instead. The NewApi pair got `@RequiresApi(R)` instead of a second in-function SDK check: their callers already return below API 30, and lint now enforces that.
  - 4 × MissingPermission (`BleAdParser`, `FieldwatchApp`): check the permission or catch `SecurityException`.
  - 2 × NewApi on API 29 (`CrashNotes.traceOf`, `WifiIeParser.ieBytes`): already inside `runCatching`, so add an explicit SDK check.
  - 3 × `StateFlowValueCalledInComposition` (`DeviceDetailScreen`): collect the flow as state.

**1B. Detection fixes** (one PR each; every golden diff is justified)
- **Shipped on immediately** (rule fixes checked against the corpus and static tags):
  - **P1-B1 SmartTag:** drop `mfg(0x0075)` and keep FD5A.
  - **P1-B2 Tile:** 0x067C plus a BLE-only `NAME_GLOB("Tile")`. Drop 0x00C7. Drop FEDD unless a capture confirms it.
  - **P1-B3 AirTag:** decode the Offline Finding status-byte device-type bits. 0x12 alone means "Find My device (type unknown)", not AirTag.
  - **P1-B4 Apple phones:** stop treating carried Apple phones as FINDER (`Geo.kt:842`); they're assessed as phones with milder wording. TrackerMatch uses the matched fleets' class, not substring tokens.
  - **P1-B5 Over-broad names:** tighten RAVEN, Vigilant and IBR\*, and every `NAME_CONTAINS` under 5 characters that `CatalogLintTest` lists (ADR-0015): an anchored glob, a longer name, or an ADR-0015 entry with a reason.
  - **P1-B6 Retired rules:** `catalog/retired.json` is subtracted in `merge`, the ladder and import. A shared `ruleKey` ignores `enabled`. Each fix bumps `CATALOG_VERSION` with a ladder step and a snapshot.
  - **P1-B7 Readings:** `Rssi.measured` is −127..−1, so 0 is unmeasured. The decoder cache gets an LRU limit.
  - **P1-B10 Bare vendor ids** (from P1-A5, ADR-0015): `fleet-dji` (DRONE class on all of DJI 0x08AA), `fleet-fitbit` (on 0x018E, Google LLC), and `fleet-google` / `fleet-microsoft` (PHONE class on 0x00E0 / 0x0006) move to payload-prefix rules, or to a class that fits everything on the id, backed by captures. Until a capture exists, open `field-capture` issues and keep the rows.
- **Shipped behind flags** (default off; acceptance checks listed in the Decision policy):
  - **P1-B8 BLE address classifier.**
    - On API 35+, use addressType.
    - Otherwise the class is **UNKNOWN** unless there's evidence: an ecosystem that requires random addresses (Apple 0x004C, FD5A, FD6F, Find Hub) means random; persistence over 20 minutes means stable.
    - Recomputed on every merge.
    - An OUI-only BLE hit on a non-PUBLIC/STATIC address shows as "possible", with no attention card or alert, unless a non-OUI rule backs it up.
    - `RadioDb.vendorForMac` skips random addresses.
  - **P1-B9 Late-tail fix.**
    - Coverage counted from first-heard, used **only inside `possibleTail`**.
    - **OWN_LIKELY requires `presentAtStart`.**
    - Minimum absolute coverage: 300 m and 5 minutes.

**1C. Platform fixes**
- **P1-C1 Location:** `currentFix()` at ScanService :281 and :393; a stale self position is left out of TAK.
- **P1-C2 Storage:**
  - LogStore resumes from the highest existing index.
  - `AtomicFiles` replaces the 7 copies and `SitStore.writeFile`.
  - Corrupt config or sit files move to `.corrupt-<ts>` and a notice is shown.
- **P1-C3 Alerter:** single-thread confinement.
- **P1-C4 Permissions:** POST_NOTIFICATIONS becomes optional.
- **P1-C5 Radio:**
  - Cache `bluetoothClass`.
  - The Wi-Fi heard time comes from `ScanResult.timestamp`.
- **P1-C6 GNSS:**
  - Re-arm `lastCue`.
  - Total loss of lock is a separate state, checked for interference.
  - The witness listener uses 0 m.
- **P1-C7 Duplicate tick:** the ViewModel stops repeating the service tick.

**1D. UX fixes**
- **P1-D1 Navigation:** a notification tap opens Detail; tabs use `popUpTo`, `saveState` and `restoreState`.
- **P1-D2 Hunt** survives rotation.
- **P1-D3 Stop** is remembered.
- **P1-D4 Exports:**
  - Can be cancelled (`runExport` rethrows `CancellationException`).
  - A typed `ExportUi.kind` sets the dialog title.
- **P1-D5 Settings and localization:**
  - Filter titles are resolved when drawn.
  - `refreshPermissions` writes only on change.
  - About 30 hard-coded strings move to keys.
- **P1-D6 Accessibility:** tabs get `Role.Tab`; bars use `heightIn(min)`; Radar gets a text alternative.

**1E. Exports and privacy**
- **P1-E1 Export formats:**
  - GPX/KML strip control characters.
  - WiGLE writes the raw SSID with proper quoting.
  - The AI prompt uses the real window length.
  - The ATAK uid includes the install tag.
- **P1-E2 Privacy:**
  - Privacy mode blocks tiles (screen, PDF, fetch) and the geocoder.
  - Settings import shows a diff of privacy-relevant changes.
  - First-run choice covers tiles, geocoder, TAK and future catalog checks. Fresh installs default to offline.
- **P1-E3 Export handling:**
  - `ShareHelper` replaces the 8 FileProvider sites.
  - Exports go to `cache/export/<id>/` with a janitor.
  - No external-files copies.
- **P1-E4 Network:**
  - Download size caps.
  - OSM: at most 2 connections, no prefetch.
- **P1-E5 Other-sensors layer returns** (off since P0-7b):
  - Ship `scripts/build_sensors.py`: an Overpass query, the ODbL license file, and the "© OpenStreetMap contributors" credit in the UI and exports.
  - Build and host the list from this repo (or a release asset); set `SensorList.OTHER.url` and `Features.OTHER_SENSORS_LAYER`.
  - Gate the download through `NetworkPolicy`.

**1F. Capture v1** (debug builds only; its own workstream, not a prerequisite for the fixes)
- **P1-F1 Contents:** full ScanRecord and IE bytes, addressType, `elapsedRealtimeNanos`, RSSI, TX power, PHY, connectable, `ScanResult.timestamp`, GPS.
- **P1-F2 Scrubber v1, field-level:**
  - Names, SSIDs and WPS fields are replaced.
  - MACs embedded in payloads (both byte orders) and rotating Find My / FD5A / FEAA ids are remapped.
  - Timestamps are shifted, the first and last 500 m are trimmed, and the path is rotated.
- **P1-F3 Leak test:** searches the output for every original address, in both byte orders, and every original name.
- **P1-F4 Storage:** the corpus lives in a **private repo**; raw captures are deleted after scrubbing.

- **Exit:**
  - Every verified bug is closed or has an issue explaining the deferral.
  - The agent-runnable field protocol passes.
  - 0.1.0 is published under the Release model.

### Phase 2: Modular skeleton, pure domain, Hilt (L; no release)
1. **Toolchain spike.** Prove one Hilt + Room + KSP + `@Serializable`-route build under R8. Then the bump PR (Kotlin, AGP, KSP, Compose BOM), then `build-logic` and the empty modules.
2. **Purify in place.**
   - `RadioDb.init(bytes)` plus `RadioDbLoader`.
   - The LanguageText `observe` hook replaces the direct Compose read, without touching the 1,433 call sites.
   - `org.json` → kotlinx `JsonObject`, checked by the parsed goldens plus the byte-order test.
   - The Tesla prefix moves into the engine; `merge` takes `stockIds`.
   - `BuildConfig` reads become `AppInfo` (CrashNotes, FlockFiles, PathTiles, the ViewModel).
   - Path-relative tests get the project directory from a Gradle system property.
3. **Move-only PRs.**
   - Order: common → **testing** → i18n → domain (detect, report/export, geo/gnss) → catalog.
   - Packages become `io.github.rlhooks13.spectre.core.*`; keep rules are updated.
   - After each move: a staging-build smoke test on the device.
4. **Decouple the Android core** before it moves.
   - Replace the `FieldwatchApp` casts in ScanService, GnssMonitor and ScanStatusScreen.
   - Replace the `MainActivity` / `R` references in Alerter and ScanService with `NotificationIntents` and `NotificationIcons` interfaces, provided by `:app`.
5. **Hilt, strangler-style.**
   - `@HiltAndroidApp`, plus modules that `@Provides` the existing stores.
   - `StartupGate` keeps the crash-note wait.
   - Entry points on the Activity and Service.

- **Exit:** `:core:domain` has no Android on its classpath; goldens are green; the test count holds. ADR-0004, ADR-0005.

### Phase 3: Data layer → 0.2.0 (L)
- **P3-1 Stable rule ids.**
  - OUI rules get content-derived ids (`oui:E0:0A:F6`); the others get explicit ids, plus the matching snapshot.
  - Override operations:
    - turn a stock rule on or off
    - add a user rule (`user:<uuid>`)
    - an edit is "turn off the stock rule plus add a user rule"
    - a removed stock signature turns its user rules into a user signature
- **P3-2 Room** (exported schema; enums stored as TEXT with tolerant converters):

  | Table | Contents |
  |---|---|
  | `sit` | sit summary |
  | `sit_radio` | per-radio rows for a sit |
  | `radio_fix` | indexed GPS fixes |
  | `payload_fix`, `path_point`, `sit_flood`, `sit_gnss_mark` | per-sit trails and events |
  | `place`, `place_hear` | Places |
  | `watch_target`, `radio_bookmark`, `known_key` | watchlist, bookmarks, known keys |
  | `signature_override`, `rule_override`, `user_signature` | the user's catalog layer |
  | `catalog_state` | catalog version and source |

- **P3-3 DataStore** (typed):
  - `settings`
  - `filter`: filter state, presets
  - `ui-state`: disclaimer, tours, first-run choice, user-stopped flag, hunt stride
  - `language`
  - map keys, encrypted with AES-GCM under an AndroidKeyStore key

  Serializers handle enums inside collections. A corrupt file is backed up and replaced by defaults, with a notice.
- **P3-4 Files** (all written through `AtomicFiles`): logs, capture, tiles, MBTiles, gnss-raw, FWFC, crash notes.
- **P3-5 Legacy importer.**
  - Runs once behind `StartupGate`, under an idempotency flag.
  - **Runs the ladder first, then a three-way diff against `snapshot[config.version]`**, so stale stock rules never become user overrides.
  - Imports one transaction per sit.
  - Kept, and tested at head, **until 1.0**.
- **P3-6 Wiring.** Interfaces over the old stores, then one repository swapped per PR. Then `NetworkPolicy` moves into `:core:network` and the Hilt `WorkerFactory` is added.
- **P3-7 Cleanup:** remove the startup `runBlocking` and add `dataExtractionRules`.
- **Verify:**
  - Robolectric repository tests.
  - `MigrationTestHelper` from every released schema.
  - The importer runs against the 1.1.32 and 0.1.x fixtures.
  - On the device: install 0.1.0, create data, upgrade to the release candidate, check with `run-as`.
- **Exit:** a toggle writes kilobytes. ADR-0006.

### Phase 4: Catalog v2 → 0.3.0 (L)
- **P4-1 Files.** `catalog/signatures/<class>/<id>.json` compiles into a **compact** `assets/catalog/catalog.json` (about 0.8 MB).
- **P4-2 Schema v2** (the model lives in `:core:domain`):
  - signature fields: `id`, `rev`, `class`, `priority`, `suppresses:[{id, unless}]`, `protocol`, `cluster`, `decode`, `provenance`, `examples`
  - rule fields: a stable `id`, a string `kind` (an unknown kind makes the rule inert, with a warning), `role` (MATCH, REQUIRE or EXCLUDE), `payload {offset, mask, value, minLen, maxLen}`, `radio`
- **P4-3 Migration steps** (one PR each, in order).
  1. A converter generates the files, and an equivalence test plus an unchanged match golden prove nothing changed.
  2. The six hard-coded suppressions become data, and the unused `policy` parameter is deleted.
  3. The overlay is stock plus Room overrides, joined by rule id; orphaned overrides are reported.
  4. The lint runs the examples plus a SIG member-UUID table; each remaining exception needs an ADR.
- **P4-4 Signing.**
  - A detached DER ECDSA P-256 signature over the raw manifest bytes `{format, serial, sha256, size, minApp, keyId}`, verified with `SHA256withECDSA` (API 29+).
  - Two public keys are pinned (current and next).
  - Rollback, files over 8 MiB, and hash mismatches are rejected.
  - A debug-only test key exists for the on-device tamper test.
- **P4-5 Distribution.**
  - Catalog releases are `catalog-vN` and are **never marked latest**, so Obtainium always sees an APK.
  - A **beta** pointer gets the catalog for 7 days before the **stable** pointer.
  - A small signed **`disable:[ids]` kill list** is checked often and is the fast fix for a bad signature.
- **P4-6 The in-app update returns**, gated by `NetworkPolicy` and the first-run choice.
- **Exit:** a signed update installs on the phone and a tampered one is rejected. ADR-0007, ADR-0008.

### Phase 5: App decomposition → 0.4.0 (L)
1. **Split FieldwatchApp:** LocationSource, HuntMotionController, ArrivalsController, OperatorPath, FlockSync.
2. **`ScanPipeline`** owns the single tick, with an injected Clock.
3. **Narrow every screen inside `:app` before moving it.**
   - Each screen goes from `(FieldwatchUi, VM)` to its own `(State, Actions)`.
   - A singleton `SessionState` holds the selection, the export job and the filtered list (computed once).
4. **Pull controllers out of the ViewModel,** one feature per PR, keyed by function name, not line range. Then one `@HiltViewModel` and feature module per PR. Finally delete `FieldwatchViewModel` and `FieldwatchUi`.
5. **Split the oversized files:** LiveScreens (2,131 lines), SettingsScreen (1,849), FieldwatchAppUi (1,392), ReportsScreen (1,319).
6. **Move the remaining Android core modules:** radio, scan, export, maps, ui.
7. **Type-safe navigation:** routes, the NavController hoisted to `:app`, and the deep link `spectre://detail/{key}`. The janitor and log pruning run on WorkManager.

- **Exit:** no UI file over 1,000 lines, no feature-to-feature dependencies, and on-device checks for the deep link and rotation pass.

### Phase 6: Detection v2 → 0.5.0 (L)
- **P6-1 Address model:** `BleAddressClass`, plus the evidence for it, per the 1B rules.
- **P6-2 Linked identity** (`link:<uuid>`).
  - An RPA that goes silent, followed within about 3 s by a new address with the same payload template and RSSI within ±6 dB.
  - Labeled "probably the same device".
  - **A link never alerts on its own.**
- **P6-3 Tail v2.**
  - One `TailAssessor`, shared by Debrief and Live.
  - Coverage counts from first-heard, across links.
  - **Only user-confirmed Mine or owned devices are excluded.**
  - An unknown finder that is on your body from the start of the sit gets its own verdict, **"unaccounted tag with you"**, and is never suppressed.
  - Verdicts list their reasons.
- **P6-4 Flood v2:** an adaptive per-place baseline, new-address rate × payload diversity, and for Wi-Fi: sequential or local BSSIDs and SSID entropy.
- **P6-5 GNSS v2:** a NOMINAL → DEGRADED → LOST state machine, interference and spoofing heuristics, one cue per episode.
- **P6-6 Wi-Fi IEs:** WPS name, manufacturer and model (serial masked), RSN, country, HE, as new string rule kinds.
- **P6-7 Trusted networks / evil twin,** per `docs/trusted-network-requirements.md`. The agent can test it if the PC can host a cloned-SSID hotspot; otherwise the owner records it.
- **P6-8 Cell and 2G-downgrade observations.**
  - Off by default; covered by Invariant 1.
  - Behind a flag, with no notifications.
  - READ_PHONE_STATE only when enabled.
  - Labeled as observations.
- **P6-9 `CaptureReplay` regression corpus** from scrubbed captures plus `.expect.json`.

- **Exit:** the corpus is green, and the gate passes. Field-dependent features stay flagged off until their acceptance checks pass.

### Phase 7: Privacy, maps, TAK, active features → 0.6.0 (M/L)
- **P7-1 Offline mode:** an "Offline" switch blocks every egress category.
- **P7-2 Offline maps:** raster MBTiles import via SAF (y-flip handled, attribution from the pack's metadata). No bulk OSM download.
- **P7-3 Privacy-mode exports:** MACs masked, coordinates coarsened, no tiles.
- **P7-4 TAK.**
  - `TakTransport` with three transports: UDP, TCP and TLS.
  - The `.p12` key goes into AndroidKeyStore.
  - Reconnects with backoff, and the queue is bounded.
  - Tested against a **local open-source TAK-compatible server**, not the official TAK Server (hard stop).
- **P7-5 Active features** (ADR-0010). All go through the `TxGate` and the Receive-only lock, with an opt-in dialog, a TX chip, time limits, and a record of each use.
  - **Bluetooth Classic discovery.** Its dialog says it **pages with this phone's public, stable Bluetooth address**.
  - **Wi-Fi RTT** and **Wi-Fi Aware NAN Remote ID.** Their dialogs say which MAC each uses.
- **Exit.**
  - Agent-runnable: `dumpsys netstats` per UID matches the inventory.
  - The owner runs an optional proxy (MITM) audit, which needs a CA certificate installed by the owner. It doesn't gate releases.

### Phase 8: Hardening → 1.0.0 (M)
- **P8-1 Accessibility:** TalkBack, 200% font, contrast, 48 dp targets.
- **P8-2 Performance:** battery and performance budgets enforced.
- **P8-3 Docs:** the manual is rebuilt from Roborazzi screenshots and published as a release asset.
- **P8-4 Cleanup:** the legacy importer and the ladder are removed once 1.0 notes the cutoff.
- **Exit:** every gate passes.

## Autonomous operation

### One-time setup by the owner (about 2–3 hours; full steps in `docs/setup/SETUP.md`)
The agent runs inside a **dedicated WSL2 distro `spectre` (Ubuntu 24.04) on this PC**. Claude Code's only tamper-proof lock is machine-wide managed settings. Inside the distro that lock binds only the agent, and the owner's own sessions on the host keep their connectors and MCP servers. The distro's `/etc/wsl.conf` turns off Windows drive mounting and interop.

1. **GitHub** (owner account).
   - Create `rlhooks13/spectre` (public) and `rlhooks13/spectre-corpus` (private).
   - `main` ruleset:
     - no force-push or deletion
     - PR required with 0 approvals, and **no bypass**, for the owner too
     - required CI checks: `Unit tests`, `APK`, and `Protected paths` (owner-signed commits for control files)
   - Tag ruleset: `v*` and `catalog-v*` can't be updated or deleted. Turn on immutable releases if available.
   - `release` environment: holds the signing secrets, deploys only from `v*` and `catalog-v*` tags, has **no reviewers** (owner decision).
   - Actions may not create or approve PRs.
2. **Agent token.** A fine-grained token for `rlhooks13`, limited to the two Spectre repos.
   - No Administration, Workflows, Secrets, Environments or Variables permission: it can't change settings, rulesets, secrets or workflows.
3. **Keys.** Generate the release keystore (and later the catalog ECDSA key) on the host, in the owner's account. Store them as `release` environment secrets with two offline backups. They never enter the distro. The owner's SSH commit-signing key also stays on the host.
4. **Distro.**
   - `wsl --install Ubuntu-24.04 --name spectre`, with Windows drive mounting and interop off.
   - Root is used only for setup (`wsl -d spectre -u root`). The agent is the **non-sudo user `spectre`**.
   - The phone is attached with usbipd-win.
   - Installed: JDK 17, the Android SDK (platform-tools, build-tools 35, platform 35), Python 3, git, gh (logged in with the agent token), tmux, and Claude Code (logged in to the shared Claude account, subscription only, extra usage off).
5. **Managed settings** at `/etc/claude-code/managed-settings.json`, root-owned (from `docs/setup/managed-settings.template.json`):
   - `disableClaudeAiConnectors`
   - managed-only permission rules, hooks and MCP
   - `dontAsk` mode, with bypass and auto mode disabled
   - toolchain allow rules
   - hard-stop deny rules
   - the guard hook and the status line, installed root-owned in `/etc/claude-code/spectre/`
   - the sandbox, if the dry run shows gradle, gh and adb work under it
6. **Dedicated phone.**
   - No personal accounts; static reference tags beside it.
   - USB adb with "Disable adb authorization timeout" and stay-awake on; automatic OS updates off.
   - Attached to the distro with `usbipd attach --wsl --auto-attach`. The host never runs its own adb server.
   - The host never sleeps, and a terminal stays open on `wsl -d spectre`.
7. **Notifications.** Remote Control on, plus the Claude mobile app. GitHub notifications as a fallback.
8. **Start.** In `wsl -d spectre`, in tmux: `claude`, then `/loop Run one Spectre cycle exactly as defined in docs/plan/LOOP_PROMPT.md.`
9. **Dry run.** One supervised cycle proves:
   - the permissions, the guard hook, and that no connectors are present
   - the watcher and a push notification
   - a CI round trip and a scratch-namespace release
   - that adb works through passthrough
   - whether the agent can re-create its own `/loop`

### The cycle (one PLAN step per iteration)
1. **Pre-flight.**
   - Run the connector check.
   - If a `STOP` file or an owner `stop` issue exists, end the loop.
   - Check that the phone is online. If it isn't, run **device-offline mode**: only pure-JVM steps, no merges that touch radio, data or UI, no releases.
   - If `main` CI is red, run fix-main mode.
2. **Pick.** Take an owner-labeled or gate-created `sev1` first. Otherwise take the first PLAN step whose dependencies are done.
3. **Do it with the per-PR loop.** Wait for CI with ScheduleWakeup (about 8–10 minutes) instead of polling. A CI flake gets one re-run and doesn't count as an attempt; quarantining a test is a protected-file PR.
4. **Merge.** Squash-merge once every gate is green.
5. **Record.** Tick the step and append to `PROGRESS.md`: changes, PR link, ADRs, follow-ups. **Every non-trivial decision becomes an ADR.** That is the user's audit trail.
6. **Release** when a phase's exit criteria are met (see Release model).
7. **Continue** immediately, unless waiting on CI, a soak window or usage.

### Usage watcher (the "parallel agent")
- **Watcher.** `tools/usage-watch.sh` is a **script**, not a model; a model polling usage would spend the quota it watches.
  - It runs under Monitor and is re-armed on every expiry (at most 30 minutes each).
  - Every 30 s it reads `usage.json` and prints:
    - `USAGE_HIGH window=… pct=… resets_at=…` at ≥ 90% (the threshold is in a protected config), once per window cycle
    - `USAGE_RESET window=…` when the window resets
    - `USAGE_STALE` if the file is more than 10 minutes stale during work
- **On `USAGE_HIGH`:**
  - Checkpoint: commit work in progress to the branch, push, and write a resume pointer.
  - Sleep with chained ScheduleWakeups (each ≤ 3600 s) until `resets_at`, then resume.
  - Send a push for each pause and each resume.
  - Claude Code's own auto-continue after a limit (v2.1.234+, at most twice in a row) is only a backstop.
- **Loop lifetime.** `/loop` expires after **7 days**, and `--resume` doesn't restore it.
  - On day 6 the agent re-creates the loop by invoking the `loop` skill, if the dry run showed it can.
  - Otherwise it sends a push and opens a `needs-human` issue saying "re-run /loop".
  - The restart runbook in CLAUDE.md covers reboots.
- **The dry run checks** whether wakeups that come due during a limit fire, and how the loop behaves across context compaction. `PROGRESS.md` is the memory either way, never chat context.

### Release model (fully autonomous, per the user's decision)
1. **Tag.** The agent tags `vX.Y.Z` on a commit that passed the phase gate. CI rejects any tag that isn't the next version.
2. **Job A** builds the unsigned APK with **no secrets** (agent-written Gradle runs here).
3. **Job B** runs in the `release` environment. It **downloads only the artifact** and runs `apksigner` and `sha256sum`: no checkout, no Gradle, actions pinned by SHA. It publishes a **pre-release** with the release notes, which list **every label and verdict change** (generated from the catalog diff and the flag states).
4. **Soak.** The agent installs that exact APK on the dedicated phone and soaks it:
   - **7 days** for a normal release, **24 h** for a severity-1 fix.
   - Health checks are filtered to the app's package and UID.
   - Runs with `dumpsys battery unplug`, `deviceidle force-idle` and screen-off cycles.
   - Checks: no FATAL or ANR, static tags classified correctly, Galaxy devices not shown as finders, drain within budget.
   - No device-touching PRs run during the battery windows.
5. **Promote.** The agent promotes **the same binary** with `gh release edit --prerelease=false --latest`, and sends a push.
6. **Revert release.** For a bad release, rebuild the last good tree as `Z+1` with a smoke-only soak, since Android can't downgrade. After Phase 4, the catalog kill list is the faster fix for a bad signature.
7. **Catalog releases** go through the same gate and the beta-then-stable 7-day path.

### Decision policy
- **Scope.** The agent decides everything inside PLAN.md: ordering, splitting, adding steps, implementation choices. Amendments land as PRs with a rationale. The Charter is protected.
- **Feature-flag acceptance** for field-dependent logic:

  | Flag | Turned on when |
  |---|---|
  | BLE classifier | The heuristic agrees with API 35 addressType over a 24 h capture on the dedicated phone (agent-runnable, if the phone runs Android 15+), and the static-tag checks pass |
  | Late-tail, Tail v2, linking, flood v2, GNSS v2, 2G | **At least 3 owner-recorded captures with owner-written ground truth**, including a no-tail commute and a tag-joins-late walk, pass in `CaptureReplay` |

  - For the second row, the agent opens `field-capture` issues that name the exact scenario to record.
  - Only the owner can supply that ground truth, so field-dependent flags stay off until the owner records the captures.
- **Gate disagreements.** When the agent rebuts a blocking reviewer finding, a **second fresh reviewer** judges the rebuttal. If it also blocks, the change doesn't ship: it's fixed or dropped. Reviewer output is saved verbatim in `docs/plan/gates/`.
- **Stuck work.** After 3 failed attempts on a step, label it `needs-human`, notify, and move to the next independent step.

## Execution and review model

**Per-PR loop.** One PLAN step per PR, at most about 600 lines changed (not counting moves and goldens). Move-only PRs carry no behavior change.
1. Write a failing test first for a bug, then implement.
2. Run `./gradlew check`: tests, lint, detekt, CatalogLint, LanguageKey, BrandGuard, module rules, Room schema diff.
3. Run `/code-review high` and fix the findings.
4. **Independent reviewer subagent.** It gets only the diff, the step text and the ADRs. It must accept a written justification for every golden diff, and every finding is fixed or rebutted.
5. Run the adb checklist: install, `am start`, logcat, `screencap`, `run-as`, and `connectedDebugAndroidTest` when radio, data or UI are touched. A staging smoke test runs after any move or keep-rule change.
6. Squash-merge.

**Phase gate.** Five reviewers run in parallel with different prompts and lenses, and each sees only the diff range and the exit criteria:
- architecture and migration
- security, privacy and egress
- detection correctness
- UX, accessibility and localization
- release readiness

They share one model, so the objective checks carry the most weight: tests, the corpus, the lint, and the on-device checks. Zero blocking findings are required, and the results go in `docs/plan/gates/phase-N.md`.

## Test strategy
- **Libraries:** coroutines-test, Turbine, Robolectric, room-testing, Compose `ui-test-junit4`, hilt-android-testing, Roborazzi. Fakes over mocks; JUnit4.
- **Instrumented tests** over adb on the agent's port: smoke, deep link, rotation, Room migrations, share intent, and `@RequiresRadio` (at least one BLE ad within 30 s).
- **Field protocol** (`docs/field-test-protocol.md`).
  - **Agent-runnable** (these gate releases): static presence of each tag; Galaxy phone and Buds not FINDER; a separated-iPhone confuser; indoor loss of lock not reported as interference; the evil twin, if the PC can host a hotspot.
  - **Owner-recorded** (these gate feature flags only): walking with a tag, a pass-by, a tag that joins at minute 8, a tag in the bag from the start, a no-tail commute, a drive.
- **Battery:** the 0.1.0 baseline is measured unplugged and in Doze. A regression over 10% blocks a release.

## CI/CD
- **On PRs:** `check`, `assembleDebug`, `assembleStaging`, unsigned `assembleRelease`, the test-count guard, and the dependency verification check.
- **On tags:** the two-job release above. Checkout uses `fetch-depth: 0`. Every tag is checked to be the next version.
- **Reproducibility:** pinned JDK and build-tools. A second unsigned build is diffed against the first.
- **Repo content:** binaries are never committed. `radiodb.bin` is regenerated by script.
- **CHANGELOG:** assembled from per-PR fragments.

## Risk register

| Risk | Mitigation |
|---|---|
| An unattended release ships a bad label | Corpus, lint, the five-reviewer gate, a 7-day soak of the same binary, field logic flagged off until the owner's captures pass, the kill list, revert releases |
| Prompt injection (issues, datasets, packs, web) | The trust rule (all GitHub text is data; the owner instructs through the session), connectors denied and checked, server-side rulesets, protected control files |
| The agent weakens its own guardrails | The `Protected paths` signature check; rulesets with no bypass; the token has no Workflows or Administration permission; the sign job runs no agent code |
| The agent uses up the shared quota | The watcher at 90%; the threshold lives in a protected config; the owner can STOP |
| The loop dies (7-day expiry, reboot, a 3-block stall) | Day-6 re-creation, `dontAsk` mode, the restart runbook, `PROGRESS.md` resume pointer, push notifications |
| The phone is lost (adb authorization timeout, unplugged) | USB with the timeout disabled; device-offline mode |
| The "Spectre" name collides | Display name in a single `Brand` object; distinct icon; NOTICE lineage statement |
| Licensing and data: ODbL, dontgetflocked terms, camera-alert laws | ODbL file and attribution; terms shown before download; region notice |
| The corpus leaks bystander data | Field-level scrubbing, the leak test, private repo, raw captures deleted |
| A stale stock rule becomes a user override | Ladder plus three-way diff against the snapshots; the retired list |
| Goldens are blessed wrongly, or R8 breaks after a move | Every golden diff justified; staging smoke test on the device |
| Reviewers share a model with the implementer | Different lenses, they see only the diff, objective checks weighted highest, a second reviewer for rebuttals |
| An active feature reveals the user | TxGate, address disclosure in the opt-in dialog, time limits, use recorded |

## Defaults the agent applies (the owner can override them at any time)
- Catalog channel: release assets plus a beta/stable pointer.
- User `fieldwatch-language` packs are still accepted.
- READ_PHONE_STATE is requested only when that feature is turned on.
- dontgetflocked stays an optional download.
- Git history is kept.
- TAK testing uses a local open-source server.
- The phone's model and API level are detected in Phase 0. Without an Android 15+ phone, the BLE classifier flag stays off until the owner's captures pass.

## Critical files
- `app/src/main/java/app/fieldwatch/FieldwatchApp.kt`
- `ui/FieldwatchViewModel.kt`, `ui/FieldwatchAppUi.kt`
- `data/ConfigStore.kt`, `data/SitStore.kt`, `data/LogStore.kt`, `data/CatalogRemote.kt`, `data/PathTiles.kt`, `data/PlaceLookup.kt`, `data/FlockFiles.kt`
- `domain/DefaultCatalog.kt`, `domain/SignatureEngine.kt`, `domain/SignatureExchange.kt`, `domain/DebriefReport.kt`, `domain/Geo.kt`, `domain/Models.kt`, `domain/LanguageText.kt`, `domain/LanguageCopy.kt`
- `radio/ScanService.kt`, `radio/GnssMonitor.kt`, `alert/Alerter.kt`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `.github/workflows/android.yml`

## Verification of the plan as a whole
- **The dry run** proves the autonomy setup before any unattended work.
- **Every phase exit** requires:
  - the test count at or above baseline
  - goldens and the corpus green
  - zero blocking gate findings
  - the agent-runnable field protocol passing on the dedicated phone
- **At 1.0.0:**
  - A clean-checkout build reproduces the release checksum.
  - A fresh install imports an official Fieldwatch settings pack and signature pack *without* bringing back retired rules.
  - The egress audit matches the inventory.
  - Static reference tags are classified correctly, Galaxy devices and separated iPhones are not labeled finders or AirTags, and the owner's tail captures pass.
