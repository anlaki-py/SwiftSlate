# AGENTS.md — Android Engineering Guidelines

> Repository: https://github.com/anlaki-py/SwiftSlate
>
> These instructions define the preferred architecture, coding style, and modification policy for AI agents contributing to this repository. Follow them unless the user explicitly requests otherwise.

---

# Core Principles

- Prioritize correctness, readability, maintainability, and consistency over cleverness.
- Produce the smallest change that completely solves the requested task.
- Respect the existing architecture and coding style unless the task explicitly requires refactoring.
- Never rewrite unrelated code.
- Do not introduce new dependencies unless they are required and justified.
- Favor composition over inheritance.
- Keep implementations simple and predictable.

---

# Modification Policy

## Always

- Make focused, minimal diffs.
- Reuse existing abstractions whenever practical.
- Preserve backwards compatibility unless instructed otherwise.
- Remove dead code when modifying the surrounding implementation.
- Keep imports clean.
- Keep files organized and cohesive.

## Never

- Perform unrelated refactors.
- Rename files, packages, or public APIs without necessity.
- Introduce duplicate implementations.
- Leave TODOs instead of implementing requested functionality.
- Disable lint checks to silence warnings.

---

# Project Structure

Organize code by feature rather than by technical layer.

Good:

```
feature/
    login/
        data/
        domain/
        ui/

    profile/
        data/
        domain/
        ui/
```

Avoid:

```
ui/
fragments/
activities/
repositories/
viewmodels/
```

---

# File Organization

- Prefer one primary class per file.
- Filename should match the primary class.
- Split files that become difficult to navigate (generally around 200–300 lines, using judgment rather than a strict limit).
- Group closely related small classes only when it improves readability.

---

# Architecture

Maintain clear boundaries.

## UI Layer

Responsible for:

- Rendering state
- User interaction
- Collecting Flow
- Navigation
- Display logic

Must NOT contain:

- Business logic
- Networking
- Database operations

---

## Domain Layer

Responsible for:

- Business rules
- Validation
- Use cases
- Pure application logic

Should remain Android-independent whenever practical.

Each UseCase should expose a single public entry point:

```kotlin
operator fun invoke(...)
```

or

```kotlin
fun execute(...)
```

---

## Data Layer

Responsible for:

- Repository implementations
- Remote APIs
- Local database
- Data mapping
- Caching

Repositories coordinate data sources.

Repositories should NOT contain business rules.

---

# Dependency Direction

Preferred dependency flow:

```
UI
 ↓
Domain
 ↓
Data
```

Framework dependencies should not leak into the Domain layer.

Business logic belongs in Domain, not UI or Repository implementations.

---

# ViewModels

- One ViewModel per screen unless shared UI state is intentionally required.
- Expose immutable StateFlow to the UI.
- Keep mutable state private.
- ViewModels should not hold Activity, Fragment, or View references.
- Never store Context directly.
- If application context is required, inject it explicitly.

Example:

```kotlin
private val _uiState = MutableStateFlow(...)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

---

# State Management

Use:

- StateFlow for UI state
- SharedFlow for one-time events

Examples of one-time events:

- Navigation
- Snackbar
- Toast
- Dialog triggers

Avoid LiveData in new code unless integrating with existing architecture.

---

# Coroutines

- Never block the Main dispatcher.
- Network and disk operations belong on Dispatchers.IO.
- CPU-intensive work belongs on Dispatchers.Default.
- Prefer structured concurrency.
- Avoid GlobalScope.

---

# Jetpack Compose

- Keep composables small and focused.
- Extract reusable UI into separate composables.
- Hoist state whenever practical.
- Avoid business logic inside composables.
- Prefer immutable UI models.
- Keep previews independent from production dependencies.

---

# Resources

Never hardcode:

- strings
- colors
- dimensions

Use:

```
res/values/
```

for shared resources.

Small internal constants may remain as private constants when localization is unnecessary.

---

# Naming

Use descriptive names.

Examples:

```
LoginViewModel
LoginScreen
ProfileRepository
LoadProfileUseCase
```

Boolean names should begin with:

- is
- has
- should
- can

Avoid unnecessary abbreviations.

Good:

```
userRepository
authenticationState
```

Avoid:

```
usrRepo
authMgr
procData
```

---

# Models

Keep models separated by responsibility.

Examples:

```
UserDto
UserEntity
User
UserUiModel
```

Avoid using a single model for every layer.

---

# Extensions

Place reusable extensions in dedicated files.

Example:

```
StringExtensions.kt
FlowExtensions.kt
ContextExtensions.kt
```

---

# Constants

Shared constants belong in dedicated files.

Example:

```
LoginConstants.kt
NetworkConstants.kt
```

Avoid "magic numbers."

---

# Documentation

Document intent rather than implementation.

Public APIs and non-trivial functions should include KDoc explaining:

- purpose
- parameters
- return value
- important business rules

Do not write KDoc for obvious private helper functions.

Prefer self-documenting code over excessive comments.

Comments should explain:

- business rules
- edge cases
- non-obvious decisions

Never comment code that is already obvious.

---

# Error Handling

Handle failures explicitly.

- Prefer Result or sealed result types where appropriate.
- Never silently swallow exceptions.
- Log meaningful diagnostic information when failures occur.
- Surface user-facing errors through UI state.

---

# Testing

When modifying business logic:

- Update existing tests when necessary.
- Add tests for newly introduced behavior when the project already contains tests.
- Do not introduce a testing framework if none exists.

---

# Performance

Prefer:

- immutable objects
- lazy work
- efficient collections
- avoiding unnecessary allocations

Avoid premature optimization.

Optimize only when measurable or clearly beneficial.

---

# Code Quality Checklist

Before finishing, verify:

- Architecture boundaries are respected.
- No duplicated logic was introduced.
- Naming is consistent.
- Imports are clean.
- Public API remains compatible.
- Business logic lives in the Domain layer.
- UI only renders state.
- Repository only manages data.
- Coroutine dispatchers are appropriate.
- StateFlow and SharedFlow usage is correct.
- No hardcoded resources.
- No dead code remains.
- The implementation is easy to read.

---

# Build Policy

Do **not** build the project locally.

Do **not** run Gradle tasks unless explicitly requested.

Continuous Integration (GitHub Actions) is responsible for project builds and verification.

---

# Priority Order

When rules conflict, follow this order:

1. Explicit user instructions
2. Correctness
3. Existing project architecture
4. These guidelines
5. Personal preference

Consistency with the existing codebase is preferred over rigid adherence to generic conventions.