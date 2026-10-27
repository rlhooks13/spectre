# ADR-0011: Language-pack file cap tracks the English map

- **Status:** Accepted, 2026-10-27
- **Context:**
  - An imported language pack is untrusted data. `LanguagePack.MAX_BYTES` refuses a file over the cap before parsing it, both at import (`LanguageFiles.readCapped`) and in `LanguagePack.parse`.
  - The inherited cap was 264 KiB. Nothing records why upstream chose it. `LanguagePackTest.capFillingPackIsAcceptedAndNotShipped` builds the worst-case pack: every English line at its slot cap, written with a two-byte letter. That pack must be either accepted whole, with every line shown at its cap, or refused and within 8 KiB of the cap. At 264 KiB it was refused, so the line-by-line branch never ran.
  - Phase 0 added English lines (new keys for changed safety and legal copy, in P0-7a, P0-7c and P0-4d). With PR #10 on top of `main`, the worst-case pack is 278,802 bytes. That is more than 8 KiB over 264 KiB (278,528 bytes), so the test fails.

## Decision
- **Raise the cap to 280 KiB (286,720 bytes).** The worst-case pack fits, so a translator who keeps every line inside its slot, in a two-byte script, is no longer refused for file size. The test's accepted branch now runs and checks every line renders at its cap.
- **Considered and rejected: 272 KiB,** just under the worst case. It keeps the test green but keeps refusing a pack that follows every documented slot rule, and it trips again after about 7.9 KB of growth.
- **The test's logic and its 8 KiB margin are unchanged.** Only the literals around it moved: a comment, and the author-guide check now derives the number from `MAX_BYTES`. A new test checks that every "KiB" figure in the author guide and the requirements doc equals the cap.
- **The author guide and the requirements doc** say 280 KiB.

## Consequences
- Bundled packs are at most about 130 KiB (`el.json`, 132,948 bytes), so the change only matters for a pack written near every line's cap.
- The 16 KiB raise is negligible for memory and parse time. The name cap (40 characters) and the key cap (2,000 keys) still apply on top of the byte cap.
- Headroom: the worst-case pack can grow by about 7.9 KB before it is refused, and by 8 KiB more before the test trips. One long body line at its cap adds up to about 1.4 KB, so this is roughly 11 more long English lines. Phase 1 adds copy, so expect to revisit this.
- Only the two-byte case is sized. A pack in a three-byte script (CJK, Devanagari, Thai) with every long line near its cap can still pass 280 KiB and be refused whole. No bundled pack uses such a script. If one is added, size the cap for three-byte text.
- When the test trips again, repeat this decision: raise the cap in KiB steps, or trim English, and record it here.
