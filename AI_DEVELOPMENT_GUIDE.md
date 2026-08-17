# GameDeck Android — AI Development Guide

## Purpose

AI coding tools are a major part of GameDeck development. This guide keeps fast code generation from becoming uncontrolled architecture drift.

The AI is a development assistant, not the source of truth.

The source of truth is:

```text
PRD
Architecture
ADRs
Schemas
Tests
Verified Android behavior
```

## Required reading order

Before implementation, an AI agent should read:

1. `README.md`
2. `PRD.md`
3. `ARCHITECTURE.md`
4. relevant module documentation
5. relevant ADRs
6. `docs/COMPATIBILITY.md`
7. `docs/CONFIGURATION_SCHEMA.md`
8. existing code and tests

## Hierarchy of truth

When information conflicts:

```text
Verified test result
↓
Android/project documentation
↓
Existing architecture
↓
PRD
↓
ADR
↓
Task description
↓
AI assumption
```

If uncertain, say so.

## Never invent APIs

Do not fabricate Android, Compose, Shizuku, Gradle, permissions, or hidden APIs. If an API cannot be verified, propose a small experiment.

## Never fake results

Do not turn “should work” into “tested.” Use explicit states such as `Unverified`, `Experimental`, `Tested`, and `Supported`.

## Task format

A good AI task should specify:

```text
Goal:
Context:
Relevant files:
Requirements:
Constraints:
Do not change:
Tests:
Acceptance criteria:
```

## Smallest reasonable change

Avoid giant rewrites, unrelated formatting, new databases without a requirement, unnecessary dependencies, or silent architecture replacements.

## Inspect before editing

The agent should inspect existing interfaces, tests, call sites, and implementation before changing code.

## Tests are part of implementation

If behavior changes, add or update relevant tests. If tests cannot run, report why.

## Android-specific claims

For Android behavior, identify API level, permissions, lifecycle constraints, OEM risk, public/hidden API status, Shizuku/root requirements, and fallback behavior where relevant.

## Shizuku rules

Never assume:

```text
Shizuku = root
```

Distinguish no Shizuku, ADB/shell, root, and unknown capability states.

## Input rules

Use semantic controller objects rather than Android event codes in UI/domain code.

Preferred:

```text
ControllerButton.A
    ↓
InputBackend
```

not direct Android injection calls from Compose components.

## Configuration rules

Remain JSON-first unless a real requirement demonstrates JSON is insufficient. Any move away from the architecture requires justification and normally an ADR.

## Built-in immutability

Never edit built-in templates in place. Enforce immutability in the repository/domain layer, not only by hiding an Edit button.

## Backward compatibility

Schema changes require versioning/migration where appropriate, tests, and documentation.

## Security rules

Do not generate code that executes downloaded configuration, runs arbitrary community commands, silently escalates privileges, collects credentials, adds hidden telemetry, or bypasses security checks.

See `SECURITY.md`.

## Dependency rules

Before adding a dependency, explain why it is needed, why platform/existing project code is insufficient, its license, Android compatibility, maintenance risk, and likely build impact.

## End-of-task report

Every implementation task should report:

```text
Implemented:
...

Files changed:
...

Tests added:
...

Tests run:
...

Device tests:
...

Known limitations:
...
```

Never invent test results.

## AI as senior development assistant

The AI should challenge impossible requirements, identify Android limitations, suggest simpler solutions, and stop for a feasibility experiment when a fundamental assumption is unproven.

The AI is not the final product or legal authority. The project owner remains responsible for product decisions, prioritization, review, physical-device testing, and release readiness.

## Principle

> **Use AI to increase development speed, not to reduce engineering discipline.**
