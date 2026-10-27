# ADR-0001: Fork identity

- **Status:** Accepted, 2026-10-26 (owner decision)
- **Context:** Fieldwatch 1.1.32 is MIT-licensed by Off Grid Pete LLC. The owner is forking it as an independent app and wants it fully separate from upstream.

## Decision
- **Display name:** "Spectre". It lives in one `Brand` object, so the display name can be changed in a single PR.
- **Permanent internal ids:** applicationId `io.github.rlhooks13.spectre`. The catalog format id `io.github.rlhooks13.spectre/catalog`, the URI scheme `spectre://`, the Kotlin packages under `io.github.rlhooks13.spectre`, and the `SPECTRE_*` secret names don't change even if the display name does.
- **Licensing:**
  - LICENSE keeps the "Copyright (c) 2026 Off Grid Pete LLC" line and adds one for "the Spectre contributors".
  - NOTICE keeps upstream's text, including the IEEE, Bluetooth SIG and Fast Pair exclusions, and appends: "Spectre is an independent fork of Fieldwatch 1.1.32 by Off Grid Pete LLC (formerly named Spectre through 1.2.14). It is not made, reviewed or endorsed by Off Grid Pete LLC."
- **Format ids:**
  - The app never writes the `spectre-signatures` format id; official Fieldwatch would accept it and misread it.
  - v1 `fieldwatch-*` formats are still written while the schema is identical, in 0.1.x.
  - New schemas get new ids.
- **Upstream:** reference only. No code merges, and no code from before Fieldwatch 1.1.32.

## Consequences
- **No in-place upgrade from official Fieldwatch.** Migration happens by importing exported settings and signature packs.
- **Recorded risk:** upstream renamed away from "Spectre" because another app already used it. The display name may have to change before or after the first public release; the internal ids won't.
