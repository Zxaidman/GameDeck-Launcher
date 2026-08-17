# GameDeck Android — Product Requirements Document v1.0

**Document: `PRD.md`**\
**Project status:** Product definition / architecture phase\
**Target platform:** Android phones\
**Minimum Android version:** Android 10 / API 29\
**Technology:** Native Kotlin + Jetpack Compose\
**License:** GPLv3\
**Primary distribution model:** Open source, initially personal-use focused\
**Primary development method:** AI-assisted development with human product direction and testing

---

## 1. Product Vision

GameDeck Android transforms an Android phone into a handheld gaming environment inspired by devices such as Steam Deck, ROG Ally, and other dedicated gaming handhelds.

The application provides a gaming-focused launcher, a permanent customizable virtual controller, application-specific controller profiles, game-screen scaling/aspect-ratio controls, skins/themes, and optional elevated integration through Shizuku.

The product is **not intended to be a general Android launcher**.

The initial target is specifically:

- Android emulators
- Game-streaming applications
- Cloud-gaming applications
- Other applications that behave like controller-oriented gaming software

Support for ordinary Android applications may be considered in a future version but is outside the initial product scope.

---

# 2. Product Goals

### Primary goals

1. Turn an Android phone into a software-based handheld gaming device.
2. Provide a controller-first gaming experience.
3. Generate gamepad-style input whenever technically possible.
4. Provide a fallback input method when true gamepad injection is unavailable.
5. Allow users to create and edit their own controller layouts.
6. Keep official/default layouts protected from accidental modification.
7. Automatically associate controller profiles with supported games and applications.
8. Allow users to manually add applications when automatic detection fails.
9. Provide adjustable game-screen dimensions and aspect ratios.
10. Support Shizuku as an optional enhancement rather than a hard requirement.
11. Remain usable without a network connection.
12. Keep the project open source and contribution-friendly.

### Non-goals for the initial release

The project will not initially attempt to:

- Replace the complete Android operating system.
- Support arbitrary Android applications.
- Implement a complete game-streaming service.
- Provide ROMs, game files, BIOS files, or copyrighted game assets.
- Require root access.
- Build a proprietary cloud backend.
- Implement an online user-account system.
- Replace existing emulators or streaming clients.

---

# 3. Target Users

## Primary user

A person who owns an Android phone and wants to use it as a handheld gaming device without attaching a physical controller.

Typical applications include:

- PPSSPP
- Dolphin
- RetroArch
- NetherSX2 / similar emulators
- Steam Link
- Moonlight
- Xbox Cloud Gaming
- GeForce NOW
- Other controller-compatible streaming clients

## Secondary user

A technical Android enthusiast who wants to customize:

- Controller layouts
- Themes
- Screen scaling
- Game profiles
- Application profiles
- Community-created configurations

---

# 4. Initial Platform Scope

## Supported

Android phones running Android 10 or newer.

The first development phase will intentionally exclude tablets and foldable devices.

After the phone version is stable and tested successfully, tablet and foldable support can be introduced as a separate compatibility phase.

## Why Android 10+

Android 10 corresponds to API level 29 and gives the project a sufficiently modern platform baseline while avoiding the need to support very old Android releases. The Shizuku ecosystem itself supports considerably older versions, but our application will intentionally choose Android 10+ as its product baseline.

---

# 5. Product Modes

GameDeck will have two primary operating modes.

## Mode A — Standard Mode

Designed to work without Shizuku.

The application uses the most capable input method available to a normal application.

Where proper system-level gamepad injection is unavailable, GameDeck may use a fallback input mechanism such as touch/gesture mapping where technically feasible.

The UI must clearly indicate which input backend is currently active.

## Mode B — Enhanced Mode

Uses Shizuku when available and authorized.

Shizuku will be treated as an optional capability provider.

The application must detect:

- Shizuku not installed
- Shizuku installed but stopped
- Shizuku running but permission not granted
- Shizuku running with ADB/shell privileges
- Shizuku running with root privileges

The application must not assume that Shizuku always provides root-equivalent capabilities. Shizuku documentation explicitly distinguishes ADB/shell UID 2000 from root UID 0 and notes that available permissions differ by Android version.

---

# 6. Critical Input Requirement

## Primary requirement

