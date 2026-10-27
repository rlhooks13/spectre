# ADR-0013: Language keys carry an integer revision

- **Status:** Accepted, 2026-10-27
- **Context:**
  - Each English key recorded the versionName that added it (`addedIn`, always "1.1.27"). A pack's `writtenFor` was compared with it to count keys "new since your pack".
  - Spectre restarts at 0.x (ADR-0002), and P0-8c takes the version from the git tag. A Spectre template would then say `writtenFor: "0.1.0"`, and every 1.1.27 key would look newer than it. The count would be wrong for every Spectre pack.
  - Phase 0 also added six keys that a pack written for Fieldwatch 1.1.27–1.1.32 never had, but they shared "1.1.27", so the count missed them.
  - PLAN (Language versioning) planned this for Phase 1. It moves to P0-4h because P0-8c depends on it.

## Decision
- **`EnglishLine.revision: Int`** replaces `addedIn`.
  - Revision 1 is the map Fieldwatch 1.1.27 shipped.
  - Revision 2 is Spectre Phase 0: `ui.permissions.receive.only`, `ui.scan.status.channel`, `ui.export.shares.catalog.no.update`, `settings.layer.download.source`, `settings.layer.source` and `settings.layer.alert.region`.
  - `EnglishCopy.KEY_REVISION` is the newest revision. The first PR that adds keys after a release raises it, and new keys take the new value.
- **The template writes `keyRevision`**, an optional integer in `fieldwatch-language`. The format only grows, so `formatVersion` stays 1, and older apps ignore the field.
- **"New since" counts untranslated keys whose revision is above the pack's `keyRevision`.** A pack without `keyRevision` came from Fieldwatch:
  - `writtenFor` 1.x at or after 1.1.27 maps to revision 1;
  - an earlier 1.x maps to revision 0;
  - anything else gives no count, as a missing `writtenFor` already did.
- `writtenFor` stays: it's shown on the confirm dialog, and it's informational.

## Consequences
- The count no longer depends on the app's version scheme.
- Someone has to raise `KEY_REVISION` once per release that adds keys. A forgotten raise undercounts; it never crashes. `LanguageKeyRevisionTest` pins the current assignment.
- PR #10 (P0-4d) adds two keys. When it is re-synced, they take revision 2.
- CLAUDE.md's rule "every line must have `addedIn == "1.1.27"`" is obsolete. CLAUDE.md is protected, so the owner updates it.
