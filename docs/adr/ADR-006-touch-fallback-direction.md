# ADR-006: Touch Fallback Direction

## Status

**Rejected — measured, and rejected on product grounds rather than technical ones.**

The mechanism was built as a probe and measured on the reference device
(`docs/FALLBACK_PROBE.md` §6a). **It works, and it works better than this record predicted.** It is
rejected anyway, and the distinction matters: this is not a record of something that failed, it is
a record of something that succeeded at the wrong thing.

The evidence, the reasoning, and what would reopen the question are in *Outcome* below. The rest of
the record is left as it was written, because the value of it now is showing which of its
predictions the measurements confirmed and which they beat.

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

---

## Outcome

Measured on the reference device — Redmi Note 13 5G, HyperOS 3.0.3, Android 15, Kestrel
`0.0.17-dev`. Every question this record listed under *What must be measured* now has an answer.

### It works

| Measured | Result |
| --- | --- |
| Gesture dispatch reaches a window | **12 of 12 taps landed, 0 cancelled** |
| Latency, Shizuku running | best 3 ms, **median 4 ms**, worst 7 ms |
| Latency, Shizuku not running | best 3 ms, **median 5 ms**, worst 20 ms |
| Drag resolution | **~242 movements a second**, both runs |
| Works with Kestrel's overlay on screen | **Yes** — the target measured against *was* an overlay window |
| `WRITE_SECURE_SETTINGS` granted through the shell | **Yes**, and visible to Kestrel without a restart |
| Kestrel enabling the service itself, Shizuku stopped | **Yes** |

Two of those beat what this record predicted.

- It said *"No analog sticks or analog triggers. A gesture has a position, not a magnitude. Sticks
  and triggers degrade to digital regions at best."* **That was too pessimistic.** A drag delivers
  around 242 movements a second, which is above display refresh — a simulated stick would be smooth,
  not digital.
- It said latency *"will be worse, by an unmeasured amount"*. A median of 4 ms is not worse in any
  way a hand could detect.

One thing it did not anticipate: the platform's **restricted settings** block. On a sideloaded
installation the accessibility toggle is greyed out and both programmatic routes write nothing while
reporting success — which is the failure mode that looks most like working code. The gate is App
info → Allow restricted settings, it is manual, and no permission substitutes for it. **After it is
lifted once**, everything above holds, including Kestrel enabling the service itself with Shizuku
not running.

### Why it is rejected anyway

**The ceiling, which no measurement raises.** An accessibility service dispatches **touches**. It
cannot create a device, and it has no key-injection API — `performGlobalAction` reaches back, home
and recents, and `AccessibilityNodeInfo` actions need accessibility nodes, which a game rendering to
a surface does not have. So the product this enables is not "Kestrel without Shizuku". It is
**Kestrel's controls puppeting the target's own on-screen controls**: a press on our A button
becomes a tap at the coordinates of *their* A button.

That product carries costs this record understated:

- **The target must draw touch controls, keep them visible, and keep them still.** A target
  configured for a controller, or a streaming client, offers nothing to press.
- **Every target needs its own coordinate map**, per layout and per screen size, and something has
  to produce and maintain it. This record said only that such targets are "candidates"; it did not
  price the calibration.
- **A user could already touch those controls directly.** What Kestrel adds is its own positions and
  skin over someone else's buttons — real, but a different and much smaller product than the one
  `ADR-INPUT-001` delivers.

**And a cost paid by everyone, including everyone who never uses it.** Declaring the accessibility
service in the manifest changed how Kestrel installs: `0.0.14-dev` sideloaded with the ordinary
unknown-source warning, and `0.0.15-dev` — which added the service and nothing else relevant — is
**blocked by Play Protect**, requiring the user to switch that protection off. A manifest
declaration is visible at install time whether the code ever runs or not, so the whole audience pays
it for a capability a subset would use. This is exactly the *distribution-policy implication* that
`ARCHITECTURE.md` §16 required to be evaluated, arriving as a measurement.

Weighed together: a working mechanism, delivering a weaker product, with per-target calibration
Kestrel would have to own, paid for by every user's install. **Not worth shipping.**

### What this changes

- **Kestrel is Shizuku-only for input.** `ADR-003` still holds — Shizuku is not required for the
  *application*, only for a session. Without it Kestrel is a launcher, a layout editor and a skin
  manager that says plainly what it cannot do (`docs/DEGRADED_STATE.md`).
- The **Reduced** capability state is removed. Three states remain: Full, Ready, Configure only.
- `platform/input/fallback/` stays empty and reserved. `ADR-002`'s backend interface is unaffected —
  it exists so that a second backend *can* arrive, and none does.
- The probe's code is deleted rather than left dormant. Its design and its numbers are in
  `docs/FALLBACK_PROBE.md`, and the implementation is in this repository's history at
  `0.0.17-dev` if the question is ever reopened.

### What would reopen it

- A target family that draws touch controls at **stable, discoverable** coordinates, making the
  calibration cheap rather than per-user.
- A distribution channel where the accessibility declaration costs nothing at install time.
- A platform capability that delivers controller input without a privileged shell. None exists
  today.

Any of those is a new record, not an amendment to this one.
