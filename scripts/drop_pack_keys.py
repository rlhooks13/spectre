#!/usr/bin/env -S python3 -I
"""Remove retired keys from the bundled language packs.

A retired key stays in the English map (keys are never removed), with the value "Unused".
Its old translations then exceed the new, shorter length cap and count as ignored lines,
so they are removed from the packs. Only whole `"key": "value"` lines inside "strings" are
dropped; everything else, line endings included, is kept byte for byte.

Usage: python -I scripts/drop_pack_keys.py [--check] key [key ...]
  --check  change nothing; exit 1 if any pack still has one of the keys.
"""

import json
import re
import sys
from pathlib import Path

LANG_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "lang"
KEY = re.compile(r"^[a-z0-9]+(\.[a-z0-9]+)+$")


def process(path: Path, keys: set[str], check: bool) -> int:
    text = path.read_bytes().decode("utf-8")
    before = json.loads(text)
    lines = text.split("\n")
    keep = []
    dropped = 0
    for line in lines:
        m = re.match(r'^\s*"([a-z0-9.]+)": ', line)
        if m and m.group(1) in keys:
            dropped += 1
            continue
        keep.append(line)
    if dropped and not check:
        # Fix the comma on the line before a closing brace if the last entry was dropped.
        for i in range(1, len(keep)):
            if keep[i].strip().startswith("}") and keep[i - 1].rstrip("\r").endswith(","):
                stripped = keep[i - 1].rstrip("\r")
                cr = "\r" if keep[i - 1].endswith("\r") else ""
                if stripped.lstrip().startswith('"'):
                    keep[i - 1] = stripped[:-1] + cr
        result = "\n".join(keep)
        after = json.loads(result)
        expected = {k: v for k, v in before["strings"].items() if k not in keys}
        assert after["strings"] == expected, f"{path.name}: strings changed beyond the dropped keys"
        path.write_bytes(result.encode("utf-8"))
    return dropped


def main(argv: list[str]) -> int:
    check = "--check" in argv
    keys = {a for a in argv if a != "--check"}
    bad = [k for k in keys if not KEY.match(k)]
    if not keys or bad:
        print(f"usage: drop_pack_keys.py [--check] key [key ...]; bad keys: {bad}")
        return 2
    found = 0
    for path in sorted(LANG_DIR.glob("*.json")):
        n = process(path, keys, check)
        found += n
        print(f"{path.name}: {'has' if check else 'dropped'} {n} line(s)")
    return 1 if check and found else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.exit(main(sys.argv[1:]))
