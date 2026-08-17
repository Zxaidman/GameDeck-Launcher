# ADR-INPUT-001: Production Input Strategy

## Status

Pending Phase 0

## Context

GameDeck's primary requirement is gamepad-style input. Android does not provide a simple universal public API for an ordinary app to register itself as a physical Xbox/PlayStation-style controller. Shizuku may expose additional capabilities, but capability varies by privilege level and device.

## Decision

No production input backend is selected yet.

Phase 0 must establish which mechanism is viable on real devices and target applications.

## Required evidence

The final decision must consider:

- buttons
- D-pad
- analog axes
- triggers
- simultaneous input
- hold/release reliability
- controller/device identity
- emulator compatibility
- streaming compatibility
- Android-version compatibility
- OEM compatibility
- lifecycle safety
- latency
- repeatability

## Possible outcomes

- virtual gamepad backend
- Shizuku-assisted backend
- system event backend
- hybrid backend
- gamepad backend with touch fallback
- touch-only fallback if no acceptable system mechanism is technically possible

## Rule

Do not mark this ADR accepted until Phase 0 provides reproducible evidence.
