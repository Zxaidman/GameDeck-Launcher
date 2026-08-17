# GameDeck Android

> Turn the Android phone you already own into a game-focused handheld experience.

GameDeck Android is an open-source, Android 10+ gaming launcher and virtual-controller project for people who want a unified way to play emulators, game-streaming clients, and cloud-gaming applications without needing a physical controller, telescopic controller, or a separate handheld device.

The goal is simple:

**One place to launch your games, configure your controls, choose your screen layout, customize the appearance, and play.**

GameDeck is intended to bring a software-first handheld experience to ordinary Android phones.

---

## Project Status

**Status: Early architecture / technical feasibility**

GameDeck is not yet a finished application.

The first engineering milestone is a technical feasibility prototype for gamepad-style input on real Android devices. Before a large amount of UI and feature work is built, the project must prove that the intended input architecture can reliably control real target applications.

See:

- [`PRD.md`](PRD.md)
- [`ARCHITECTURE.md`](ARCHITECTURE.md)
- [`docs/PHASE-0.md`](docs/PHASE-0.md)

> **Important:** Some of the project's most important technical goals depend on Android system restrictions, device/OEM behavior, and the capabilities available through Shizuku. GameDeck will document what is actually proven on physical devices rather than promising functionality that has not been verified.

---

# Why GameDeck Exists

Not everyone can justify buying a dedicated handheld, a physical gamepad, or a telescopic controller.

Some people already have a capable Android phone and simply want a better way to use it for gaming.

Others already use:

- emulators
- Steam Link
- Moonlight
- Xbox Cloud Gaming
- GeForce NOW
- other game-streaming applications

but have to configure each application separately and switch between different interfaces.

GameDeck is an attempt to bring those experiences together.

The idea is not to make Android into a general desktop replacement.

The idea is to make an Android phone feel more like a **single-purpose gaming device when you are gaming**.

---

# The Vision

Imagine turning a normal Android phone into something like this:

```text
┌──────────────┬──────────────────────┬──────────────┐
│              │                      │              │
│    LEFT      │                      │     RIGHT    │
│  CONTROLS    │        GAME          │   CONTROLS   │
│              │                      │              │
│   D-Pad      │                      │    A B X Y   │
│   Stick      │                      │    Stick     │
│   Triggers   │                      │    Triggers  │
│              │                      │              │
└──────────────┴──────────────────────┴──────────────┘
```

Or:

```text
┌────────────────────────────┐
│                            │
│            GAME            │
│                            │
│                            │
├────────────────────────────┤
│      VIRTUAL CONTROLLER    │
└────────────────────────────┘
```

The user should be able to choose the arrangement.

The game area may use:

- Fit
- Fill
- Stretch
- Aspect-ratio based layouts
- Future integer scaling
- Custom dimensions

And the controller may be:

- Xbox-style
- PlayStation-style
- Nintendo-style
- Emulator-specific
- Custom

The important part is that **the launcher, game profile, controller configuration, visual skin, and display configuration all live in one ecosystem.**

---

# What GameDeck Is

GameDeck is designed as a gaming environment consisting of several connected systems:

```text
GameDeck
│
├── Gaming Launcher
│
├── Application Profiles
│
├── Controller Engine
│
├── Layout Editor
│
├── Skin / Theme Engine
│
├── Display / Scaling Configuration
│
├── Input Backend System
│
├── Compatibility Registry
│
├── Optional Shizuku Integration
│
└── Community Configuration System
```

The individual systems are intentionally separated so that one experimental Android mechanism does not become a dependency for the entire application.

---

# Initial Target

GameDeck initially targets **Android phones running Android 10 or newer**.

The first release deliberately does **not** target:

- tablets
- foldables
- arbitrary Android applications
- replacing the Android operating system

Those may become future projects once the phone experience is stable.

---

# Supported Application Category

The initial target is gaming software.

### Emulators

Examples include:

- PPSSPP
- Dolphin
- RetroArch
- NetherSX2 and similar emulator applications

### Game Streaming

Examples include:

