# CI and release migration

## Changes

- Removed `.github/workflows/build.yml`, the automatic publisher.
- Added `ci.yml`, owner/branch-locked `release-beta.yml` and `release-main.yml`, plus shared `release.yml` and Android setup action.
- Added release scripts and regression tests under `.github/scripts/` for stable-based versioning, changelog notes, pinned signing identity, APK inspection, checksums, metadata, collision checks and publication.
- Removed release-to-debug signing fallback in `app/build.gradle.kts`. Debug builds retain Android's default debug signing.
- Added `CHANGELOG.md` and README release/cutover instructions. No application features or ABI splits changed.

## Inspected state

GitHub's default branch was `master`. Remote `master` and development branch `test` both pointed to `517eaf8e9284abad0eed38ffbf6bb4826ab152cc`, with zero commits unique to either branch. The latest published stable release was `v1.0.48`. Local tags `v1.0.46`, `v1.0.47` and `v1.0.48` differed from remote tag objects; fetch rejected overwriting them. No historical refs were changed. CI fetches a fresh checkout of the authoritative remote tags.

Read-only inspection of the existing `v1.0.48` APK found package `swiftslate.anlaki.dev`, version code `1314188`, min SDK 23 and target SDK 36. The SHA-256 certificate fingerprint extracted from its PKCS#7 certificate was `dc4e2fb73678637f443600a76ce484b52f197a5c8448993e5f047da339795511`. Release validation requires this identity using Android SDK `apksigner`.

The repository has all four required signing secret names and optional `GRADLE_CACHE_ENCRYPTION_KEY`. No secret values were inspected. The existing default workflow token setting allows writes, and `master` had no branch protection. Individual workflows now restrict permissions explicitly.

## Verification and deployment

Pending the first migration push. All verification will run through GitHub Actions, per owner instruction. No local build, Gradle task, script test suite, or workflow linter was run. No release workflow will be dispatched as part of migration.

## Owner settings and acceptance

Require CI checks on `main` and `beta`, and review any repository rulesets/tag restrictions so only authorized manual publication can create release tags. Preserve `master` and `test` until explicitly retired. Full installation/signature/update testing requires the owner's separate manual release decision. Missing notes/secrets, wrong branch/actor, build/test errors, certificate mismatch, or collisions block publication. Artifacts retain diagnostics without creating duplicate failure issues.

If publication fails after creating a tag, the workflow leaves it and any draft for diagnosis; it never rewrites history. Check retained assets before recovery. The GitHub API cannot make tag creation and asset publication one atomic operation.
