# Changelog

**Document:** `CHANGELOG.md`  
**Status:** Active — records only what has actually been established  

All notable changes to Kestrel will be documented in this file.

The project is currently in an early architecture and feasibility stage, so this changelog intentionally documents only decisions and artifacts that have actually been established.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/). Semantic versioning will be used once actual releasable software versions exist.

---

## [Unreleased]

### Project Definition

- Established the Kestrel product vision.
- Defined Kestrel as a gaming-focused Android launcher and virtual-controller environment.
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

### Build Foundation Established

- Added a Gradle build: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and the
  Gradle wrapper pinned to 8.14.3.
- Added `gradle/libs.versions.toml` as the single declaration point for plugin and dependency
  versions, per `DEVELOPMENT.md`.
- Added two modules, keeping the module count small per `PROJECT_STRUCTURE.md` §24:
  - `:app` — Android assembly layer, containing a manifest, a single activity, and a placeholder
    screen. No feature, input, or configuration logic.
  - `:core` — Kotlin/JVM module, so that the dependency rule in `PROJECT_STRUCTURE.md` §21 is
    enforced by the compiler: Compose, Android UI, and Shizuku cannot resolve there.
- Added `core/common/Outcome.kt` — the typed success/failure result that domain code returns instead
  of throwing for expected failures, as required by `docs/CONFIGURATION_SCHEMA.md`.
- Pinned Android 10 / API 29 as `minSdk`, per ADR-004.

Verified, with the Android SDK installed:

- `./gradlew build` completes successfully — both modules compile, lint reports no errors, and the
  domain tests pass.
- `./gradlew :core:test` — 9 tests, all passing, on JDK 21 with Gradle 8.14.3.
- Every pinned version in `gradle/libs.versions.toml` resolved. AGP 8.13.2, Kotlin 2.2.21, Compose
  BOM 2026.05.01 and `compileSdk`/`targetSdk` 36 are confirmed mutually compatible.
- `app-debug.apk` builds with identity `io.github.zxaidman.kestrel`, label Kestrel.

Not verified:

- Neither APK has been installed on a physical device or launched. Nothing about runtime behaviour,
  rendering, or OEM firmware interaction is known.
- No layout, skin, profile, input backend, overlay, or session behaviour exists.

### Phase 0 Harness Established

- Added `tools/phase0/` — the input feasibility harness, as its own application with its own
  identifier (`io.github.zxaidman.kestrel.phase0`), no permissions, and no dependency on `:app` or
  `:core`. Labelled experimental per `PROJECT_STRUCTURE.md` §27.
- The harness observes only: it enumerates reported input devices, listens for device hot-plug, and
  logs every key and motion event its window receives with the id and source of the originating
  device. It injects nothing, so that a measured result cannot be produced by the instrument.
- Added `docs/phase0/README.md` — the test procedure, structured as six tiers from baseline
  inventory through to real target applications, with the OEM preparation steps the target device
  requires.
- Added `docs/phase0/results/` for exported evidence.

Fixed during first compilation:

- `IntArray` has no `mapNotNull`; device enumeration used `map` and `filterNotNull` instead.
- The harness consumed key events, including BACK, which would have trapped the user on the screen.
  It now records each event and passes it on untouched — an observer must not swallow what it
  measures.
- Rumble detection used an API deprecated from API 31; it now selects the API by version.
- Removed redundant manifest labels and a mis-declared composable.

Verified:

- `./gradlew :tools:phase0:assembleDebug` produces `phase0-debug.apk` with identity
  `io.github.zxaidman.kestrel.phase0`, label Kestrel Phase 0, installable alongside the product.
- Lint reports no errors for the module.

Not verified:

- The harness has never been installed or launched on a device.
- No tier has been executed and no evidence has been recorded. `ADR-INPUT-001` remains Pending.

### Setup Documentation

- Added `docs/SETUP.md` — a build and install guide for contributors who are not software
  developers, using the command-line tools and a code editor rather than the full IDE. The Linux
  path in it was executed end to end; the Windows and macOS paths were not, and say so.

### Rebranding

- Renamed the project to Kestrel, for a distinctive mark.
- Set the package identity to `io.github.zxaidman.kestrel`.

### Documentation Artifacts Established

These files exist in the repository.

- Established the root documentation set: `README.md`, `PRD.md`, `ARCHITECTURE.md`,
  `PROJECT_STRUCTURE.md`, `DEVELOPMENT.md`, `AI_DEVELOPMENT_GUIDE.md`, `CLAUDE.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`,
  `THIRD_PARTY_LICENSES.md`, `LICENSE`.
- Established the supporting documentation set under `docs/`: `PHASE-0.md`, `COMPATIBILITY.md`,
  `INPUT_BACKENDS.md`, `CONFIGURATION_SCHEMA.md`.
- Established the accepted decision records under `docs/adr/`:
  - `ADR-001-json-first-config.md` — JSON-first configuration
  - `ADR-002-input-backend-abstraction.md` — input backend abstraction
  - `ADR-003-shizuku-optional.md` — Shizuku is optional
  - `ADR-004-android-10-baseline.md` — Android 10 / API 29 baseline, phones only
  - `ADR-005-gplv3.md` — GPLv3 for original project code
- Recorded `ADR-INPUT-001.md` as pending, awaiting Phase 0 evidence.
- Established contribution infrastructure under `.github/`: pull request template and issue
  templates for bug reports, feature requests, and compatibility reports.
- Designated `PROJECT_STRUCTURE.md` as canonical for folder organization, and corrected the
  repository tree in `ARCHITECTURE.md` §4 to match it.
- Defined a single decision-record naming convention in `CONTRIBUTING.md` §57.
- Documented how the compatibility statuses, Phase-0 evidence grades, and claim-verification states
  relate to one another in `docs/COMPATIBILITY.md` §4a.

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

Kestrel is currently an early-stage project.

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

Version comparison links are added once the first release exists. Until then only the repository
link below is meaningful, because there is no tag to compare against.

```text
[Unreleased]: https://github.com/Zxaidman/GameDeck-Launcher/commits/main
```

After the first release, the pattern becomes:

```text
[Unreleased]: https://github.com/Zxaidman/GameDeck-Launcher/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Zxaidman/GameDeck-Launcher/releases/tag/v0.1.0
```
