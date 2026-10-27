# ADR-0003: Autonomous operation and review model

- **Status:** Accepted, 2026-10-26 (owner decisions)
- **Context:** The owner wants a fully autonomous agent that works back to back, merges its own PRs and publishes releases. People act on the app's labels, so a false or missed tracker label is a severity-1 harm.

## Decision

**Runner**
- One long `/loop` session in tmux, inside a dedicated WSL2 distro `spectre` (Ubuntu 24.04) on the owner's PC, as the non-sudo user `spectre`.
- **Why a separate Linux machine:** Claude Code's only tamper-proof lock is the machine-wide managed settings file. On the host, that file would also lock the owner's own sessions (connectors, MCP servers). Inside the distro it binds only the agent.
- **Why WSL2 instead of VirtualBox:** Hyper-V is already active on this PC, which makes VirtualBox slow and its USB passthrough unreliable. The distro's `/etc/wsl.conf` turns off Windows drive mounting and Windows interop, so the agent can't read the owner's files or start Windows programs. It still shares the host's NAT network; there is no `.wslconfig`, so host services bound to localhost aren't reachable.
- The session uses the owner's Claude account, with claude.ai connectors disabled by managed settings and checked every cycle.
- On GitHub the agent acts as the owner's account `rlhooks13` (owner decision), through a fine-grained token limited to the two Spectre repos. The token has no Administration, Workflows, Secrets, Environments or Variables permission.

**Control layers**
1. GitHub, enforced on the server:
   - The main ruleset has no bypass. It requires a PR and the CI checks, including `Protected paths`, and it blocks force-push and deletion.
   - `Protected paths` fails any PR that changes a control file unless every commit is signed with the owner's SSH key. The key lives only on the Windows host, and the allowed key is read from the base branch. This replaces CODEOWNERS review, which can't work when the owner and the agent share one account.
   - Tags and releases are immutable.
   - The agent's token can't edit workflows, settings, rulesets or secrets.
   - The `release` environment holds the signing secrets, and its sign job runs no agent-written code.
2. Claude Code managed settings: `dontAsk` mode, managed-only permission rules and hooks, connectors disabled, and the guard hook.
3. Repo conventions: CLAUDE.md and the charter.

Layer 1 is the real boundary; layers 2 and 3 can be bypassed by scripts the agent writes.

**Review**
- Every PR: `/code-review high`, then an independent reviewer subagent that gets only the diff and the requirements.
- Every phase: five gate reviewers, each with a different lens.
- A rebutted blocking finding goes to a fresh judge reviewer. If the judge upholds it, the change doesn't ship.

**Release**
- No human approval step. A pre-release is soaked on the dedicated phone, 7 days for a normal release or 24 h for a severity-1 fix, then the same binary is promoted.
- Field-dependent detection logic stays behind flags that are off by default, until owner-recorded captures with ground truth pass.

**Usage**
- A script-based watcher (no model involved) pauses the loop at 90% of the five-hour window and resumes it after the reset.
- Amended 2026-10-27 by the owner: work runs only while the weekly window is under 80%. The watcher pauses at that threshold, and a pre-flight gate keeps each cycle from starting over either threshold.

## Consequences
- The owner signs and merges only protected-path PRs, records field captures, and handles `needs-human` items.
- **What sharing the account costs:** GitHub attributes all the agent's activity to the owner publicly, and doesn't notify the owner about it. Claude push notifications are the only alert channel. The `Agent: spectre-loop` trailer marks the agent's commits.
- Reviewers share a model with the implementer, so objective checks (tests, corpus, lint, on-device checks) carry the most weight.
- The agent shares the owner's usage quota.