- Steam Link
- Moonlight
- PS Remote Play and similar clients

### Cloud Gaming

Examples include:

- Xbox Cloud Gaming
- GeForce NOW
- other controller-oriented cloud gaming clients

GameDeck should treat these as examples, not as a promise that every version of every application will work.

Compatibility will be measured and documented.

---

# What GameDeck Is Not

GameDeck is not intended to be:

- a replacement for an emulator
- a ROM distributor
- a BIOS distributor
- a game streaming service
- a cloud gaming provider
- a piracy tool
- a generic Android launcher
- a proprietary controller hardware company
- a closed ecosystem

GameDeck does not provide game files or copyrighted game assets.

Users are responsible for the software, games, ROMs, BIOS files, accounts, and services they use with their device and third-party applications.

---

# The Controller Is the Core

The most important technical requirement is **proper gamepad-style input**.

The long-term goal is not merely to make a button appear on screen and tap a coordinate.

The goal is to make the controller behave as closely as practical to a real game controller.

Conceptually:

```text
Touch
  ↓
GameDeck Controller
  ↓
Input Abstraction
  ↓
Gamepad-style Backend
  ↓
Android Input System
  ↓
Game / Emulator / Streaming Client
```

This is also the hardest part of the project.

Android does not provide a simple general-purpose public API that allows an ordinary application to register itself as a universal Xbox/PlayStation controller.

Shizuku may provide additional system-level access depending on how it is configured, but **Shizuku is not being treated as a magical guarantee of virtual HID/gamepad support**.

That is why the project starts with Phase 0.

---

# Phase 0: Prove the Input Architecture First

Before building the complete product, GameDeck will run an input-feasibility experiment.

The prototype will investigate:

```text
Normal Android application
        ↓
Shizuku + ADB
        ↓
Shizuku + root
        ↓
Other technically appropriate system/input mechanisms
        ↓
Touch/gesture fallback
```

The prototype will test real applications such as:

- PPSSPP
- Dolphin
- RetroArch
- Moonlight
- Steam Link

It will test:

- digital buttons
- D-pad
- analog axes
- triggers
- simultaneous inputs
- hold/release behavior
- lifecycle interruptions
- device recognition
- compatibility
- repeatability

The result must be based on actual device testing.

**The project will not call an input backend "gamepad support" simply because an Android API call succeeded or a screen tap happened.**

See [`docs/PHASE-0.md`](docs/PHASE-0.md).

---

# Shizuku

GameDeck is designed to work in two broad modes.

## Standard Mode

Works without Shizuku where Android permits the necessary functionality.

## Enhanced Mode

Uses Shizuku when available and authorized.

Shizuku is treated as a **capability provider**, not as a permanent requirement.

The application must distinguish between:

- Shizuku unavailable
- Shizuku stopped
- Shizuku available but unauthorized
- Shizuku with ADB/shell privileges
- Shizuku with root privileges

Different devices and privilege levels may expose different capabilities.

The project therefore uses a capability-driven architecture.

More information:

- [`ARCHITECTURE.md`](ARCHITECTURE.md)
- [`docs/PHASE-0.md`](docs/PHASE-0.md)

Official Shizuku documentation:
https://github.com/RikkaApps/Shizuku

---

# One Launcher, One Configuration Space

A major part of the idea is that users should not have to manage separate configuration systems for every game.

A GameDeck profile might look like:

```text
PPSSPP
├── Layout: PSP
├── Skin: Minimal Black
├── Orientation: Landscape
├── Aspect Ratio: 16:9
├── Scaling: Fit
└── Input Backend: Best Available
```

Another:

```text
Dolphin
├── Layout: GameCube
├── Skin: Retro
├── Orientation: Landscape
├── Aspect Ratio: 4:3
├── Scaling: Fit
└── Input Backend: Best Available
```

Another:

```text
Moonlight
├── Layout: Xbox
├── Skin: Glass
├── Orientation: Landscape
├── Aspect Ratio: 16:9
├── Scaling: Fill
└── Input Backend: Best Available
```

