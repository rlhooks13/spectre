#!/usr/bin/env bash
# Spectre usage watcher. Run it under the Monitor tool: every stdout line becomes an event for the loop.
#
# Reads the usage file that tools/statusline.sh writes from Claude Code's status-line JSON and prints:
#   USAGE_HIGH window=<five_hour|seven_day> pct=<n> resets_at=<epoch>   once per window cycle at or above THRESHOLD
#   USAGE_RESET window=<w> resets_at=<epoch>                             once, after a window that went high has reset
#   USAGE_STALE age=<s>                                                  once per episode of the file going stale
#   USAGE_UNKNOWN                                                        once per episode of the file having no windows
#
# State goes to a file so a re-armed Monitor (at most 30 min each) doesn't repeat events.
# The config is parsed as KEY=integer lines. It is never sourced, so it can't run code.
set -u

home_dir="${USERPROFILE:-$HOME}"
home_dir="${home_dir//\\//}"
state_dir="${SPECTRE_AGENT_DIR:-$home_dir/.spectre-agent}"
usage_file="$state_dir/usage.json"
state_file="$state_dir/usage-watch.state"
conf="$(cd "$(dirname "$0")" && pwd)/usage-watch.conf"

THRESHOLD=90
POLL=30
STALE=600
if [ -f "$conf" ]; then
    while IFS='=' read -r key value; do
        case "$key" in
            THRESHOLD|POLL|STALE)
                [[ "$value" =~ ^[0-9]+$ ]] && printf -v "$key" '%s' "$value" ;;
        esac
    done < <(grep -E '^[A-Z]+=[0-9]+$' "$conf")
fi
[ "${1:-}" = "--once" ] && once=1 || once=0

mkdir -p "$state_dir"
touch "$state_file"

seen() { grep -qxF "$1" "$state_file"; }
mark() { printf '%s\n' "$1" >> "$state_file"; }
unmark() {
    grep -vxF "$1" "$state_file" > "$state_file.tmp" || true
    mv -f "$state_file.tmp" "$state_file"
}

windows() {
    python -I -c '
import json, sys
sys.stdout.reconfigure(newline="\n")
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        d = json.load(f)
except Exception:
    sys.exit(0)
for w in ("five_hour", "seven_day"):
    v = d.get(w) or {}
    p, r = v.get("used_percentage"), v.get("resets_at")
    if isinstance(p, (int, float)) and isinstance(r, (int, float)):
        print(w, int(p), int(r))
' "$usage_file" | tr -d '\r'
}

while :; do
    now=$(date +%s)
    if [ ! -f "$usage_file" ]; then
        age=$STALE
    else
        age=$(( now - $(stat -c %Y "$usage_file") ))
    fi

    if [ "$age" -ge "$STALE" ]; then
        if ! seen "STALE"; then echo "USAGE_STALE age=$age"; mark "STALE"; fi
    else
        seen "STALE" && unmark "STALE"
        rows=$(windows)
        if [ -z "$rows" ]; then
            if ! seen "UNKNOWN"; then echo "USAGE_UNKNOWN"; mark "UNKNOWN"; fi
        else
            seen "UNKNOWN" && unmark "UNKNOWN"
            while read -r window pct resets; do
                key="HIGH $window $resets"
                if [ "$pct" -ge "$THRESHOLD" ] && [ "$now" -lt "$resets" ] && ! seen "$key"; then
                    echo "USAGE_HIGH window=$window pct=$pct resets_at=$resets"
                    mark "$key"
                fi
            done <<< "$rows"
        fi
    fi

    # A window that went high and whose reset time has now passed gets one USAGE_RESET.
    while read -r tag window resets; do
        [ "$tag" = "HIGH" ] || continue
        if [ "$now" -ge "$resets" ] && ! seen "RESET $window $resets"; then
            echo "USAGE_RESET window=$window resets_at=$resets"
            mark "RESET $window $resets"
        fi
    done < <(grep '^HIGH ' "$state_file")

    [ "$once" -eq 1 ] && exit 0
    sleep "$POLL"
done
