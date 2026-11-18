# Changelog

Release notes are maintained here. Beta releases read `Unreleased`; stable releases read their exact version section. Historical release notes have not been reconstructed.

## [Unreleased]

### Added

- Sign in with one or more Google accounts to use eligible Gemini models without entering an API key.
- Manage enabled accounts, refresh credentials and quota information, and review Google account verification requests.
- Native Gemini API-key providers with paginated model discovery.

### Fixed

- Cancel network sockets when a command is cancelled or times out, preserving the original text on incomplete responses.
- Preserve provider endpoints and model selection when switching providers.

### Changed

- Replaced blocking HTTP clients with one shared OkHttp connection pool and bounded responses.
- Negotiate structured output and reasoning-model temperature support only after explicit parameter errors, with no automatic replay of uncertain generation requests.
- Keep Google credentials encrypted on the device and excluded from backups; browser login uses PKCE and a temporary foreground callback service.

- Replaced automatic APK publishing with owner-dispatched beta and stable release workflows.
- Added release APK identity checks, checksums, and downloadable release metadata.
- Removed the debug signing fallback from release builds.

## [1.0.49] - 2026-11-18

### Changed

- Replaced automatic APK publishing with owner-dispatched beta and stable release workflows.
- Added release APK identity checks, checksums, and downloadable release metadata.
- Removed the debug signing fallback from release builds.