The user should be able to launch the application and have the appropriate configuration restored automatically.

---

# Controller Layouts

GameDeck will include default controller templates.

Initial template families:

- Xbox-style
- PlayStation-style
- Nintendo-style
- Generic
- Emulator-specific

Built-in templates are **read-only**.

Users do not edit the original template.

Instead:

```text
Default Xbox
      ↓
Duplicate
      ↓
My Xbox Layout
      ↓
Edit
```

This makes updates to official templates safer and prevents user customization from corrupting the originals.

---

# Fully Editable User Layouts

User-created layouts may eventually configure:

- button positions
- button sizes
- opacity
- shapes
- analog-stick size
- analog-stick dead zones
- sensitivity
- triggers
- D-pad
- shoulder buttons
- stick-click buttons
- labels
- visibility
- mappings
- other controller-specific behavior

The controller system is intended to be data-driven.

The goal is to avoid hard-coding the controller into the UI.

---

# Skins

GameDeck will separate:

**Layout**

from:

**Appearance**

For example:

```text
Xbox Layout
+
Minimal Skin
```

or:

```text
Xbox Layout
+
Neon Skin
```

or:

```text
PS Layout
+
Retro Skin
```

This means users can change the visual appearance without rebuilding their controller configuration.

Future skins may contain:

- button artwork
- stick appearance
- D-pad appearance
- pressed states
- transparency
- borders
- highlights
- backgrounds
- effects

---

# Screen and Aspect Ratio Controls

GameDeck is intended to support a configurable game area.

Initial scaling modes:

- Fit
- Fill
- Stretch

Planned aspect-ratio presets include:

- 4:3
- 16:9
- 18:9
- 19.5:9
- 20:9
- 21:9
- custom ratios in future versions

The project will clearly separate:

1. what GameDeck can visually control itself
2. what Android allows for another application's window
3. what may require elevated system capabilities

The desired user experience is important, but the project will not pretend Android supports something that has not been demonstrated.

---

# Gaming Launcher

The launcher is intentionally gaming-focused.

It should prioritize:

- recently played games
- favorite games
- installed gaming applications
- emulator applications
- streaming applications
- cloud gaming applications

It should not initially become a replacement for the normal Android launcher.

---

# Automatic Detection + Manual Add

GameDeck should detect common gaming applications automatically.

However, automatic detection will never be assumed to be perfect.

The launcher will therefore include:

**Add Application Manually**

This is important for:

- old applications
- unsupported packages
- regional versions
- forks
- modified builds
- applications not included in the compatibility registry

---

# JSON-First Configuration

GameDeck is designed to be JSON-first.

Examples:

```text
layouts/*.json
skins/*.json
profiles/*.json
controllers/*.json
compatibility/*.json
aspect-ratios.json
community manifests/*.json
```

The idea is simple:

> Configuration should be data whenever configuration can replace hard-coded logic.

This makes the project easier to:

- export
- backup
- version
- inspect
- modify
- share
- validate
- migrate
- contribute to

Small local application preferences may use Android-native persistence where appropriate, but JSON remains the canonical portable configuration format.

---

# Community Sharing Without a Proprietary Cloud

GameDeck is not planned to require a private backend just to share configurations.

The initial community system can use GitHub repositories.

A community repository may contain:

```text
community/
├── layouts/
├── skins/
├── profiles/
├── manifests/
└── previews/
```

GameDeck can read a machine-readable index and allow users to discover and import community content.

This gives the project an initial sharing mechanism without requiring:

- user accounts
- a database
- a paid server
- a proprietary cloud platform

The design can later evolve if the project becomes large enough to justify a dedicated service.

---

# Community Content Safety

Community content will initially be declarative.

That means:

- JSON
- images
- metadata

rather than arbitrary executable plugins.

GameDeck must treat downloaded community files as untrusted data.

Community content must be validated before being imported.

---

# Offline-First

The core gaming experience should not depend on the internet.

After configuration is installed, GameDeck should be able to operate locally without needing a server.

Network connectivity may be used for:

