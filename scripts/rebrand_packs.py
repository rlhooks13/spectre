#!/usr/bin/env python3
"""Rebrand the bundled language packs from Fieldwatch to Spectre, values only.

Keys, metadata (format id, writtenFor) and line layout are left alone. A value gets the
new name only where the old one stands alone or starts a hyphen compound
("Fieldwatch-logg"). Inflected forms such as "Fieldwatchin" or "Fieldwatch’ı" need a
person who knows the grammar, so they are left as they are and listed per language.

Usage: python -I scripts/rebrand_packs.py [--check] [lang_dir]
  --check  change nothing; exit 1 if any value still needs the plain rebrand.
"""

import json
import re
import sys
from pathlib import Path

DEFAULT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "lang"

# One "key": "value" line inside "strings". Keys follow ^[a-z0-9]+(\.[a-z0-9]+)+$.
LINE = re.compile(r'^(\s*"[a-z0-9]+(?:\.[a-z0-9]+)+": ")(.*)("\s*,?\s*)$')

# The old name, not followed by a letter, digit, or apostrophe (an inflection).
PLAIN = re.compile(r"(Fieldwatch|FIELDWATCH)(?![\w’'])")
INFLECTED = re.compile(r"(?:Fieldwatch|FIELDWATCH)[\w’']+")

NEW = {"Fieldwatch": "Spectre", "FIELDWATCH": "SPECTRE"}


def rebrand_value(value: str) -> str:
    return PLAIN.sub(lambda m: NEW[m.group(1)], value)


def process(path: Path, check: bool) -> tuple[int, list[str]]:
    text = path.read_text(encoding="utf-8")
    json.loads(text)  # refuse to touch a file that isn't valid JSON
    changed = 0
    inflected: list[str] = []
    out = []
    for line in text.split("\n"):
        m = LINE.match(line)
        if m:
            head, value, tail = m.groups()
            new_value = rebrand_value(value)
            if new_value != value:
                changed += 1
                line = head + new_value + tail
            key = head.strip().split('"')[1]
            inflected += [f"{key}: {form}" for form in INFLECTED.findall(new_value)]
        out.append(line)
    if changed and not check:
        result = "\n".join(out)
        json.loads(result)
        path.write_text(result, encoding="utf-8", newline="\n")
    return changed, inflected


def main(argv: list[str]) -> int:
    check = "--check" in argv
    args = [a for a in argv if a != "--check"]
    lang_dir = Path(args[0]) if args else DEFAULT_DIR
    pending = 0
    for path in sorted(lang_dir.glob("*.json")):
        changed, inflected = process(path, check)
        pending += changed
        verb = "would change" if check else "changed"
        print(f"{path.name}: {verb} {changed} value(s); {len(inflected)} inflected form(s) to fix by hand")
        for item in inflected:
            print(f"  {item}")
    return 1 if check and pending else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.exit(main(sys.argv[1:]))
