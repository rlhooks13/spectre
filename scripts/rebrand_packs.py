#!/usr/bin/env -S python3 -I
"""Rebrand the bundled language packs from Fieldwatch to Spectre, values only.

Keys, metadata (format id, writtenFor), line endings and layout are left alone. In a
value, the old name is replaced where it stands alone. A hyphen compound
("Fieldwatch-logg") is replaced too, and listed, because a hyphen can also attach a case
ending ("Fieldwatch-ot") that has to change with the new name. Any other inflected form
("Fieldwatchin", "Fieldwatch’ı", "Fieldwatchʼın") is left as it is and listed: it needs a
person who knows the grammar.

Usage: python -I scripts/rebrand_packs.py [--check] [lang_dir]
  --check  change nothing; exit 1 while any value still needs the rebrand or a hand fix.
"""

import json
import re
import sys
from pathlib import Path

DEFAULT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "lang"

# One "key": "value" line inside "strings". Keys follow ^[a-z0-9]+(\.[a-z0-9]+)+$.
LINE = re.compile(r'^(\s*"[a-z0-9]+(?:\.[a-z0-9]+)+": ")(.*)("\s*,?\s*)$')

OLD = r"(?:Fieldwatch|FIELDWATCH)"
# Characters that can glue a case ending onto a name: letters, digits, apostrophes,
# modifier letters and colons. A hyphen is handled separately.
GLUE = r"[\w’'‘ʼ:]"

# Left edge: not inside a word, or right after a JSON escape such as \n.
START = r"(?:(?<=\\[nrt])|(?<![\w-]))"
STANDALONE = re.compile(rf"{START}({OLD})(?!{GLUE}|-)")
COMPOUND = re.compile(rf"{START}({OLD})(-\w+)")
INFLECTED = re.compile(rf"{OLD}{GLUE}+")

NEW = {"Fieldwatch": "Spectre", "FIELDWATCH": "SPECTRE"}


def rebrand_value(value: str) -> tuple[str, list[str]]:
    compounds = [NEW[m.group(1)] + m.group(2) for m in COMPOUND.finditer(value)]
    value = COMPOUND.sub(lambda m: NEW[m.group(1)] + m.group(2), value)
    value = STANDALONE.sub(lambda m: NEW[m.group(1)], value)
    return value, compounds


def process(path: Path, check: bool) -> tuple[int, list[str], list[str]]:
    # Bytes in, bytes out: "\r" stays at the end of its line if a file has CRLF.
    text = path.read_bytes().decode("utf-8")
    json.loads(text)  # refuse to touch a file that isn't valid JSON
    changed = 0
    compounds: list[str] = []
    inflected: list[str] = []
    out = []
    for line in text.split("\n"):
        m = LINE.match(line.rstrip("\r"))
        if m:
            head, value, tail = m.groups()
            new_value, found = rebrand_value(value)
            key = head.strip().split('"')[1]
            compounds += [f"{key}: {form}" for form in found]
            inflected += [f"{key}: {form}" for form in INFLECTED.findall(new_value)]
            if new_value != value:
                changed += 1
                line = head + new_value + tail + ("\r" if line.endswith("\r") else "")
        out.append(line)
    if changed and not check:
        result = "\n".join(out)
        json.loads(result)
        path.write_bytes(result.encode("utf-8"))
    return changed, compounds, inflected


def main(argv: list[str]) -> int:
    check = "--check" in argv
    args = [a for a in argv if a != "--check"]
    lang_dir = Path(args[0]) if args else DEFAULT_DIR
    pending = 0
    for path in sorted(lang_dir.glob("*.json")):
        changed, compounds, inflected = process(path, check)
        pending += (changed if check else 0) + len(inflected)
        verb = "would change" if check else "changed"
        print(
            f"{path.name}: {verb} {changed} value(s); "
            f"{len(compounds)} hyphen compound(s) to check; {len(inflected)} inflected form(s) to fix by hand"
        )
        for item in compounds:
            print(f"  check   {item}")
        for item in inflected:
            print(f"  fix     {item}")
    return 1 if pending else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.exit(main(sys.argv[1:]))