- community repositories
- updates
- optional future services

but the core launcher, controller configuration, and local profiles should remain useful offline.

---

# Privacy

GameDeck should be local-first.

The project does not intend to require:

- an account
- mandatory cloud storage
- mandatory telemetry
- mandatory analytics
- mandatory advertising

The application should not quietly collect information simply because it can.

Any future telemetry or analytics proposal should be explicit, documented, optional where practical, and justified by a real project need.

---

# Open Source

GameDeck is licensed under the **GNU General Public License v3.0 (GPLv3)**.

See the [`LICENSE`](LICENSE) file in the repository for the complete legal terms.

The purpose of choosing GPLv3 is to keep the project open for use, study, modification, and redistribution while preserving the freedoms of downstream users. The GNU GPL is a copyleft license designed to keep covered software and modified versions under the same general freedoms. See the official GNU GPLv3 resources for the authoritative legal text and guidance:

https://www.gnu.org/licenses/gpl-3.0.html

---

# Why GPLv3 Instead of a Proprietary License?

GameDeck is intended to remain a community-oriented open-source project.

A major part of the idea is that people should be able to inspect how the software works, improve it, adapt it to their devices, and share improvements under the project's license.

This is especially important for a project involving:

- Android system behavior
- input handling
- device compatibility
- configuration formats
- community-created layouts
- long-term maintenance

The code should not become inaccessible simply because the original author loses interest or stops maintaining it.

---

# Project Philosophy

GameDeck is being built around a simple principle:

> **A useful gaming tool should help people get more out of the hardware they already own.**

The project is not being created primarily as a startup.

It is not being built around a promise that contributors will eventually be rewarded with a company, salary, equity, or commercial ownership.

The initial motivation is much simpler:

**Build something I personally want, make it genuinely useful, and keep it available to other gaming enthusiasts who may want the same thing.**

The project is especially aimed at people who:

- cannot afford a physical controller
- do not want to buy another accessory
- have no room for a controller
- travel frequently
- prefer touch controls
- want one unified gaming interface
- already have an Android phone capable of running their games

That purpose is more important than turning GameDeck into a profit-driven product.

---

# A Transparent Note From the Project Owner

GameDeck is being started by someone who **does not have a formal background in Android software development**.

That is worth stating publicly rather than hiding it.

The project owner currently understands the product vision and requirements well, but is not presenting themselves as a senior Android engineer.

A large part of the implementation will therefore be assisted by coding AI.

That means this repository is intentionally designed to compensate for that limitation through:

- written requirements
- architecture documentation
- small implementation tasks
- test requirements
- explicit technical experiments
- compatibility records
- code review
- documented decisions
- honest acknowledgement of uncertainty

AI-generated code is not considered automatically correct.

A feature is not considered complete simply because an AI agent produced code that compiles.

Real devices, real applications, tests, and reproducible results matter more than confidence.

Contributors are encouraged to challenge incorrect assumptions, identify technical problems, improve implementations, and say clearly when something will not work.

---

# What Contributors Should Expect

Contributors are joining an open-source project, not a guaranteed commercial opportunity.

A contribution does **not** create an entitlement to:

- payment
- ownership of the project
- control of the roadmap
- guaranteed feature acceptance
- commercial revenue
- a future company role
- equity
- employment
- a promise that GameDeck will become a business

The project owner may eventually consider donations, sponsorship, premium services, or other sustainable funding models, but there is **no commitment that GameDeck will become profit-driven**, and financial sustainability will not automatically take priority over the project's original community purpose.

Any significant change in project direction should be discussed openly with the community rather than quietly changing the project's character.

Likewise, contributors should not be expected to work for free in exchange for promises of future profit.

If money ever becomes part of the project, that should be discussed transparently and handled separately from assumptions about unpaid contribution.

---

# What Contributors Are Entitled To

Contributors should still receive the normal respect expected in a serious open-source project.

That includes:

- credit for meaningful contributions where appropriate
- a safe and respectful collaboration environment
- clear technical reasoning
- honest communication
- visibility into major project decisions
- the ability to disagree technically without being treated as disloyal
- recognition that their time has value

