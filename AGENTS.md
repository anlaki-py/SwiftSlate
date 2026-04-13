# AGENTS.md — Android Coding & Workflow Rules

- My repo url       : https://github.com/anlaki-py/SwiftSlate

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

> *Never try to build the app, it will be built by github actions*
