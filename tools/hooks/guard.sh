#!/usr/bin/env bash
# Spectre PreToolUse guard: a deterministic backstop for the charter's hard stops.
# The owner installs a root-owned copy at /etc/claude-code/spectre/guard.sh and registers it in managed settings
# for the Bash, Edit and Write tools (see docs/setup/managed-settings.template.json).
# Claude Code sends the tool call as JSON on stdin. Exit code 2 blocks the call, with stderr as the reason.
#
# This is not a security boundary: scripts the agent writes can do what this hook never sees. The real
# controls are the GitHub rulesets, CODEOWNERS, the bot's token scopes, and the VM.
python -I -c '
import json, re, sys

try:
    call = json.load(sys.stdin)
except Exception:
    sys.exit(0)
tool = call.get("tool_name", "")
args = call.get("tool_input") or {}

def block(why):
    print("Blocked by the Spectre guard: " + why + ". See docs/plan/CHARTER.md (hard stops).", file=sys.stderr)
    sys.exit(2)

if tool == "Bash":
    cmd = " ".join(str(args.get("command", "")).split())
    rules = [
        (r"\bgit\b(\s+-C\s+\S+)?(\s+-c\s+\S+)*\s+push\b.*(\s--force\b|\s--force-with-lease\b|\s-f\b|\s-\w*f\w*\b|\s\+\S)",
         "force-push"),
        (r"\bgit\b.*\bpush\b.*(\s--delete\b|\s-d\b|\s:\S)", "deleting a remote branch or tag"),
        (r"\bgit\b.*\bpush\b.*\s--mirror\b", "mirror push"),
        (r"\bgit\s+(filter-branch|filter-repo|replace)\b", "history rewriting"),
        (r"\bgit\s+config\s+(--system|--global)\b.*\b(hooksPath|credential|url\.)", "changing global git hooks, credentials or URL rewrites"),
        (r"\bgh\s+(release|repo)\s+delete\b", "deleting a release or the repo"),
        (r"\bgh\s+repo\s+(edit|rename|archive)\b", "changing repo settings"),
        (r"\bgh\s+(secret|variable|ruleset)\b", "touching secrets, variables or rulesets"),
        (r"\bgh\s+auth\s+(login|logout|refresh|setup-git|token|switch)\b", "changing or printing GitHub credentials"),
        (r"\bgh\s+api\b.*(-X|--method)\s*(DELETE|delete)\b", "DELETE through the GitHub API"),
        (r"\bgh\s+api\b.*/(rulesets|protection|environments|secrets|variables|collaborators|hooks|keys)\b", "changing protected repo settings through the API"),
        (r"\bclaude\s+mcp\s+(add|enable)\b", "enabling MCP servers"),
        (r"\b(curl|wget)\b.*\|\s*(ba|z)?sh\b", "piping a download into a shell"),
        (r"\b(keytool|apksigner)\b.*\bsign", "signing locally; releases are signed only in CI"),
    ]
    for pattern, why in rules:
        if re.search(pattern, cmd):
            block(why)

if tool in ("Edit", "Write", "NotebookEdit"):
    path = str(args.get("file_path", "")).replace("\\", "/").lower()
    guarded = ("/.claude/settings", "/.claude.json", "/etc/claude-code/", "/program files/claudecode/")
    if any(g in path for g in guarded):
        block("editing Claude Code settings or managed files")

sys.exit(0)
'
