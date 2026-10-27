# Spectre: one-time setup (owner)

This sets up the autonomous agent. Steps marked ✅ were done for you on 2026-10-26. The rest are yours, and step 9 is a supervised dry run before the agent runs alone.

The WSL2 distro `spectre` is the isolation boundary. Claude Code's only tamper-proof lock is the machine-wide `managed-settings.json`. Inside the distro it locks only the agent. Your own Claude Code sessions on the Windows host keep their connectors and MCP servers.

**What runs where**

| Where | What |
|---|---|
| Windows host, your account | GitHub admin work (your `gh` login), the release keystore, your commit-signing key, usbipd |
| WSL2 distro `spectre` (Ubuntu 24.04), user `spectre` (no sudo) | The agent: Claude Code in tmux, the repo, the Android SDK, adb, its own limited GitHub token |
| GitHub `rlhooks13/spectre` | Rulesets, the `Protected paths` check, the `release` environment with secrets, CI |
| Dedicated phone | Test device attached to the distro with usbipd, with static reference tags next to it |

**About the shared account.** The agent acts on GitHub as your account, `rlhooks13`, so GitHub shows its work as yours. Three things make that safe:
- **Its token is limited.** It covers only the two Spectre repos, with no admin, workflows, secrets or environments permission.
- **Control files need your signature.** A required check fails any PR that changes a control file unless you signed every commit.
- **GitHub text is never an instruction.** You instruct the agent through its Claude session.

## 1. GitHub ✅

| Item | State |
|---|---|
| Repos | `rlhooks13/spectre` (public), `rlhooks13/spectre-corpus` (private) |
| Remotes in this checkout | `origin` = your fork; `upstream` = OffGridPete/Fieldwatch, fetch-only (its push URL is `DISABLED`). Upstream's 40 release tags were not pushed |
| History | The 2 kit commits were re-authored to `87336707+rlhooks13@users.noreply.github.com` and signed |
| Merging | Squash only; merged branches are deleted |
| Actions | Workflow token is read-only; Actions can't create or approve PRs; outside contributors' PRs need approval to run |
| Security | Secret scanning and push protection are on |
| `main` ruleset | No deletion, no force-push, PR required (0 approvals), required checks `Unit tests`, `APK`, `Protected paths`, **no bypass** |
| `release-tags` ruleset | `v*` and `catalog-v*` tags can't be moved or deleted |
| Releases | Immutable |
| `release` environment | Deploys only from `v*` and `catalog-v*` tags; no reviewers (your decision) |
| Labels | `sev1`, `stop`, `needs-human`, `field-capture`, `gate` |

With no bypass, you also make every change through a PR. To change the rules in an emergency, use Settings → Rules → Rulesets, or `gh api` with your own login on Windows.

## 2. The agent's GitHub token (your step)

Create a **fine-grained** token for your account at GitHub → Settings → Developer settings → Personal access tokens → **Fine-grained tokens** → Generate new token:

| Field | Value |
|---|---|
| Name | `spectre agent (WSL distro)` |
| Expiration | Custom, 1 year. Put the date in your calendar; when it expires the agent stops and sends a push notification |
| Resource owner | `rlhooks13` |
| Repository access | **Only select repositories:** `spectre`, `spectre-corpus` |
| Contents | Read and write |
| Issues | Read and write |
| Pull requests | Read and write |
| Actions | Read-only |
| Commit statuses | Read-only |
| Metadata | Read-only (set automatically) |
| **Everything else** | **No access**, especially Administration, Workflows, Secrets, Environments and Variables |

Then give it to the distro. **Never paste it into a chat.** In a Windows terminal:
```
wsl -d spectre
gh auth login --hostname github.com --git-protocol https
```
Choose **Paste an authentication token**, paste it, then type `exit`.

## 3. Keys ✅

