# ADR-0011: Language-pack file cap tracks the English map

- **Status:** Accepted, 2026-10-27
- **Context:**
  - An imported language pack is untrusted data. `LanguagePack.MAX_BYTES` refuses a file over the cap before parsing it.
  - Upstream set the cap at 264 KiB, just under the theoretical worst case: every English line translated to its slot cap with a two-byte letter. `LanguagePackTest.capFillingPackIsAcceptedAndNotShipped` keeps that relationship. The worst-case pack must be either accepted whole, or refused and within 8 KiB of the cap.
  - Phase 0 added English lines (new keys for changed safety and legal copy, in P0-7a, P0-7c and P0-4d). With PR #10 on top of `main`, the worst-case pack is 278,802 bytes. The 264 KiB cap (270,336 bytes) plus the 8 KiB margin gives 278,528, so the test fails.

## Decision
- **Raise the cap to 272 KiB (278,528 bytes).** The worst-case pack (278,802 bytes) is still refused, and it's within the 8 KiB margin of the new cap.
- **The test stays unchanged.** Its 8 KiB margin is the tripwire that forces this decision each time the English map grows enough.
- **The author guide and the requirements doc** say 272 KiB.

## Consequences
- Real packs are far smaller: a bundled pack is about 100 KiB. The change only matters for a pack written at every line's cap.
- The parse still caps the language name (40 characters) and the map (2,000 keys), so the protection against a huge or abusive file stays.
- When the English map grows again and the test trips, repeat this decision. Raise the cap in KiB steps, or trim English, and record it here.