Open source does not mean contributors are disposable.

At the same time, open source does not mean every contributor automatically becomes a project maintainer.

Code ownership, maintainer access, roadmap authority, and release authority will be handled explicitly rather than assumed.

---

# Contribution Model

The repository will use a normal open-source contribution process.

The intended workflow is:

```text
Issue / Discussion
        ↓
Understand the problem
        ↓
Proposal or implementation
        ↓
Branch
        ↓
Tests
        ↓
Pull Request
        ↓
Review
        ↓
Revision if needed
        ↓
Merge
```

Detailed contribution instructions belong in:

[`CONTRIBUTING.md`](CONTRIBUTING.md)

GitHub recommends maintaining contribution guidelines separately from the README so contributors can easily find the project's development, issue, pull-request, and community expectations.

---

# Contribution Expectations

Contributors are encouraged to:

- read the architecture before changing core systems
- keep changes focused
- add or update tests
- document Android-version limitations
- avoid assumptions about undocumented APIs
- report compatibility differences between devices
- preserve public interfaces unless a change is justified
- explain trade-offs in pull requests
- distinguish proven behavior from speculation

Especially valuable contributions include:

- Android platform research
- real-device compatibility testing
- input backend experiments
- performance testing
- controller UX improvements
- accessibility considerations
- emulator compatibility
- streaming-client compatibility
- JSON schema improvements
- skins and layouts
- documentation
- bug reports with reproducible steps

---

# AI-Assisted Development

AI coding tools are part of the development process.

That does **not** mean AI-generated code receives special treatment.

The same rules apply:

```text
AI-generated
        ≠
Correct
```

Code must still:

- compile
- pass tests
- follow the architecture
- use real Android APIs
- respect version constraints
- survive review
- work on actual devices where applicable

Contributors are welcome to use AI coding tools as well.

However, contributors remain responsible for understanding and reviewing the changes they submit.

Do not submit code simply because an AI model said it was correct.

---

# Documentation Is Code

For this project, documentation is not an afterthought.

The repository is expected to maintain:

```text
PRD.md
ARCHITECTURE.md
docs/PHASE-0.md
docs/INPUT_BACKENDS.md
docs/COMPATIBILITY.md
docs/CONFIGURATION_SCHEMA.md
CONTRIBUTING.md
SECURITY.md
CHANGELOG.md
```

Architecture changes should be documented.

Experimental results should be documented.

Device-specific limitations should be documented.

If a contributor discovers that something previously believed to be possible is actually impossible on Android, documenting that discovery is a valuable contribution.

---

# Repository Structure

The intended structure is approximately:

```text
GameDeck/
│
├── app/
├── core/
├── feature/
├── input/
├── platform/
├── data/
├── community/
├── docs/
├── tests/
│
├── .github/
│
├── PRD.md
├── ARCHITECTURE.md
├── CONTRIBUTING.md
├── SECURITY.md
├── CODE_OF_CONDUCT.md
├── LICENSE
├── CHANGELOG.md
└── README.md
```

The exact structure may evolve as implementation begins.

Architecture decisions should be documented before major reorganizations.

---

# Current Architecture

The high-level architecture is:

```text
                         GameDeck
                            │
           ┌────────────────┼────────────────┐
           │                │                │
           ▼                ▼                ▼
       Launcher       Gaming Session       Settings
           │                │
           │       ┌────────┼────────┐
           │       │        │        │
           ▼       ▼        ▼        ▼
       Profiles  Layout   Display   Input
                         Engine    Engine
                                      │
                         ┌────────────┼────────────┐
                         ▼            ▼            ▼
                     Gamepad      Shizuku      Fallback
                     Backend      Backend       Backend
```

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the detailed design.

---

# Development Roadmap

## Phase 0 — Input Feasibility

Determine whether the desired controller architecture is technically achievable.

This is the current priority.

---

## Phase 1 — Core Application

Build:

- launcher
- application discovery
- manual application addition
- JSON configuration
- profiles
- basic session management

