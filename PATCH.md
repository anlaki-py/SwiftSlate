# PATCH.md — Custom Feature Log

Last updated: 2026-04-12
Upstream base: https://github.com/Musheer360/SwiftSlate @ `8d8c5d4` (master, v1.0.35)

---

## Intent

This fork extends SwiftSlate with a fully customizable command management system.
Users can edit, delete, and restore built-in commands — not just add custom ones.
The translate trigger defaults to the shorter `?tr:<code>` instead of `?translate:<code>`.
The CI pipeline also supports beta releases from non-master branches.

---

## Features

### Full Built-In Command Editing
- Intent: Allow users to modify the trigger, prompt, and description of any built-in command, not just custom ones.
- Affected files:
  - `app/src/main/java/com/musheer360/swiftslate/manager/CommandConstants.kt` [NEW]
  - `app/src/main/java/com/musheer360/swiftslate/manager/CommandOverrides.kt` [NEW]
  - `app/src/main/java/com/musheer360/swiftslate/manager/CommandManager.kt` [MODIFIED — heavy]
  - `app/src/main/java/com/musheer360/swiftslate/model/Command.kt` [MODIFIED]
  - `app/src/main/java/com/musheer360/swiftslate/ui/CommandsScreen.kt` [MODIFIED — heavy]
  - `app/src/main/java/com/musheer360/swiftslate/service/AssistantService.kt` [MODIFIED]
  - `app/src/main/res/values/strings.xml` [MODIFIED]
  - `app/src/main/res/values-de/strings.xml` [MODIFIED]
  - `app/src/main/res/values-es/strings.xml` [MODIFIED]
  - `app/src/main/res/values-fr/strings.xml` [MODIFIED]
  - `app/src/main/res/values-hi/strings.xml` [MODIFIED]
  - `app/src/main/res/values-pt-rBR/strings.xml` [MODIFIED]
  - `app/src/main/res/values-zh-rCN/strings.xml` [MODIFIED]
  - `app/src/test/java/com/musheer360/swiftslate/manager/CommandManagerTest.kt` [MODIFIED]
- Behavior change:
  - **Command model** (`Command.kt`): Added `builtInKey` (nullable key for tracking override identity), `isOverridden` (flag shown in UI), and `description` (user-facing summary shown in list).
  - **CommandConstants** (new file): Extracted built-in command definitions from CommandManager into a standalone `object`. Holds `BuiltInDef` data class, all default built-in prompts/descriptions, the `UNDELETABLE_KEYS` set (`translate`, `undo`), translate-specific constants (`LANG_PLACEHOLDER`, `DEFAULT_TRANSLATE_TRIGGER_NAME = "tr"`, default prompt/description).
  - **CommandOverrides** (new file): Persistence layer for built-in command overrides using a dedicated `command_overrides` SharedPreferences file. Supports `getOverride()`, `saveOverride()`, `markDeleted()`, `reset()`, `isUndeletable()`, `migratePrefix()`, and translate-specific helpers (`getTranslateTriggerName()`, `getTranslatePromptTemplate()`). Stores overrides as JSON objects with `trigger`, `prompt`, and `description` fields.
  - **CommandManager**: Refactored `buildBuiltInCommands()` to apply overrides, skip deleted commands, and include translate as a visible entry. Added `overrideBuiltInCommand()`, `deleteBuiltInCommand()`, `resetBuiltInCommand()`, `getDeletedBuiltInCommands()`, `getTranslatePrefix()`, `isUndeletable()`, `isBuiltInOverridden()`. Custom command JSON now stores `description`. The `findCommand()` translate matching uses overridable trigger name and prompt template.
  - **CommandsScreen**: Complete UI overhaul — extracted `CommandListItem` and `DeletedCommandItem` composables. Edit form now works for both custom and built-in commands. Shows "Modified" badge on overridden built-ins. Displays a `description` field (optional) in the edit form. Commands list shows descriptions, falling back to truncated prompt snippets. "Reset to Default" button for overridden built-ins. "Deleted" section at the bottom with restore buttons. Translate command now visible in the list with a `:<lang>` hint.
  - **AssistantService**: `updateTriggers()` uses `commandManager.getTranslatePrefix()` instead of hardcoded `"${cachedPrefix}translate:"`. Filters translate display entry from `triggerLastChars` to avoid false-positive matching on literal `<lang>` suffix. Undo detection uses `cmd.builtInKey == "undo"` instead of `cmd.trigger.endsWith("undo")`.
  - **Localization**: Added 9 new string resources across all 7 locale files: `commands_modified`, `commands_edit_title`, `commands_reset_command`, `commands_reset_confirm_title`, `commands_reset_confirm_message`, `commands_translate_hint`, `commands_deleted_section`, `commands_restore_command`, `commands_description_label`.
  - **Tests**: Updated translate trigger assertions from `?translate:<code>` to `?tr:<code>`. Updated default command count from 9 to 10 (translate is now included). Added `command_overrides` prefs cleanup in `setUp()`.
