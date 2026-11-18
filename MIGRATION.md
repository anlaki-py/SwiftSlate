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

The migration was pushed as `de86fd5` to `master`, `test`, `main`, and `beta`, all as fast-forward updates or new branches. GitHub's default branch is now `main`. The historical GitHub workflow **Beta Build**, ID `304445818`, was also explicitly disabled. All new release controls are registered on the default branch.

The initial migration passed full [CI on test](https://github.com/anlaki-py/SwiftSlate/actions/runs/44347217027), including Android lint, `testDebugUnitTest`, nine release regression tests, and `actionlint` 1.7.12. The initial main run also passed unit tests and release automation checks. Two additional tests cover metadata-based and legacy version-code ordering, plus rejection of malformed published version codes. Follow-up verification appears in the [CI run list](https://github.com/anlaki-py/SwiftSlate/actions/workflows/ci.yml).

All checks run through GitHub Actions, per owner instruction. No local build, Gradle task, script test suite, or workflow linter was run. No release workflow was dispatched; the latest release remains `v1.0.48`. The local untracked `instruction.md` was preserved unchanged.

## Owner settings and acceptance

Require CI checks on `main` and `beta`, and review any repository rulesets/tag restrictions so only authorized manual publication can create release tags. Preserve `master` and `test` until explicitly retired. Full installation/signature/update testing requires the owner's separate manual release decision. Missing notes/secrets, wrong branch/actor, build/test errors, certificate mismatch, or collisions block publication. Artifacts retain diagnostics without creating duplicate failure issues.

If publication fails after creating a tag, the workflow leaves it and any draft for diagnosis; it never rewrites history. Check retained assets before recovery. The GitHub API cannot make tag creation and asset publication one atomic operation.

## Cleanup review

Removed the automatic publisher and debug signing fallback. No uncertain code was removed. The two short manual release wrappers remain separate to provide the requested Actions buttons; their implementation and release queue are shared. Application release links use GitHub's stable `releases/latest` URL and need no changes. No unrelated app code or dependencies changed.
