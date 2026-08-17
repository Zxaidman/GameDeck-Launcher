# CLAUDE.md

Guidance for Claude Code and other AI coding agents working in this repository.

---

## 1. What this repository currently is

**GameDeck Android** — an open-source Android 10+ gaming launcher and virtual-controller
environment. It aims to turn an ordinary phone into a handheld gaming device: one place to
launch emulators/streaming clients, configure a virtual gamepad, pick a layout and skin, and play.

**Current state: documentation and specification only. There is no source code yet.**

The repository contains no `app/`, `core/`, `feature/`, `platform/`, or `data/` directories, no
Gradle files, no wrapper, no CI workflows. Consequences for any agent working here:

- There is **nothing to build, run, lint, or test**. Do not invent `./gradlew` commands or claim
  they were run.
- The only meaningful verification available today is reading, cross-checking, and editing Markdown.
- If asked to implement code, the first deliverable is usually the missing build foundation
  (`settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `app/`), and that decision should follow
  `PROJECT_STRUCTURE.md` §24 and §26 — start small, do not scaffold every directory.

Planned stack (per `ARCHITECTURE.md`): Kotlin, Jetpack Compose, min SDK 29 (Android 10), Gradle
Kotlin DSL, GPLv3. Exact tool versions are deliberately unpinned until the first build exists
(`DEVELOPMENT.md`).

---

## 2. Documentation map — who is authoritative for what

| File | Authoritative for |
| --- | --- |
| `README.md` | Product overview, vision, status |
| `PRD.md` | Product requirements, scope, non-goals, development phases, MVP definition |
| `ARCHITECTURE.md` | Layers, boundaries, domain model, input/Shizuku/session/display architecture |
| `PROJECT_STRUCTURE.md` | **Canonical** folder organization and dependency rules |
| `DEVELOPMENT.md` | Build/test workflow, testing levels, definition of done |
| `AI_DEVELOPMENT_GUIDE.md` | **Rules for AI-assisted implementation — read this before writing code** |
| `CONTRIBUTING.md` | Contributor workflow, coding style, commit/branch/PR conventions, governance |
| `SECURITY.md` | Security policy and threat boundaries |
| `docs/PHASE-0.md` | Input feasibility spec: tests, evidence grades, acceptance criteria |
| `docs/INPUT_BACKENDS.md` | Input abstraction and backend categories |
| `docs/CONFIGURATION_SCHEMA.md` | JSON configuration model, schema versioning, validation |
| `docs/COMPATIBILITY.md` | Device/application compatibility matrix |
| `docs/adr/` | Architecture Decision Records — the *why* behind each choice |
| `CHANGELOG.md` | Records decisions and artifacts actually established (not aspirations) |

Reading order before implementation work: `README.md` → `PRD.md` → `ARCHITECTURE.md` → relevant
module docs → relevant ADRs → `docs/COMPATIBILITY.md` → `docs/CONFIGURATION_SCHEMA.md` → existing
code and tests.

### Hierarchy of truth

When sources conflict, higher wins:

```
Verified test result
  ↓ Android/project documentation
  ↓ Existing architecture
  ↓ PRD
  ↓ ADR
  ↓ Task description
  ↓ AI assumption
```

### Single-source rules

Several lists used to be duplicated across documents and had drifted. One owner each, now:

- **Folder placement** — `PROJECT_STRUCTURE.md` is canonical. `ARCHITECTURE.md` §4 reproduces the
  tree for context and says so; if they ever disagree, the canonical document wins and the other is
  corrected.
- **Which decision records exist** — the directory `docs/adr/` itself. Never cite a record from a
  list quoted in prose without checking the directory.
- **Status vocabularies** — three exist for different purposes and are not interchangeable:
  compatibility Status + Confidence (`docs/COMPATIBILITY.md` §3–§4), Phase-0 evidence Grades A–E
  (`docs/PHASE-0.md` §28), and claim-verification states (`AI_DEVELOPMENT_GUIDE.md`). The mapping
  between them is `docs/COMPATIBILITY.md` §4a. A Phase-0 grade never by itself sets a support status.

When a document conflicts with its owner, fix the copy and say so — do not silently change the owner.

---

## 3. Accepted architecture decisions

| ADR | Decision | Status |
| --- | --- | --- |
| ADR-001 | JSON is the canonical portable configuration format | Accepted |
| ADR-002 | Input is abstracted behind a backend interface | Accepted |
| ADR-003 | Shizuku is optional, never mandatory | Accepted |
| ADR-004 | Android 10 / API 29 baseline; phones only (no tablets/foldables) | Accepted |
| ADR-005 | GPLv3 for original project code | Accepted |
| ADR-INPUT-001 | Production input backend selection | **Pending Phase 0** |

Significant new decisions require a new ADR in `docs/adr/`. Naming (`CONTRIBUTING.md` §57):
sequential `ADR-NNN-topic.md`, numbers never reused or renumbered; a reserved prefix
(`ADR-INPUT-001.md`) only when a decision is gated on an experiment or scoped to one domain.
Superseded records stay in place with their status changed.

---

## 4. Architecture rules

### Layers

```
Presentation (Compose)  →  Feature/Application  →  Core/Domain (pure Kotlin)
                                                        ↑
                                              Platform (Android-specific)
```

### Where code goes

- Pure domain logic → `core/` (`common`, `model`, `configuration`, `input`, `layout`, `profile`,
  `skin`, `compatibility`, `diagnostics`)
- User-facing features → `feature/` (`launcher`, `gaming-session`, `controller-editor`, `skins`,
  `settings`, `community`)
- Android-specific implementations → `platform/` (`android`, `display`, `foreground-app`, `overlay`,
  `shizuku`, `input/{gamepad,shizuku,fallback}`)
- Packaged configuration, built-ins, schemas, migrations → `data/`
- Developer utilities → `tools/`
- Shared test fixtures → `tests/`; module-specific unit tests stay with their module
- Research and evidence → `docs/`

### Dependency rules

Allowed: `feature → core`, `feature → platform abstractions`, `platform → core`,
`app → feature/platform/core`.

**Never:** `core → Compose`, `core → Android UI`, `core → Shizuku`, `core → a specific input
implementation`. A Composable must never call Shizuku or an injection API directly — it goes
UI → InputEngine → InputBackend → platform implementation.

Do not create a new top-level directory because nothing seems to fit. Classify the file first
(Domain / Feature / Platform / Data / Tool / Documentation / Test); if the architecture genuinely
lacks a category, propose an architecture change instead.

Gradle module count should stay small initially. Package/domain boundaries are mandatory even when
modules are physically grouped.

---

## 5. Subsystem rules that are easy to get wrong

### Input (highest-risk subsystem)

- Domain and UI code use **controller semantics** (`GamepadButton.A`, `LEFT_X = 0.73`), never Android
  key codes.
- Sticks normalize to `-1.0…+1.0`; triggers to `0.0…+1.0`. Dead zone, sensitivity, inversion, and
  curves belong to the transformation layer, not to individual backends — and must be pure,
  unit-testable Kotlin.
- Backend selection is **capability-driven**, not name-driven.
- Every backend must release active buttons, reset axes, stop privileged services, and clean up on
  session end or failure. A backend that can leave stuck input is not production-ready.
- Do not call anything a "true virtual gamepad" unless device testing proves target applications
  receive controller-style input. Distinguish touch simulation, key-event injection, motion/axis
  injection, and virtual HID identity — they are different capabilities.
- High-frequency analog updates must not drive global Compose recomposition.

### Shizuku

- `Shizuku installed ≠ Shizuku running ≠ permission granted ≠ capability available`, and
  **Shizuku ≠ root**. Privilege levels are `NONE`, `ADB_SHELL`, `ROOT`, `UNKNOWN`.
- Detect actual capabilities; never infer them from installation state.
- Keep Shizuku behind a narrow capability interface. Do not scatter Shizuku calls through features.
- Never put normal application lifecycle/UI code inside the Shizuku UserService.

### Configuration / JSON

- Every configuration document carries `schemaVersion`, `type`, `id`, `name`.
- Stable IDs (`builtin.xbox.default`, `user.<uuid>`) are distinct from display names; renaming must
  not change an ID.
- Built-ins are **immutable**. The workflow is Built-in → Duplicate → User copy → Edit, and
  immutability must be enforced in the repository/domain layer, not by hiding an edit button.
- Validate every import (schema version, required fields, types, ID format, ranges, enums,
  collection sizes, references, file limits). Invalid data returns a typed error and must not crash.
- Preserve unknown non-executable fields where safe.
- Configuration is **data**. Never add fields that execute shell commands, code, native libraries,
  or downloaded plugins. Community content stays declarative and non-executable.
- Schema changes need versioning, migration where appropriate, tests, and documentation updates.

### Display

Never claim an external game was resized because GameDeck changed its own layout. Cross-app activity
embedding is Android 13+ with trust/opt-in restrictions and must not be a dependency of the
Android 10+ architecture.

### Security

Do not generate code that executes downloaded configuration, runs arbitrary community commands,
silently escalates privileges, collects credentials, adds hidden telemetry, or bypasses security
checks. Structured logs must exclude personal data, credentials, tokens, and screen content.
See `SECURITY.md`.

---

## 6. Development phases (gating)

`Phase 0 (input feasibility)` → `1 Core app` → `2 Controller engine` → `3 Layout editor` →
`4 Gaming session` → `5 Shizuku` → `6 Skins` → `7 Community system`.

**Phase 0 is not complete.** Do not build large downstream features on an unverified Phase-0
assumption, and do not mark `ADR-INPUT-001` accepted without reproducible device evidence. Phase-0
prototypes may live temporarily under `tools/phase0/` and must be clearly labeled experimental.

---

## 7. Working conventions

**Branches:** `feature/…`, `fix/…`, `docs/…`, `test/…`, `experiment/…` (e.g.
`feature/shizuku-capability-check`, `fix/layout-import-validation`). Avoid substantial work directly
on the default branch.

**Commits:** clear, focused, imperative-ish subjects — `Add layout schema validation`,
`Fix analog stick normalization`. Not `fix stuff`. Keep unrelated changes in separate commits.

**Kotlin style:** clear names, focused functions, immutable data where practical, interfaces at
meaningful boundaries, no giant classes, no global mutable state, no Android specifics in domain
code, no new architectural pattern without a requirement. Simplest design that satisfies the
requirement wins.

**Tests are part of implementation.** Unit tests for JSON/schema/layout geometry/profile
matching/analog processing/capability selection; instrumentation tests for lifecycle, services,
overlays, discovery, persistence; **device tests** for input backends, Shizuku, overlays, OEM
behavior, target-app compatibility, and performance. Desktop compilation proves none of the Android
behavior.

**Dependencies:** before adding one, state why it is needed, why platform/existing code is
insufficient, its license, Android compatibility, maintenance risk, and build impact. Update
`THIRD_PARTY_LICENSES.md`.

**Documentation upkeep:** device-specific results go to `docs/COMPATIBILITY.md` with device, Android
version, firmware, GameDeck version/commit, target app, backend, result, and limitations. JSON
changes update `docs/CONFIGURATION_SCHEMA.md`. Architecture changes update `ARCHITECTURE.md` and add
an ADR. `CHANGELOG.md` records only what has actually been established.

**Definition of done:** required behavior exists, relevant tests pass, Android-specific behavior was
tested where necessary, architecture boundaries preserved, docs updated, limitations documented, no
debug code or secrets remaining.

---

## 8. Rules specific to AI agents

These come from `AI_DEVELOPMENT_GUIDE.md`, `ARCHITECTURE.md` §34, and `CONTRIBUTING.md` §54–55.

- **Never fabricate an API.** Do not invent Android, Compose, Shizuku, Gradle, permission, or hidden
  APIs. If an API cannot be verified, propose a small experiment instead of guessing.
- **Never fake results.** "Should work" is not "tested." Use explicit states: `Unverified`,
  `Experimental`, `Tested`, `Supported`. Never invent test output or device results.
- **Inspect before editing.** Read existing interfaces, call sites, tests, and implementations first.
- **Smallest reasonable change.** No giant rewrites, no unrelated reformatting, no new database, no
  silent architecture replacement.
- **Report Android specifics** for platform claims: API level, permissions, lifecycle constraints,
  OEM risk, public vs hidden API status, Shizuku/root requirement, fallback behavior.
- **Say when uncertain.** The agent is expected to challenge impossible requirements and stop for a
  feasibility experiment when a fundamental assumption is unproven.

### Task format worth requesting or restating

```
Goal / Context / Relevant files / Requirements / Constraints /
Do not change / Tests / Acceptance criteria
```

### End-of-task report

```
Implemented:
Files changed:
Tests added:
Tests run:
Device tests:
Known limitations:
```

State plainly when something could not be run — in this repository, "Tests run: none, no build
system exists yet" is the correct and honest answer.

---

## 9. Principle

> Use AI to increase development speed, not to reduce engineering discipline.

The project owner remains responsible for product decisions, review, physical-device testing, and
release readiness.
