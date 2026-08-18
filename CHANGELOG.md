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

Confirmed on hardware afterwards:

- Both APKs install and launch on a Redmi Note 13 5G running HyperOS 3.0.3, side by side, with no
  security warning shown during installation.

Not verified:

- No layout, skin, profile, input backend, overlay, or session behaviour exists.

### Phase 0 — Tier 0 Executed on Hardware

First real device evidence. See `docs/phase0/results/tier0-report.md` and the raw export beside it.

- Ran the harness on a Redmi Note 13 5G (Dimensity 6080, Android 15 / API 35, HyperOS 3.0.3).
- Recorded the baseline input inventory: eight devices, none of them a usable controller, as
  expected with nothing attached.
- Found that two devices on this stock unrooted phone are created through the kernel virtual-input
  facility by vendor components. This is the first evidence bearing on the highest-value tier: the
  mechanism exists and is in use on this hardware. It does not establish that an ordinary
  application or a shell-privileged process can reach it.
- Found that one of those devices advertises the gamepad source while advertising zero gamepad
  buttons and zero axes. A capability check based on source flags alone would report a controller
  present on this phone. This is why capability must be read from advertised keys and axes, and the
  harness records all three.
- Confirmed the volume keys originate from two separate hardware devices, so a backend must not
  assume one device covers a logical group of controls.
- Test 13 has a partial result: the harness survives backgrounding and re-registers its listener.

Not verified:

- No injection tier has been attempted. No evidence grade applies, since a grade describes a
  mechanism and no mechanism has been exercised. `ADR-INPUT-001` remains Pending.
- No physical controller has been attached, so there is no calibration reference for what a genuine
  controller looks like on this device.

### Phase 0 — Tier 1 Calibration Executed on Hardware

A second phone running remote-gamepad software was paired over Bluetooth to act as a controller,
supplying the calibration reference Tier 0 lacked. See `docs/phase0/results/tier1-report.md`.

- Recorded the signature of a genuine controller on the reference device: sources
  `KEYBOARD|GAMEPAD|JOYSTICK`, ten axes, twelve buttons, and a system-assigned controller number.
  Anything Kestrel creates must match this to claim an equivalent result.
- Found that every button carrying a system meaning is delivered twice on one scan code — notably
  `BUTTON_B` also arrives as `BACK`, and `START`, `THUMBL` and `THUMBR` all collapse to
  `DPAD_CENTER`. Input handling must match on the controller keycode and originating device, and
  discard the fallback, or it will double-count every press.
- Found that each trigger reports on two axes simultaneously, so the transformation layer must pick
  one per trigger rather than treat them independently.
- Found that the D-pad arrives both as hat axes and as synthesised key events, and that the left
  stick also synthesises directional keys past a threshold.
- Found that the system virtual device aggregates the capabilities of connected devices: it
  advertised four keys with nothing attached and sixteen with a controller attached. Capability
  detection must skip it, or it will report a controller present on a bare phone.
- Confirmed that dead zones are declared per axis by the device, so the transformation layer should
  read the declared value rather than hardcode one.

Not verified:

- This exercises the receiving half only. It does not show that Kestrel can create a controller for
  applications on the same phone, because the software used works by making a second device
  advertise itself as a Bluetooth peripheral to the first. The core question is unchanged and
  `ADR-INPUT-001` remains Pending.
- `BUTTON_A` was not pressed during the run, so one button has no observed delivery.

Noted for possible future work:

- The same mechanism suggests Kestrel could implement the peripheral role itself, turning a spare
  phone into a controller for a main device. `BluetoothHidDevice` has been public since API 28,
  within the project baseline. This is unverified, is not the core requirement, and would not help a
  user with a single phone; it would need its own decision record if pursued.

### Phase 0 Harness — Privilege Probe

- Added a Probe tab to the harness that reports the privilege state and runs read-only checks
  through a shell-privileged service, using Shizuku. This makes the virtual-device tier runnable
  from the phone alone, with no computer and no typed commands, which matters because the project
  owner is not a developer.
- The privilege state is reported as four separate facts — service running, permission granted,
  identity actually obtained, and version — implementing the model in `ARCHITECTURE.md` §14 and
  testing its central claim that none of those facts implies another.
- The probe reads only: device node existence, permissions and owning group, readability and
  writability from the obtained identity, presence of the helper command, and enforcement mode. It
  creates no device and emits no event, so the harness still cannot manufacture the result it
  measures.
- Probe output and privilege state are included in the export, so they become evidence.
- Added `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider` 13.1.5 to `tools/phase0` only, with
  the justification recorded in the module's build script and the entries added to
  `THIRD_PARTY_LICENSES.md`.

Verified:

- `./gradlew build` succeeds with lint clean.
- The product's runtime classpath was inspected and contains no Shizuku artifact, so the boundary
  required by ADR-003 and `PROJECT_STRUCTURE.md` §21 holds.

Not verified:

- The probe has never been run. Whether Shizuku binds, whether the service starts, and what the
  device node permissions actually are on this firmware are all unknown until it runs on hardware.

### Phase 0 — Tier 5 Privilege Probe Executed on Hardware

See `docs/phase0/results/tier5-probe-report.md`.

- The privilege chain works end to end: Shizuku bound, permission was granted, and the identity
  actually obtained was `shell`, uid 2000. Root was neither obtained nor expected.
- `/dev/uinput` is `crw-rw----`, owned by `system`, group `net_bt_admin`, and the shell identity is
  a member of that group. Both permission tests reported the node readable and writable.
