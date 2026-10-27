# ADR-0002: Versioning and release identity

- **Status:** Accepted, 2026-10-26
- **Context:** Releases are fully autonomous. Obtainium installs from GitHub Releases, and Android can't downgrade a versionCode.

## Decision
- **SemVer.** Versions start at `0.x` and reach `1.0.0` at the end of Phase 8. That keeps the fork from looking like a successor to Fieldwatch 1.1.32 or to Spectre 1.2.14.
- **The git tag is the source of truth.** A `vX.Y.Z` tag sets the version. Untagged builds are `X.Y.Z-dev+<sha>`, where `X.Y.Z` comes from the last tag; this needs `fetch-depth: 0` in CI.
- **versionCode.** `X·1,000,000 + Y·1,000 + Z`. CI rejects a tag where Y or Z is above 999, or that isn't the next version after the last release tag.
- **One binary per version.** A version is built and signed once. It's published as a GitHub pre-release, soaked, and then the **same binary** is promoted with `gh release edit --prerelease=false --latest`. There's no rc suffix in the versionCode.
- **Reverting.** A bad release is replaced by a revert release: the last good tree rebuilt as `Z+1`.
- **Catalog releases** use `catalog-vN` tags and are never marked latest.

## Consequences
- Obtainium users who follow pre-releases get the soaking build early.
- `v*` and `catalog-v*` tags are immutable (tag ruleset), so a stray tag permanently uses up a version number. CI validates tags before it builds.