---

## Phase 2 — Controller Engine

Build:

- controller definitions
- controller rendering
- digital buttons
- D-pad
- analog sticks
- triggers
- input abstraction

---

## Phase 3 — Layout Editor

Build:

- duplicate template
- rename
- move controls
- resize
- configure mappings
- save
- export/import

---

## Phase 4 — Gaming Session

Build:

- launch selected game
- load profile
- activate controller
- detect application transitions
- manage gaming session

---

## Phase 5 — Shizuku

Build:

- Shizuku detection
- authorization
- capability detection
- privileged backend where proven useful
- diagnostics

---

## Phase 6 — Skins

Build:

- built-in skins
- custom skins
- skin import/export
- visual customization

---

## Phase 7 — Community

Build:

- GitHub repository integration
- community layouts
- community skins
- manifests
- compatibility metadata
- checksums and validation

---

# Current Success Criterion

The first meaningful milestone is not a beautiful launcher.

It is this:

```text
Open GameDeck
      ↓
Choose a supported gaming application
      ↓
Launch it
      ↓
Load a GameDeck layout
      ↓
Use the virtual controller
      ↓
Actually play
```

At least one emulator and one streaming application must successfully complete this flow before the project should be considered a functional MVP.

---

# Compatibility Philosophy

GameDeck will not claim universal compatibility without evidence.

Compatibility labels should eventually include:

- Supported
- Supported with Shizuku
- Touch fallback available
- Limited
- Untested
- Unsupported

A device-specific limitation is not treated as a user error.

Android manufacturers modify system behavior, and the same GameDeck version may behave differently across devices.

The project will document those differences rather than hiding them.

---

# Security Philosophy

GameDeck will eventually interact with:

- overlays
- system APIs
- Shizuku
- imported JSON
- downloaded community content

These are security-sensitive areas.

The project must therefore:

- validate imported data
- treat community files as untrusted
- avoid executing downloaded configuration
- keep privileged code minimal
- separate privileged services from normal UI code
- document security assumptions
- provide a security reporting path

See [`SECURITY.md`](SECURITY.md).

---

# Third-Party Software

GameDeck will depend on third-party libraries and services where appropriate.

Every dependency should be reviewed for:

- license compatibility
- maintenance status
- Android compatibility
- security
- unnecessary permissions
- long-term project risk

Third-party software should be documented in the project's third-party notices.

GameDeck's GPLv3 license does not mean every third-party component automatically becomes GPLv3; dependency licensing must be respected individually.

---

# Community Repositories and User Content

GameDeck may eventually support community repositories for:

- layouts
- skins
- profiles
- compatibility data

A community contribution is not automatically an endorsement by the GameDeck project.

Repository content should include appropriate licensing and attribution.

Users should be able to import, export, remove, and manage community content locally.

---

# No Promise of Commercialization

GameDeck currently has no promised:

- paid version
- subscription
- investor-backed roadmap
- company structure
- employee program
- revenue-sharing program

That may change in the distant future, but it is not the purpose of the project today.

The project exists because the underlying problem is worth solving:

> **Can a person with an Android phone get a more coherent handheld gaming experience without needing to buy additional hardware?**

That is the problem GameDeck is trying to solve.

---

# If the Project Becomes Successful

Success should not automatically mean "turn it into a commercial product."

Success may simply mean:

- people use it
- people contribute
- devices become easier to support
- controller layouts improve
- community skins grow
- compatibility improves
- the project survives beyond its original author

A sustainable funding model can be considered separately if the project eventually needs one.

The project's original mission should remain visible even if its infrastructure grows.

---

# Maintainer Philosophy

The maintainer's role is not to pretend to know everything.

The maintainer's role is to:

- preserve the project's purpose
- coordinate the roadmap
- make decisions when decisions are needed
- listen to technical criticism
- protect project quality
- keep documentation honest
- avoid unnecessary commercialization
- recognize contributors
- make the project easier for the next person to understand

Technical expertise from contributors is welcome.