- `/system/bin/uinput` is present on this build.
- **This is not yet a yes.** `test -w` calls access(2), which consults only the classic permission
  bits and is blind to SELinux. SELinux is Enforcing, the node is labelled `uhid_device`, and no
  actual open has been attempted. Policy is decided at open, and policy is where this usually fails.
- Found a second candidate path that was not previously believed available: the platform `input`
  command on this build advertises `gamepad`, `joystick` and `dpad` as injection sources, and
  accepts named motion axes via `--axis`. If it behaves as advertised, a shell-privileged process
  could deliver controller semantics with continuous axis values without creating a virtual device
  at all.
- Two paths now exist, both reachable from the phone alone: creating a virtual device, which could
  carry a real device identity, and injecting through `input`, which could not. No evidence grade
  applies to either yet and `ADR-INPUT-001` remains Pending.

### Phase 0 Harness — Actual Access Tests and User-Chosen Export

- Added an actual open-for-write test against the virtual-input node, because the permission-bit
  test cannot see SELinux and would otherwise have been mistaken for a positive result.
- Added injection attempts issued through the platform's own `input` tool in the shell-privileged
  process, covering the gamepad and dpad sources and both candidate analog-axis syntaxes.
- The command issued is written into the event log immediately before it runs and its result
  immediately after, so the log interleaves stimulus and response and a delivered event can always
  be traced to what caused it. The harness still does not synthesise events into its own window.
- Export now opens the system file picker so the destination is chosen by the user, replacing the
  previous write into the application's private directory, which was not reachable through an
  ordinary file manager. Sharing is now a separate action.
- Captured the full `input` usage text rather than a truncated head, which is what surfaced the
  gamepad and axis support above.

- Documented how to obtain an installable build without any toolchain: build artifacts are attached
  to every workflow run, and releases are published by tagging. Tag pushes must be done by the
  repository owner, since the development environment's git proxy refuses them.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: none of the new tests has been run on hardware.

### Phase 0 — Virtual-Input Access Confirmed

The decisive question is answered. See `docs/phase0/results/tier5-open-report.md`.

- A shell-privileged process obtained through Shizuku, with no computer attached, **opened the
  kernel virtual-input node for writing** on a stock unrooted device with SELinux Enforcing. The
  kernel denial log was empty, so policy permits it outright rather than permitting-and-auditing.
- This was the prerequisite most likely to fail, and it did not. The path that could produce a
  device with its own controller identity is open on this hardware. Creating and recognising such a
  device is still unproven.
- The full `input` usage text, captured intact, establishes a hard ceiling on the shell path:
  `motionevent` accepts only `x` and `y`, and the `--axis` option belongs to `scroll` alone. The
  shell path can therefore drive buttons, the D-pad and one analog stick, but **cannot address the
  right stick or the triggers**. Against `docs/PHASE-0.md` §29, which requires a working trigger, it
  cannot pass on its own. It is a fallback and a comparison baseline, not a candidate answer.
- The release mechanism works: the axis returned to rest and the repeat flood stopped. Two further
  repeats arrived after the release was issued, so a release must be issued early and confirmed,
  not assumed effective the moment it is sent. Measured repeat rate is about 15 per second.

### Phase 0 Harness — Virtual Device Creation Attempts

- Added attempts to create a virtual controller through the platform `uinput` helper, holding the
  device open for five seconds so it can be observed in the inventory and by the hot-plug listener.
- Two descriptor schemas are attempted, because the helper's accepted schema is undocumented
  on-device and its help output is empty. A rejection is informative: the error states what the
  schema requires.
- Button and axis numbers are Linux input-event constants, which are stable kernel ABI rather than
  values invented for this project.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: no creation attempt has been run. Whether the helper accepts either schema, whether a
device appears, and whether it carries controller semantics are all unknown.

### Fixed After First Device Run

- The on-screen event counter was a plain integer, invisible to composition, so it stopped matching
  the log it was counting. It is now snapshot-backed.
- Neither screen applied window insets, so content drew underneath the status bar on Android 15,
  which draws edge to edge by default at this target level. Both now inset for system bars.

### Product Identity

- Added an adaptive launcher icon to both applications: a double-chevron mark reading as swept wings
  and ascent, drawn as vector art so it stays sharp at every density with no bitmap assets.
- Earlier illustrative attempts at a falcon silhouette were rendered and inspected before being
  rejected: at icon size they read as an aircraft or an insect. The geometric mark survives being
  reduced to 48dp, which is the size that actually matters.
- The harness carries the same mark in neutral steel rather than the product's colours, so the
  experimental build is never mistaken for the product on a home screen.

### Continuous Builds

- Added `.github/workflows/build.yml`. Every push and pull request compiles, lints, tests, and
  attaches both APKs as downloadable artifacts; a tag beginning with `v` additionally publishes a
  release with both APKs attached, giving a link that can be opened directly on a phone.
- This removes the need for a local toolchain to obtain an installable build.
- Both APKs are debug-signed. Release signing requires a keystore in repository secrets and is
  deliberately not set up; a debug-signed build must not be treated as distributable.

Verified:

- The workflow's first run completed successfully on GitHub-hosted runners, building, linting and
  testing both modules and attaching both APKs as artifacts. No change to the workflow was needed.

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
[Unreleased]: https://github.com/Zxaidman/Kestrel/commits/main
```

After the first release, the pattern becomes:

```text
[Unreleased]: https://github.com/Zxaidman/Kestrel/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Zxaidman/Kestrel/releases/tag/v0.1.0
```
