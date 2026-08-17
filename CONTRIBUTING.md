# Contributing to GameDeck Android

Thank you for considering contributing to GameDeck Android.

GameDeck is an open-source project built around a simple idea:

> Give Android gaming enthusiasts a unified handheld-style gaming experience using the phone they already own, without requiring a physical controller or telescopic accessory.

The project is intentionally community-oriented and is not currently being built as a profit-driven product.

This document explains how the project is developed, what contributors can expect, what is expected from contributors, and how technical decisions are made.

---

## 1. Start Here

Before contributing, please read:

1. [`README.md`](README.md)
2. [`PRD.md`](PRD.md)
3. [`ARCHITECTURE.md`](ARCHITECTURE.md)
4. [`docs/PHASE-0.md`](docs/PHASE-0.md)

These documents answer different questions:

- `README.md` — what GameDeck is and why it exists
- `PRD.md` — what the product is supposed to do
- `ARCHITECTURE.md` — how the software is organized
- `docs/PHASE-0.md` — the first major technical feasibility experiment

GitHub specifically recommends contribution guidelines as a way to make expectations, issue reporting, pull requests, and project conventions clear to contributors. This file exists for that purpose. citeturn979184search0turn979184search1

---

# 2. Project Context

GameDeck is being developed by a project owner who has the product vision but does **not** have a formal professional background in Android software engineering.

That is deliberately stated here because contributors should know who they are working with.

A substantial amount of implementation will be assisted by coding AI.

That does not mean technical standards are lower.

It means the project relies heavily on:

- documented requirements
- architecture boundaries
- small implementation tasks
- automated tests
- real-device testing
- code review
- technical experiments
- honest documentation of uncertainty

AI-generated code is treated exactly like any other code:

**It must be correct, reviewable, testable, and appropriate for the project.**

A contributor who identifies an AI-generated mistake is providing a valuable contribution, not "working against" the project.

---

# 3. Project Mission Comes First

The purpose of GameDeck is not simply to accumulate features.

The core mission is:

> Build an all-in-one gaming interface for Android phones that makes games, emulators, streaming clients, controller configuration, layouts, and visual customization feel like one unified handheld gaming environment.

The project is especially intended for people who:

- cannot afford a physical controller
- cannot justify buying another gaming accessory
- do not want to carry additional hardware
- already own a capable Android phone
- want one consistent controller experience across supported gaming applications

Technical decisions should be evaluated against that mission.

A technically impressive feature that makes the product substantially harder to use may not be the right feature.

---

# 4. Open Source and Project Purpose

GameDeck is licensed under the GNU General Public License v3.0.

See [`LICENSE`](LICENSE).

The project is currently intended to remain open source and community-oriented.

At the present stage:

- there is no promised paid version
- there is no promised subscription
- there is no promised investor model
- there is no promised contributor salary
- there is no promised equity program
- there is no promise that the project will eventually become a company

The project owner may consider donations, sponsorship, premium functionality, or other ways of sustaining development in the future. None of those are currently commitments.

The project's original purpose should not be assumed to be commercial simply because the software is successful.

---

# 5. Important GPLv3 Reality

GameDeck uses GPLv3.

GPLv3 is a software freedom license and allows recipients to use, modify, and redistribute covered software under its terms.

That means the project cannot honestly promise:

> "Nobody will ever make money from GameDeck."

The GPL permits redistribution, including commercial redistribution, as long as the license requirements are respected.

The project's own intent is currently community-first and non-profit-driven, but that intent is not a restriction on the rights granted by GPLv3.

Likewise, contributors should not assume that contributing gives them a right to future revenue.

The license and project governance are separate questions.

---

# 6. Contributor Expectations

Contributors are expected to behave like contributors to a real open-source software project.

Please:

- read the relevant documentation
- understand the issue before implementing it
- keep changes focused
- write tests where appropriate
- document important technical decisions
- avoid guessing about Android APIs
- test Android-specific behavior on real hardware where necessary
- report uncertainty honestly
- explain trade-offs in pull requests
- respect other contributors
- accept technical review in good faith

You are welcome to disagree with a design decision.

A technically supported disagreement is healthy.

---

# 7. What Contributors Are Not Promised

A contribution does not automatically provide:

- project ownership
- maintainer status
- repository write access
- roadmap control
- financial compensation
- equity
- employment
- future revenue
- a guaranteed role in a future company
- a guaranteed feature being accepted
- the right to dictate project direction

These things should never be assumed.

Maintainer privileges are granted based on demonstrated responsibility, technical understanding, reliability, and long-term involvement.

---

# 8. What Contributors Can Expect

The absence of financial promises does not mean contributors should be treated casually.

Contributors should receive:

- respectful communication
- honest technical feedback
- credit for meaningful work
- reasonable explanation when a contribution is rejected
- visibility into major architectural changes
- a place to discuss technical concerns
- protection from harassment or bad-faith behavior

Contributors are giving their time and expertise.

That should be respected.

---

# 9. No Ownership Assumption

GameDeck is currently maintained as an open-source project by its project owner.

Contributing code does not automatically make someone a project co-owner or maintainer.

At the same time, the project owner does not claim that contributors are merely unpaid labor.

Contributors are collaborators whose work can improve the project substantially.

The distinction is:

**Collaboration does not automatically equal ownership.**

If the project's governance changes significantly in the future, that change should be discussed openly.

---

# 10. No Forced Commercial Direction

Contributors should not assume that a successful project automatically has to become a commercial product.

Likewise, the project owner should not force contributors to participate in commercial activities they did not agree to.

If GameDeck ever introduces:

- donations
- sponsorships
- premium features
- paid services
- commercial partnerships

those decisions should be communicated clearly.

The open-source project should not quietly change its character while contributors are already working under one set of expectations.

---

# 11. Getting Started

A normal contribution workflow is:

```text
Read the project documentation
        ↓
Find or discuss an issue
        ↓
Understand the intended behavior
        ↓
Create a fork or development branch
        ↓
Make a focused change
        ↓
Run tests
        ↓
Test on a real device when necessary
        ↓
Commit your changes
        ↓
Open a Pull Request
        ↓
Review / discussion
        ↓
Revision if required
        ↓
Merge
```

This follows the normal GitHub open-source workflow of using a fork/topic branch and submitting changes through a pull request. citeturn979184search1

---

# 12. Before Starting a Large Change

For substantial changes, please open an issue or discussion first.

Examples:

- new input backend
- major launcher redesign
- new configuration architecture
- database introduction
- new community distribution mechanism
- major dependency change
- permission-model change
- privileged Android API integration
- major build-system change

This is particularly important for GameDeck because AI-assisted implementation makes it easy to generate a large amount of code before discovering that the underlying architecture was wrong.

A short design discussion can prevent a large amount of wasted work.

---

# 13. Small Contributions

Small pull requests are welcome.

Examples:

- documentation correction
- typo fix
- test improvement
- compatibility report
- layout improvement
- JSON schema improvement
- small UI bug
- build fix
- Android-version compatibility fix

You do not need to implement an entire subsystem to be useful.

---

# 14. Large Contributions

For a substantial feature, please describe:

### Problem

What problem does this solve?

### Proposed solution

What do you want to change?

### Alternatives

What other approaches were considered?

### Android constraints

What platform restrictions affect the implementation?

### Compatibility

Which Android versions/devices are expected to work?

### Testing

How will the feature be verified?

### Scope

What is explicitly not included?

This can be submitted as an issue or design discussion before implementation.

---

# 15. Coding Style

Use the project's existing style.

For Kotlin:

- prefer clear names
- keep functions focused
- avoid unnecessary abstractions
- prefer immutable data where practical
- document non-obvious platform behavior
- avoid giant classes
- avoid global mutable state
- use interfaces at meaningful boundaries
- keep Android-specific logic out of domain code

Do not introduce a new architectural pattern just because it is fashionable.

The simplest design that satisfies the requirements is preferred.

---

# 16. Android API Rules

GameDeck is Android-specific software, so platform behavior matters.

Before adding an Android API:

1. Verify that the API exists.
2. Verify the minimum Android version.
3. Check whether the behavior differs across Android versions.
4. Determine whether special permission is required.
5. Determine whether OEM behavior may differ.
6. Provide a fallback where appropriate.
7. Document important limitations.

Never invent an Android API because an AI tool suggested it.

---

# 17. Hidden APIs and Shizuku

GameDeck may need system-level functionality that is not normally exposed to ordinary applications.

These experiments must be isolated.

Do not spread Shizuku calls throughout the application.

