# AGENTS.md — SwiftSlate engineering and release rules

Repository: https://github.com/anlaki-py/SwiftSlate

These rules apply to this repository. Explicit user instructions take priority. Preserve correctness, existing behavior and architecture, then make the smallest complete change.

## Session startup and communication

- Read this file and inspect the working tree before editing. Preserve unrelated and untracked user files.
- Use the available `unslop` and `i-have-adhd` skills for plain, concise communication. Use `maintainable-engineering` for substantial code changes and `cleanup` before finishing them.
- Report concrete results and remaining limits. Never claim a build, test, release or device flow passed without evidence.
- Finish authorized implementation and verification work. Ask only when essential information or authorization is missing.

## Verification policy: GitHub Actions only

**Never build locally. Never run local Gradle tasks, including lint, tests, packaging or wrapper version checks.** The owner requires verification through CI even when local tools are available. Do not install a local build toolchain to work around this rule.

Read-only source review, repository inspection and reading CI logs/reports are allowed. Push authorized changes, inspect the GitHub Actions results and correct actual failures. Do not hide deterministic failures with retries, lint suppressions, baselines or skipped tests.

## Code style and scope

- Make focused diffs. Preserve public APIs, serialized data, app identity and supported devices unless the task requires a change.
- Reuse existing abstractions and dependencies. Add a dependency only when needed and compatible with the current Android toolchain.
- Keep one main responsibility per class and one primary class per file. Aim for roughly 200–350 lines; split mixed responsibilities rather than enforcing an arbitrary line limit.
- Use descriptive names and Boolean prefixes such as `is`, `has`, `can` and `should`. Avoid vague managers/helpers and stacked Boolean arguments in new APIs.
- Remove imports, duplicate logic and dead code orphaned by the current change. Do not expand cleanup into unrelated refactoring.
- Document public APIs and non-obvious rules with KDoc. Comments explain intent, limitations and workarounds, not obvious code.
- Put user-facing strings, shared dimensions and colors in Android resources. Keep localization complete across the existing app languages.

## Architecture and repository map

New functionality belongs under `feature/<feature>/data`, `domain` and `ui`. Existing shared packages remain in place unless the task needs a scoped refactor.

- `app/src/main/java/com/musheer360/swiftslate/feature/google/` owns Google account login, credential storage, selection policy and account UI.
- `api/` owns the shared HTTP transport and provider protocol adapters. `data/` contains existing provider, key, command and backup persistence.
- `service/` owns accessibility processing and foreground-service lifecycle. `ui/` contains existing Compose screens and shared UI state.
- `app/src/test/` contains JUnit/Robolectric tests. `.github/scripts/` contains release planning, validation, publication and non-publishing regression tests.

UI renders state, collects flows, handles interactions and navigates. Keep business rules, networking and storage out of composables. ViewModels expose immutable `StateFlow`, keep mutable state private and use `SharedFlow` for one-time events. Do not retain an Activity, Fragment or View. Use explicitly supplied application dependencies when Android context is required.

Keep business rules in domain code, independent of Android where practical. Use cases expose one entry point, normally `operator fun invoke`. Data code coordinates APIs, persistence and mapping. Use immutable models suited to each responsibility and explicit dependencies rather than hidden globals.

## Networking, state and security

- Use the application-scoped `AiServices` transport and request router. Preserve old providers as `OPENAI_COMPATIBLE` when their saved protocol is absent.
- Keep network, disk and keystore work off Main. Use structured concurrency, cancellation and bounded timeouts/responses; never `GlobalScope` or blocking network calls in UI code.
- Coroutine cancellation must cancel the underlying request. Do not consume `CancellationException` as an ordinary failure.
- Do not replay a possibly accepted generation after a network or partial-stream error. Negotiate unsupported parameters only after explicit rejection. Refused, truncated or incomplete responses must preserve the input text.
- Synchronize compound state changes. Serialize token renewal per account, preserve concurrent account updates and persist model cooldowns. A rate limit is not a revoked credential.
- Validate URLs and external responses. Never forward OAuth tokens to user-defined endpoints, follow authenticated redirects, fabricate a Google project, or switch backend hosts to work around entitlement errors.
- Keep Google tokens encrypted with Android Keystore in no-backup storage. Never add tokens to UI models, provider preferences, backups, logs, screenshots or CI artifacts. A failed storage read/write must not erase existing credentials or fall back to plaintext.
- Inject installed-app OAuth client configuration through build environment values. Never commit client configuration, signing passwords, keystores, access tokens or refresh tokens. Installed-app client configuration is recoverable from an APK; it is not a confidential user credential.
- Surface typed, useful errors through UI state and log safe diagnostic context. Never log raw authenticated requests or response bodies containing credentials.

