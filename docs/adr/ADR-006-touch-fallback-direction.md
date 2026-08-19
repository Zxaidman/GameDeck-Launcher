# ADR-006: Touch Fallback Direction

## Status

**Accepted as direction — implementation deferred.**

This record fixes *what the fallback will be* so that Phase 1 has something concrete to design
against. It does not claim the mechanism works: nothing in it has been tested, and none of it may
be described as anything but an intention until it has been.

## Context

`ADR-INPUT-001` selected a virtual input device with shell privilege as the preferred backend, and
Phase 0 proved it on the reference device. That backend requires Shizuku, and `ADR-003` keeps
Shizuku optional to the product. So a user without it must still get something, and Phase 0 tested
nothing for that user at all.

Two facts constrain what is possible:

- **An ordinary application cannot send input to another application.** The permission that would
  allow it is signature-level, and `Instrumentation` injection is confined to the injecting
  application's own windows. This is reasoned from the platform's documented permission model, not
  measured, and `docs/PHASE-0.md` Tier 2 exists to confirm it.
- **Shizuku is not a one-time grant.** It is a separate application that must be running, and it
  stops on every reboot. No permission an application can hold confers the shell identity or the
  SELinux domain that the virtual-input node requires. There is no way to make the preferred
  backend survive a reboot unattended, and there is no way for Kestrel to keep Shizuku alive —
  it is a different application, and its lifetime is not ours to manage.

The fallback therefore cannot be a lesser version of the preferred backend. It has to work by a
different mechanism entirely.

## Decision

**The fallback backend will simulate touch through an accessibility service, driving Kestrel's own
overlay drawn on top of the target application.**

- **Accessibility service** for gesture dispatch. It is the one public mechanism by which an
  ordinary application can deliver touch outside its own windows, it is granted explicitly by the
  user, and it is visible in system settings for as long as it is enabled.
- **Overlay** for the controls themselves, drawn over the target, so the user sees where the
  controls are rather than memorising invisible regions.
- **`WRITE_SECURE_SETTINGS`**, when Shizuku is available even once, to enable the accessibility
  service without the user navigating the accessibility menu themselves. The permission persists
  across reboots, so a single privileged moment sets up a fallback that then needs no privilege at
  all. Where Shizuku is never available, the user enables the service by hand and the fallback is
  otherwise identical. **Untested.**

### What this fallback is, and is not

It presses a picture of a button on the target's own on-screen controls. That places it at
**Level 1 — touch simulation** in `docs/COMPATIBILITY.md` §10, and at **Grade D** in
`docs/PHASE-0.md` §28. It is not a controller and must never be presented as one.

Expected consequences, all of which need measuring rather than assuming:

- **No device identity.** Target applications will see touches, not a controller. Anything that
  reads controller state — a binding screen, a streaming client forwarding a gamepad — will see
  nothing.
- **No analog sticks or analog triggers.** A gesture has a position, not a magnitude. Sticks and
  triggers degrade to digital regions at best.
- **Only targets with on-screen controls can work at all.** Emulators that draw their own touch
  overlay are candidates. Streaming clients, which forward a controller rather than accepting
  touches as one, largely are not.
- **Latency will be worse**, by an unmeasured amount.

## Consequences

- `platform/input/fallback/` is where this lives, behind the same interface as the preferred
  backend (`ADR-002`). Selection between them is capability-driven, never name-driven.
- The capability difference is user-visible and must be designed for rather than hidden. The rules
  are in `docs/DEGRADED_STATE.md`, and `ADR-007` decides how a layout behaves when controls it
  declares are unavailable.
- Kestrel must never silently substitute this for the preferred backend. A user who thinks they
  have a controller and actually has simulated touches will conclude the product is broken, which
  is worse than being told plainly what is available.
- An accessibility service is a serious permission. `SECURITY.md` applies: it may be used for the
  input this record describes and nothing else, and the reason it is needed must be stated where
  the user grants it.

## What must be measured before this is more than an intention

1. Confirm Tier 2 — that an unprivileged application genuinely cannot reach another application's
   input. If that assumption is wrong, this record is the wrong design.
2. Whether gesture dispatch is fast and reliable enough to be worth shipping.
3. Which of the named target applications can be driven this way, and which cannot.
4. Whether `WRITE_SECURE_SETTINGS` can in fact enable the accessibility service on the reference
   device and firmware.

Until each is answered, `docs/COMPATIBILITY.md` records the fallback as Untested, and no support
status may be claimed from this record.

## Alternatives considered

- **No fallback: require Shizuku.** Rejected — it contradicts `ADR-003` and excludes every user who
  cannot or will not install a second application.
- **Root as the fallback.** Rejected — a smaller audience than Shizuku, not a fallback.
- **A companion application on a computer.** Rejected — the product is a phone that behaves like a
  handheld; requiring a computer defeats it.