GameDeck should generate **proper gamepad-style input**, rather than merely simulating screen taps, wherever technically possible.

The preferred conceptual pipeline is:

```text
Physical touch
        ↓
GameDeck Controller Engine
        ↓
Input Abstraction Layer
        ↓
Gamepad-style event generation
        ↓
Android input subsystem
        ↓
Target game/emulator/streaming application
```

## Critical technical constraint

The project must not assume that Shizuku alone creates a universally recognized virtual Xbox/PlayStation controller.

A dedicated technical prototype must determine the strongest practical input mechanism available on:

- Standard Android 10+
- Shizuku + ADB
- Shizuku + root
- Non-Shizuku devices

Possible mechanisms to investigate include:

- Supported Android input APIs
- System/Binder interfaces
- Shizuku UserService
- InputManager-related capabilities
- Shell-level input mechanisms
- Virtual HID/uinput approaches where available
- Other legally and technically appropriate mechanisms

Shizuku provides access to system APIs through a privileged server and can run developer code through UserService under shell/root identity, but its capabilities remain dependent on the privilege level and Android version.

### Mandatory engineering rule

**Do not build the entire controller subsystem around a presumed virtual-HID solution until the input feasibility prototype proves it.**

---

# 7. Input Backend Architecture

The application must use an abstraction layer.

Conceptually:

```text
InputEngine
│
├── Native/Gamepad Backend
├── Shizuku Backend
├── Touch Mapping Backend
└── Future Backend(s)
```

The rest of the application must not care which backend is active.

Example:

```text
Controller Button A
        ↓
InputEngine.sendButton(A, DOWN)
```

rather than directly calling a specific Android API throughout the application.

This architecture allows future developers or AI agents to implement additional backends without rewriting the controller UI.

---

# 8. GameDeck Launcher

GameDeck will provide a dedicated gaming launcher.

The home screen displays supported gaming applications rather than the complete Android application list.

Example:

```text
GAMEDECK

Recently Played

PPSSPP
Dolphin
RetroArch
Moonlight
Steam Link
GeForce NOW
```

## Application discovery

GameDeck should automatically identify likely gaming applications using a combination of:

- Installed application metadata
- Known package identifiers
- Supported category metadata where available
- A maintained compatibility registry

## Manual application addition

The launcher must provide:

**Add Game/Application**

This allows the user to select an installed application even when automatic detection fails.

Manual entries should support:

- Application package
- Display name
- Icon
- Preferred profile
- Preferred layout
- Launch behavior

This is particularly important for old, modified, regional, or otherwise unsupported packages.

---

# 9. Gaming Application Profiles

Each gaming application may have a profile.

Example:

```text
PPSSPP
Profile: PSP Default
Layout: PSP
Orientation: Landscape
Screen Ratio: 16:9
Scaling: Fit
```

Another:

```text
Dolphin
Profile: GameCube
Layout: GameCube
Orientation: Landscape
Screen Ratio: 4:3
Scaling: Fit
```

Another:

```text
Moonlight
Profile: Xbox
Layout: Xbox
Orientation: Landscape
Screen Ratio: 16:9
Scaling: Fill
```

Profiles must be stored independently from the built-in default templates.

---

# 10. Controller Layout System

GameDeck will support:

- Xbox-style
- PlayStation-style
- Nintendo-style
- Generic
- Emulator-specific
- Custom layouts

## Important rule

Built-in/default layouts are **read-only**.

A user cannot directly edit the official default template.

Instead:

```text
Default Xbox
     ↓
Duplicate
     ↓
"My Xbox Layout"
     ↓
Edit
```

The duplicate becomes a user-owned editable layout.

This prevents built-in configurations from being accidentally destroyed.

---

# 11. Fully Editable Controller

User-created layouts may contain configurable:

- D-pad
- Analog sticks
- Face buttons
- Shoulder buttons
- Triggers
- Start
- Select / Back
- Guide / Home-style action
- Stick-click buttons
- Touchpad-style controls where supported
- Extra programmable buttons
- Macros, if later technically appropriate
- Visibility
- Opacity
- Size
- Shape
- Position
- Dead zones
- Sensitivity
- Haptic behavior where available

The product must avoid hard-coding a fixed controller structure.

The layout should be data-driven.

---

# 12. Dynamic Controller Space

The size of the controller area will be configurable.