That does not mean contributors should have to accept an artificial hierarchy in which the maintainer always knows more than everyone else.

A good technical correction is more valuable than false agreement.

---

# How to Help

You do not need to write Kotlin to contribute.

Useful contributions can include:

- testing
- bug reports
- Android compatibility research
- emulator testing
- streaming-client testing
- UI feedback
- controller-layout design
- skin design
- documentation
- accessibility feedback
- performance measurements
- reviewing AI-generated code
- improving build/development instructions

Good evidence from a real Android device can be more valuable than a large code contribution.

---

# Development Principles

GameDeck follows these principles:

### Prove difficult assumptions early

Especially anything involving Android system behavior.

### Prefer simple mechanisms

Do not use elevated privileges when normal Android APIs are sufficient.

### Keep privileged code isolated

Shizuku/root-related code should be small and replaceable.

### Make configuration portable

Users should be able to export their work.

### Make compatibility visible

Do not hide device-specific limitations.

### Keep the core project open

Do not make a feature deliberately dependent on a proprietary service unless there is a strong technical reason.

### Avoid unnecessary complexity

Do not build infrastructure simply because a larger commercial product might eventually need it.

### Optimize for actual users

The goal is a useful gaming experience, not an impressive architecture diagram.

---

# Project Documentation

Recommended reading order:

1. [`PRD.md`](PRD.md)
2. [`ARCHITECTURE.md`](ARCHITECTURE.md)
3. [`docs/PHASE-0.md`](docs/PHASE-0.md)
4. [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md)
5. [`CONTRIBUTING.md`](CONTRIBUTING.md)

---

# Building From Source

Build instructions will be expanded as the first working project is established.

Expected baseline:

- Android Studio
- Android SDK
- JDK version compatible with the project's Gradle/Android Gradle Plugin configuration
- Android 10+ test device recommended

Do not assume the current branch is production-ready.

During the early phases, build instructions may change as the architecture is validated.

---

# Testing Philosophy

A feature is not complete because it compiles.

For Android-specific features, testing should ideally happen at three levels:

```text
Unit tests
    ↓
Android/instrumentation tests
    ↓
Real device tests
```

For controller input specifically, real-device testing is essential.

Emulators and desktop test environments cannot prove that a particular Android phone's input stack behaves correctly.

---

# Reporting Bugs

When reporting a bug, include as much reproducible information as possible.

Useful information includes:

- GameDeck version or commit
- Android version
- device manufacturer
- device model
- target application
- target application version
- Shizuku version/state if applicable
- active input backend
- selected profile
- layout
- exact reproduction steps
- logs/diagnostics where safe to share

The goal is not to make users fill out a giant form.

The goal is to make technically useful bug reports possible.

---

# Reporting Security Issues

Please do not publish a sensitive vulnerability immediately as a normal issue if it could expose:

- privileged functionality
- arbitrary code execution
- account data
- imported-content vulnerabilities
- Shizuku/root escalation paths
- serious privacy issues

Use the security reporting process documented in [`SECURITY.md`](SECURITY.md).

---

# License

GameDeck Android is licensed under the:

**GNU General Public License v3.0**

See [`LICENSE`](LICENSE).

Official license:
https://www.gnu.org/licenses/gpl-3.0.html

---

# A Final Note

GameDeck started from a very practical idea:

**A phone can already be a surprisingly capable gaming device. Why should someone have to buy another piece of hardware just to make the experience feel unified?**

The project is an attempt to answer that with software.

It may succeed.

It may hit Android restrictions that require changing parts of the plan.

Some experiments may fail.

Some features may turn out to be impossible without root or device-specific support.

That is okay.

The repository should record those discoveries honestly.

The goal is not to pretend that every idea is possible.

The goal is to keep trying to build the best practical version of the idea that Android actually allows.

If GameDeck eventually becomes a useful tool for other people, that is the measure of success.

---

## Project Status

**Early development**

The current priority is:

**Phase 0 — Input Feasibility**

Start here:

[`docs/PHASE-0.md`](docs/PHASE-0.md)

