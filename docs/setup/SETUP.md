# Spectre: one-time setup (owner)

This sets up the autonomous agent. Plan on 2–3 hours. Do the steps in order. Anything marked **(dry run)** is checked in step 9.

The VM is the isolation boundary. Claude Code's only tamper-proof lock is the machine-wide `managed-settings.json`. Inside a VM it locks only the agent. Your own Claude Code sessions on the Windows host keep their connectors and MCP servers.

**What runs where**

| Where | What |
|---|---|
| Windows host, your account | GitHub admin work, the signing keys, VirtualBox |
| Ubuntu VM, user `spectre` (no sudo) | The agent: Claude Code in tmux, the repo, the Android SDK, adb |
| GitHub | Rulesets, CODEOWNERS, the `release` environment with secrets, CI |
| Dedicated phone | Test device over USB passthrough, with static reference tags next to it |

## 1. GitHub, as `rlhooks13`

1. **Create the repos.**
   - Create **public** `rlhooks13/spectre`, empty: no README or license.
   - Create **private** `rlhooks13/spectre-corpus`.
2. **Push this checkout from the Windows host** (in Git Bash, in this folder). Upstream stays fetch-only.
   ```
   git remote rename origin upstream
   git remote set-url --push upstream DISABLED
   git remote add origin https://github.com/rlhooks13/spectre.git
   git switch main && git merge --ff-only p0/bootstrap   # after the bootstrap kit is committed
   git push -u origin main
   ```
3. **Actions settings** (Settings → Actions → General):
   - Workflow permissions: **Read repository contents**.
   - **Untick** "Allow GitHub Actions to create and approve pull requests".
   - Fork pull requests: require approval for all outside contributors.
4. **Rulesets** (Settings → Rules → Rulesets). Create these *after* the push above.
   - **`main`**, targeting the default branch:
     - Restrict deletions.
     - Block force pushes.
     - Require a pull request: **0 approvals**, **Require review from Code Owners**, dismiss stale approvals.
     - Require status checks: the CI jobs (`Unit tests`, `APK`, and later ones).
     - Bypass list: only you.
   - **`release-tags`**, targeting tags `v*` and `catalog-v*`: Restrict updates and Restrict deletions. Leave creation allowed so the bot can tag.
   - Turn on release immutability (Settings → General → Releases) if your account offers it.
5. **`release` environment** (Settings → Environments).
   - Deployment branches and tags: **selected tags** `v*` and `catalog-v*`.
   - **No required reviewers**. That's your decision: releases are fully autonomous.
   - Secrets come in step 3.
6. **Collaborators.** Invite the bot (step 2) with the **Write** role on both repos. Never give it Admin.

## 2. Bot account

1. **Create the account.** Make a separate GitHub account (e.g. `spectre-bot`) with its own email, and turn on 2FA. Accept both invites.
2. **Create the token.** Use a **classic** token with **only the `repo` scope**: no `workflow`, `delete_repo`, `admin:*` or `write:packages`.
   - Without `workflow`, the bot can't push changes under `.github/workflows/`. The agent drafts workflow changes in `docs/setup/workflows/` for you to copy over.
   - Fine-grained tokens generally can't be scoped to another user's personal repo. If GitHub lets you create one limited to `rlhooks13/spectre` (Contents, Pull requests and Issues set to read/write, Workflows off), prefer it.
3. **Expiry.** Set it to 1 year. Put the date in your calendar: an expired token stops the agent, and it will push-notify you.

## 3. Signing keys (Windows host, your account; never in the VM)

1. **Generate the keystore:**
   ```
   keytool -genkeypair -v -keystore spectre-release.jks -alias spectre -keyalg RSA -keysize 4096 \
     -validity 36500 -dname "CN=Spectre contributors, O=Spectre"
   keytool -list -v -keystore spectre-release.jks -alias spectre     # copy the SHA256 fingerprint
   base64 -w0 spectre-release.jks > spectre-release.jks.b64
   ```
2. **Add the `release` environment secrets:** `SPECTRE_KEYSTORE_B64` (the .b64 contents), `SPECTRE_STORE_PASSWORD`, `SPECTRE_KEY_ALIAS` (`spectre`), `SPECTRE_KEY_PASSWORD`.
3. **Add the environment variable** `SPECTRE_CERT_SHA256` (the fingerprint). The sign job checks it.
4. **Back up** the `.jks` and its passwords in two offline places. Then delete the `.b64`.
   - **If this key is lost**, no update can ever install over existing copies.
5. **The catalog signing key** (ECDSA P-256) comes in Phase 4. The agent opens a `needs-human` issue with the command when it's needed.

## 4. The VM

### On the host
- **VirtualBox:** install VirtualBox 7 and its Extension Pack (USB 3). VMware Workstation also works.
- **The VM:** Ubuntu 24.04 LTS (Server is enough), with 8 GB RAM, 4 vCPUs, 80 GB disk and NAT networking.
- **USB filter:** add one for the dedicated phone, so the VM claims it whenever it's plugged in.
- **Power:** the host never sleeps. In an admin PowerShell:
  ```
  powercfg /change standby-timeout-ac 0
  powercfg /change hibernate-timeout-ac 0
  ```
- **Autostart:** use a Task Scheduler task at your logon to start the VM headless: `VBoxManage startvm "spectre" --type headless`.
- **adb:** don't run adb or Android Studio on the host while the VM owns the phone. The host's adb server would grab it.

