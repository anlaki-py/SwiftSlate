<div align="center">

<img src="playstore-icon.png" width="140" alt="SwiftSlate Icon" />

# SwiftSlate

### System‑wide AI text assistant for Android — refined, stable, and fully configurable

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#getting-started)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![License: MIT](https://img.shields.io/badge/MIT-blue?style=for-the-badge)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/anlaki-py/SwiftSlate?style=flat-square&label=Latest&color=brightgreen)](https://github.com/anlaki-py/SwiftSlate/releases/latest)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/unluky)

**A more polished and dependable fork of [Musheer Alam](https://github.com/Musheer360)'s original project — with multi‑provider support, built‑in command overrides, and background reliability enhancements.**

</div>

---

## Table of Contents

- [Why Choose This Fork?](#why-choose-this-fork)
- [Features](#features)
- [Getting Started](#getting-started)
- [Commands](#commands)
  - [Built‑in Commands](#built-in-commands)
  - [Custom Commands & Text Replacers](#custom-commands--text-replacers)
  - [Editing and Overriding Built‑ins](#editing-and-overriding-built-ins)
- [Providers and API Keys](#providers-and-api-keys)
- [Backup & Restore](#backup--restore)
- [Privacy & Security](#privacy--security)
- [Requirements](#requirements)
- [Building from Source](#building-from-source)
- [Known Limitations](#known-limitations)
- [License & Credits](#license--credits)

---

## Why Choose This Fork?

While the original SwiftSlate introduced a powerful concept, this fork was created to address real‑world usability gaps and deliver a more robust, flexible, and user‑friendly experience. If you have tried the original and felt something was missing, this version is for you.

| Aspect | Original SwiftSlate | This Fork (anlaki‑py) |
|--------|---------------------|------------------------|
| **Provider Setup** | Fixed Gemini / Groq / Custom, one at a time | **Multiple named providers** — add, edit, and switch between any OpenAI‑compatible service |
| **API Keys** | Global pool of keys mixed together | **Keys are scoped per provider** — clearer organisation, no cross‑provider contamination |
| **Model Selection** | Hardcoded dropdown lists | **Dynamic model fetching** from the provider’s `/models` endpoint, plus manual entry fallback |
| **Command Customisation** | Built‑in commands are read‑only | **Override or delete any built‑in command**, reset to defaults, add descriptions |
| **Background Reliability** | Service may be killed by system | **Foreground keep‑alive service** with persistent notification, auto‑restart after boot/update |
| **Battery Optimisation** | No guidance | **One‑tap “Unrestrict” button** prevents Android from killing the assistant |
| **UI & Interaction** | Basic list | **Search bar, expand/collapse all, swipe actions, long‑press menus** |
| **Error Handling** | Basic | **Transient error retries, improved error messages, rate‑limit cooldown tracking** |
| **Additional Settings** | Temp and prefix | **Timeout slider, temperature, trigger prefix customisation** |

In short: this fork gives you **full control over your providers, keys, and commands**, and it **stays alive in the background** so it works when you need it.

---

## Features

### Universal Text Assistance
Works in almost any app — messaging, email, social media, notes, browsers. Type a trigger anywhere and your text is replaced inline without copy‑pasting.

### Flexible AI Providers
Use **Google Gemini**, **Groq**, or **any OpenAI‑compatible endpoint** (cloud services, local models like Ollama or LM Studio). Add multiple providers and switch between them with a single tap.

### Dynamic Model Loading
No more guessing model names. The app fetches available models directly from your provider and lets you pick from a list — or enter a model ID manually.

### Command System Reimagined
- **9 built‑in AI commands** (fix, improve, shorten, expand, formal, casual, emoji, reply, undo)
- **Parametric translation** with `?tr:fr` (uses your chosen trigger prefix)
- **Text Replacer commands** run offline instantly for signatures, snippets, canned responses
- **Override built‑ins** with your own triggers, prompts, and descriptions
- **Delete built‑in commands** you don’t use (except undo and translate)
- **Reset any modified command to factory defaults**

### Smart API Key Management
- Keys are encrypted with AES‑256‑GCM using the Android Keystore
- Keys belong to specific providers — keep work and personal keys separate
- Round‑robin rotation across multiple keys per provider
- Automatic handling of rate limits and invalid keys

### Stay‑Alive Guarantee
A lightweight foreground service keeps SwiftSlate active even when the app is closed. The system sees it as an essential process, and a silent notification confirms it’s running.

### Battery Optimisation Helper
A dashboard card shows if Android is restricting SwiftSlate. One tap opens the system dialog to exempt the app — critical for uninterrupted service.

### Backup and Portability
Export your custom commands to a JSON file and import them on a new device. All settings travel with you.

### Polished Interface
- Material 3 design with AMOLED black and light themes
- Search commands by trigger name
- Expand/collapse command details
- Swipe to edit or delete custom commands
- Long‑press for action menu

---

## Getting Started

### 1. Download and Install
Get the latest APK from the [Releases](https://github.com/anlaki-py/SwiftSlate/releases/latest) page. Allow installation from unknown sources if prompted.

### 2. Add a Provider
Open the **Settings** tab and tap “Add Provider”. Give it a name (e.g., “My Gemini”) and an endpoint URL (e.g., `https://generativelanguage.googleapis.com/v1beta/openai/` for Gemini’s OpenAI compatibility layer). Save.

### 3. Add an API Key
Go to the **Keys** tab. The active provider is shown at the top. Paste your API key and tap “Add Key”. It will be validated before saving.

### 4. Enable Accessibility Service
On the **Dashboard**, tap “Enable” and find “SwiftSlate Assistant” in your device’s Accessibility settings. Turn it on.

### 5. (Recommended) Disable Battery Optimisation
On the Dashboard, if you see “Restricted”, tap “Unrestrict” and confirm in the system dialog. This prevents Android from stopping the assistant.

### 6. Start Using It
Open any app that accepts text, type your message, add a trigger like `?fix` at the end, and watch it transform.

---

## Commands

### Built‑in Commands

| Trigger | Action |
|:--------|:-------|
| `?fix` | Correct grammar, spelling, and punctuation |
| `?improve` | Enhance clarity and readability |
| `?shorten` | Condense while preserving meaning |
| `?expand` | Add detail and context |
| `?formal` | Rewrite in a professional tone |
| `?casual` | Rewrite in a friendly, relaxed tone |
| `?emoji` | Insert relevant emojis |
| `?reply` | Generate a contextual reply to the text |
| `?undo` | Revert the last replacement |
| `?tr:XX` | Translate to language code `XX` (e.g., `?tr:es` for Spanish) |

> The translation trigger uses the same prefix as other commands. You can customise the trigger name (e.g., change `?tr` to `?t`) by editing the built‑in translate command.

### Custom Commands & Text Replacers

Create your own triggers in the **Commands** tab:

- **AI Command** – sends your custom prompt to the AI provider. Example: `?eli5` with prompt “Explain this like I’m five.”
- **Text Replacer** – instantly replaces the trigger with a fixed string, no network required. Example: `?sig` → “— Jane Doe”.

### Editing and Overriding Built‑ins

Tap any built‑in command to expand it, then long‑press or swipe to edit. You can:

- Change the trigger word (still starts with the global prefix)
- Modify the AI prompt
- Add a description (visible when expanded)
- Delete a built‑in command (except undo and translate)
- Reset an overridden command to its original state

This gives you complete freedom to shape the assistant to your workflow.

---

## Providers and API Keys

### Adding Providers
Navigate to **Settings → Provider** and tap “Add Provider”. Enter a friendly name and the base URL of any OpenAI‑compatible chat completions endpoint.

Supported examples:
- **Gemini (OpenAI compatibility):** `https://generativelanguage.googleapis.com/v1beta/openai/`
- **Groq:** `https://api.groq.com/openai/v1`
- **Local Ollama:** `http://localhost:11434/v1`
- **LM Studio:** `http://localhost:1234/v1`

You can add as many providers as you need and switch between them from the dropdown.

### Managing Keys
Keys are stored per provider. On the **Keys** screen, the current provider’s name is shown. Add one or more keys; SwiftSlate will rotate through them automatically. If a key is rate‑limited or invalid, it is temporarily or permanently skipped.

### Selecting a Model
Tap the model selector under the provider name. SwiftSlate will fetch the list of available models using your first valid key. Choose from the list or type a model ID manually.

---

## Backup & Restore

Use the **Settings → Backup & Restore** section to:

- **Export** all custom commands (and overrides) to a JSON file.
- **Import** a previously exported file — replacing existing custom commands after confirmation.

API keys and provider configurations are **not** included in backups for security reasons.

---

## Privacy & Security

- **No telemetry, no analytics, no tracking.** The app does not communicate with any servers except the AI provider you configure.
- Text is only processed when you explicitly type a trigger; password fields are always ignored.
- API keys are encrypted using the Android Keystore (AES‑256‑GCM) and never leave your device in plain text.
- The app requires only the Accessibility Service permission — nothing else.

---

## Requirements

- Android 6.0 (API 23) or newer
- An active internet connection for AI commands
- An API key from a supported provider (free tiers available)

---

## Building from Source

If you prefer to build the APK yourself:

```bash
git clone https://github.com/anlaki-py/SwiftSlate.git
cd SwiftSlate
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Known Limitations

- Some applications with custom text input implementations (e.g., WeChat, Chrome address bar) may not fully support direct text replacement. The fallback clipboard method works in most standard text fields.
- On certain devices (OnePlus, Xiaomi), third‑party accessibility services may be hidden or require extra steps to enable. Search for “SwiftSlate” in the accessibility settings if it doesn’t appear immediately.
- The keep‑alive notification is required for background reliability. It can be hidden in system notification settings but doing so may affect stability.

---

## License & Credits

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file.

**Original SwiftSlate** by [Musheer Alam](https://github.com/Musheer360). This fork builds upon that foundation with numerous enhancements in stability, configurability, and user experience.

Maintained by [anlaki](https://anlaki.dev).  
[Report an issue](https://github.com/anlaki-py/SwiftSlate/issues) · [Download latest release](https://github.com/anlaki-py/SwiftSlate/releases/latest)
## CI and manual releases

Pushes and pull requests to `main` and `beta` run **CI** with separate **Lint**, **Unit Tests**, and **Release automation checks** jobs. There are no path filters, so documentation PRs receive required checks too. `master` and `test` retain CI-only coverage during migration. Tags never publish automatically. **CI** also supports **Run workflow**. CI does not read signing secrets or publish APKs.

`main` is the stable branch. `beta` is the development branch, migrated from SwiftSlate's former `test` branch. `master` and `test` are retained, not deleted or reset. The initial branch comparison found both at `517eaf8`; no unmerged development commits existed at cutover.

To publish, the repository owner must:

1. Update [CHANGELOG.md](CHANGELOG.md). Beta requires real notes under `## [Unreleased]`; stable requires the exact computed version section, for example `## [1.0.49]`. Update the date to the intended release date. Empty sections fail. Stable tags alone determine the next patch; beta builds increment `-beta.1`, `-beta.2`, and so on.
2. Open **Actions**, choose **Release Beta** or **Release Stable**, and select **Run workflow**. Select `beta` for beta or `main` for stable. Other branches and non-owner actors fail before checkout/build. Workflows must exist on the default branch for the dispatch controls to appear.
3. Wait for fresh lint/tests, signed `assembleRelease`, APK verification, and publication. Both channels share a release queue. Download the universal APK, `checksums-sha256.txt`, and `release-metadata.json` from [GitHub Releases](https://github.com/anlaki-py/SwiftSlate/releases). Beta releases are marked prerelease.

Required repository Actions secrets are `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, using SwiftSlate's existing keystore. `GRADLE_CACHE_ENCRYPTION_KEY` is optional and only used by manual releases. These five secret names were present during migration; their values were not read. The release certificate is pinned to the published `v1.0.48` APK in [.github/scripts/release-config.json](.github/scripts/release-config.json). Replacing the keystore with a different certificate blocks publication.

Only the publication job receives `contents: write`; repository/organization policy and branch/tag rules must permit its `GITHUB_TOKEN` to create new tags and releases. CI and release build jobs use read permission. If publication fails after tag creation, inspect the retained tag, any draft release, and the `release-assets` artifact before retrying. The workflow never overwrites an existing tag/release, and never deletes historical ones. Reports and validated artifacts are retained for 14 days. Failure reports replace automatic bug-issue creation, avoiding duplicate notifications.

Beta and stable both use package `swiftslate.anlaki.dev` and the same release certificate. A later stable release can update an installed beta because its version code exceeds prior published codes. Version codes use seconds since 2024-01-01 UTC, increased above existing release metadata when necessary, with a conservative bound for legacy minute-based releases. Android rejects downgrades and certificate changes. Previously distributed `.debug` beta APKs are separate applications; export a backup and install the signed release package to migrate, then restore supported settings. Local debug builds use the normal debug certificate and `.debug` package.

### Repository cutover

For a fresh migration, first commit these workflow changes on `master` to disable its old publisher. Then create the new branches from the appropriate history, preserving old branches:

```bash
git fetch origin --no-tags
git rev-list --left-right --count origin/master...origin/test
# Run these creation commands only if the new remote branches do not exist.
git push origin origin/master:refs/heads/main
git push origin origin/test:refs/heads/beta
gh repo edit anlaki-py/SwiftSlate --default-branch main
```

If `test` differs, merge the migration changes into its development history before creating `beta`. Also merge the publisher removal into retained `test`; otherwise pushes to that branch could still run its old publisher. Never force-push or delete historical branches/tags. On this migration, the histories matched, allowing one migration commit to advance all four branches.

In **Settings → Rules → Rulesets** or **Settings → Branches**, protect `main` and `beta` and require **Lint**, **Unit Tests**, and **Release automation checks**. Review merge permissions and tag rules without blocking the manual publisher's token. Make `main` the default branch and verify the Actions controls are visible. Old `master`/`test` rules may remain until the owner explicitly retires those branches.

Verification runs on GitHub Actions only. The automation checks exercise version selection against fetched tags, legacy beta names, collisions, version code bounds, changelog parsing, APK identity checks, and `actionlint`. No local Gradle build is needed. After cutover, test a push and a PR for CI behavior. Live release dispatch is a separate owner decision; migration does not publish a release.
