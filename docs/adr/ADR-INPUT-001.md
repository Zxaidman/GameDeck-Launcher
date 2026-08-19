# ADR-INPUT-001: Production Input Strategy

## Status

Pending Phase 0

## Context

Kestrel's primary requirement is gamepad-style input. Android does not provide a simple universal public API for an ordinary app to register itself as a physical Xbox/PlayStation-style controller. Shizuku may expose additional capabilities, but capability varies by privilege level and device.

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

## Evidence so far

Phase 0 has produced evidence against most of the list above, on one device — Xiaomi Redmi Note 13
5G, Android 15, HyperOS 3.0.3, unrooted, Shizuku at shell privilege. Recorded in
`docs/phase0/results/`, and summarised here only to say what is still missing.

| Required evidence | State |
| --- | --- |
| buttons | Working |
| D-pad | Working |
| analog axes | Working, and scaled rather than saturated |
| triggers | Working, analog |
| simultaneous input | Working |
| hold/release reliability | Working — every control returned to rest |
| controller/device identity | Working — own device id, own descriptor, player slot 1 |
| emulator compatibility | Three emulators list and auto-map it |
| streaming compatibility | Host sees a controller through Artemis to Apollo, over a cable |
| Android-version compatibility | One version tested |
| **OEM compatibility** | **One OEM tested** |
| lifecycle safety | Session ends on demand, and on force-stop or uninstall within ~15s |
| **latency** | **Unmeasured** |
| **repeatability** | **Partial — one device, no reboot cycle** |

Every acceptance criterion in `docs/PHASE-0.md` §29 is now satisfied on the reference device, so
this record is ready to be decided rather than deferred. The two entries still in bold are not §29
criteria; they are the reasons a decision should state its own scope.

The mechanism that produced these results is a kernel virtual input device created through the
platform's own helper with shell privilege, held for the length of a session by a lease that a
privileged watchdog enforces. It is the candidate this decision would name.

What a decision must not do is generalise past its evidence. Everything here comes from one device,
one firmware and one OEM; latency has never been measured; and **no fallback path has been tested at
all**, so what a user without Shizuku gets remains entirely unknown. Naming a preferred backend is
supported by the evidence. Declaring the input problem solved is not.

Whatever is decided, ADR-003 stands: the privilege this mechanism needs is optional to the product,
so a backend built on it can only ever be the preferred one, never the only one.

## Possible outcomes

- virtual gamepad backend
- Shizuku-assisted backend
- system event backend
- hybrid backend
- gamepad backend with touch fallback
- touch-only fallback if no acceptable system mechanism is technically possible

## Rule

Do not mark this ADR accepted until Phase 0 provides reproducible evidence.
