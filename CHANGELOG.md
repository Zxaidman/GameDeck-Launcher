# Changelog

All notable changes to GameDeck Android will be documented in this file.

The project is currently in an early architecture and feasibility stage, so this changelog intentionally documents only decisions and artifacts that have actually been established.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/). Semantic versioning will be used once actual releasable software versions exist.

---

## [Unreleased]

### Project Definition

- Established the GameDeck Android product vision.
- Defined GameDeck as a gaming-focused Android launcher and virtual-controller environment.
- Defined Android phones running Android 10 or newer as the initial platform target.
- Deferred tablet and foldable support until the phone experience is sufficiently stable.
- Defined the initial application scope as:
  - emulators
  - game-streaming applications
  - cloud-gaming applications
- Deferred broad support for ordinary Android applications to a future version.
- Added manual application addition as a required fallback when automatic detection fails.

### Product Experience

- Defined the intended handheld layouts:
  - landscape with controller areas on both sides
  - portrait with controller area below the game
  - future dynamic layouts
- Defined scaling modes:
  - Fit
  - Fill
  - Stretch
- Defined initial aspect-ratio presets:
  - 4:3
  - 16:9
  - 18:9
  - 19.5:9
  - 20:9
  - 21:9
- Defined a future custom-aspect-ratio capability.
- Defined dynamic controller-space sizing.
- Defined skins as a separate visual layer from controller layouts.

### Controller System

- Defined proper gamepad-style input as the primary long-term input objective.
- Defined a capability-based input architecture.
- Defined fallback input for environments where the preferred backend is unavailable.
- Defined Xbox-style, PlayStation-style, Nintendo-style, generic, and emulator-oriented controller templates as planned initial families.
- Defined built-in controller templates as immutable.
- Defined user customization through duplication of built-in layouts rather than direct editing.
- Defined fully editable user-created layouts.

### Shizuku

- Defined Shizuku as an optional enhancement rather than a mandatory dependency.
- Defined separate capability handling for:
  - no Shizuku
  - Shizuku unavailable/stopped
  - Shizuku with ADB/shell privileges
  - Shizuku with root privileges
- Defined a requirement to validate Shizuku-based input capabilities before making them part of the production controller architecture.

### Configuration

- Chosen technology direction:
  - Native Kotlin
  - Jetpack Compose
- Chosen Android minimum:
  - Android 10 / API 29
- Chosen configuration direction:
  - JSON-first
  - data-driven
  - exportable/importable
  - schema-versioned
- Defined built-in configuration as immutable.
- Defined user configuration as duplicated/editable data.
- Defined application profiles.

### Community

- Defined an open-source, community-first direction.
- Chosen license:
  - GNU GPLv3
- Defined GitHub repositories as the initial community distribution mechanism for:
  - controller layouts
  - skins
  - profiles
  - compatibility metadata
- Deferred a proprietary cloud backend.
- Defined community content as untrusted declarative data.
- Defined validation requirements for imported/downloaded community content.

### Documentation

- Established:
  - `README.md`
  - `PRD.md`
  - `ARCHITECTURE.md`
  - `CONTRIBUTING.md`
  - `SECURITY.md`
  - `CODE_OF_CONDUCT.md`
  - `CHANGELOG.md`
  - `LICENSE`
- Established the intention to maintain architecture decision records.
- Established AI-assisted development and review principles.

### Engineering Direction

- Defined modular, capability-driven architecture.
- Defined separation between:
  - presentation
  - application/features
  - domain/core
  - Android/platform implementations
- Defined abstraction of input backends so experimental Android mechanisms can be replaced without rewriting the controller UI.
- Defined the requirement for real-device testing of Android-specific behavior.
- Defined Phase 0 as a technical feasibility gate before large-scale application development.

### Phase 0

- Defined `docs/PHASE-0.md`.
- Defined initial input-feasibility targets:
  - PPSSPP
  - Dolphin
  - RetroArch
  - Moonlight
  - Steam Link
- Defined testing across:
  - normal Android
  - Shizuku + ADB
  - Shizuku + root
  - other technically appropriate input mechanisms
  - touch/gesture fallback
- Defined testing requirements for:
  - digital buttons
  - analog axes
  - triggers
  - simultaneous input
  - hold/release behavior
  - lifecycle interruptions
  - controller/device recognition
  - repeatability
- Defined the requirement to distinguish touch simulation, key-event injection, axis/event injection, and true virtual gamepad/HID identity.
- Defined `ADR-INPUT-001` as the intended decision record for the production input strategy.

---

## Versioning Policy

Before the first meaningful release, changes remain under:

`[Unreleased]`

When a release is created, entries should be moved into a versioned section such as:

```text
## [0.1.0] - YYYY-MM-DD
```

The project should avoid inventing release numbers for prototypes that were never actually distributed as meaningful software releases.

---

## Changelog Categories

Use these categories when applicable:

### Added

New functionality.

### Changed

Changes to existing behavior.

### Deprecated

Features that remain available but are planned for removal.

### Removed

Removed functionality.

### Fixed

Bug fixes.

### Security

Security-related fixes or changes.

### Internal

Important architectural or developer-tooling changes that do not directly affect users.

---

## What Belongs Here

The changelog should record meaningful project changes, such as:

- new input backends
- new Android-version support
- new controller templates
- major launcher functionality
- configuration schema changes
- compatibility changes
- security fixes
- breaking behavior changes
- significant performance improvements

The changelog should not become a copy of every Git commit.

---

## What Usually Does Not Belong Here

Avoid listing every:

- typo fix
- variable rename
- formatting-only change
- internal refactor with no relevant behavior change
- temporary debugging change
- failed local experiment

Those belong in Git history or development documentation where appropriate.

---

## Breaking Changes

Breaking changes should be clearly identified.

Examples include:

- incompatible JSON schema changes
- removal of a public configuration format
- changed profile semantics
- changed controller mappings
- removal of supported Android versions

When possible, migration instructions should accompany breaking changes.

---

## Security Changes

Security fixes should be documented here when disclosure is appropriate.

Do not include sensitive exploit details merely for completeness.

Refer to [`SECURITY.md`](SECURITY.md) for security reporting and disclosure policy.

---

## Compatibility Changes

Android compatibility changes should identify the relevant environment when useful.

Example:

```text
- Fixed Shizuku capability detection on Android 14 devices.
- Added compatibility information for a specific emulator version.
```

Device/OEM-specific findings should also be recorded in compatibility documentation.

---

## Development-Stage Notes

GameDeck is currently an early-stage project.

The current goal is not to create a long changelog full of artificial version numbers.

The goal is to preserve a trustworthy record of how the project evolves.

Failed experiments may be better recorded in:

```text
docs/
docs/adr/
docs/phase0/
```

A failed experiment can still be valuable documentation.

---

## Links

Version comparison links can be added after the GitHub repository URL and first release are known.

Example:

```text
[Unreleased]: https://github.com/<OWNER>/<REPOSITORY>/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/<OWNER>/<REPOSITORY>/releases/tag/v0.1.0
```
