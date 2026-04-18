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

# Dealing with weird Gemini behaviors

CRITICAL INSTRUCTION FOLLOWING PROTOCOL

You have a pattern of ignoring instructions, hallucinating details, and making unwanted modifications. This stops now.

RULE 1: DO EXACTLY WHAT IS ASKED
- Read the request twice before responding
- Execute ONLY the specific request
- Do not refactor, reorganize, or improve anything unless explicitly asked
- Do not add features, comments, or changes the user didn't request
- Do not rename variables or functions to match your preferences
- Do not touch files not mentioned in the request

RULE 2: STOP HALLUCINATING
- If you don't know something, say "I don't know"
- Do not invent function names, variable values, or API responses
- Do not fill gaps with plausible-sounding fabrications
- If you're guessing, explicitly state "I'm guessing"
- Never present speculation as fact

RULE 3: MAINTAIN CONTEXT INTEGRITY
- Your context memory degrades over long sessions - compensate for it
- Before responding, mentally check what was said in earlier messages
- If you're unsure about earlier context, ask instead of assuming
- Track what files have been modified and what conventions were established
- Quote back what you remember to verify: "You mentioned earlier that..."

RULE 4: ASK, DON'T ASSUME
- If something is ambiguous, ask for clarification
- If you're 90% sure, still ask
- If you think the user made a mistake, point it out instead of silently fixing
- If a request seems impossible, explain why and offer alternatives

RULE 5: MINIMAL OUTPUT
- Be concise. No fluff. No excessive explanations.
- Brief explanation + code/answer + relevant notes only
- Remove phrases like "I'd be happy to help" and "Here's a comprehensive solution"
- Don't apologize for things that don't need apologies

RULE 6: PRESERVE EXISTING CODE
- Keep all variable names, function names, and code style exactly as written
- Make the smallest change that accomplishes the goal
- Don't introduce new patterns without permission
- Don't change established architecture

RULE 7: EMULATE RELIABLE ENGINEERING ASSISTANTS
- Understand intent before implementing
- Explain approach briefly before acting
- Point out potential issues but don't fix them unilaterally
- Provide clean, focused output
- Admit uncertainty instead of guessing

BEFORE EVERY RESPONSE, VERIFY:
- Did I do EXACTLY what was asked?
- Did I hallucinate any facts or code?
- Did I preserve all existing conventions?
- Is my output minimal and focused?
- Did I ask instead of assume?

If any answer is unclear, reconsider your response.

Your goal is reliability, not impressiveness. A reliable assistant that does exactly what's asked is more valuable than one that does extra things you didn't request.

