# Kestrel — Project Structure

**Document:** `PROJECT_STRUCTURE.md`  
**Status:** Canonical — folder organization and dependency rules  

## Purpose

This document is the canonical initial folder architecture for the Kestrel repository.

It exists so that:

- human contributors know where code belongs
- AI coding agents have one structural source of truth
- platform-specific code stays isolated
- product features do not become mixed with domain logic
- experimental Android capabilities remain replaceable
- documentation remains easy to discover

This is the **initial clean architecture**, not a requirement to create every empty directory immediately.

Directories should be created when the corresponding implementation begins.

---

# 1. Top-Level Structure

```text
Kestrel/
│
├── app/
├── core/
├── feature/
├── platform/
├── data/
├── community/
├── tools/
├── tests/
│
├── docs/
├── .github/
├── gradle/
│
├── README.md
├── PRD.md
├── ARCHITECTURE.md
├── DEVELOPMENT.md
├── AI_DEVELOPMENT_GUIDE.md
├── CLAUDE.md
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── CHANGELOG.md
├── THIRD_PARTY_LICENSES.md
├── PROJECT_STRUCTURE.md
├── LICENSE
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── .gitignore
```

---

# 2. Root Documentation

The repository root is intentionally kept for documents that every contributor or coding agent should see immediately.


| File                      | Purpose                                             |
| --------------------------- | ----------------------------------------------------- |
| `README.md`               | Project overview, mission, status, high-level usage |
| `PRD.md`                  | Product requirements and scope                      |
| `ARCHITECTURE.md`         | Software architecture and boundaries                |
| `PROJECT_STRUCTURE.md`    | Canonical repository/folder organization            |
| `DEVELOPMENT.md`          | Build, test, and development workflow               |
| `AI_DEVELOPMENT_GUIDE.md` | Rules for AI-assisted implementation                |
| `CLAUDE.md`               | Condensed operating brief for AI coding agents      |
| `CONTRIBUTING.md`         | Contributor workflow and expectations               |
| `SECURITY.md`             | Security policy                                     |
| `CODE_OF_CONDUCT.md`      | Community conduct                                   |
| `CHANGELOG.md`            | User-facing project changes                         |
| `THIRD_PARTY_LICENSES.md` | Dependency/license tracking                         |
| `LICENSE`                 | Full GPLv3 license                                  |

---

# 3. `docs/`

Project knowledge that is too detailed for the root README but is not application source code belongs here.

```text
docs/
│
├── SETUP.md
├── PHASE-0.md
├── COMPATIBILITY.md
├── INPUT_BACKENDS.md
├── CONFIGURATION_SCHEMA.md
│
├── adr/
│   ├── ADR-001-json-first-config.md
│   ├── ADR-002-input-backend-abstraction.md
│   ├── ADR-003-shizuku-optional.md
│   ├── ADR-004-android-10-baseline.md
│   ├── ADR-005-gplv3.md
│   └── ADR-INPUT-001.md
│
├── compatibility/
│   ├── devices/
│   ├── applications/
│   ├── input/
│   └── reports/
│
└── phase0/
    ├── README.md          test procedure for the feasibility harness
    ├── results/           exported evidence
    ├── reports/
    ├── logs/
    └── screenshots/
```

### `docs/SETUP.md`

Toolchain installation and on-device install, for contributors not using the full IDE.

### `docs/PHASE-0.md`

The feasibility specification for input and related system capabilities.

### `docs/COMPATIBILITY.md`

High-level, maintained compatibility matrix.

### `docs/INPUT_BACKENDS.md`

Defines the input abstraction and experimental/production backend categories.

### `docs/CONFIGURATION_SCHEMA.md`

Defines the JSON configuration model.

### `docs/adr/`

Architecture Decision Records.

Use ADRs for decisions that are significant enough that a future contributor or AI agent needs to understand why the project chose a particular approach.

---

# 4. `app/`

`app/` is the Android application assembly layer.

It should contain:

- AndroidManifest
- application startup
- dependency wiring
- top-level navigation setup
- application resources
- final APK configuration

Conceptually:

```text
app/
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/
    │   └── res/
    ├── test/
    └── androidTest/
```

The `app` module should **not become the home for every feature implementation**.

Business/domain logic belongs in the appropriate `core`, `feature`, or `platform` module.

---

# 5. `core/`

`core/` contains platform-independent or mostly platform-independent domain and application contracts.

