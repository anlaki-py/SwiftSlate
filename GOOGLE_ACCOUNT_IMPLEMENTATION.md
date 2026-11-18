# Google accounts and AI provider modernization

## What changed

The `beta` branch adds browser Google login, multiple owned accounts, encrypted credential storage, automatic renewal, account enable/remove/refresh controls, quota display, account-verification links, model discovery and Gemini transformations. Login uses PKCE, an independent random state, and a five-minute loopback session held by a temporary foreground service. The callback accepts one matching authorization result, ignores unrelated callbacks, and releases its port on cancellation or completion.

Google traffic uses one pinned backend host for onboarding, models and generation. The current compatibility client version replaces the older supplied reference header. No project ID is fabricated and no backend host fallback changes entitlements. Eligibility and quotas remain controlled by Google. Model-specific rate limits are persisted, so another available model can work without forgetting a cooldown on restart. Token refresh is serialized per account; all account storage updates are atomic and outside the UI thread.

Existing OpenAI-compatible providers retain their configuration. Native Gemini API-key providers are supported too. One cancellable OkHttp pool handles all requests, with bounded response bodies and no implicit generation retries or authenticated redirects. Explicitly rejected JSON-output and temperature parameters are negotiated and cached per endpoint/model. Refusal, filtering and truncation preserve the original input. API keys rotate only after explicit authentication/rate-limit errors. Model fetching and protocol validation have been moved out of composables.

## Files touched

- `feature/google/domain`, `data`, and `ui` contain account policy, OAuth/network/storage services, and account UI.
- `api/` contains the shared transport, provider router, OpenAI protocol/client, native Gemini client/protocol, and application services.
- Existing provider persistence, key validation, accessibility command processing, Settings, Keys and Dashboard integrate the feature without changing commands or accessibility triggers.
- Localized resources cover all seven existing app languages. Gradle adds OkHttp and its mock server for tests. Android min SDK 23, compile/target SDK 36, package name and signing identity are preserved.
- Manual release workflows inject `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET`. CI receives neither. These repository secrets were configured from the supplied installed-app reference without printing or committing the values.

## Checks ran

All compilation, lint, unit tests and workflow validation run on GitHub Actions only. No local Gradle task, APK build, test suite or linter was run. Initial CI exposed a library compile-SDK mismatch and two Android 6 compatibility calls; these were corrected using OkHttp 5.3.2 and compatible APIs. CI has passed the feature regression tests. Latest exact-commit results are available in the [beta CI runs](https://github.com/anlaki-py/SwiftSlate/actions/workflows/ci.yml?query=branch%3Abeta). The manual beta workflow independently reruns lint/tests before building and verifying the signed APK.

Tests cover native and compatible response/model parsing, JSON/temperature negotiation, request cancellation, response bounds, authenticated redirects, callback state/path/port handling, concurrent token refresh, revoked credentials, rate-limit persistence, per-model cooldowns, failed storage writes, legacy provider migration, concurrent provider writes and protocol-neutral key validation. Test doubles use synthetic credentials and never authenticate a real Google account.

## Risks and device checks

A real Google consent flow requires interactive sign-in on an Android device. CI cannot establish the owner's Google account eligibility or prove access to live models. The account integration uses the supplied installed-app compatibility client, not an official SwiftSlate Android OAuth registration; Google can change this backend independently. Installed-app client configuration is recoverable from the APK, while account tokens stay encrypted and out of backups/logs. Removing a local account does not revoke a shared grant for other applications.

Install the signed beta, open Settings → Google accounts, complete browser consent, and try a transformation. Add a second account, disable one, refresh quota, and remove an account to check the intended device flow. Test browser cancellation and app/process recreation on the target Android build. Stable source is unchanged on `main`; only `beta` receives this feature and release.

## Cleanup

Removed the obsolete blocking model fetcher, transient retry helper, duplicate HTTP response readers, duplicated provider UI state, and an unused coroutine-test dependency. Account policy and key validation have one shared path. The small native and compatibility protocol adapters remain separate because their authentication and response formats differ. The supplied `antigravity-files/` reference and `instruction.md` remain unchanged and untracked.