Prefer:

```text
Feature
   ↓
Capability Interface
   ↓
Shizuku Implementation
```

rather than:

```text
Every Feature
   ↓
Shizuku
```

Shizuku availability is not the same thing as guaranteed capability.

ADB-backed and root-backed environments can provide different levels of access.

---

# 18. Input Development

Input is the highest-risk subsystem.

Do not create a full application-wide dependency on an unproven input mechanism.

The first phase of development is defined in:

[`docs/PHASE-0.md`](docs/PHASE-0.md)

The goal is to determine what input mechanisms actually work on physical Android devices.

Do not label:

- screen taps
- accessibility gestures
- arbitrary key events

as a "true virtual gamepad" unless testing demonstrates controller-style behavior appropriate to the project's requirements.

Document what was actually observed.

---

# 19. Real Device Testing

For Android-specific features, real devices are often more important than desktop tests.

When reporting a compatibility result, include:

- manufacturer
- device model
- Android version
- GameDeck commit/version
- Shizuku state
- relevant permissions
- target application
- target application version
- input backend
- observed behavior
- reproduction steps

A report such as:

> "Doesn't work on my phone"

is much less useful than:

> "Samsung Galaxy X, Android 14, Shizuku ADB, Moonlight version X, digital buttons work but analog axes are not detected."

---

# 20. Tests

A pull request should add or update tests when the behavior is testable.

Examples:

### Unit tests

- JSON parsing
- schema validation
- aspect-ratio calculations
- controller mapping
- analog-stick processing
- profile selection
- configuration migration

### Integration tests

- repository behavior
- profile loading
- configuration import/export
- capability selection

### Device tests

- overlay behavior
- input
- lifecycle
- orientation
- Shizuku behavior
- foreground application changes

---

# 21. Configuration and JSON

GameDeck is intentionally JSON-first.

New configuration types should:

- have a schema
- include a schema version
- have validation
- have predictable IDs
- remain exportable
- support future migration where practical

Do not put behavior that requires arbitrary code execution into a JSON configuration.

Community content should remain declarative.

---

# 22. Built-in Templates

Built-in controller layouts are immutable.

Do not modify a built-in template directly.

The intended workflow is:

```text
Built-in Layout
      ↓
Duplicate
      ↓
User Layout
      ↓
Edit
```

If you believe a built-in template itself is wrong, propose a change to the source template.

This keeps user customization separate from official defaults.

---

# 23. Community Layouts and Skins

Community content may eventually be distributed through GitHub-hosted repositories.

Contributions may include:

- controller layouts
- skins
- application profiles
- compatibility data
- documentation

Community files should be:

- reviewable
- versioned
- clearly licensed
- safe to parse
- free from arbitrary executable code

Do not submit copyrighted artwork, trademarked assets, or third-party material unless you have the right to redistribute it.

---

# 24. Licensing Your Contribution

GameDeck is GPLv3.

Unless an explicit project policy later states otherwise, by submitting code to GameDeck you are granting the project and recipients the rights to use your contribution under the project's GPLv3 licensing terms.

You do **not** automatically transfer your copyright ownership to the project owner simply by making a contribution.

You should only submit material that you have the legal right to contribute.

Do not submit:

- proprietary company code
- code copied from a closed-source project
- source whose license is incompatible
- code generated from material you are not allowed to redistribute
- third-party assets without permission

When in doubt, ask before submitting.

---

# 25. AI-Assisted Contributions

AI tools are allowed.

They are not a substitute for contributor responsibility.

If you use AI to generate code, you are responsible for reviewing the resulting code before submitting it.

You should understand:

- what the code does
- which APIs it uses
- which licenses affect included code
- why the implementation is appropriate
- how it was tested

AI tools may hallucinate:

- Android APIs
- permissions
- framework behavior
- Gradle configuration
- compatibility claims

Do not submit code simply because an AI tool says it is correct.

---

# 26. AI Disclosure

For substantial AI-generated contributions, mentioning AI assistance in the pull request is encouraged.

For example:

```text
AI assistance:
Used an AI coding assistant for initial implementation and test scaffolding.
Reviewed and tested manually on Android 14.
```

This is not intended to stigmatize AI use.

It helps maintainers understand how the code was developed and where additional review may be appropriate.

---

# 27. AI and Copyright

Contributors are responsible for the code they submit.

