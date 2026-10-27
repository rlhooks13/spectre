# Phase gate reviewers

Run all five lenses in parallel as separate fresh subagents. Each one gets:
- the phase's diff range (`git diff <phase-start-tag>..HEAD`)
- the phase's exit criteria from PLAN.md
- `docs/plan/CHARTER.md`
- the repo, to read

None of them gets the implementer's reasoning or another reviewer's output. Each returns findings as BLOCKING, MAJOR or MINOR, with evidence and a fix. The agent writes all five outputs **verbatim** to `docs/plan/gates/phase-N.md`.

The gate passes only with zero BLOCKING findings. If the agent rebuts a BLOCKING finding, use `rebuttal-judge.md`.

## Lens 1: Architecture and migration
- Are the module boundaries and dependency rules respected? Any leaks?
- Is DI or startup ordering broken (the crash-note gate, `StartupGate`)?
- Migration safety:
  - Room migrations are tested from every released schema.
  - The legacy importer is idempotent.
  - Formats only grow.
  - R8/keep rules are covered by a staging smoke test.
- Dead code, duplication, files over budget.
- Does the phase meet its exit criteria literally?

## Lens 2: Security, privacy and egress
- Every URL and socket in the diff maps to an egress category and goes through `NetworkPolicy`.
- The first-run choice and Privacy mode are honored on every path, including PDFs, exports and background work.
- Size caps exist on downloads and imports.
- Signatures are verified (catalog and sensor manifests), with no rollback.
- No secrets in the repo. Keystore use is correct.
- FileProvider scope is narrow.
- `dataExtractionRules` and backup settings are correct.
- Capture and corpus scrubbing:
  - run the leak test
  - no bystander names, SSIDs or MACs in fixtures
- Prompt-injection exposure: any new place where untrusted text reaches the agent.

## Lens 3: Detection correctness
- Run `CaptureReplay` and the match corpus, and report every changed label or verdict with the reason.
- Probe the confusers:
  - Galaxy phone and Buds
  - separated or powered-off iPhone and AirPods
  - a companion iPhone
  - Tattile cameras
  - Quuppa tags
  - random BLE addresses that collide with OUIs
  - a tag already in the bag at the start of a sit
  - a tag that joins late
  - a pass-by
  - a no-tail commute
- Feature flags: field-dependent logic must be off by default, and each flag's acceptance evidence must be present before it turns on.
- Missed detections are severity 1 just like false alarms.

## Lens 4: UX, accessibility and localization
- TalkBack labels and roles on new UI. Works at 200% font scale. Touch targets are at least 48 dp. Contrast in night/red mode.
- Navigation: back stack, deep links, rotation.
- Cancellation and progress for long work.
- Language: no raw keys, keys never renamed, caps respected, English fallback, safety copy accurate.
- The receive-only and transmit wording is truthful.

## Lens 5: Release readiness
- Version and versionCode are correct, and the tag is the next version.
- The release notes list every label and verdict change and every flag state.
- Soak evidence: health checks filtered by package; battery measured unplugged and in Doze; static tags checked; Galaxy devices not shown as finders.
- The revert plan is written down.
- Catalog releases are not marked latest.
- Changelog fragments are present.
- Anything in the phase that users must be told about: permissions, transmitting features, data changes.
