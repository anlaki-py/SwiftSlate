# AGENTS.md — Android Coding & Workflow Rules

- My repo url       : https://github.com/anlaki-py/SwiftSlate
- Original repo url : https://github.com/Musheer360/SwiftSlate

---

## Structure

- One class per file. Filename must match class name.
- Package by feature, not by layer (`feature/login/` not `ui/fragments/`).
- Never create god files. If a file exceeds ~200 lines, split it.
- Separate concerns strictly: `data/`, `domain/`, `ui/` layers must not cross-import.

---

## Modularity

- One ViewModel per screen. No shared ViewModels unless explicitly for shared UI state.
- Repositories own data access only — no business logic.
- UseCases own business logic only — one public `invoke()` or `execute()` function each.
- Extract reusable UI into its own `@Composable` function or `View` subclass.
- Constants → `FooConstants.kt`. Extension functions → `FooExtensions.kt`. Types/models → `FooModels.kt`.

---

## Android-Specific Practices

- Never hold Context in a ViewModel. Use `ApplicationContext` via Hilt if needed.
- Observe LiveData/Flow only in the UI layer (Fragment/Activity/Composable).
- All network and disk I/O must run on a non-main dispatcher (`Dispatchers.IO`).
- Use `StateFlow` for UI state, `SharedFlow` for one-shot events.
- Never hardcode strings, dimensions, or colors — use `res/values/`.

---

## Comments

- Every function needs a KDoc block: purpose, `@param`, `@return`.
- Explain *why*, not *what*. Comment business rules, edge cases, and non-obvious decisions.
- Comment every 3–5 lines in dense logic blocks.
- Do not comment self-evident code. Do not use comments to excuse bad code — refactor instead.

---

## Naming

- ViewModels: `ScreenNameViewModel`. Fragments: `ScreenNameFragment`. Composables: `ScreenNameScreen`.
- Boolean variables/functions: prefix with `is`, `has`, or `should`.
- Avoid abbreviations unless industry-standard (`id`, `url`, `vm` are fine; `mgr`, `proc` are not).

- *Never try to build the app, it will be built by github actions*
---

## PATCH.md

### Purpose

`PATCH.md` is a living document that records every intentional customization made to the app
relative to its upstream source. It exists so that when the upstream repository releases a new
version, the agent can perform a structured migration that preserves the user's features without
manual diff archaeology.

---

### When to Update

The user triggers an update by saying **"update patch.md"** at the end of a work session or after
a meaningful set of changes. The agent must not update `PATCH.md` mid-task or speculatively — only
on explicit instruction.

---

### How the Agent Writes PATCH.md

When the user says "update patch.md", the agent must:

1. Review the full diff between the current codebase and the known upstream baseline.
2. Group every change into one of the sections defined below.
3. For each change, write enough detail that a future agent — with no prior context — could
   understand the intent, locate the affected files, and re-apply the change to a fresh checkout.
4. Preserve all previously recorded entries. Append or revise — never silently delete.

---

### PATCH.md Format

```markdown
# PATCH.md — Custom Feature Log

Last updated: YYYY-MM-DD
Upstream base: <repo URL> @ <commit hash or tag>

---

## Intent

<A plain-language description of what this fork of the app is trying to be.
What problem does it solve? How does it differ from the upstream app in terms of
purpose, audience, or behavior? This section is the north star for migration decisions.>

---

## Features

### [Feature Name]
- Intent: <Why this was added and what user need it addresses.>
- Affected files: <List every modified, added, or deleted file with its path.>
- Behavior change: <What the app does differently because of this feature.>
- Dependencies added: <Any new libraries, permissions, or resources introduced.>
- Notes: <Edge cases, known limitations, decisions made and why.>

<!-- Repeat block per feature -->

---

## Configuration Changes

<Document any changes to build.gradle, AndroidManifest.xml, proguard rules,
gradle.properties, or environment/flavor configuration. Include the reason for each.>

---

## Removed Upstream Behavior

<List any upstream features, screens, or logic that were intentionally deleted or
disabled. Explain why so the migration agent does not attempt to restore them.>

---

## Known Conflicts with Upstream

<List any areas of the codebase that are likely to conflict with upstream changes.
Flag files that have been heavily modified and will need manual review on every upgrade.>
```

---

### Migration Protocol (Agent Behavior on Upgrade)

When the user wants to migrate to a new upstream version, the agent must follow these steps in
order:

**Step 1 — Baseline Checkout**
Clone the new upstream version at the target tag or commit. Do not modify it yet.

**Step 2 — Conflict Assessment**
Cross-reference every entry in `PATCH.md` against the new upstream source. For each recorded
change, determine:
- **Clean apply** — The upstream file is unchanged or changed in unrelated areas. The patch can
  be re-applied automatically or with trivial edits.
- **Conflicted** — The upstream file has changed in the same area as the custom modification.
  Manual resolution is required.
- **Obsolete** — The upstream has introduced equivalent behavior natively. The custom patch may
  no longer be needed.

**Step 3 — Report Before Touching Anything**
Present the user with a full migration report before making any changes:

```
MIGRATION REPORT — Upstream <old tag> → <new tag>

CLEAN APPLIES (can be re-applied automatically):
  - [Feature Name] — <file path>

CONFLICTS (require your input):
  - [Feature Name] — <file path>
    Upstream changed: <summary of upstream change>
    Your change:      <summary of custom change>
    Recommended resolution: <agent's suggested approach>

OBSOLETE PATCHES (upstream now covers this natively):
  - [Feature Name] — <explanation>
    Recommended action: Remove custom patch and adopt upstream implementation.

NO ACTION NEEDED:
  - [Feature Name] — upstream untouched, patch applies as-is.
```

**Step 4 — Guided Resolution**
Work through each conflict one at a time, in the order presented. Do not proceed to the next
conflict until the user has confirmed the resolution of the current one. Apply clean patches
only after all conflicts are resolved and confirmed.

**Step 5 — PATCH.md Reconciliation**
After migration is complete, update `PATCH.md` with the new upstream base commit, revise any
entries that changed during resolution, and remove entries marked obsolete.

---

### Rules for the Agent

- Never modify `PATCH.md` without an explicit "update patch.md" instruction.
- Never skip the migration report. The user must see the full conflict surface before any file
  is touched on the new version.
- Never silently resolve a conflict. Every ambiguous case requires a decision from the user.
- If `PATCH.md` does not yet exist in the project, create it with the current upstream commit
  as the baseline on the first "update patch.md" call.