```text
core/
│
├── common/
├── model/
├── configuration/
├── input/
├── layout/
├── profile/
├── skin/
├── compatibility/
└── diagnostics/
```

---

## `core/common/`

Shared utilities and foundational abstractions that do not belong to a specific feature.

Examples:

- result types
- common errors
- IDs
- small utility abstractions

Do not turn this into a dumping ground.

---

## `core/model/`

Canonical domain models.

Examples:

```text
Controller
ControllerButton
ControllerAxis
ControllerDefinition
ControllerInput
GameApplication
GamingProfile
Layout
Skin
DisplayConfiguration
```

Models should describe the domain rather than Android implementation details.

---

## `core/configuration/`

Configuration contracts and validation logic.

Examples:

```text
ConfigurationDocument
SchemaVersion
ConfigurationValidator
MigrationContract
ImportResult
ExportResult
```

JSON parsing implementation can remain separated where useful.

---

## `core/input/`

This is the **platform-independent input layer**.

It describes:

```text
What is a controller event?
What buttons exist?
What axes exist?
How are analog values represented?
How are input states transformed?
```

It should NOT know how Android actually injects the event.

Example:

```text
core/input/
├── InputBackend.kt
├── ControllerInput.kt
├── GamepadButton.kt
├── GamepadAxis.kt
├── ButtonState.kt
├── AnalogProcessor.kt
└── InputCapabilities.kt
```

---

## `core/layout/`

Layout-domain logic.

Examples:

- layout models
- layout validation
- coordinate calculations
- control placement
- normalization
- default-template rules

The editor UI belongs elsewhere.

---

## `core/profile/`

Profile matching and profile-domain logic.

Examples:

- application/profile matching
- profile selection
- built-in/user profile rules
- profile inheritance/duplication logic

---

## `core/skin/`

Skin-domain models and validation.

The rendering implementation belongs in the feature/UI layer.

---

## `core/compatibility/`

Compatibility-domain models.

Examples:

- compatibility status
- confidence level
- device identity
- target application identity
- backend compatibility
- compatibility record

---

## `core/diagnostics/`

Domain models for diagnostics and reports.

Examples:

```text
BackendDiagnostics
DeviceDiagnostics
SessionDiagnostics
CompatibilityReport
```

---

# 6. `feature/`

`feature/` contains user-facing Kestrel functionality.

```text
feature/
│
├── launcher/
├── gaming-session/
├── controller-editor/
├── skins/
├── settings/
└── community/
```

---

## `feature/launcher/`

Gaming launcher.

Responsibilities:

- display installed gaming applications
- favorites
- recent games
- manual application addition
- profile association
- launching

It should not implement low-level package discovery itself if that belongs in a platform repository.

---

## `feature/gaming-session/`

Orchestrates a gaming session.

Responsibilities:

- choose target application
- resolve profile
- resolve layout/skin
- select input backend
- start/stop session
- react to foreground-app changes

This is where multiple subsystems are coordinated.

---

## `feature/controller-editor/`

User controller layout editor.

Responsibilities:

- duplicate built-in layouts
- rename layouts
- move controls
- resize controls
- configure mappings
- preview
- save
- import/export

The editor does not directly inject input.

---

## `feature/skins/`

Skin browser/editor/application UI.

It consumes skin-domain models and rendering capabilities.

---

## `feature/settings/`

Application settings.

Examples:

- input preferences
- Shizuku status
- diagnostics
- appearance
- community source configuration
- privacy settings

---

## `feature/community/`

Community browser/import interface.

Responsibilities:

- browse repository metadata
- download declarative content
- validate/import
- show authorship/license
- update content

It must not execute arbitrary downloaded code.

---

# 7. `platform/`

`platform/` contains Android-specific implementations.

This is where knowledge of:

- Android APIs
- Android lifecycle
- WindowManager
- PackageManager
- Services
- Shizuku
- device-specific behavior

belongs.

```text
platform/
│
├── android/
├── display/
├── foreground-app/
├── overlay/
├── shizuku/
└── input/
```

Two of these are large enough to get their own sections below: `platform/shizuku/` is §8 and
`platform/input/` is §9. Section numbering in this document is flat, so those sections are still
part of `platform/` despite their top-level numbers.

---

## `platform/android/`

General Android implementations shared by features.

Examples:

- Context providers
- Activity helpers
- Android lifecycle integration
- Android resource integration

---

## `platform/display/`

Display-specific implementation.

Responsibilities may include:

- orientation
- available display metrics
- game-area calculations
- display capabilities
- supported scaling operations

Do not claim the ability to resize arbitrary third-party activities unless that capability has been demonstrated.

---

## `platform/foreground-app/`

Foreground application detection.

Provides the abstraction used by the gaming session without leaking Android implementation details into the domain.

---

## `platform/overlay/`

Android overlay/window implementation.

Responsibilities:

- create/destroy overlay
- controller window
- lifecycle cleanup
- safe-area handling
- permissions

The controller's visual/domain logic stays outside this module.

---

# 8. `platform/shizuku/`

Shizuku-specific capability implementation.

Responsibilities:

- detect Shizuku
- request permission
- determine privilege level
- start/stop UserService
- expose capabilities
- perform narrowly scoped privileged operations

Do not put unrelated Kestrel business logic here.

---

# 9. `platform/input/`

This is where actual Android input implementations live.

```text
platform/input/
│
├── gamepad/
├── shizuku/
└── fallback/
```

---

## `platform/input/gamepad/`

Potential true virtual/gamepad-style backend.

This is experimental until Phase 0 proves a reliable mechanism.

---

## `platform/input/shizuku/`

Input operations that specifically require Shizuku.

This must remain separate from the generic Shizuku platform service so the project can replace the input implementation without redesigning all Shizuku functionality.

---

## `platform/input/fallback/`

Fallback mechanisms such as touch/gesture mapping where appropriate.

This module must remain clearly classified as fallback.

---

# 10. `data/`

`data/` contains configuration assets, schemas, migrations, and packaged data rather than feature behavior.

```text
data/
│
├── builtin/
│   ├── controllers/
│   ├── layouts/
│   ├── skins/
│   ├── profiles/
│   └── presets/
│
├── schema/
│   ├── controller-definition/
│   ├── controller-layout/
│   ├── controller-skin/
│   ├── gaming-profile/
│   ├── application-record/
│   ├── aspect-ratio/
│   └── community-manifest/
│
├── migrations/
│
└── compatibility/
```

---

# 11. Built-In Content

`data/builtin/` contains official Kestrel defaults.

Examples:

```text
data/builtin/layouts/
    xbox-default.json
    playstation-default.json
    nintendo-default.json
```

These must be treated as immutable at runtime.

Users duplicate them before editing.

---

# 12. JSON Schemas

`data/schema/` contains machine-readable schemas once they are formalized.

Example:

```text
data/schema/controller-layout/v1/schema.json
```

This is separate from `docs/CONFIGURATION_SCHEMA.md`.

The documentation explains the design.

The schema files validate actual data.

---

# 13. `data/migrations/`

Contains migration logic or migration resources for older configuration versions.

Example:

```text
data/migrations/
├── layout/
├── profile/
└── skin/
```

Only create a migration when an actual schema version requires one.

---

# 14. `data/compatibility/`

Packaged, reviewed compatibility data that Kestrel ships with.

This is distinct from:

```text
docs/compatibility/
```

The documentation contains human-readable test evidence.

The application data contains machine-readable runtime information.

---

# 15. `community/`

This directory is optional and should not be confused with the application's runtime community downloader.

It can hold repository examples, documentation, or development fixtures.

Suggested:

```text
community/
├── README.md
├── layouts/
├── skins/
├── profiles/
├── manifests/
└── previews/
```

Do not put every downloaded user/community file in the source repository.

---

# 16. `tools/`

Developer tooling that is not part of the application runtime.

```text
tools/
├── schema/
├── compatibility/
├── phase0/
└── development/
```

Examples:

- schema validators
- compatibility-report generators
- Phase-0 experiment helpers
- development scripts

Tools should be deterministic and documented.

---

# 17. `tests/`

Repository-wide test fixtures and integration assets.

```text
tests/
├── fixtures/
├── sample-configurations/
├── compatibility/
└── integration/
```

Use this for test data shared across modules.

Module-specific unit tests should remain with their module.

---

# 18. `.github/`

GitHub-specific project automation and contribution infrastructure.

```text
.github/
│
├── ISSUE_TEMPLATE/
│   ├── bug_report.md
│   ├── compatibility_report.md
│   └── feature_request.md
│
├── PULL_REQUEST_TEMPLATE.md
├── workflows/
├── CODEOWNERS
└── dependabot.yml
```