Do not ask an AI model to reproduce proprietary source code or paste licensed code into a prompt if you do not have permission to use it.

For new code, prefer generating from the project's own requirements and interfaces.

When using a third-party snippet, verify its origin and license.

---

# 28. Commit Messages

Keep commit messages clear and focused.

Prefer:

```text
Add JSON layout schema validation
```

over:

```text
fix stuff
```

A useful commit should answer:

> What changed?

Keep unrelated changes in separate commits when practical.

GitHub's open-source contribution guidance also recommends concise commit titles and keeping changes focused. citeturn979184search1

---

# 29. Branches

For contributions, use a descriptive branch.

Examples:

```text
feature/controller-profile-editor
feature/shizuku-capability-check
fix/layout-import-validation
test/ppsspp-input-report
docs/phase0-results
```

Avoid working directly on the default branch for a substantial change.

---

# 30. Pull Requests

A good pull request should include:

### Summary

What changed?

### Why

What problem does it solve?

### Technical approach

How was it implemented?

### Testing

What was tested?

### Device information

For Android-specific changes:

- device
- Android version
- target application
- Shizuku state if relevant

### Limitations

What does not work?

### Screenshots/video

Useful for UI/controller changes.

---

# 31. Pull Request Checklist

Before submitting:

- [ ] I read the relevant project documentation.
- [ ] I understand the intended behavior.
- [ ] My change is focused.
- [ ] I added/updated tests where appropriate.
- [ ] I tested the application where practical.
- [ ] I tested Android-specific functionality on a real device when required.
- [ ] I did not invent undocumented Android behavior.
- [ ] I checked relevant third-party licenses.
- [ ] I did not include copyrighted assets without permission.
- [ ] I updated documentation if the architecture or behavior changed.
- [ ] I explained important limitations.
- [ ] I reviewed my own diff.
- [ ] I removed debug code and secrets.

---

# 32. Code Review

Review is not a statement that a contributor is wrong or unwelcome.

Review exists to protect the project.

A reviewer may ask for:

- tests
- architecture changes
- documentation
- simplification
- additional device testing
- clearer error handling
- removal of unnecessary dependencies

A pull request may also be rejected if:

- the approach conflicts with the project's goals
- the implementation is too risky
- the feature is out of scope
- the Android behavior is unproven
- maintenance cost is too high
- licensing is unclear
- security concerns are unresolved

A rejected pull request can still be a valuable contribution if it reveals something useful.

---

# 33. Maintainer Decisions

The project owner currently acts as the primary maintainer and product decision-maker.

That role includes responsibility for:

- project direction
- release decisions
- architecture acceptance
- repository permissions
- licensing decisions
- final merge decisions

This does not mean the maintainer is assumed to be the most technically knowledgeable person in every area.

Specialist contributors may know substantially more about:

- Android internals
- input subsystems
- emulator behavior
- networking
- UI
- build systems
- security

Technical expertise is welcome and should influence decisions.

---

# 34. Technical Disagreement

Disagreement is allowed.

Please argue about:

- evidence
- requirements
- platform behavior
- performance
- maintenance cost
- user impact
- security
- licensing

rather than about personalities.

A useful disagreement might look like:

> "This approach requires a hidden API that changed between Android 13 and 14. I tested it on three devices and the behavior is inconsistent. I suggest isolating it behind a capability interface and retaining the existing fallback."

That is exactly the kind of contribution the project needs.

---

# 35. Maintainer Review Standard

The project should prefer:

**proven + understandable + maintainable**

over:

**clever + complicated + barely tested**

A feature that works on one developer's device is not necessarily production-ready.

---

# 36. When a Feature Is Not Merged

A contributor should not interpret rejection as dismissal of their effort.

Possible reasons include:

- wrong project scope
- incomplete evidence
- unsupported Android versions
- maintenance burden
- security concerns
- licensing concerns
- another implementation already exists
- feature belongs in a future phase
- architecture needs further research

Whenever practical, the maintainer should explain the reason.

---

# 37. Experimental Work Is Valuable

Not every contribution needs to become production code.

Examples of useful experimental contributions:

- Android API investigation
- input backend proof-of-concept
- OEM compatibility testing
- performance measurements
- reverse engineering of publicly observable behavior where lawful
- UI prototypes
- benchmark results