- **Release signing keystore.**
  - Location: `C:\Users\RLHooks\SpectreSigning\spectre-release.p12` (PKCS12, alias `spectre`).
  - Its password is in `BACKUP-ME.txt` in the same folder.
  - Uploaded to the `release` environment: `SPECTRE_KEYSTORE_B64`, `SPECTRE_STORE_PASSWORD`, `SPECTRE_KEY_PASSWORD`, `SPECTRE_KEY_ALIAS`, plus the variable `SPECTRE_CERT_SHA256` = `5bf1c8c5b14f97fd6c93db8b96d48bcacff58d2397985b18310abd4c95a77fe5`.
  - **Your step:** copy that folder to two offline places (a USB drive, a password manager), then delete it from this PC.
    - If it's lost, no update can install over existing copies.
    - If it leaks, someone else can sign Spectre updates.
- **Commit-signing key.**
  - Location: `C:\Users\RLHooks\.ssh\spectre_signing` (ed25519, no passphrase). It never leaves Windows; the distro can't see your drives.
  - Its public half is `docs/setup/allowed_signers`. This checkout signs every commit with it (repo-local git config).
  - Back it up with the keystore. If it's lost, create a new one and update `allowed_signers` through a PR signed with the old key. If both are gone, temporarily disable the `main` ruleset to make that change.
- **Optional:** to show "Verified" badges on GitHub, run `gh auth refresh -s admin:ssh_signing_key`, then `gh ssh-key add ~/.ssh/spectre_signing.pub --type signing`. The `Protected paths` check doesn't need this.
- **The catalog signing key** (ECDSA P-256) comes in Phase 4.

## 4. The distro

### ✅ Done
| Item | State |
|---|---|
| Distro | `spectre` (Ubuntu 24.04), stored in `%USERPROFILE%\WSL\spectre`. Your default distro is unchanged |
| `/etc/wsl.conf` | Windows drive mounting **off**, Windows interop **off**, systemd on, default user `spectre` |
| User | `spectre`: no sudo, password login locked, in group `plugdev` |
| Packages | JDK 17, git, tmux, Python 3 (`python`), gh, bubblewrap, socat, adb udev rules |
| Android SDK | `~/Android/Sdk`: cmdline-tools (checksum-verified), platform-tools, build-tools 35.0.0, platform 35. Your existing license acceptances were copied over from the Windows SDK; no new licenses were accepted |
| Claude Code | 2.1.311, native install in `~/.local/bin` |
| Environment | `ANDROID_HOME` and `PATH` set in `~/.profile` |

Isolation was checked from inside the distro: no Windows drives mounted, `.exe` files can't run, `sudo` is denied, managed settings and hooks are read-only, and the guard blocks a force-push.

### Your part
- **Keep it running.** The host must never sleep (admin PowerShell):
  ```
  powercfg /change standby-timeout-ac 0
  powercfg /change hibernate-timeout-ac 0
  ```
  Keep one terminal open on `wsl -d spectre`; WSL can stop a distro once every session has closed.
- **Claude login** (as `spectre`): run `claude`, then `/login` with your subscription. With interop off the browser won't open by itself: copy the URL it prints into your Windows browser.
- **Root access** for setup work only: `wsl -d spectre -u root`. The agent always runs as `spectre`.
- After step 2, Claude clones the repo into `~/spectre` and sets the agent's git identity (`Spectre agent`, noreply email, unsigned commits).

## 5. Managed settings ✅

Installed as root:
- `/etc/claude-code/managed-settings.json`, from `docs/setup/managed-settings.template.json`
- `/etc/claude-code/spectre/guard.sh`
- `/etc/claude-code/spectre/statusline.sh`

All are owned by root and read-only for `spectre`.

- **What the settings do:**
  - connectors off; no MCP servers
  - only managed permission rules and hooks count
  - `dontAsk` mode, with bypass and auto mode disabled
  - the toolchain allowlist and the hard-stop denylist
  - the guard hook on Bash, Edit, Write and NotebookEdit
  - the usage status line
- **When the repo copies change** (through a PR you signed), reinstall them:
  ```
  wsl -d spectre -u root -- bash -c 'cd /home/spectre/spectre && install -m 0755 tools/hooks/guard.sh /etc/claude-code/spectre/guard.sh && install -m 0755 tools/statusline.sh /etc/claude-code/spectre/statusline.sh && install -m 0644 docs/setup/managed-settings.template.json /etc/claude-code/managed-settings.json'
  ```