Only create automation files when the required workflow is actually configured.

---

# 19. `gradle/`

Gradle wrapper files.

Do not manually edit generated wrapper internals unless necessary.

---

# 20. Dependency Direction

The intended dependency direction is:

```text
                 feature
                    ↓
                  core
                    ↑
                    │
                platform
```

More precisely:

```text
Feature
   ↓
Core interfaces/models
   ↑
Platform implementations
```

The important rule is:

**Core must not depend on platform-specific implementation.**

For example:

```text
core/input
```

may define:

```text
InputBackend
```

while:

```text
platform/input/gamepad
```

implements it.

---

# 21. Dependency Rules

### Allowed

```text
feature → core
feature → platform abstractions
platform → core
app → feature/platform/core
```

### Avoid

```text
core → Android UI
core → Shizuku
core → Compose
```

### Never

```text
Controller Composable
    ↓
direct Shizuku Binder call
```

Instead:

```text
Controller UI
    ↓
InputEngine
    ↓
InputBackend
    ↓
Platform implementation
```

---

# 22. Where Code Should Go

Ask this question:

### Is it pure domain logic?

Put it in:

```text
core/
```

### Is it a user-facing feature?

Put it in:

```text
feature/
```

### Is it Android-specific?

Put it in:

```text
platform/
```

### Is it packaged configuration/data?

Put it in:

```text
data/
```

### Is it a development utility?

Put it in:

```text
tools/
```

### Is it documentation/research?

Put it in:

```text
docs/
```

---

# 23. Where Code Should NOT Go

Do not use:

```text
app/src/main/
```

as a dumping ground.

Do not put:

- Shizuku calls
- JSON schema validation
- controller-domain models
- emulator compatibility databases
- complex profile logic

directly into the application module merely because it is convenient.

---

# 24. Initial Gradle Module Philosophy

The folder structure describes logical boundaries first.

The project should not automatically create dozens of Gradle modules on day one.

A practical initial implementation may start with fewer Gradle modules and split them when:

- compilation boundaries are useful
- ownership becomes clear
- tests benefit
- build time justifies it

The **package/domain boundaries are mandatory** even if the first implementation groups some of them physically.

---

# 25. AI Agent Rule

When an AI coding agent is unsure where a new file belongs, it must first classify the file:

```text
Domain
Feature
Platform
Data
Tool
Documentation
Test
```

It should not create a new top-level directory merely because no existing directory appears to fit.

If the architecture truly lacks a needed category, propose an architecture change before introducing one.

---

# 26. Initial Clean Structure vs Future Growth

The repository should begin small.

Do not create every directory shown in this document as an empty folder.

A recommended initial implementation might contain only:

```text
Kestrel/
├── app/
├── core/
├── feature/
├── platform/
├── data/
├── docs/
├── .github/
├── README.md
├── PRD.md
├── ARCHITECTURE.md
├── DEVELOPMENT.md
├── AI_DEVELOPMENT_GUIDE.md
├── CLAUDE.md
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── CHANGELOG.md
├── THIRD_PARTY_LICENSES.md
├── PROJECT_STRUCTURE.md
└── LICENSE
```

Then add subdirectories when real implementation begins.

---

# 27. Phase-0 Exception

Phase 0 prototypes may temporarily live under:

```text
tools/phase0/
```

or a dedicated experimental module.

Experimental code must not be mistaken for production code.

Clearly label:

```text
experimental
prototype
temporary
```

where appropriate.

When Phase 0 completes, the accepted production input approach should be moved behind the proper `platform/input/` abstraction and documented in `ADR-INPUT-001.md`.

---

# 28. Final Architecture Principle

The structure should make this conceptual separation obvious:

```text
WHAT IS KESTREL?
        ↓
core/

WHAT DOES THE USER DO?
        ↓
feature/

HOW DOES ANDROID MAKE IT POSSIBLE?
        ↓
platform/

WHAT DATA DOES IT USE?
        ↓
data/

WHAT DID WE LEARN?
        ↓
docs/

HOW DO WE BUILD IT?
        ↓
DEVELOPMENT.md

HOW SHOULD AI CHANGE IT?
        ↓
AI_DEVELOPMENT_GUIDE.md
```

If a future contributor can answer those questions from the repository structure, the architecture is doing its job.
