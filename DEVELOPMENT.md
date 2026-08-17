# GameDeck Android — Development Guide

## Purpose

This guide explains how to build, test, and work on GameDeck. It is intentionally practical for both human contributors and AI coding agents.

## Tooling

The project is intended to use:

- Android Studio
- Android SDK
- JDK compatible with the repository Gradle/Android Gradle Plugin version
- Git
- a physical Android 10+ phone for Android-specific testing

Exact versions should be pinned when the initial build is established.

## Initial setup

```text
Clone repository
    ↓
Open in Android Studio
    ↓
Install required SDK components
    ↓
Sync Gradle
    ↓
Run unit tests
    ↓
Build debug APK
    ↓
Install on Android test device
```

A successful build is not proof of device compatibility.

## Development order

1. Phase 0 input feasibility
2. core project/build foundation
3. configuration models/repositories
4. launcher
5. controller engine
6. layout editor
7. gaming session
8. Shizuku integration
9. skins
10. community system

Do not build large downstream features on an unverified Phase-0 assumption.

## Testing levels

### Unit tests

Use for JSON parsing, schema validation, layout calculations, profile matching, aspect-ratio calculations, and pure input transformations.

### Instrumentation tests

Use for Android lifecycle, services, overlays, package discovery, persistence, and other Android-dependent behavior.

### Device tests

Required for input backends, Shizuku, overlays, OEM behavior, target-app compatibility, and performance.

## Real-device rule

If behavior depends on Android system behavior, test on a physical device.

Desktop compilation cannot prove controller recognition, overlay behavior, Shizuku behavior, OEM restrictions, or target-app input compatibility.

## Git workflow

Use a focused branch:

```text
feature/controller-editor
fix/json-import
experiment/input-shizuku
test/moonlight-input
docs/phase0-results
```

Prefer pull requests over direct pushes to the default branch for substantial changes.

## Commit style

Prefer:

```text
Add layout schema validation
Fix analog stick normalization
Document Shizuku capability detection
```

Avoid vague messages such as `stuff`, `fix`, or `changes`.

## AI-assisted development

AI coding tools are expected to be used heavily. AI-generated code must still compile, pass tests, follow the architecture, use real APIs, respect Android version constraints, and be tested on real devices when applicable.

See `AI_DEVELOPMENT_GUIDE.md`.

## Android API verification

Before using an Android API:

1. verify it exists
2. verify its minimum API
3. verify permission requirements
4. verify behavior on relevant Android versions
5. identify fallback behavior where appropriate
6. document important limitations

Never rely only on an AI-generated API suggestion.

## Privileged features

Shizuku/root/system-sensitive functionality must remain behind a small interface. Do not scatter privileged calls throughout Compose or domain code.

## Configuration work

When modifying JSON:

- update schema documentation
- add/update validation
- add migration if needed
- test import/export
- preserve built-in immutability

## Compatibility work

Every new device-specific result should update `docs/COMPATIBILITY.md` with device, Android version, firmware, GameDeck version/commit, target app, backend, result, and limitations.

## Pull request readiness

Before submission, run relevant:

- build
- unit tests
- instrumentation tests
- lint/static checks where configured
- real-device tests where required
- target-application tests where required

Then review the diff manually.

## Definition of done

A change is done when required behavior exists, relevant tests pass, Android-specific behavior has been tested where necessary, architecture boundaries are preserved, documentation is updated, limitations are documented, and no debug secrets remain.

## Current first task

Complete Phase 0 before substantial product implementation. See `docs/PHASE-0.md`.
