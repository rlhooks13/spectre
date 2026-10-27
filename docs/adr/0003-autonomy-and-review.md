# ADR-0003: Autonomous operation and review model

- **Status:** Accepted, 2026-10-26 (owner decisions)
- **Context:** The owner wants a fully autonomous agent that works back to back, merges its own PRs and publishes releases. People act on the app's labels, so a false or missed tracker label is a severity-1 harm.

## Decision

**Runner**
- One long `/loop` session in tmux, inside a dedicated Ubuntu VM on the owner's PC, as the non-sudo user `spectre`.
- **Why a VM:** Claude Code's only tamper-proof lock is the machine-wide managed settings file. On the host, that file would also lock the owner's own sessions (connectors, MCP servers). Inside the VM it binds only the agent, and Linux adds Claude Code's sandbox option.
- The session uses the owner's Claude account, with claude.ai connectors disabled by managed settings and checked every cycle.
- The agent acts on GitHub as a bot account.

**Control layers**
1. GitHub, enforced on the server:
   - The main ruleset blocks force-push and deletion and requires CI and CODEOWNERS review on protected paths.
   - Tags are immutable.
   - The bot's token has no workflow scope.
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
- A script-based watcher (no model involved) pauses the loop at 90% of either plan window and resumes it after the reset.

## Consequences
- The owner reviews only protected-path PRs, records field captures, and handles `needs-human` items.
- Reviewers share a model with the implementer, so objective checks (tests, corpus, lint, on-device checks) carry the most weight.
- The agent shares the owner's usage quota.