The controller region should adapt according to:

- Screen size
- Orientation
- User configuration
- Selected layout
- Aspect ratio
- Profile
- Device-safe areas

Example landscape:

```text
┌──────────┬──────────────────────┬──────────┐
│          │                      │          │
│  LEFT    │                      │  RIGHT   │
│CONTROL   │        GAME          │ CONTROL  │
│  AREA    │                      │   AREA   │
│          │                      │          │
└──────────┴──────────────────────┴──────────┘
```

Example portrait:

```text
┌────────────────────────┐
│                        │
│          GAME          │
│                        │
├────────────────────────┤
│      CONTROLLER        │
└────────────────────────┘
```

The exact proportions must be configurable rather than fixed.

---

# 13. Game Display Modes

GameDeck will support:

### Fit

Preserve the source aspect ratio and fit the game into the available display region.

### Fill

Preserve aspect ratio while filling the available area. Cropping may occur.

### Stretch

Scale independently in horizontal and vertical dimensions.

### Integer Scale

Reserved for future implementation, particularly useful for retro/emulator content.

---

# 14. Aspect Ratio Profiles

Initial presets:

- 4:3
- 16:9
- 18:9
- 19.5:9
- 20:9
- 21:9

The exact preset list must remain data-driven.

Users should eventually be able to define custom ratios.

The architecture must therefore avoid hard-coding the supported list.

---

# 15. Screen Configuration

Where the Android platform and target application permit, GameDeck should support:

- Game area width
- Game area height
- Aspect ratio
- Scaling mode
- Position
- Alignment
- Margins
- Controller-region dimensions

The project must clearly distinguish between:

**UI-level scaling**

and

**actual Android display/window manipulation**.

The former is expected to be widely implementable; the latter may depend on Android version, manufacturer, permissions, and whether the target activity can be organized/embedded.

Android's official activity-embedding system has host/task ownership restrictions, so GameDeck must not assume arbitrary third-party activities can simply be embedded inside its own Compose layout.

---

# 16. Landscape-First Experience

The initial gaming experience should prioritize landscape orientation.

Portrait support may exist where practical, but landscape is the principal handheld mode.

The primary goal is:

```text
┌──────────────┬──────────────────────┬──────────────┐
│              │                      │              │
│    LEFT      │                      │     RIGHT    │
│  CONTROLS    │        GAME          │   CONTROLS   │
│              │                      │              │
└──────────────┴──────────────────────┴──────────────┘
```

---

# 17. Skins and Themes

The controller system must support visual skins.

A skin can define visual properties of:

- Buttons
- Analog sticks
- D-pad
- Triggers
- Backgrounds
- Labels
- Highlight states
- Press states
- Optional effects

The skin system must be separated from the controller logic.

Therefore:

```text
Layout = What controls exist and where they are

Skin = How those controls look
```

A user should be able to apply a skin to different layouts without modifying the underlying controls.

---

# 18. Skin and Layout Community System

The project should support community sharing in a decentralized manner during the initial phase.

There will be no mandatory GameDeck cloud backend.

GitHub can serve as the initial community distribution mechanism.

Example repository structure:

```text
community/
    layouts/
    skins/
    profiles/
    manifests/
    previews/
    README.md
```

A machine-readable index can describe available content.

Example conceptual structure:

```text
community-index.json
```

Each item can include:

- ID
- Name
- Author
- Version
- Description
- Type
- Compatibility
- Preview
- License
- Download path
- Minimum GameDeck version
- SHA-256/checksum
- Repository revision

This allows GameDeck to download public community content directly from GitHub without requiring a proprietary backend.

---

# 19. Community Security

Community files must never automatically execute arbitrary code.

Community content should initially be limited to declarative data such as:

- JSON
- Images
- Metadata

The application must treat community content as untrusted input.

Files must be:

- Validated
- Schema-checked
- Size-limited
- Parsed safely
- Sanitized before use

Future extensible scripting or executable plugins should be considered a separate security review and are outside the initial scope.

---

# 20. JSON-First Data Architecture

The project will use JSON as the primary configuration/data format wherever practical.

Examples:

```text
layouts/*.json
skins/*.json
profiles/*.json
community manifests/*.json
aspect-ratios.json
controller-definitions.json
compatibility.json
```

The architecture must be data-driven rather than hard-coded.

