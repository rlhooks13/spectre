#!/usr/bin/env bash
# Status line for the Spectre agent account. Claude Code pipes the session JSON to stdin.
# Saves the plan usage windows (rate_limits) for tools/usage-watch.sh and prints one short line.
# The owner installs a root-owned copy at /etc/claude-code/spectre/statusline.sh (see docs/setup/SETUP.md).
home_dir="${USERPROFILE:-$HOME}"
home_dir="${home_dir//\\//}"
out_dir="${SPECTRE_AGENT_DIR:-$home_dir/.spectre-agent}"
mkdir -p "$out_dir"

python -I -c '
import json, os, sys, time
sys.stdout.reconfigure(encoding="utf-8", newline="\n")
out = sys.argv[1]
try:
    d = json.load(sys.stdin)
except Exception:
    print("spectre-agent · status n/a")
    sys.exit(0)
rl = d.get("rate_limits") or {}
saved = {"captured_at": int(time.time())}
parts = []
for w, label in (("five_hour", "5h"), ("seven_day", "7d")):
    v = rl.get(w) or {}
    p, r = v.get("used_percentage"), v.get("resets_at")
    if isinstance(p, (int, float)) and isinstance(r, (int, float)):
        saved[w] = {"used_percentage": p, "resets_at": int(r)}
        parts.append(f"{label} {int(p)}%")
tmp = out + ".tmp"
with open(tmp, "w", encoding="utf-8", newline="\n") as f:
    json.dump(saved, f)
os.replace(tmp, out)
ctx = (d.get("context_window") or {}).get("used_percentage")
if isinstance(ctx, (int, float)):
    parts.append(f"ctx {int(ctx)}%")
print("spectre-agent · " + (" · ".join(parts) if parts else "usage n/a"))
' "$out_dir/usage.json"
