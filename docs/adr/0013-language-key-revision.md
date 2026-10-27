# ADR-0013: Language keys carry an integer revision

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Each English key recorded `addedIn = "1.1.27"`. A pack's `writtenFor` was compared with it to count keys "new since your pack".
  - **Spectre's version scheme breaks that count.** Spectre restarts at 0.x (ADR-0002), and P0-8c takes the version from the git tag. A Spectre template would say `writtenFor: "0.1.0"`, and every key would look newer than it.
  - **The count was already wrong.** Fieldwatch kept adding keys through 1.1.32, all stamped "1.1.27":

    | Release | Keys added |
    |---|---|
    | 1.1.28 | 5 |
    | 1.1.29 | 2 |
    | 1.1.31 | 53 |
    | 1.1.32 | 69 |

    Spectre Phase 0 added 6 more. A pack written for 1.1.27 was told nothing was new.
  - PLAN (Language versioning) planned an integer revision for Phase 1. It moves to P0-4h because P0-8c depends on it.

## Decision
- **`EnglishLine.revision: Int`** replaces `addedIn`. `LanguageKeyRevisions` holds the table, read from each release commit:

  | Revision | Source | Keys |
  |---|---|---|
  | 1 | Fieldwatch 1.1.27 (`3ebda52`) | 1,099 |
  | 2 | Fieldwatch 1.1.28 (`a2563a3`) | 5 |
  | 3 | Fieldwatch 1.1.29 (`73d1006`); 1.1.30 added none | 2 |
  | 4 | Fieldwatch 1.1.31 (`da086fc`) | 53 |
  | 5 | Fieldwatch 1.1.32 (`819365d`) | 69 |
  | 6 | Spectre Phase 0 | 8 (6, plus 2 from P0-4d) |

  `EnglishCopy.KEY_REVISION` is the newest (6).
- **Revision 1 is frozen.** A test hashes its key set, which matches `3ebda52`'s keys exactly. A key left out of the table falls to revision 1 and fails that test, so a new key can't be silently treated as old.
- **New keys** go in the table at the newest revision. The first key-adding PR after a release raises `NEWEST`.
- **The template writes `keyRevision`,** an optional integer in `fieldwatch-language`. The format only grows, so `formatVersion` stays 1, and older apps ignore unknown keys.
- **"New since"** counts untranslated keys whose revision is above the pack's `keyRevision`.
  - The value is read leniently. Anything other than a positive integer drops the count, never the pack.
  - A pack without `keyRevision` came from Fieldwatch. Its `writtenFor` maps to the newest release at or before it (1.1.27 to 1.1.32). An earlier 1.x maps to 0, and anything else, such as a hand-edited 0.x or a future Spectre 1.x, gives no count.
- **Not stored.** Packs on the phone don't keep `keyRevision`, because nothing computes "new since" after import.
- `writtenFor` stays: it's shown on the confirm dialog, and it's informational.

## Consequences
- The count no longer depends on the app's version scheme, and it's now right for packs from every Fieldwatch release that had this map. A 1.1.27 pack now reports 135 new keys (fewer if it translates some), where it reported 0.
- PR #10 (P0-4d) adds two keys. When it is re-synced they go in the table at revision 6.
- CLAUDE.md's rule "every line must have `addedIn == "1.1.27"`" is obsolete. CLAUDE.md is protected, so the owner updates it to: add the key to `LanguageKeyRevisions` at the newest revision.
