# AGENTS.md

This file defines how AI coding agents should behave in this repository.
It is not a replacement for `README.md`. Read it alongside the README and any
module-level documentation.

**Core principle:** Maintenance over performance. Human-readable over clever.
Simple over abstract. Explicit over implicit.

---

## 1. Non-Negotiable Rules

Violating these rules means the task was done incorrectly.

- **Never modify `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`,
  or `gradle.properties`** without explicit user approval.
- **Never introduce new dependencies** without asking first. If you believe a
  library is necessary, propose it and wait for confirmation.
- **Never perform "drive-by cleanups"** — refactoring, reformatting, or removing
  "dead" code outside the task scope. If you notice an issue, report it; do not
  fix it silently.
- **Never introduce abstractions "for future use"** (YAGNI). Abstractions are
  allowed only when the user has explicitly stated they are needed for
  adaptability.
- **Never write comments for trivial things.** Comments are permitted only to
  explain *why* something deviates from expected behavior, or to document a
  requirement/business rule and the reason behind it.
- **Always create a new branch and a PR.** Never commit directly to `main` or
  `master`.
- **Before finishing a task, run verification** (see Section 5). If verification
  is not possible, explicitly state what was not checked and why.

---

## 2. Workflow (Required Order)

1. **Read relevant files before writing any code.** Do not assume structure.
2. **If the request is unclear or missing information — ask.** Do not fill gaps
   with assumptions. Ask especially when:
   - A new library or dependency is involved.
   - An abstraction or interface is being introduced.
   - A configurable option exists (propose it before implementing).
   - Multiple valid approaches exist (propose them; do not choose silently).
3. **Propose improvements before implementing** if you see a better approach.
   Briefly explain the tradeoff.
4. **Research and explore.** Before settling on an implementation, investigate
   similar ideas, alternative approaches, or more elegant/interesting solutions.
   Be open-minded — if you find a creative or cleaner way, propose it with
   reasoning.
5. **Implement the minimal change** that solves the task. Nothing more.
6. **Verify** (build, tests, lint — see Section 5).
7. **Create a branch and PR** with a commit message following Conventional Commits.

---

## 3. Code Style & Architecture

These are guidelines, not absolute rules — adapt them to the project.

- **Architecture:** MVVM + Clean Architecture where applicable. Feature-driven
  module boundaries.
- **DI:** Koin. Prefer constructor injection. Avoid service locator patterns
  in application code.
- **State management:** `StateFlow` for screen state (UI state). `SharedFlow`
  for one-off events. Do not mix them without reason.
- **UI:** Jetpack Compose (Android and KMP where applicable). Do not introduce
  XML layouts for new features.
- **Code style:** Follow official Kotlin coding conventions. If a linter
  (ktlint/detekt) already exists, rely on it — do not add custom formatting rules.
- **Modules:** Favor independence and reusability. A feature module must not
  depend on another feature module. Shared code belongs in `core` or `shared`
  modules.
- **Documentation:** KDoc for public APIs in `core`/`shared` modules. A
  module-level `README.md` is allowed and encouraged for modules with
  non-trivial logic. Do not document the obvious.

---

## 4. Testing

- **Write tests for every non-trivial change** (business logic, state holders,
  mappers). Trivial changes (rename, formatting) do not require tests.
- **Use the existing testing stack.** Do not introduce a new framework without
  asking.
- **If tests cannot be run** (missing emulator, environment), state this
  explicitly in the PR description.
- **Do not reduce coverage** of existing tests. If you change an API, update
  its tests.

---

## 5. Verification (Before Every PR)

Run whatever is applicable to the project. If a command is not defined, ask
the user.

```bash
# Build
./gradlew assembleDebug        # or the appropriate task

# Tests
./gradlew test                 # KMP / common
./gradlew testDebugUnitTest    # Android unit tests

# Lint / Static analysis
./gradlew lint                 # Android Lint
./gradlew ktlintCheck          # if present
./gradlew detekt               # if present