A well-documented failed experiment can save the project from months of wrong implementation.

---

# 38. Security Issues

Do not immediately publish sensitive security issues as public issues if doing so could make users vulnerable.

Examples:

- arbitrary code execution
- privileged command execution
- unsafe community content execution
- Shizuku/root security issues
- credential exposure
- malicious configuration parsing
- dangerous update mechanisms

See [`SECURITY.md`](SECURITY.md) for the reporting process.

If `SECURITY.md` does not yet define a private reporting channel, use caution and contact the maintainer before public disclosure.

---

# 39. Code of Conduct

Be respectful.

Do not harass contributors.

Do not use:

- personal attacks
- discrimination
- intimidation
- malicious trolling
- threats
- deliberate disruption

Technical disagreement is allowed.

Personal hostility is not.

GitHub also recommends clear community expectations through contribution guidelines and related community-health files. citeturn979184search4turn979184search5

---

# 40. Contribution Types

Contributions are welcome in many forms.

## Code

Kotlin, Android, Compose, build tooling, testing.

## Testing

Especially valuable for device/OEM compatibility.

## Documentation

Architecture, development setup, troubleshooting, user documentation.

## Controller Layouts

New layouts and improved mappings.

## Skins

Visual customization that can legally be redistributed.

## Compatibility

Reports for:

- emulators
- streaming clients
- cloud-gaming clients
- Android devices

## Research

Android input behavior, Shizuku capabilities, window management, performance, and other technical questions.

---

# 41. Issue Reports

A useful bug report should include:

```text
Environment
-----------
Device:
Android:
GameDeck version/commit:
Shizuku:
Target application:
Target version:

Problem
-------
What happened?

Expected
--------
What should have happened?

Steps
-----
1.
2.
3.

Logs
----
Relevant logs/diagnostics
```

Do not include:

- passwords
- authentication tokens
- personal private information
- private account data

---

# 42. Feature Requests

Feature requests should explain the user problem before proposing a solution.

Prefer:

> "I want an easy way to switch between two controller profiles for the same emulator."

over:

> "Add a ProfileManagerV2 singleton with SQLite."

The project may choose a different technical solution.

---

# 43. Roadmap and Scope

Not every good feature belongs in the current release.

The current priority is:

```text
Phase 0
Input feasibility
        ↓
Core application
        ↓
Controller engine
        ↓
Gaming session
        ↓
Shizuku integration
        ↓
Skins
        ↓
Community system
```

See [`PRD.md`](PRD.md).

Please avoid pulling future-phase features into the current implementation unless they solve a current architectural need.

---

# 44. Dependency Changes

Adding a library is not free.

Before introducing a dependency, consider:

- What problem does it solve?
- Can the existing platform solve it?
- Does it increase APK size?
- Does it increase permissions?
- Is it actively maintained?
- Is its license compatible?
- Does it support Android 10+?
- Can it be removed later?

For small functionality, prefer the Android/Kotlin standard library and existing project infrastructure when reasonable.

---

# 45. Third-Party Assets

Do not add:

- copyrighted controller artwork
- trademarked logos
- leaked game assets
- ROMs
- BIOS files
- proprietary UI assets
- unlicensed fonts

unless the project has appropriate rights.

A visual design can be inspired by familiar controller conventions without copying proprietary artwork.

---

# 46. Community Repository Contributions

Community layouts/skins may eventually live in a dedicated repository rather than the core code repository.

When contributing community content:

- include metadata
- specify compatibility
- include a license
- do not include copyrighted assets without permission
- avoid arbitrary scripts
- provide previews where appropriate
- test imported content

---

# 47. No Vendor Lock-In

Contributions should not unnecessarily force GameDeck to depend on:

- a proprietary cloud provider
- a paid API
- a closed SDK
- an online account

unless there is a strong technical justification.

The project is intentionally offline-first.

---

# 48. Financial Contributions

If the project eventually accepts:

- donations
- sponsorships
- grants
- crowdfunding

those financial mechanisms do not automatically give donors or sponsors:

- ownership
- roadmap control
- merge authority
- special technical privileges
- guaranteed feature delivery

Financial support and project governance should remain separate unless an explicit agreement says otherwise.

---

# 49. Commercial Contributions

A company or individual may contribute code under the GPLv3 project terms where legally appropriate.

The project is not anti-commercial.

The distinction is:

> GameDeck's purpose is community-first, but GPLv3 software can be used commercially by others.

A contributor who wants to build a commercial service around GameDeck should review the GPLv3 obligations independently and seek legal advice when appropriate.

---

# 50. Contributor Recognition

Meaningful contributors may be credited in:

- release notes
- changelogs
- contributor documentation
- Git history
- project acknowledgements

Credit is not a substitute for compensation, nor does credit create ownership.

It is recognition of work.

---

# 51. Maintainer Access

Repository write/maintainer access should be earned through demonstrated:

- reliability
- technical judgment
- respectful communication
- understanding of project architecture
- security awareness
- long-term contribution

Not simply through:

- number of commits
- popularity
- financial support
- personal friendship

---

# 52. Long-Term Governance

The project may eventually need additional maintainers.

If that happens, responsibilities should be documented clearly.

Potential roles might include:

- maintainer
- release maintainer
- Android platform specialist
- input subsystem maintainer
- UI/UX maintainer
- community/content maintainer
- documentation maintainer

Roles should be based on actual responsibilities, not titles alone.

---

# 53. If the Original Maintainer Becomes Inactive

The project should eventually document a succession policy.

The goal is to prevent the codebase from becoming unusable because the original creator disappears.

The GPLv3 license is intentionally compatible with continued independent development and redistribution.

Future governance documents should define how trusted maintainers can preserve the project in the event of prolonged inactivity.

---

# 54. AI-Assisted Repository Maintenance

Because AI is part of the development process, maintainers should keep AI-generated changes reviewable.

Prefer:

```text
small task
   ↓
small diff
   ↓
tests
   ↓
review
```

over:

```text
"Build the whole application"
   ↓
thousands of lines
   ↓
unknown behavior
```

Every AI development task should ideally define:

- goal
- relevant files
- constraints
- expected behavior
- tests
- acceptance criteria

---

# 55. Suggested AI Task Format

```text
Task:
Implement LayoutRepository.save()

Context:
Layouts are JSON-backed configurations.

Requirements:
- Built-in layouts are immutable.
- User layouts may be overwritten.
- Validate schemaVersion.
- Preserve unknown fields.
- Return a typed error for invalid JSON.

Files:
- core/layout/*
- core/configuration/*

Do not:
- introduce a database
- modify UI
- add new dependencies

Tests:
- built-in layout cannot be overwritten
- valid user layout saves
- invalid schema fails
```

This style makes AI-assisted development easier to review.

---

# 56. Documentation Changes

When behavior changes, update the relevant documentation.

Examples:

Architecture change:

```text
ARCHITECTURE.md
```

Feature behavior:

```text
PRD.md
```

New compatibility result:

```text
docs/COMPATIBILITY.md
```

Input implementation:

```text
docs/INPUT_BACKENDS.md
```

Configuration change:

```text
docs/CONFIGURATION_SCHEMA.md
```

---

# 57. Architecture Decision Records

Major technical decisions should use an ADR under:

```text
docs/adr/
```

Example:

```text
ADR-001-json-first-config.md
ADR-002-input-backend-abstraction.md
ADR-003-shizuku-capability-model.md
ADR-004-android-10-baseline.md
```

An ADR should explain:

- context
- decision
- alternatives
- consequences

This is particularly important when the implementation is being developed with AI assistance and multiple people may later work on the repository.

---

# 58. Definition of Done

A feature is not done merely because it compiles.

A feature is considered done when applicable:

- requirements are implemented
- code follows project architecture
- tests exist
- tests pass
- relevant device testing is complete
- errors are handled
- documentation is updated
- known limitations are documented
- no unnecessary dependency was added
- no secrets/debug code remain
- pull request review is complete

---

# 59. Final Principle

GameDeck exists to solve a practical problem.

Please optimize your contributions for:

**Useful**

**Reliable**

**Understandable**

**Maintainable**

**Open**

rather than:

**Large**

**Fancy**

**Commercial**

**Over-engineered**

A smaller feature that genuinely helps someone play a game is more valuable than a complicated feature that only looks impressive in a README.

---

# 60. Thank You

Whether you contribute code, testing, research, documentation, layouts, skins, compatibility reports, or simply a carefully written bug report, your work can make GameDeck better for people who want a unified gaming experience from the Android hardware they already own.

Thank you for helping build it.
