# Spectre progress

## Resume pointer
- State: RUNNING
- Current step: P0-4f (README and docs rebrand), PR #12 (waiting on CI, then squash-merge). Waiting on the owner: PR #6 (P0-3 part 2, protected), PR #10 (P0-4d, protected) and the P0-4e applicationId go-ahead. Next: P0-6 (BrandGuard).
- Branch: `p0/4f-docs-rebrand`
- Phone serial: RFCX205JR3K
- Phone model / API level: Galaxy S23 SM-S911U, Android 16, API 36 (checked with getprop on 2026-10-27). adb authorized, stay awake on (`stay_on_while_plugged_in=7`), adb authorization timeout off, automatic system updates off (owner-reported).
- Loop created at: 2026-10-27T05:41Z
- Last cycle: 2026-10-27T08:47Z

## Needs human (owner)
- Disable the `engineering` and `legal` plugins for this account. Their MCP servers (asana, github, pagerduty, box) show up in the session as failed connections. They aren't claude.ai connectors, aren't authenticated, and managed settings deny every `mcp__` tool, but runbook step 4 expects no servers at all.
- Set up Remote Control and push notifications. Until then, pushes may not arrive.
- Host power settings (SETUP.md).
- Logon tasks: `docs/setup/install-logon-tasks.ps1`.
- `approved-dependencies.md` (protected) is missing build plugins the plan needs: `com.android.library`, `org.jetbrains.kotlin.jvm`, and for `build-logic` the `com.android.tools.build:gradle` and `org.jetbrains.kotlin:kotlin-gradle-plugin` artifacts. Plugins already in use (`com.android.application`, `org.jetbrains.kotlin.android`, `.plugin.compose`, `.plugin.serialization`) and `androidx.compose.ui:ui-tooling` aren't listed by name either. This blocks P2-1. The agent will push a draft branch for the owner to sign before Phase 2.
- On the Windows checkout that signs protected PRs, run `git config core.longpaths true` (P0-2). It's a per-clone setting that can't be committed, and it has no effect in the distro.
- Permission rules: `gh pr merge --delete-branch` was silently denied while a plain `gh pr merge --squash` worked, and `cat` and some piped `git ls-files` commands are denied too. Merged agent branches stay on the remote until a rule allows `git push origin --delete p<phase>/*` or `gh pr merge --delete-branch`. Remote branches `p0/1-agent-scaffolding` and `p0/2-repo-hygiene` are waiting for that.
- **Staging test keystore (P0-3).** The guard hook blocks `keytool -genkeypair` ("signing locally"), so the agent can't create the committed, non-secret staging test keystore that PLAN's Build types call for. Until then, staging is signed with each machine's debug key, so a CI staging APK can't update a locally built one. Either generate `app/signing/staging-test.jks` on the host and commit it (add `!app/signing/staging-test.jks` to `.gitignore`; use alias `staging` and store and key password `spectre-staging`, which the build already expects), or confirm that the debug key is acceptable for staging.
- **Phone screen sleeps.** At 06:22Z the screen was dozing while USB-powered, even with `stay_on_while_plugged_in=7`, so the screenshots came out black until `input keyevent KEYCODE_WAKEUP`. Check the screen timeout and Samsung's power settings.
- **PR #6 (P0-3 part 2, protected): sign and merge.** It adds `gradle/verification-metadata.xml` (SHA-256, trust on first use) and raises `test-baseline.txt` to 767. CI's `Unit tests` and `APK` pass with verification on; `Protected paths` fails only for want of your signature. After #6 lands, the baseline can go to 769 (P0-4a adds `BrandTest`).
- **P0-4e applicationId go-ahead.** CHARTER's identity decision names `io.github.rlhooks13.spectre`, but its hard stops say the agent never changes the applicationId. The agent won't make that change until you confirm in this session. Changing it means a fresh install: data from `app.fieldwatch` doesn't carry over unless the user exports and imports it.

Until the remaining dry-run checks pass, any step that relies on an unverified control (protected-path enforcement, the workflow-push rejection, server-side 403s) verifies that control first.