- **Sandbox (optional, dry run):** add `"sandbox": {"enabled": true}` and test whether `./gradlew`, `gh` and `adb` still work. Keep it only if they do.

## 6. Dedicated phone (your step; usbipd is installed ✅)

- No personal Google account. Don't put anything on it you wouldn't want the agent to see.
- Developer options:
  - USB debugging **on**
  - **Disable adb authorization timeout** on
  - **Stay awake** on
- Automatic system updates **off**. Location, Wi-Fi and Bluetooth **on**.
- On the charger, with the reference tags beside it: AirTag, SmartTag, Tile, Galaxy Buds, plus a Galaxy phone if you have one, as a confuser.
- Plug it in, then attach it to the distro:
  ```
  usbipd list                                   # find the phone's BUSID
  usbipd bind --busid <BUSID>                   # admin PowerShell, one time
  usbipd attach --wsl --busid <BUSID> --auto-attach
  ```
  Leave the `attach` window running; it reattaches after a replug. Inside the distro, run `adb devices`, accept the prompt on the phone, and tick "Always allow".
- Don't run adb or Android Studio on Windows while the phone is attached to the distro.

## 7. Notifications (your step)

- In the agent's Claude session, turn on Remote Control.
- On your own phone, install the Claude mobile app, signed in to the same account.
- **This is your only alert channel.** GitHub doesn't notify you about activity from your own account, and the agent posts as you.

## 8. Start the loop

```
wsl -d spectre
tmux new -A -s spectre
cd ~/spectre && claude
/loop Run one Spectre cycle exactly as defined in docs/plan/LOOP_PROMPT.md.
```

To detach, press Ctrl-b then d. To reattach later, run `tmux attach -t spectre`.

## 9. Dry run (supervised, before you leave it alone)

Watch the first cycle and tick each check. The agent writes the results into PROGRESS.md.

| ✓ | Check | Expected |
|---|---|---|
| ☐ | `/mcp`, plus the agent listing its tools | No servers and no `mcp__` tools |
| ☐ | Ask it to run `git push --force origin HEAD` on a scratch branch | Blocked by managed deny or the guard |
| ☐ | Ask it to edit `~/.claude/settings.json` | Blocked |
| ☐ | Ask it to run `sudo ls` | Denied |
| ☐ | Status line | Shows `5h N% · 7d N%`; `~/.spectre-agent/usage.json` exists |
| ☐ | Monitor runs `bash tools/usage-watch.sh` | Starts. If `dontAsk` denies Monitor, add the rule the agent reports |
| ☐ | `./gradlew testDebugUnitTest` in the distro | 766 tests pass |
| ☐ | `adb -s <serial> get-state` | `device` |
| ☐ | Push notification | Arrives on your phone |
| ☐ | It opens a PR changing a non-protected file | CI runs; it can squash-merge it |
| ☐ | It opens a PR changing `CLAUDE.md` | `Protected paths` fails; can't merge |
| ☐ | It pushes a change under `.github/workflows/` | Rejected: its token has no Workflows permission |
| ☐ | It runs `gh secret list` or tries to edit a ruleset | 403 |
| ☐ | Ask it to re-create its own loop with the `loop` skill | Works, or it reports the fallback |

If anything fails, fix it here before the loop runs unattended.

## Your ongoing part

- **Changing a control file** (the agent pushes a branch and pings you, or you start one). From this checkout on Windows, which signs automatically:
  ```
  git fetch origin && git switch -c owner/<slug> origin/main
  git merge --squash origin/<agent-branch>   # only if you're adopting an agent branch; review the diff first
  # edit as needed, then commit: commits made in this checkout are signed
  git commit
  git push -u origin owner/<slug>
  gh pr create --fill && gh pr merge --squash --auto
  ```
  The agent's own commits are unsigned, so adopting its branch means squashing it into a commit you sign. Read the diff first: your signature is what unlocks protected paths.
- Record the field captures the agent asks for (`field-capture` issues: walks and drives with ground truth).
- Handle `needs-human` issues. Re-run `/loop` if asked. Renew the agent's token before it expires.
- To stop the agent, open an issue labeled `stop`, add a `STOP` file at the repo root, or attach to tmux and press Esc.
