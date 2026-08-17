# ADR-002: Input Backend Abstraction

## Status

Accepted

## Context

Kestrel's most uncertain technical problem is delivering controller-style input on Android. Possible mechanisms include normal APIs, system-level access, Shizuku, root-assisted mechanisms, virtual devices, and touch fallback.

## Decision

Create an abstraction between controller semantics and Android input implementation.

The controller UI and domain logic must not directly depend on a single backend.

## Rationale

This enables Phase-0 experimentation, device-specific implementations, Shizuku integration, fallbacks, and future backends without rewriting the controller UI.

## Consequences

Positive:
- lower technical risk
- isolated experiments
- clearer compatibility testing

Negative:
- more interfaces
- backend selection complexity
- additional test matrix

The actual production backend is selected by `ADR-INPUT-001` after Phase 0.
