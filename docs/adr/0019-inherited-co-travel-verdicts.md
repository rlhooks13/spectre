# ADR-0019: Inherited co-travel verdicts stay on; new tail logic stays flagged

- **Status:** Accepted, 2026-10-28. This records the owner's decision of 2026-10-27.
- **Context:**
  - The charter puts field-dependent logic behind flags that default off.
  - The finder, beacon, wearable and phone "moved with you" and "possible tail" verdicts in the Debrief came from Fieldwatch 1.1.32 unflagged, and the Debrief has always shown them.
  - P1-B4 replaced carried-phone FINDER verdicts with milder PHONE ones and left the rest as it found them.
  - The open question was whether to flag these inherited verdicts off, which would lose the Debrief's tracking assessment until field acceptance, or keep them on.

## Decision

- **The inherited verdicts stay on** as inherited behavior: the finder, beacon, wearable and phone "moved with you" and "possible tail" verdicts in the Debrief.
  - Turning them off would mean missed trackers, which is also severity 1.
  - They are not "new field-dependent logic". They are the behavior users already rely on, and the corpus, goldens and reviews guard them as they are.
- **New or changed tail logic stays behind flags that default off** until its field captures pass in `CaptureReplay` (charter Decision policy). That covers:
  - P1-B9's late-tail fix (`Features.LATE_TAIL_FIX`);
  - Phase 6 Tail v2;
  - linking.
- **Fixes to the inherited verdicts** that change what they say still need a corpus case or golden and a written justification in the PR. If the change is field-dependent (a threshold, a new rule), it goes behind a flag like any new logic.

## Consequences

- The Debrief keeps its tracking assessment from 0.1.0 on.
- Known weaknesses in the inherited logic stay live until their flagged replacements pass. For example, a loud tag that joined late is called "with you the whole sit"; P1-B9 fixes that behind its flag. Each is written down in PLAN under the step that fixes it.
- The Needs-human item on co-travel verdicts is closed.