## Branches and CI

- `main` is stable. `beta` is development/prerelease, migrated from the former `test` history.
- `master` and `test` remain historical branches with CI-only coverage. Do not delete, reset or force-push them, or any historical tag/release, without explicit owner authorization.
- `.github/workflows/ci.yml` runs on pushes and pull requests to `main`, `beta`, `master` and `test`, and supports manual checks on those branches.
- CI uses Temurin JDK 21, the committed Gradle wrapper and the shared Gradle setup action. It has read-only contents permission and no signing/OAuth secrets.
- Preserve the **Lint**, **Unit Tests**, and **Release automation checks** jobs. They run `lint`, `testDebugUnitTest`, release-script tests and `actionlint` on GitHub runners. Reports are retained even when a job fails.
- Keep required status checks reliable for documentation-only PRs; do not introduce path filters that leave them pending. Cancel stale CI runs by branch.
- A push, PR, tag, successful CI run, `workflow_run` or release event must never publish a GitHub Release or create release tags.

## Manual releases

Release publication requires explicit owner authorization in the current task or an owner dispatch through Actions. A request to implement or push code alone does not authorize publishing. Never trigger a live release solely to demonstrate a migration.

- **Release Beta**, `.github/workflows/release-beta.yml`, runs only through manual dispatch selecting `beta`. Example: `gh workflow run release-beta.yml --ref beta -R anlaki-py/SwiftSlate`.
- **Release Stable**, `.github/workflows/release-main.yml`, runs only through manual dispatch selecting `main`. Example: `gh workflow run release-main.yml --ref main -R anlaki-py/SwiftSlate`.
- Both wrappers reject the wrong branch and non-owner dispatch/rerun actors before checkout or build. Their shared `release.yml` implementation must retain the same checks.
- Keep the common release concurrency group with cancellation disabled. Both channels must independently rerun lint and unit tests before signing/publication, regardless of previous CI results.
- CI/build jobs use `contents: read`; only publication receives `contents: write`. Do not expose a write token or signing secrets to PR code.

Stable tags determine the next patch under `baseVersion`; beta tags append `-beta.N` to that upcoming stable version. Legacy `-beta.test.*` tags must not affect stable patch selection. Android version codes must increase across beta and stable installs, including legacy minute-based releases and closely timed runs.

Build `assembleRelease` for both channels. SwiftSlate publishes one universal APK, with package `swiftslate.anlaki.dev` and its existing release certificate. Do not publish `.debug` APKs, add ABI splits without need, rotate the certificate, or restore the debug signing fallback. Read current SDK/version values from the Gradle configuration; the integration retains min SDK 23 and target SDK 36.

Required signing secrets are `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Google-enabled release builds also need `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET`. `GRADLE_CACHE_ENCRYPTION_KEY` is optional. Never print values or put them in report artifacts. Decode the keystore only in the manual release build job and remove it with an `always()` cleanup step.

Validate the exact APK count, nonempty/plausible size, package/version/SDK values and `apksigner` signature against `.github/scripts/release-config.json`. Create/push a new tag only after verification, signing and asset validation succeed. Check remote tags/releases again before publication; never overwrite a collision. If publication fails after tag creation, retain the tag/draft/artifacts for inspection rather than rewrite history.

## Changelog, assets and completion

- Beta notes come from nonempty `## [Unreleased]` in `CHANGELOG.md`. Stable notes come from the exact nonempty version section, with its intended release date. Missing or empty notes must block publication before a tag is created.
- Write short, concrete release notes about actual changes. Do not invent historical notes or ship placeholders. Use `Fixed` only for bugs in shipped behavior; fold corrections to unreleased work into `Added` or `Changed`.
- Preserve the extracted Markdown exactly. Do not replace canonical notes with generated commit summaries. The current metadata schema stores them in `release_notes`, with a commit-specific changelog URL.
- Publish the versioned universal APK, `checksums-sha256.txt` and `release-metadata.json`. Metadata must describe SwiftSlate, the actual APK, channel, version, source commit, certificate and release/changelog URLs. Beta is a prerelease; stable is not.
- Before finishing, review the session diff with `cleanup`, inspect exact-commit CI results and report any device-only or external-service checks still needed. Keep implementation/release documentation current. Never claim that synthetic tests proved a real Google account can sign in or access a model.

## Priority

Explicit user instructions, correctness, existing architecture, these repository rules, then personal preference. The no-local-build policy and manual-only publishing model are deliberate owner requirements.