## Dry-run checks (SETUP.md §9)
The owner skipped the supervised dry run on 2026-10-27 (UTC) and told the agent to verify these during Phase 0.

| Check | Result |
|---|---|
| `/mcp` and tool list | Partial pass: no claude.ai servers and no usable `mcp__` tools. Four plugin servers listed as failed (see Needs human) |
| `git push --force` on a scratch branch | Pending |
| Edit `~/.claude/settings.json` | Pending |
| `sudo ls` | Pass: denied by permissions |
| Status line / `~/.spectre-agent/usage.json` | Partial: the file exists with `five_hour` and `seven_day` windows, and the watcher reports no `USAGE_STALE` (written within the last 10 minutes). The agent can't see the rendered `5h N% · 7d N%` line, so the owner should check it |
| Monitor runs `tools/usage-watch.sh` | Pass: started |
| `./gradlew testDebugUnitTest` | Pass: 766 tests, 0 failures, 0 skipped |
| `adb -s RFCX205JR3K get-state` | Pass: `device` |
| Push notification | Partial: the terminal notification was sent, but the mobile push wasn't (Remote Control inactive) |
| PR changing a non-protected file: CI runs, squash-merge | Pass: PR #2's APK, Unit tests and Protected paths checks passed, and it was squash-merged as `cd37758` |
| PR changing `CLAUDE.md`: `Protected paths` fails | Pass, checked with PR #6, which changes protected `gradle/verification-metadata.xml` and `docs/plan/test-baseline.txt` and has unsigned agent commits: `Protected paths` failed (run 40464487804) |
| Push under `.github/workflows/` rejected | Pending (P0-8) |
| `gh secret list` / ruleset edit gives 403 | Partial: blocked locally by the guard hook before reaching GitHub. The server-side 403 isn't verified |
| Re-create the loop with the `loop` skill | Pending (at the 6-day loop-age check) |

## Field-capture requests (owner)
None yet. The agent adds an entry here, plus a `field-capture` issue, for each scenario it needs.

## Log
Newest first. One entry per cycle that changed something. Dates from 2026-10-27 on are UTC; earlier entries use the owner's local time (UTC−7).

- 2026-10-27: **P0-4f README and docs rebrand.** PR #12.
  - **`README.md` rewritten for the fork:**
    - Fork statement; the separate-app claim is conditional on the P0-4e app id.
    - "Receive-only by default", noting that scans can send OS probe and scan requests.
    - Future-tense release and Obtainium install.
    - Build from source and the dist files.
    - A safety section with the full egress list from the app disclaimer, including the Privacy-mode log caveat and the TAK/CoT feed.
    - Issues on `rlhooks13/spectre`.
    - Upstream's first-person text and its 1.0.5 re-signing story are gone.
    - The copyright and liability holders aren't restated: the README points to LICENSE and the in-app disclaimer until the owner decides PR #10.
  - **Follow-up once PR #10 merges:** sync the README and instruction-card copyright lines with LICENSE.
  - **Follow-up for P0-8:** release notes must list the APK SHA-256 and the signing-certificate SHA-256, which the README and card now promise. Added to PLAN P0-8.
  - **`docs/instruction.txt` and `dist/instruction.txt`** (still identical):
    - The new header drops upstream's APK hash, signing certificate and APK and manual file names; each release will list hashes.
    - The upstream re-sign note is replaced with "releases install next to Fieldwatch; export and import to move settings".
    - Receive-only wording as in the README. The catalog update notes that it currently downloads upstream's pack (P0-7 turns it off). The Limits section names Releases and Obtainium for updates.
    - Body rebranded; the footer social handles are gone.
  - **`dist/CHANGELOG.md`** and **`dist/fieldwatch-sensors.txt`** synced and rebranded too.
  - **`docs/language-pack-requirements.md`, `docs/trusted-network-requirements.md`, `docs/signature-localization-options.md`:** product name rebranded. "Not reviewed or certified by" and "publisher" now name the Spectre contributors.
    - The two quoted official sentences in `language-pack-requirements.md` are left to PR #10, which already changes them, to avoid a conflict.
    - The `FIELDWATCH-SELF` UID stays.
  - **`CHANGELOG.md`:** a note on top that Spectre's entries start at 0.1.0 and the rest is upstream history, kept as written.
  - **PLAN:** P0-4a, 4b, 4c and 4f ticked; P0-4 stays open until 4d (owner) and 4e (owner) land.
  - **Not in scope:** `docs/build_manual.py` (the 6,000-line manual source). It's rebuilt from screenshots in P8-3; until then it still says Fieldwatch and isn't shipped.
