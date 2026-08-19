# ADR-INPUT-001: Production Input Strategy

## Status

**Accepted — scoped to the reference device.** Decided by the project owner on the evidence below.

The scope is part of the decision, not a caveat attached to it: this record names a preferred
backend on the strength of one device, and says so in the decision itself so that nobody can quote
the conclusion without the boundary. See **Scope of validity** and **What reopens this**.

## Context

Kestrel's primary requirement is gamepad-style input. Android does not provide a simple universal public API for an ordinary app to register itself as a physical Xbox/PlayStation-style controller. Shizuku may expose additional capabilities, but capability varies by privilege level and device.

## Decision

**The preferred production input backend is a kernel virtual input device, created through the
platform's own helper with shell privilege, and held for the length of a session by a lease that a
privileged watchdog enforces.**

Concretely, the mechanism this names is the one Phase 0 measured:

1. Shell privilege obtained through Shizuku — `shell`, uid 2000, **not root**.
2. A virtual input device registered through the platform's own `uinput` helper, from a descriptor
   declaring twelve buttons and ten axes.
3. The device held open by a process that outlives any single command, so a session survives the
   user leaving Kestrel — which is the entire point of a controller.
4. That process governed by a **lease**: the application renews a timestamp while a visible
   foreground service runs, and a watchdog in the privileged process destroys the device when
   renewals stop. This is what makes force-stop, cleared data and uninstall end a session without
   the application running any code.
5. Control events written to the device's own stream, with input pausable independently of the
   device staying open.

**This is the preferred backend, not the only one.** ADR-003 stands: the privilege it needs is
optional to the product. A user without Shizuku must still get something, and what that is has not
been decided, because it has not been tested.

### What this decision does not say

- It does not say the input problem is solved.
- It does not say Kestrel works on any device other than the one tested.
- It does not authorise removing the backend abstraction in ADR-002. The abstraction is what makes
  this decision revisable, and this record being Accepted is not a reason to collapse it.

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

## Scope of validity

Everything supporting this decision was measured on one device:

| | |
| --- | --- |
| Device | Xiaomi Redmi Note 13 5G (`2312DRAABI`) |
| Fingerprint | `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys` |
| Android | 15 (API 35) |
| Firmware | HyperOS 3.0.3 |
| Privilege | Shizuku 13, shell (uid 2000), not rooted |
| Targets confirmed | Eden, NetherSX2, RetroArch 1.22.2, PPSSPP, Dolphin, a browser Gamepad API tester, and a Windows streaming host through Artemis and Apollo |

**This decision is valid for that device and firmware.** It is the project's working assumption
elsewhere, and an assumption is not a result. Other manufacturers and Android versions are expected
to be tested as hardware becomes available; until each is, `docs/COMPATIBILITY.md` records them as
Untested, and no reader of this record may upgrade that on the strength of this one.

The specific things that remain unmeasured, stated so they are not mistaken for oversights:

- **Latency.** Never measured, at any point, by any test. The harness timestamps arrival, not the
  interval from intent to delivery, and the streaming test drove input from a schedule rather than
  from a person, so there was nothing to feel.
- **Playing.** Binding screens and a host controller panel prove input arrives. Nobody has played
  anything, so behaviour under load, during scene changes, or over a contended link is unknown.
- **Wireless streaming.** The streaming test ran over a cable.
- **Other OEMs and Android versions.** One of each.
- **Every fallback path.** Nothing has been tested for a user without Shizuku. This is the largest
  gap in the project and it is not addressed by this decision.

## Consequences

- `platform/input/` gains this backend as its first implementation, behind the interface in
  ADR-002. The Phase 0 harness is a measuring instrument and is not that implementation; nothing
  may be promoted from `tools/phase0/` without being rebuilt behind the abstraction
  (`PROJECT_STRUCTURE.md` §27).
- The session model is part of the decision, not an implementation detail: **persistence must be
  governed, not prevented** (`docs/phase0/results/tier5-orphan-report.md` §4a). A backend that
  holds a device without a lease is not an acceptable implementation of this record, because it can
  strand a controller on a user's phone until they reboot.
- Identity must key on the device descriptor, never the numeric id, which changes on every
  registration (`docs/phase0/results/tier5-press-report.md` §3).
- The input layer must de-duplicate on `(deviceId, scanCode)`, tolerate auto-repeat while a control
  is held, and treat an unmatched release as idempotent — all three observed on a physical
  controller as well as a created one, so they are platform behaviour, not artifacts.
- Kestrel's own interface will be driven by the controller Kestrel creates: focus navigation,
  activation, and Back all respond to it. The session UI and overlay must be built for that
  (`docs/phase0/results/tier5-exercise-report.md` §4).
- A fallback backend is now the open question this record leaves behind, and it is the natural
  subject of the next input ADR.

## What reopens this

This record should be revisited, and may need superseding, if any of the following happens:

- A second device or OEM fails to reproduce the mechanism.
- A platform change removes or restricts shell access to the virtual-input facility.
- Measured latency proves unacceptable for real play.
- A fallback path turns out to require a different primary design to remain coherent.

A superseding record keeps this one in place with its status changed, per `CONTRIBUTING.md` §57.

## Possible outcomes considered

- virtual gamepad backend
- Shizuku-assisted backend
- system event backend
- hybrid backend
- gamepad backend with touch fallback
- touch-only fallback if no acceptable system mechanism is technically possible

The outcome chosen is the first: a virtual gamepad backend, obtained with Shizuku-provided shell
privilege, with a fallback still to be decided.

## Rule

The rule this record was written under — *do not mark this ADR accepted until Phase 0 provides
reproducible evidence* — was satisfied before it was accepted. The evidence is in
`docs/phase0/results/`, as machine-readable exports from the device alongside the reports that
interpret them, and the acceptance criteria it was measured against are `docs/PHASE-0.md` §29.

The same rule now applies to everything this decision does not cover. A second device, a latency
figure, and a fallback path each need their own evidence before anything is claimed about them.