### Principle

A developer should ideally be able to add:

```text
new controller
new layout
new skin
new aspect ratio
new emulator profile
```

by adding or modifying data rather than rewriting application logic.

## Supporting local storage

JSON remains the canonical configuration format.

Small device/application preferences may use Android DataStore for reliability and platform integration.

More complex databases are **not required for the initial release** unless testing proves that a specific feature genuinely needs one.

This preserves the user's requested JSON-first architecture without artificially limiting the application.

---

# 21. Profile Inheritance

The configuration architecture should support inheritance or duplication conceptually.

Example:

```text
Default Xbox
      ↓
User Xbox - Racing
      ↓
Modify triggers
Modify stick sensitivity
```

Defaults remain immutable.

User copies remain editable.

---

# 22. Versioning

Every configuration format must include a schema version.

Example:

```json
{
  "schemaVersion": 1,
  "type": "controller-layout"
}
```

When schemas change, GameDeck should migrate old configurations where practical.

This is essential for long-term open-source development.

---

# 23. Open Source Strategy

The project will be released under **GNU GPLv3**.

All original GameDeck code should comply with the project's license policy.

Third-party dependencies must be reviewed individually for license compatibility before inclusion.

The Shizuku API/project currently uses Apache 2.0 licensing, which should be recorded correctly in the project's third-party notices rather than having its source treated as GameDeck GPL code.

---

# 24. GitHub Repository Strategy

The repository should be designed for future contributors from the beginning.

Suggested structure:

```text
GameDeck/
│
├── app/
│
├── core/
│   ├── model/
│   ├── input/
│   ├── layout/
│   ├── profile/
│   ├── skin/
│   └── compatibility/
│
├── feature/
│   ├── launcher/
│   ├── controller-editor/
│   ├── settings/
│   └── gaming-session/
│
├── shizuku/
│
├── data/
│
├── community/
│
├── docs/
│
├── tests/
│
├── .github/
│   ├── workflows/
│   ├── ISSUE_TEMPLATE/
│   └── pull_request_template.md
│
├── CONTRIBUTING.md
├── ARCHITECTURE.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── LICENSE
└── README.md
```

The exact Gradle/module structure may change during implementation, but separation of responsibilities is mandatory.

---

# 25. AI-First Development Requirements

Because the primary implementation will be performed through coding AI, the project must be unusually well documented.

Every major module should have:

- Purpose
- Inputs
- Outputs
- Public interfaces
- Dependencies
- Error conditions
- Tests
- Known Android-version limitations

AI coding tasks should be small enough to review independently.

Example:

```text
Task:
Implement ControllerLayoutRepository.

Requirements:
- Load JSON
- Validate schema
- Reject malformed data
- Preserve unknown future fields
- Unit tests
- No UI dependencies
```

This is preferable to asking an AI coding agent to implement an entire subsystem in one instruction.

---

# 26. AI Coding Rules

Coding agents working on the project should follow these principles:

### Never invent Android APIs

The agent must verify API availability and version requirements before using platform APIs.

### Never silently replace a requested architecture

If a requested approach does not work, the agent must explain the limitation and propose an alternative.

### No giant rewrites

Changes should remain focused.

### Tests are part of implementation

A feature is incomplete if its critical behavior is untested.

### Preserve interfaces

Changing a public interface requires documenting the reason.

### Android-version awareness

Every use of an Android API with version-specific behavior must identify its minimum API and fallback behavior.

### No fake implementation

A placeholder must be explicitly marked as a placeholder.

---

# 27. Development Phases

## Phase 0 — Technical Feasibility

This phase comes before building the complete UI.

The key experiment:

**Can GameDeck reliably produce gamepad-compatible input for our target applications under the intended privilege combinations?**

Test environments:

```text
Android 10+
Normal app
Shizuku + ADB
Shizuku + root
```

Target applications:

```text
PPSSPP
Dolphin
RetroArch
Moonlight
Steam Link
```

Success must be measured by whether the target application receives useful controller events—not merely whether an Android API call succeeds.

### Phase 0 outcome

Produce a documented compatibility matrix.

Example:

```text
                    Normal   Shizuku ADB   Root
PPSSPP                 ?          ?          ?
Dolphin                ?          ?          ?
RetroArch              ?          ?          ?
Moonlight              ?          ?          ?
Steam Link             ?          ?          ?
```

