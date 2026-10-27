# Spectre progress

## Resume pointer
- State: RUNNING
- Current step: gate fix for the startup crash, PR #5 (waiting on CI, then squash-merge). Next: P0-3 part 2 (dependency verification, protected PR).
- Branch: `p0/fix-viewmodel-init-order`
- Phone serial: RFCX205JR3K
- Phone model / API level: Galaxy S23 SM-S911U, Android 16, API 36 (checked with getprop on 2026-10-27). adb authorized, stay awake on (`stay_on_while_plugged_in=7`), adb authorization timeout off, automatic system updates off (owner-reported).
- Loop created at: 2026-10-27T05:41Z
- Last cycle: 2026-10-27T06:44Z

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
| PR changing `CLAUDE.md`: `Protected paths` fails | Pending |
| Push under `.github/workflows/` rejected | Pending (P0-8) |
| `gh secret list` / ruleset edit gives 403 | Partial: blocked locally by the guard hook before reaching GitHub. The server-side 403 isn't verified |
| Re-create the loop with the `loop` skill | Pending (at the 6-day loop-age check) |

## Field-capture requests (owner)
None yet. The agent adds an entry here, plus a `field-capture` issue, for each scenario it needs.

## Log
Newest first. One entry per cycle that changed something. Dates from 2026-10-27 on are UTC; earlier entries use the owner's local time (UTC−7).

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
