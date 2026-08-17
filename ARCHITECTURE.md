# GameDeck Android — Architecture

**Document:** `ARCHITECTURE.md`  
**Status:** Initial architecture baseline  
**Platform:** Android 10+ phones  
**Language:** Kotlin  
**UI:** Jetpack Compose  
**License:** GPLv3  
**Architecture principle:** Modular, capability-driven, JSON-first, offline-first

---

## 1. Purpose

This document defines how GameDeck should be structured internally.

It does not attempt to describe every Android implementation detail. Instead, it establishes stable boundaries so that:

- AI coding agents can implement individual components safely.
- Future human contributors can understand the project.
- Experimental input technologies can be replaced without rewriting the application.
- Device/OEM-specific behavior remains isolated.
- Configuration remains data-driven.
- Tests can be written against interfaces instead of Android-specific implementations.

The architecture MUST support the possibility that the best input mechanism changes during development.

---

# 2. Core Architecture Principles

## 2.1 Capability over assumption

GameDeck must ask:

> "What capabilities does this device currently provide?"

rather than:

> "Does the user have Shizuku?"

For example:

```text
Shizuku available
        ↓
Query capabilities
        ↓
Can inject input?
Can access required system APIs?
Can observe foreground app?
Can perform required window operation?
```

A Shizuku installation by itself is never treated as proof that a capability exists.

---

## 2.2 Interfaces before implementations

Core modules must depend on interfaces.

Example:

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

The UI must never directly call Shizuku APIs.

The UI communicates with `InputEngine`.

---

## 2.3 JSON-first

GameDeck configuration should remain data-driven.

The following should be JSON whenever practical:

```text
Controller definitions
Layouts
Profiles
Skins
Aspect-ratio presets
Compatibility registry
Community manifests
User mappings
```

Application runtime state may use Android-native persistence mechanisms when necessary, but JSON remains the portable/exportable representation.

---

## 2.4 Immutable built-ins

Built-in templates are read-only.

```text
Built-in template
       ↓
Duplicate
       ↓
User-owned configuration
       ↓
Edit
```

No UI should expose direct modification of built-in configuration.

---

## 2.5 No giant subsystem

Avoid creating one enormous `GameDeckManager`.

Each feature must have a defined responsibility.

---

# 3. High-Level System

```text
                         GameDeck Application
                                  │
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
          ▼                       ▼                       ▼
      Launcher               Gaming Session          Settings
          │                       │
          │                       ├───────────────┐
          │                       │               │
          ▼                       ▼               ▼
   App/Profile Engine      Controller Engine   Display Engine
                                  │
                                  ▼
                            Input Engine
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
              Gamepad Backend  Shizuku      Fallback
                              Backend       Backend(s)
```

---

# 4. Proposed Repository Structure

```text
GameDeck/
│
├── app/
│   └── src/
│
├── core/
│   ├── common/
│   ├── model/
│   ├── input/
│   ├── layout/
│   ├── profile/
│   ├── skin/
│   ├── compatibility/
│   ├── configuration/
│   └── diagnostics/
│
├── feature/
│   ├── launcher/
│   ├── gaming-session/
│   ├── controller-editor/
│   ├── settings/
│   ├── skins/
│   └── community/
│
├── platform/
│   ├── android/
│   ├── overlay/
│   ├── foreground-app/
│   └── display/
│
├── input/
│   ├── api/
│   ├── gamepad/
│   ├── shizuku/
│   └── fallback/
│
├── data/
│   ├── builtin/
│   ├── migrations/
│   └── repositories/
│
├── docs/
│
├── tools/
│
├── tests/
│
└── .github/
    ├── workflows/
    ├── ISSUE_TEMPLATE/
    └── PULL_REQUEST_TEMPLATE.md
```

This is an initial logical structure. The exact Gradle modules may be simplified during Phase 1 if the module boundaries create unnecessary build complexity.

---

# 5. Layered Architecture

## Layer 1 — Presentation

Jetpack Compose.

Responsibilities:

- Screens
- Controller rendering
- Editor UI
- Launcher UI
- Settings
- Diagnostics
- User interaction

Presentation code MUST NOT directly access:

- Shizuku
- Android hidden APIs
- Input injection
- PackageManager implementation details
- File-format internals

---

## Layer 2 — Application/Feature

Responsibilities:

- Gaming session orchestration
- Profile selection
- Layout selection
- Launcher behavior
- Community imports
- Skin management

Example:

```text
GamingSessionCoordinator
```

may orchestrate:

```text
ProfileRepository
LayoutRepository
InputEngine
DisplayController
ForegroundAppMonitor
```

without knowing the implementation details of any one subsystem.

---

## Layer 3 — Domain/Core

Pure Kotlin where possible.

Responsibilities:

- Controller models
- Layout calculations
- Profile matching
- Aspect ratio calculations
- Input state transformations
- Validation
- Configuration schemas

This layer should be highly testable without an Android device.

---

## Layer 4 — Platform

Android-specific implementations.

Responsibilities:

- Overlay windows
- Activity launching
- Package discovery
- Orientation
- Foreground application detection
- Android lifecycle
- Services
- Shizuku integration
- Accessibility fallback where legitimately applicable

---

# 6. Core Domain Model

## Controller

A controller is a logical collection of controls.

```text
ControllerDefinition
 ├── buttons
 ├── axes
 ├── triggers
 ├── dpad
 └── metadata
```

---

## Layout

A layout specifies:

- Which controls are present
- Where controls are placed
- Sizes
- Mapping
- Behavior
- Interaction parameters

A layout does NOT define appearance exclusively.

---

## Skin

A skin defines visual presentation.

```text
Layout
   +
Skin
   ↓
Rendered Controller
```

This separation allows the same layout to use different visual styles.

---

## Profile

A profile connects a target application to GameDeck behavior.

```text
GamingProfile
 ├── package identifier
 ├── layout
 ├── skin
 ├── orientation
 ├── display configuration
 ├── input preference
 └── other options
```

---

# 7. Immutable Template Model

Built-in templates should be tagged:

```json
{
  "id": "builtin.xbox.default",
  "type": "controller-layout",
  "schemaVersion": 1,
  "builtin": true,
  "editable": false
}
```

A user copy:

```json
{
  "id": "user.xbox.racing",
  "type": "controller-layout",
  "schemaVersion": 1,
  "builtin": false,
  "editable": true,
  "sourceTemplate": "builtin.xbox.default"
}
```

The editor MUST reject writes to immutable built-ins.

This should be enforced at the repository/domain layer, not merely by hiding an edit button.

---

# 8. Configuration Architecture

Recommended logical directories:

```text
config/
├── layouts/
├── skins/
├── profiles/
├── controllers/
├── compatibility/
└── presets/
```

The physical storage implementation may differ, but import/export must preserve these logical types.

---

# 9. Configuration Repository

Example:

```kotlin
interface LayoutRepository {
    suspend fun get(id: LayoutId): Layout?
    suspend fun list(): List<LayoutSummary>
    suspend fun save(layout: Layout): SaveResult
    suspend fun duplicate(
        sourceId: LayoutId,
        destinationId: LayoutId
    ): SaveResult
    suspend fun delete(id: LayoutId): DeleteResult
    suspend fun export(id: LayoutId): JsonDocument
    suspend fun import(document: JsonDocument): ImportResult
}
```

Important:

`save()` must refuse modification of immutable built-ins.

---

# 10. Input Architecture

This is the most important subsystem.

```text
Controller UI
      │
      ▼
Controller Event
      │
      ▼
InputEngine
      │
      ▼
Selected InputBackend
      │
      ├── Gamepad backend
      ├── Shizuku backend
      └── Fallback backend
```

---

## 10.1 Input abstraction

The domain representation should use controller semantics rather than Android-specific key codes.

Example:

```kotlin
sealed interface ControllerInput {
    data class Button(
        val button: GamepadButton,
        val state: ButtonState
    ) : ControllerInput

    data class Axis(
        val axis: GamepadAxis,
        val value: Float
    ) : ControllerInput
}
```

This keeps the controller engine independent of the eventual injection method.

---

# 11. Input Capabilities

```kotlin
enum class InputCapability {
    DIGITAL_BUTTONS,
    ANALOG_AXES,
    TRIGGERS,
    MULTI_TOUCH_FALLBACK,
    SYSTEM_INPUT_INJECTION,
    VIRTUAL_DEVICE,
    GAMEPAD_DEVICE_IDENTITY,
    TARGET_APP_COMPATIBILITY
}
```

Backend selection should be capability-based.

Example:

```text
Preferred backend:
GamepadIdentity

Fallback:
SystemInputInjection

Fallback:
TouchMapping
```

The exact ordering will be determined by Phase 0.

---

# 12. Real Gamepad Identity

GameDeck's ultimate target is not merely:

```text
"send a key event"
```

but:

```text
Android sees an input source compatible with game-controller expectations.
```

This distinction is critical.

