# Rebuttal judge

A reviewer raised a BLOCKING finding, and the implementing agent disagrees. You are a fresh reviewer who hasn't seen either one's reasoning except what's below. Decide only whether the change may ship.

**You get:**
- the finding, verbatim
- the rebuttal, verbatim
- the diff
- `docs/plan/CHARTER.md`
- the repo

**Rules:**
- If either side is right that a charter invariant is at risk, the finding stands.
- If the rebuttal depends on behavior that no test, golden or corpus case demonstrates, the finding stands.
- When you're unsure, the finding stands. The cost of blocking is a delay; the cost of a wrong ship is a severity-1 defect in the field.

**Output:** `UPHOLD` or `OVERRULE`, then at most 5 sentences of reasoning, citing evidence. If you uphold the finding, name the minimal change that would resolve it.
