# Kestrel — Input Backends

**Document:** `docs/INPUT_BACKENDS.md`  
**Status:** Active — preferred backend selected by ADR-INPUT-001, fallback still undecided  

## Purpose

This document defines how Kestrel abstracts controller input so the UI and gaming-session code do not depend on one Android injection mechanism.

The primary product goal is **gamepad-style input**. Touch/gesture simulation is a fallback, not the definition of gamepad support.

## Backend categories

### 1. Virtual/Gamepad backend

**Selected as the preferred backend by `ADR-INPUT-001`, scoped to the reference device.**

Phase 0 proved it there: a kernel virtual input device created through the platform's own helper
with Shizuku-provided shell privilege, enumerated by the platform as a controller in player slot 1,
accepted and auto-mapped by five emulators, reported through the web Gamepad API by a browser, and
forwarded by a streaming client to a host that showed it as a game controller.

Held for a session by a lease that a privileged watchdog enforces — see `ADR-INPUT-001` for why that
is part of the backend rather than a detail of it.

Proven on one device and one firmware. Everywhere else it is an assumption, and
`docs/COMPATIBILITY.md` is where that distinction is kept.

### 2. Shizuku backend

Uses Shizuku as an optional capability provider. The implementation must distinguish ADB/shell and root capability and must never assume that “Shizuku installed” means “virtual gamepad available.”

### 3. System input/event backend

Experimental system-level event delivery where a valid Android mechanism exists. It may provide useful input without persistent virtual-device identity.

### 4. Touch/gesture fallback

Simulates touchscreen interaction. It can provide broader compatibility but is not classified as a true virtual gamepad.

## Core abstraction

Use an interface conceptually equivalent to:

```kotlin
interface InputBackend {
    val id: String
    val capabilities: Set<InputCapability>

    suspend fun initialize(): BackendResult

    suspend fun sendButton(
        button: GamepadButton,
        state: ButtonState
    ): InputResult

    suspend fun sendAxis(
        axis: GamepadAxis,
        value: Float
    ): InputResult

    suspend fun shutdown()
}
```

The exact interface may evolve. The architectural boundary must remain.

## Capability model

Potential capabilities:

- DIGITAL_BUTTONS
- DPAD
- ANALOG_AXES
- ANALOG_TRIGGERS
- SIMULTANEOUS_INPUT
- SYSTEM_INPUT_INJECTION
- VIRTUAL_DEVICE
- GAMEPAD_DEVICE_IDENTITY
- TARGET_APP_COMPATIBILITY
- TOUCH_FALLBACK

Backend selection is capability-driven.

## Selection flow

```text
Gaming Session
    ↓
Discover backends
    ↓
Determine device/Android capabilities
    ↓
Determine Shizuku capability
    ↓
Check target-app compatibility
    ↓
Choose best verified backend
```

The runtime ordering is intentionally not finalized until Phase 0.

## Input semantics

Kestrel internally uses controller semantics rather than Android-specific key codes.

Examples:

```text
A = DOWN
LEFT_X = 0.73
RT = 1.0
```

Backends translate these semantics into implementation-specific events.

## Analog processing

Stick values are normalized to `-1.0 ... +1.0`. Trigger values are normalized to `0.0 ... +1.0` where supported.

Dead zone, sensitivity, inversion, and response curves belong to the controller/input transformation layer rather than individual backends.

## Lifecycle safety

Every backend must release active buttons, reset active axes/triggers, stop privileged services, and clean up resources when a session ends or becomes invalid.

A backend that can leave stuck input is not production-ready.

## Target-aware compatibility

Backend selection may depend on:

- Android version
- device/OEM
- Shizuku state
- privilege level
- target package
- target application version
- known compatibility data

## Testing

Every backend must be tested for:

- digital buttons
- D-pad
- analog sticks
- triggers
- simultaneous input
- hold/release
- rapid input
- session interruption
- application switching
- shutdown/reset

See `docs/PHASE-0.md` and `docs/COMPATIBILITY.md`.

## Hard rule

Do not call an implementation a “true virtual gamepad” unless testing proves that target applications receive controller-style input consistent with project acceptance criteria.

## Architecture gate

The production input strategy is recorded in `docs/adr/ADR-INPUT-001.md`, Accepted and scoped to the
device it was measured on. The **fallback** for a user without Shizuku is not decided, has not been
tested, and is the open question that record leaves behind.
