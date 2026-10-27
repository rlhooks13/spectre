# Spectre progress

## Resume pointer
- State: RUNNING
- Current step: P0-1 (committed on the branch; check `gh pr list --head p0/1-agent-scaffolding` for the PR and its CI)
- Branch: `p0/1-agent-scaffolding`
- Phone serial: RFCX205JR3K
- Phone model / API level: Galaxy S23 SM-S911U, Android 16, API 36 (checked with getprop on 2026-10-27). adb authorized, stay awake on (`stay_on_while_plugged_in=7`), adb authorization timeout off, automatic system updates off (owner-reported).
- Loop created at: 2026-10-27T05:41Z
- Last cycle: 2026-10-27T05:41Z

## Needs human (owner)
- Disable the `engineering` and `legal` plugins for this account. Their MCP servers (asana, github, pagerduty, box) show up in the session as failed connections. They aren't claude.ai connectors, aren't authenticated, and managed settings deny every `mcp__` tool, but runbook step 4 expects no servers at all.
- Set up Remote Control and push notifications. Until then, pushes may not arrive.
- Host power settings (SETUP.md).
- Logon tasks: `docs/setup/install-logon-tasks.ps1`.
- `approved-dependencies.md` (protected) is missing build plugins the plan needs: `com.android.library`, `org.jetbrains.kotlin.jvm`, and for `build-logic` the `com.android.tools.build:gradle` and `org.jetbrains.kotlin:kotlin-gradle-plugin` artifacts. Plugins already in use (`com.android.application`, `org.jetbrains.kotlin.android`, `.plugin.compose`, `.plugin.serialization`) and `androidx.compose.ui:ui-tooling` aren't listed by name either. This blocks P2-1. The agent will push a draft branch for the owner to sign before Phase 2.

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
| PR changing a non-protected file: CI runs, squash-merge | Pending: this PR (P0-1) |
| PR changing `CLAUDE.md`: `Protected paths` fails | Pending |
| Push under `.github/workflows/` rejected | Pending (P0-8) |
| `gh secret list` / ruleset edit gives 403 | Partial: blocked locally by the guard hook before reaching GitHub. The server-side 403 isn't verified |
| Re-create the loop with the `loop` skill | Pending (at the 6-day loop-age check) |

## Field-capture requests (owner)
None yet. The agent adds an entry here, plus a `field-capture` issue, for each scenario it needs.

## Log
Newest first. One entry per cycle that changed something. Dates from 2026-10-27 on are UTC; earlier entries use the owner's local time (UTC−7).

- 2026-10-27: **P0-1 Agent scaffolding.** PR: see the resume pointer (filled in once it's opened). The scaffolding (CLAUDE.md, CHARTER, PLAN, PROGRESS, review prompts, approved dependencies, test baseline, ADR-0001 to ADR-0003) already landed in `bdfe6c3` and `023d2d1`. I checked it against the step text. The step asks for step ids, and PLAN.md had none outside Phases 0, 2 and 5, so this PR adds ids to every step plus a tick convention. It also records the phone facts and the dry-run results above, and ticks P0-1. Result: 766 tests green. Decisions: none new (ADR-0001, ADR-0002 and ADR-0003 already exist). Follow-ups:
  - P0-3 now pins Build-Tools 35.0.0 and turns off SDK auto-download (AGP fetched Build-Tools 34 during this run).
  - Approved dependencies are missing the build plugins (see Needs human).
  - CLAUDE.md's golden section is only the re-bless command. When P1-A2 lands, propose a protected CLAUDE.md PR that adds the golden rules.

- 2026-10-26: Bootstrap kit drafted in the owner's session: charter, plan, CLAUDE.md, loop prompt, watcher, status line, guard hook, setup guide and templates. Branch `p0/bootstrap`, not committed.