Android exposes APIs for inspecting actual input devices, including device IDs and input-device listeners.

The architecture must therefore distinguish:

```text
Event Injection
```

from:

```text
Virtual Input Device
```

and from:

```text
Touch Simulation
```

These are different capabilities.

---

# 13. Shizuku Architecture

Shizuku is an optional platform adapter.

```text
GameDeck process
       │
       ▼
ShizukuCapabilityService
       │
       ▼
Shizuku UserService
       │
       ├── shell UID 2000
       └── root UID 0
```

Shizuku's UserService can run Java/native code using the identity of shell or root, and its documentation notes that it has access to non-SDK APIs. It is nevertheless not a normal Android application process, so some ordinary Android APIs do not work normally inside the UserService.

Therefore:

**Never put normal application lifecycle/UI code inside the Shizuku UserService.**

The UserService should be a narrow system-capability adapter.

---

# 14. Shizuku Capability Detection

GameDeck should expose something similar to:

```kotlin
data class PrivilegeState(
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val permissionGranted: Boolean,
    val privilegeLevel: PrivilegeLevel,
    val capabilities: Set<SystemCapability>
)
```

Possible privilege levels:

```text
NONE
ADB_SHELL
ROOT
UNKNOWN
```

The application must not infer `ROOT` merely because Shizuku is installed.

---

# 15. Fallback Input

Touch mapping is a fallback capability, not the definition of GameDeck's controller system.

Android's AccessibilityService can dispatch gestures to the touchscreen, but that is still touchscreen interaction rather than a native virtual gamepad device.

Therefore:

```text
Native/Virtual Gamepad
        ↓
Preferred

System-level event injection
        ↓
Secondary candidate

Touch mapping
        ↓
Fallback
```

The final ordering depends on Phase 0 results.

---

# 16. Accessibility Consideration

AccessibilityService MUST NOT be used merely because it is an easy way to inject taps.

The project must evaluate:

- Technical suitability
- Android behavior
- Device compatibility
- User disclosure
- Distribution-policy implications

The Android documentation explicitly describes accessibility services as specialized assistive tools, not a general-purpose automation mechanism.

Therefore the architecture must allow accessibility to be removed without affecting the rest of the system.

---

# 17. Foreground Application Monitoring

The system needs to know which gaming application is active.

Create an abstraction:

```kotlin
interface ForegroundAppMonitor {
    fun currentPackage(): String?
    fun observe(): Flow<String?>
}
```

Implementations may vary by Android version and permissions.

The profile engine consumes package identities without knowing how they were obtained.

---

# 18. Application Discovery

Create:

```kotlin
interface GameApplicationRepository {
    suspend fun discover(): List<GameApplication>
    suspend fun addManual(packageName: String): Result<GameApplication>
    suspend fun removeManual(packageName: String)
}
```

The discovery engine should use:

- Known package database
- Application metadata
- User additions
- Future compatibility updates

---

# 19. Compatibility Registry

Compatibility information should be data-driven.

Example:

```json
{
  "package": "org.ppsspp.ppsspp",
  "name": "PPSSPP",
  "category": "emulator",
  "recommendedLayout": "builtin.psp.default"
}
```

The registry must support multiple package IDs for:

- Different versions
- Regional versions
- Forks
- Modified distributions

---

# 20. Gaming Session Architecture

```text
Launcher
   │
   ▼
GamingSessionCoordinator
   │
   ├── Resolve application
   ├── Resolve profile
   ├── Resolve layout
   ├── Resolve skin
   ├── Resolve input backend
   ├── Configure display
   └── Launch application
```

The coordinator should expose a simple session state:

```text
IDLE
PREPARING
LAUNCHING
ACTIVE
PAUSED
STOPPING
ERROR
```

---

# 21. Display Architecture

Create an abstraction:

```kotlin
interface GameDisplayController {
    fun setOrientation(...)
    fun setScalingMode(...)
    fun setAspectRatio(...)
    fun setGameArea(...)
}
```

The implementation must distinguish:

### UI composition

What GameDeck can directly control in its own surface.

### External activity/window

What Android permits for the selected target application.

### System-level display manipulation

What may require elevated privileges.

GameDeck must never claim that an external game has been resized merely because GameDeck changed its own layout.

---

# 22. Activity Embedding

Cross-application activity embedding is available on Android 13+ under defined trust/opt-in conditions, not as a universal mechanism for arbitrary third-party apps. Android's documentation explicitly describes cross-UID embedding restrictions.