No architecture decision should be considered final until this experiment is complete.

---

# 28. Phase 1 — Core Application

Implement:

- Application shell
- Compose navigation
- Launcher
- Gaming application discovery
- Manual application addition
- Configuration system
- JSON schema system
- Profile repository

No advanced controller injection yet.

---

# 29. Phase 2 — Controller Engine

Implement:

- Controller abstraction
- Controller definitions
- Layout rendering
- Touch interaction
- Button states
- Analog sticks
- D-pad
- Triggers
- Haptic feedback where feasible
- Input backend abstraction

---

# 30. Phase 3 — Layout Editor

Implement:

- Duplicate default layout
- Rename
- Move controls
- Resize controls
- Rotate where appropriate
- Change opacity
- Edit mappings
- Save
- Reset
- Preview
- Import/export JSON

---

# 31. Phase 4 — Gaming Session

Implement:

- Launch selected application
- Load profile
- Load controller layout
- Configure orientation
- Configure display mode
- Start gaming session
- Detect session/application changes where possible
- Show controller layer

---

# 32. Phase 5 — Shizuku Integration

Implement:

- Detect Shizuku
- Detect running state
- Request authorization
- Identify ADB vs root
- Start privileged UserService where required
- Expose capability status to the rest of the application
- Use capability-specific implementations

The rest of GameDeck must consume a generic capability interface rather than directly assuming Shizuku is available.

---

# 33. Phase 6 — Skins

Implement:

- Built-in skins
- Skin selector
- JSON skin definitions
- Image assets where necessary
- Skin preview
- Skin import/export

---

# 34. Phase 7 — GitHub Community System

Implement:

- Community repository configuration
- Manifest download
- Validation
- Layout browsing
- Skin browsing
- Download
- Import
- Update
- Version compatibility
- Checksum verification

No user account system is required.

---

# 35. MVP Definition

The first meaningful MVP is considered complete when the user can:

```text
Open GameDeck
      ↓
See gaming applications
      ↓
Select a game
      ↓
Launch it
      ↓
Load a controller profile
      ↓
Display the controller
      ↓
Send functional gamepad-style input
      ↓
Play the game
```

At least one emulator and one streaming application must successfully complete this flow.

---

# 36. MVP Controller Templates

The initial built-in templates should include:

### Xbox-style

- D-pad
- A/B/X/Y
- LB/RB
- LT/RT
- Left stick
- Right stick
- Start
- Back
- Guide/action button where appropriate

### PlayStation-style

- D-pad
- Cross
- Circle
- Square
- Triangle
- L1/R1
- L2/R2
- Analog sticks
- Create/Options-style controls

### Nintendo-style

- Directional controls
- A/B/X/Y
- Shoulder controls
- Triggers
- Analog sticks

These are visual/interaction templates. The project must avoid using proprietary artwork or copyrighted controller imagery without permission.

---

# 37. Compatibility Philosophy

GameDeck must report compatibility honestly.

Do not display:

> "Works with every game."

Instead use capability states such as:

```text
Supported
Supported with Shizuku
Touch fallback available
Limited
Untested
Unsupported
```

This is especially important because Android behavior can vary between OS releases and OEM implementations.

---

# 38. Error Handling

The application must explain failures in user-readable language.

Example:

Instead of:

```text
Binder transaction failed: -8
```

display:

```text
Gamepad input could not be enabled.

Shizuku is running, but this device does not provide
the required input capability.

Current mode:
Touch fallback
```

Technical details can be available under:

**Diagnostics → Technical Details**

---

# 39. Diagnostics System

The application should contain a diagnostics page showing:

- Android version
- Device model
- Manufacturer
- GameDeck version
- Input backend
- Shizuku status
- Shizuku privilege level
- Overlay capability
- Current foreground package
- Selected profile
- Current layout
- Current scaling mode
- Input test result

This will be particularly valuable because the project will initially be developed and tested by AI-assisted development rather than an experienced Android engineer.

---

# 40. Automated Testing

Testing is mandatory.

## Unit tests

For:

- JSON parsing
- Configuration validation
- Layout calculations
- Aspect-ratio calculations
- Profile selection
- App matching
- Controller state transformations

## Integration tests

For:

- Profile loading
- Layout loading
- Shizuku capability detection
- Application discovery
- Configuration migration

## Manual device tests

Required for:

- Controller latency
- Analog behavior
- Orientation
- Overlay stability
- Game launch
- Background/foreground transitions
- Device-specific Android behavior

---

# 41. Performance Requirements

The controller layer must feel immediate.

Important targets:

- Minimal input latency
- Stable frame rate
- No visible controller jitter
- No unnecessary recompositions
- Low battery overhead
- Low memory overhead
- No continuous high-frequency polling where event-driven APIs are possible

Performance measurement will be added after the first functional prototype.

---

# 42. Privacy

GameDeck should operate locally by default.

The application should not require:

- User account
- Cloud storage
- Analytics account
- Telemetry account

Community browsing may contact GitHub or another configured repository.

The user should be able to see and understand network activity.

---

# 43. Data Ownership

All user-created:

- Layouts
- Skins
- Profiles
- Application mappings

belong to the user and should remain exportable.

The user must be able to copy their configuration files out of the application.

No proprietary lock-in should exist.

---

# 44. Future Expansion

Not part of the first release, but architecture should leave room for:

- Tablets
- Foldables
- External physical controllers
- Controller remapping from physical controllers
- Additional Android-native games
- Community repository federation
- Cloud sync
- Optional donation system
- Optional premium functionality
- More advanced window management
- More advanced virtual-input backends
- Dedicated handheld/device integrations

---

# 45. Future Android Game Support

After the emulator + streaming target is stable, GameDeck may expand toward Android games that support standard controllers.

The architecture should therefore use:

```text
Gaming Application
```

rather than:

```text
```

as the generic profile concept.

This keeps the product architecture extensible.

---

# 46. Product Success Criteria

The project is successful when a user can take an ordinary Android phone, install GameDeck, configure a gaming application, and experience it as a handheld device without needing a physical controller.

The first major milestone is not:

> "We have a beautiful launcher."

The first major milestone is:

> **"I can launch a supported game and actually play it using GameDeck's virtual gamepad."**

---

# 47. Remaining Architectural Questions

The major product decisions are now sufficiently defined.

The remaining questions are primarily implementation discoveries rather than product-definition questions.

The most important is:

**Which mechanism provides reliable virtual gamepad input across the target applications and Android versions?**

This must be established through Phase 0.

A second implementation question is how much of the desired “game in the center, controllers on the sides/bottom” experience can be achieved using Android's available windowing/display mechanisms without requiring the application to control the entire Android system. Android activity embedding has restrictions, and the host application is responsible for organizing activities in supported embedding scenarios.

Therefore, the product should distinguish between:

```text
Desired UX
```

and

```text
Guaranteed Android capability
```

until the technical prototype validates each part.

---

# 48. Recommended Repository Documentation

The GitHub repository should contain these documents from the beginning:

```text
README.md
PRD.md
ARCHITECTURE.md
DEVELOPMENT.md
AI_DEVELOPMENT_GUIDE.md
CONTRIBUTING.md
SECURITY.md
COMPATIBILITY.md
INPUT_BACKENDS.md
CONFIGURATION_SCHEMA.md
THIRD_PARTY_LICENSES.md
CHANGELOG.md
LICENSE
```

The PRD defines **what** we are building.

The architecture document defines **how** it is built.

The AI development guide defines **how coding agents must modify the project**.

The compatibility document records **what actually works on real devices**.

This separation will be especially important as other developers eventually join the GitHub project.

---

# 49. Final Technical Direction

The current agreed direction is:

```text
Android 10+
       │
       ▼
Native Kotlin
       │
       ▼
Jetpack Compose
       │
       ├── Gaming Launcher
       ├── Profile System
       ├── Layout Engine
       ├── Skin Engine
       ├── Display/Layout Engine
       ├── Input Abstraction
       │      ├── Gamepad Backend
       │      ├── Shizuku Backend
       │      └── Fallback Backend
       │
       ├── Compatibility Engine
       ├── JSON Configuration System
       └── GitHub Community System
```

The design is **JSON-first, modular, offline-first, open source, and extensible**.

The first development priority is **not the launcher UI**.

It is proving the input architecture.

If the input prototype succeeds, the remainder of the product becomes a conventional Android application engineering problem rather than an unresolved platform experiment.