- 2026-10-27: **P0-5 Language packs.** PR #11, merged as `fb7397b`.
  - New `scripts/rebrand_packs.py` (`python -I`, UTF-8, LF).
    - It rewrites only `"key": "value"` lines inside `strings`, and only where "Fieldwatch"/"FIELDWATCH" stands alone or starts a hyphen compound.
    - It leaves keys, metadata (`format`, `writtenFor`) and layout alone, checks the JSON before and after, and lists inflected forms per language.
    - `--check` makes no changes and exits 1 if anything still needs the plain rebrand.
  - Ran on all 26 bundled packs: 1,288 lines changed, same keys.
  - 39 inflected forms fixed by hand, then corrected after the localization review:
    - da: Spectres.
    - et: apostrophe endings for a silent final letter, read [spekter]: Spectre'i (genitive, 18 lines), Spectre'it (partitive), Spectre'il, Spectre'iga, Spectre'ile, SPECTRE'I.
    - fi: Spectren, Spectreen, Spectrelle.
    - hu: Spectre-nek, Spectre-rel, Spectre-t.
    - tr: Spectre’in, Spectre’i (front-vowel harmony; the first draft wrongly used ’ın/’ı).
    - sv: also fixed an upstream genitive slip, "Spectres batterisida".
  - Localization review (independent reviewer with the gates.md Lens 4): da, fi and hu are correct, and agreement in the other 21 packs is fine. The et and tr errors are fixed as above.
  - Left for the pack authors: Hungarian upstream typos "TEREMI" → "TEREPI" and "a értesítésből" → "az értesítésből" (not brand-related).
  - Script hardening from review:
    - Left word boundary that still allows a JSON escape such as `\n` right before the name.
    - Hyphen compounds are listed for checking.
    - Any joiner (’ ' ‘ ʼ :) counts as an inflection.
    - `--check` fails while inflected forms remain.
    - Line endings are preserved; the shebang enforces `-I`.
  - New `BrandTest.noBundledPackNamesTheUpstreamApp` checks every pack in `BundledLanguages.all`. It's case-insensitive (`field\s?watch`), allowing only the app id `app.fieldwatch` until P0-4e.
  - Disclaimer: packs don't translate it; it's compiled English, so the English disclaimer is what users see (the step's condition holds).
  - Upstream's copyright and footer text in packs stays under the old keys, which PR #10 retires in favor of new ones.
  - Result: 773 tests green.
  - PLAN: added P0-4f (README and docs rebrand), which code review asked for on PR #10.
- 2026-10-27: **P0-4d Disclaimer and legal copy.** PR #10, protected, waiting for the owner's review and signature. It proposes the liability, LICENSE, NOTICE, copyright-line, PDF-footer and language-pack-sentence wording (see the PR table). The two changed attribution lines got new keys (`ui.copyright.spectre`, `ui.footer.operationally.sensitive`), so stale translations can't override them. `DISCLAIMER_REV` goes to 4.
- 2026-10-27: **P0-4c Upstream social links.** PR #9, merged as `029f39c`.
  - Removed the Settings credit footer's Instagram (`instagram.com/OffGridPete`) and X (`x.com/OGridPete`) chips, the now-unused `SocialChip` composable, and `ic_instagram.xml` and `ic_x.xml`. The footer keeps the copyright line, which P0-4d settles.
  - The language keys `ui.offgridpete.label` and `ui.ogridpete.label` stay (keys are never removed). Their English values change from the upstream handles to "Unused", so the exported template no longer carries them.
  - Removed the now-dead `openUrl` and five unused imports.
  - The language-pack author-guide link now points to this repo's `docs/language-pack-author-guide.md` (`Brand.REPO_URL`). The repo is public, so the link resolves.
  - The guide itself is rebranded: the title, "Spectre version/update/terms/user manual", issues at `rlhooks13/spectre`, and review by "the Spectre contributors". The in-app `NOT_REVIEWED` sentence is still upstream's until P0-4d.
  - New `BrandTest.noUpstreamSocialHandleOrLinkShips` checks that no handle or link ships.
  - Result: 772 tests green. Staging cold-started with 0 `FATAL`. The Settings footer itself wasn't screenshotted: it sits behind the first-run disclaimer, which device checks don't accept. Follow-up: an instrumented UI check once P1-A adds Robolectric and Compose UI tests.
  - Not in this step: in-app fetches still go to upstream URLs (`CatalogRemote`, `FlockFiles` sensor list). P0-7 disables the catalog update and replaces or turns off the sensor layer.
- 2026-10-27: **P0-4b English copy values.** PR #8, merged as `ca0676a`.
  - "Fieldwatch"/"FIELDWATCH" → "Spectre"/"SPECTRE" in English values only:
    - `ChromeLines` (49) and `LanguageCopy` (7).
    - The hard-coded copy in `GnssInterference` (5) and `PlaceLookup` (1).
    - `strings.xml` `app_name`.
  - `ui.fieldwatch.app.fieldwatch.stock` becomes "Spectre (app.fieldwatch) · …". It keeps the app id until P0-4e.
  - The idle top-bar title (`FieldwatchAppUi.kt`, a hard-coded `"FIELDWATCH"`) is now `Brand.NAME_UPPER`.
  - New `BrandTest.noEnglishLineNamesTheUpstreamApp` guards against regressions.
  - Moved to P0-4d (owner-reviewed): copy naming Off Grid Pete LLC (copyright line, PDF footer, language-pack review sentence).
  - Known effect: a translation's length cap scales with the English text, so shorter English values give slightly tighter caps. No bundled pack is affected (both reviewers checked). An external pack written for upstream may lose a few long CONTROL lines to English fallback.
  - No key changed: a diff check shows every touched key once as removed and once as added. Every line keeps `addedIn = "1.1.27"`.
  - Left for later: the upstream author-guide URL in `LanguageCopy` (P0-4c), translated packs (P0-5), the disclaimer (P0-4d, protected), catalog notes (catalog checklist or P0-6).
  - Four tests updated to the new English values, none removed. `launcherNameStaysFieldwatch` became `launcherNameIsSpectre`.
  - Result: 771 tests green.
  - Device: the staging launcher label is `Spectre` (aapt2). Cold start with 0 `FATAL`; it opens on the disclaimer.
- 2026-10-27: **P0-4a Brand literals.** PR #7, merged as `b40423b`.
  - PLAN: P0-4 is split into P0-4a to P0-4e. 4d is protected, and 4e waits for the owner (see Needs human).
  - New `domain/Brand`: `NAME`, `NAME_UPPER`, `FILE_PREFIX`, `REPO_URL`, `LOG_TAG`, `userAgent()`.
  - Now using `Brand`:
    - GPX creator, KML/GeoJSON names, WiGLE `appRelease`.
    - Sit export file names and share subjects; map, log, signatures, settings, language, debrief and compare file names.
    - TAK default callsign `Spectre-<tag>`, heartbeat and tombstone remarks, KML track name.
    - Crash report header and clipboard label; PDF, debrief and compare headers; GNSS raw-log header.
    - The 3 AI prompts. They also drop the `(app.fieldwatch)` app id.
    - User-Agent `Spectre/<ver> (+https://github.com/rlhooks13/spectre)` on tiles, the sensor list and the catalog fetch.
    - Logcat tags; the Settings "about" line; the "Not a … pack" errors.
  - Kept on purpose: format ids, TAK UIDs (`FIELDWATCH-…`), notification channel ids, internal log file names, upstream download URLs (P0-7 disables or replaces them), and language copy (P0-4b).
  - 12 tests updated to the new strings (none removed). New `BrandTest` (+3) pins the User-Agent, the kept stable ids and sit-heading detection.
  - Review fixes:
    - Debrief share subject: `debriefSubject` checked `startsWith("FIELDWATCH SIT")`, which the rename would have broken. Now `DebriefReport.isSitHeading` with a shared prefix.
    - Two spaces that edits dropped are restored.
    - Last `"Fieldwatch · gone"` tombstone; shared `CotEvent.TOMBSTONE_REMARKS`.
    - Missed share subjects and the device-detail header.
    - Three TAK tests that would now pass whatever happens assert `Brand.NAME`.
    - The signature export is `spectre-signature-pack-…`, so it can't be confused with the legacy `spectre-signatures` id.
  - Result: 770 tests green. Staging installed and cold-started on the phone with 0 `FATAL`.
- 2026-10-27: **P0-3 Build, part 2 (dependency verification).** PR #6, protected, waiting for the owner's signature.
  - The first metadata came from a warm cache and left out the `junit-bom` 5.9.2/5.9.3 `.module` files, so CI failed verification.
  - Regenerating with an empty `--gradle-user-home` added them plus one `kotlinx-serialization-bom` pom. Every other checksum matched.
  - `lintDebug` has 9 pre-existing errors (for example `MissingPermission`, `BleAdParser.kt:31`). That's for P0-8.
- 2026-10-27: **Gate fix** PR #5 merged as `a7d5d92`.

- 2026-10-27: **Gate fix: startup crash in `FieldwatchViewModel`.** PR #5. Follow-up: `cachedCameraReport` can compute twice under concurrent calls (upstream, performance only). Revisit when P5-1/P5-2 move the camera report out of the ViewModel.
  - The bug was found by P0-3's device check and reproduced on `main` (see the P0-3 entry).
  - Inside `init`, `app.sits.ui.collect` runs at once on `Main.immediate` and calls `refreshSitPath()`. That starts `cachedCameraReport()` on `Dispatchers.Default` while the constructor is still running. `cameraCacheLock`, `cameraCacheStamp` and `cameraCache` were initialized after `init`, so the lock could still be null.
  - Fix: those three properties now sit above `init`, with a comment. An audit found no other class property initialized after `init`.
  - Failing test first: `ViewModelInitOrderTest` scans the ViewModel source and fails if any property with an initializer comes after `init`. Before the fix it reported exactly lines 3320–3322; after the fix it passes.
  - Result: 767 tests green (+1). `assembleDebug` and `assembleStaging` OK.
  - Device: no `FATAL` in the crash buffer after cold starts from `pm clear` (staging ×3, debug ×2). Staging stays alive on the first-run disclaimer screen.
  - Follow-up (needs human): `test-baseline.txt` (protected) is still 766. Raise it to 767 in the next owner-signed PR.
- 2026-10-27: **P0-3 Build, part 1.** PR #4, merged as `c3ebf37`. Follow-up for P0-8: `testReleaseUnitTest` also writes results, so the test-count guard must count one variant (debug), not sum every XML file. P0-3 stays unticked until part 2 (dependency verification) lands.
  - Added the version catalog `gradle/libs.versions.toml`. Same coordinates and versions, no new dependencies.
  - Build types now follow PLAN:
    - `debug`: debuggable, `.debug` suffix, no longer minified.
    - `staging`: release settings with a `.staging` suffix, signed with the debug key for now (see Needs human). It has no version-name suffix, because `LanguagePack.parseVersion` and the export headers expect a plain `X.Y.Z`.
    - `release`: never signed by Gradle. The `local.properties` signing block is gone; the CI sign job signs.
  - Staging switches to `app/signing/staging-test.jks` automatically once that file exists.
  - Staging unit tests are disabled, so they don't run a second time and inflate the summed test count.
  - A `finalizeDsl` check fails the build if release ever gets a signing config.
  - `dependenciesInfo` is off for APK and bundle. `buildToolsVersion = "35.0.0"` and `android.builder.sdkDownload=false`.
  - **R8 coverage gap:** debug is no longer minified, and CI builds only `assembleDebug`. Until P0-8 adds `assembleStaging` to CI, every PR's Verification must include a local `./gradlew assembleStaging`. P0-8 should also upload the staging APK as a CI artifact.
  - Result: 766 tests green. `assembleDebug`, `assembleStaging` and `assembleRelease` (unsigned) all build. The phone shows `app.fieldwatch.debug` and `app.fieldwatch.staging` installed side by side.
  - **Gate finding: startup crash in 1.1.32, not caused by this PR.**
    - What: `NullPointerException: Null reference used for synchronization` at `FieldwatchViewModel.cachedCameraReport` (`FieldwatchViewModel.kt:3339`), reached from `refreshSitPath`.
    - Cause: `cameraCacheLock` is declared at line 3320, after the `init` block (line 671). The coroutines that block launches on `Dispatchers.Default` can read the lock before its initializer runs.
    - Seen on: debug and staging, on the first launch of each. The second staging launch survived.
    - **Reproduced on `main`:** an `assembleDebug` built from `45a5f70` (minified, non-debuggable, `app.fieldwatch`) crashed the same way on first launch at 06:26Z. Released 1.1.32 has the race.
    - Next cycle fixes it before P0-3 part 2, with an audit of every other property declared after `init` that launched coroutines read.
  - The crash screen's title and stack-trace text are nearly invisible (dark on dark). That's a contrast follow-up for P1-D6.
- 2026-10-27: **P0-2 Repo hygiene.** PR #3.
  - Added `.gitattributes`: `* text=auto eol=lf`; `*.bat` stays CRLF, because cmd.exe misreads LF batch files; goldens (`**/golden/**`, `*.golden`) and binaries are `-text`.
  - Renormalized: only `gradlew.bat` was stored with CRLF, and its index copy is now LF.
  - Deleted `dist/Fieldwatch.apk`, `dist/Fieldwatch_User_Manual.pdf` and `docs/Fieldwatch_User_Manual.pdf`; `*.apk` is now gitignored with no exceptions, and so are the manual outputs `/docs/*.pdf` and `/dist/*.pdf`.
  - `scripts/build_lookups.py` writes `radiodb.txt` with `newline="\n"` and reads it back as UTF-8. The other scripts write only binaries (PDF, PNG).
  - README rows that pointed at the deleted files are fixed; the full README rebrand is P0-4.
  - `core.longpaths` is a per-clone setting, so it's a needs-human item for the owner's Windows checkout.
  - Result: 766 tests green, `assembleDebug` OK.
  - Follow-up for P0-4: `dist/instruction.txt` and `docs/instruction.txt` still list `Fieldwatch.apk`, the manual PDF and the APK SHA-256. The rebrand rewrites them. The card also points to the manual for the full notice. Until P0-8 publishes the manual as a release asset, the in-app disclaimer is the only copy this repo ships.
  - Follow-up for P0-3: the debug build type sets `applicationIdSuffix = ""`, so debug and release share an app id, which contradicts the `run-as <appId>.debug` checklist.

- 2026-10-27: **P0-1 Agent scaffolding.** PR #2. The scaffolding (CLAUDE.md, CHARTER, PLAN, PROGRESS, review prompts, approved dependencies, test baseline, ADR-0001 to ADR-0003) already landed in `bdfe6c3` and `023d2d1`. I checked it against the step text. The step asks for step ids, and PLAN.md had none outside Phases 0, 2 and 5, so this PR adds ids to every step plus a tick convention. It also records the phone facts and the dry-run results above, and ticks P0-1. Result: 766 tests green. Decisions: none new (ADR-0001, ADR-0002 and ADR-0003 already exist). Follow-ups:
  - P0-3 now pins Build-Tools 35.0.0 and turns off SDK auto-download (AGP fetched Build-Tools 34 during this run).
  - Approved dependencies are missing the build plugins (see Needs human).
  - CLAUDE.md's golden section is only the re-bless command. When P1-A2 lands, propose a protected CLAUDE.md PR that adds the golden rules.

- 2026-10-26: Bootstrap kit drafted in the owner's session: charter, plan, CLAUDE.md, loop prompt, watcher, status line, guard hook, setup guide and templates. Branch `p0/bootstrap`, not committed.