### In the VM, as your admin user
```
sudo apt update
sudo apt install -y openjdk-17-jdk-headless git tmux unzip python3 python-is-python3 \
  android-sdk-platform-tools-common bubblewrap socat
sudo adduser spectre            # set a password; do NOT add spectre to sudo
sudo usermod -aG plugdev spectre
```
Install the GitHub CLI by following https://github.com/cli/cli/blob/trunk/docs/install_linux.md.

### In the VM, as `spectre`
1. **Android SDK.** Get the command-line tools from https://developer.android.com/studio#command-tools and unzip them to `~/Android/Sdk/cmdline-tools/latest`. Then:
   ```
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   cat >> ~/.profile <<'EOF'
   export ANDROID_HOME="$HOME/Android/Sdk"
   export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"
   EOF
   ```
2. **GitHub as the bot.** Paste the token when asked, then clear your clipboard:
   ```
   gh auth login --hostname github.com --git-protocol https
   gh auth setup-git
   gh repo clone rlhooks13/spectre ~/spectre
   git -C ~/spectre config user.name "spectre-bot"
   git -C ~/spectre config user.email "<bot id>+spectre-bot@users.noreply.github.com"
   ```
3. **Claude Code.** Install it with the native Linux installer from https://code.claude.com/docs. Run `claude` and `/login` with your Claude subscription (not an API key).
4. **Phone.** Plug it in. Run `adb devices`, accept the prompt on the phone and tick "Always allow". Write the serial into `docs/plan/PROGRESS.md`.

## 5. Managed settings (VM, as your admin user)

```
sudo mkdir -p /etc/claude-code/spectre
sudo install -m 0755 -o root -g root /home/spectre/spectre/tools/hooks/guard.sh   /etc/claude-code/spectre/guard.sh
sudo install -m 0755 -o root -g root /home/spectre/spectre/tools/statusline.sh    /etc/claude-code/spectre/statusline.sh
sudo install -m 0644 -o root -g root /home/spectre/spectre/docs/setup/managed-settings.template.json /etc/claude-code/managed-settings.json
```

- **What it sets:**
  - connectors off; no MCP servers
  - only managed permission rules and hooks count
  - `dontAsk` mode, with bypass and auto mode disabled
  - the toolchain allowlist and the hard-stop denylist
  - the guard hook on Bash, Edit, Write and NotebookEdit
  - the usage status line
- **When the repo copies change** (they're protected, so you'll have reviewed the PR), re-run the `install` lines. The agent can't do this itself.
- **Sandbox (optional, dry run):** add `"sandbox": {"enabled": true}` and test whether `./gradlew`, `gh` and `adb` still work. Keep it only if they do.

## 6. Dedicated phone

- No personal Google account. Don't put anything on it you wouldn't want the agent to see.
- Developer options:
  - USB debugging **on**
  - **Disable adb authorization timeout** on
  - **Stay awake** on
- Automatic system updates **off**. Location, Wi-Fi and Bluetooth **on**.
- On the charger, with the reference tags beside it: AirTag, SmartTag, Tile, Galaxy Buds. Also a Galaxy phone if you have one, as a confuser.

## 7. Notifications

- In the agent's Claude session, turn on Remote Control.
- On your own phone, install the Claude mobile app, signed in to the same account.
- **Fallback:** watch the repo on GitHub, so `needs-human` issues and releases notify you.

## 8. Start the loop (VM, as `spectre`)

```
tmux new -A -s spectre
cd ~/spectre && claude
/loop Run one Spectre cycle exactly as defined in docs/plan/LOOP_PROMPT.md.
```

To detach, press Ctrl-b then d. To reattach later, run `tmux attach -t spectre`.

## 9. Dry run (supervised, before you leave it alone)

Watch the first cycle and tick each check. Write the results into PROGRESS.md (the agent does this when asked).

| ✓ | Check | Expected |
|---|---|---|
| ☐ | `/mcp`, plus the agent listing its tools | No servers and no `mcp__` tools |
| ☐ | Ask it to run `git push --force origin HEAD` on a scratch branch | Blocked by managed deny or the guard |
| ☐ | Ask it to edit `~/.claude/settings.json` | Blocked |
| ☐ | Ask it to run `sudo ls` | Denied |
| ☐ | Status line | Shows `5h N% · 7d N%`; `~/.spectre-agent/usage.json` exists |
| ☐ | Monitor runs `bash tools/usage-watch.sh` | Starts. If `dontAsk` denies Monitor, add the rule the agent reports |
| ☐ | `./gradlew testDebugUnitTest` in the VM | 766 tests pass |
| ☐ | `adb -s <serial> get-state` | `device` |
| ☐ | Push notification | Arrives on your phone |
| ☐ | The bot opens a PR changing a non-protected file | CI runs; the bot can merge it |
| ☐ | The bot opens a PR changing `CLAUDE.md` | Can't merge until you approve |
| ☐ | The bot pushes a change under `.github/workflows/` | Rejected (no `workflow` scope) |
| ☐ | Ask it to re-create its own loop with the `loop` skill | Works, or it reports the fallback |

If anything fails, fix it here before the loop runs unattended. The agent marks the matching PLAN step `needs-human` if it hits one of these later.

## Your ongoing part

- Review PRs that touch protected paths (CODEOWNERS sends you a review request).
- Record the field captures the agent asks for (`field-capture` issues: walks and drives with ground truth).
- Handle `needs-human` issues. Re-run `/loop` if asked. Renew the bot token before it expires.
- To stop the agent, create an issue labeled `stop`, add a `STOP` file at the repo root, or attach to tmux and press Esc.