- Dependencies added: None.
- Notes: Translate and Undo are protected from deletion (`UNDELETABLE_KEYS`). The translate command appears as a display-only entry in the commands list (`?tr:<lang>`) and is skipped during `findCommand()` iteration — its matching is handled by separate parametric logic. The `{lang}` placeholder in translate prompts is replaced at match time.

### Shortened Default Translate Trigger
- Intent: Make the translate trigger shorter and more convenient (`?tr:` instead of `?translate:`).
- Affected files:
  - `app/src/main/java/com/musheer360/swiftslate/manager/CommandConstants.kt` [NEW]
  - `app/src/main/java/com/musheer360/swiftslate/manager/CommandManager.kt` [MODIFIED]
  - `app/src/test/java/com/musheer360/swiftslate/manager/CommandManagerTest.kt` [MODIFIED]
- Behavior change: Default translate trigger is `?tr:<code>` instead of `?translate:<code>`. Users can override this to any name via the edit form.
- Dependencies added: None.
- Notes: This is part of the broader command editing feature but called out separately because it changes the default user-facing trigger. Existing users who relied on `?translate:` will need to manually re-add it as an override if they prefer the old trigger.

### CI Beta Release Support
- Intent: Automatically produce beta-tagged pre-releases when pushing to any non-master branch, so feature branches can be tested via GitHub Releases without polluting the stable release train.
- Affected files:
  - `.github/workflows/build.yml` [MODIFIED]
- Behavior change:
  - Trigger expanded from `branches: [master]` to `branches: ['**']` (all branches fire the build).
  - Version step now has three paths: tag push (no new tag), master push (stable `v1.0.X`), and non-master push (beta `v1.0.X-beta.<branch>.<build>`).
  - Branch name is sanitized for filename/tag safety (special chars replaced with `-`).
  - New outputs: `is_beta` and `apk_name`.
  - APK artifact and GitHub Release names use `apk_name` output.
  - Release step sets `prerelease: true` and appends `[BETA]` to the release title for beta builds.
- Dependencies added: None.
- Notes: Beta tag format is `v1.0.X-beta.<branch-safe>.<build-counter>`. The build counter increments per branch to avoid tag collisions on repeated pushes to the same branch.

---

## Configuration Changes

- **`.github/workflows/build.yml`**: Branch trigger widened to `['**']`. Version calculation refactored with beta path. New job outputs `is_beta` and `apk_name` propagated through the pipeline. Release step conditionally marks as pre-release.

---

## Removed Upstream Behavior

- **Hardcoded translate trigger**: The upstream translate trigger `?translate:<code>` is no longer hardcoded anywhere. It is now derived from `CommandConstants.DEFAULT_TRANSLATE_TRIGGER_NAME` (set to `"tr"`) and can be overridden by the user. The old `?translate:` prefix is not available unless the user manually sets it.

---

## Known Conflicts with Upstream

- **`CommandManager.kt`**: Heavily modified. `buildBuiltInCommands()` completely rewritten to support overrides and deletions. `findCommand()` translate logic refactored. Any upstream changes to built-in command definitions, command loading, or translate matching will conflict.
- **`CommandsScreen.kt`**: Heavily modified. The entire commands list rendering was refactored and split into extracted composables (`CommandListItem`, `DeletedCommandItem`). The edit form now handles both custom and built-in commands. Any upstream UI changes to the commands screen will conflict.
- **`Command.kt`**: Three new fields added to the data class. Upstream changes to this model will likely conflict.
- **`AssistantService.kt`**: `updateTriggers()` and undo detection logic changed. Any upstream changes to trigger caching or command matching will need review.
- **`strings.xml` (all locales)**: 9 new entries added in the commands section. Upstream additions in the same area may cause ordering conflicts.