Therefore the initial Android 10+ architecture MUST NOT depend on cross-app activity embedding.

Potential future implementations can add:

```text
EmbeddedDisplayController
```

as a capability-specific implementation for supported environments.

---

# 23. Overlay Architecture

The controller overlay should be isolated from the core controller engine.

```text
ControllerRenderer
      │
      ▼
OverlayHost
      │
      ▼
Android WindowManager
```

The controller UI should be renderable independently during testing.

This permits:

- Preview mode
- Editor mode
- In-game overlay
- Screenshot tests

---

# 24. Controller Rendering

The renderer receives:

```text
Layout
+
Skin
+
Current controller state
```

and produces Compose UI.

It must not perform input injection itself.

Example:

```text
Button composable
      ↓
ControllerInteractionEvent
      ↓
InputEngine
```

---

# 25. Analog Stick Processing

Analog sticks require a separate processing pipeline.

```text
Touch coordinate
       ↓
Stick geometry
       ↓
Dead zone
       ↓
Normalization
       ↓
Sensitivity
       ↓
Curve
       ↓
Output [-1, +1]
       ↓
InputBackend
```

The processing stage should be pure Kotlin and fully unit-testable.

---

# 26. Input State Machine

Button state must be represented consistently.

```text
UP
 ↓
DOWN
 ↓
HELD
 ↓
UP
```

The engine must correctly handle:

- Press
- Release
- Repeated movement
- Multi-button combinations
- Cancellation
- Overlay disappearance
- Application switching

When a gaming session stops, GameDeck MUST release any active inputs.

---

# 27. Safety State

The input engine should maintain:

```text
activeButtons
activeAxes
activeBackend
sessionId
```

On unexpected termination:

```text
release all buttons
reset all axes
shutdown backend
```

This prevents stuck virtual buttons or axes.

---

# 28. Diagnostics Architecture

Every backend must report capability and errors.

Example:

```kotlin
data class BackendDiagnostics(
    val backendId: String,
    val available: Boolean,
    val capabilities: Set<InputCapability>,
    val reasonUnavailable: String?,
    val lastError: String?
)
```

Diagnostics should be exportable as JSON for bug reports.

---

# 29. Logging

Use structured logs.

Example fields:

```text
timestamp
component
event
backend
package
sessionId
severity
message
```

Avoid logging:

- Personal data
- Account credentials
- Authentication tokens
- Arbitrary screen content

---

# 30. Performance Boundary

The Compose controller renderer and input backend must remain independent.

High-frequency analog updates MUST NOT trigger unnecessary global application recomposition.

Prefer:

```text
Controller input stream
        ↓
Input backend
```

over storing every analog event in Compose application state.

---

# 31. Dependency Rules

Core/domain modules should not depend on:

- Compose
- Android UI
- Shizuku
- specific input implementations

Feature modules may depend on core interfaces.

Platform implementations may depend on Android APIs.

This keeps experimental technology replaceable.

---

# 32. Testing Pyramid

## Pure unit tests

- JSON
- Layout geometry
- Mapping
- Analog processing
- Profile resolution
- Capability selection

## Android instrumentation tests

- Overlay behavior
- lifecycle
- package discovery
- services
- configuration persistence

## Device tests

- Input latency
- Target-app compatibility
- Shizuku
- OEM behavior
- lifecycle interruption

## Manual gameplay tests

Required before calling a backend stable.

---

# 33. Architecture Decision Records

Every significant architecture choice should be recorded under:

```text
docs/adr/
```

Example:

```text
ADR-001-json-first-config.md
ADR-002-input-backend-abstraction.md
ADR-003-shizuku-is-optional.md
ADR-004-gplv3.md
ADR-005-android-10-minimum.md
```

A new contributor or AI agent should be able to determine why a decision was made.

---

# 34. AI Coding-Agent Contract

Every AI coding request should contain:

```text
Goal
Context
Files allowed to change
Requirements
Non-requirements
API constraints
Tests required
Acceptance criteria
```

AI agents MUST:

- Inspect existing code before editing.
- Reuse established interfaces.
- Avoid duplicate implementations.
- Add tests.
- Report uncertain Android behavior.
- Never fabricate an API.
- Never silently change architecture.
- Never delete working functionality without justification.

---

# 35. Architecture Gate

The architecture is considered validated only after Phase 0 establishes at least one practical gamepad-style input path for the initial target applications.

Until then:

```text
Input implementation = experimental
```

not:

```text
Input implementation = finalized
```

This keeps the remainder of GameDeck insulated from the main technical uncertainty.
