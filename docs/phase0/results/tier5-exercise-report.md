# Phase 0 — Every Control Delivered Through a Created Controller

**Document:** `docs/phase0/results/tier5-exercise-report.md`  
**Status:** Grade A mechanism complete on one device — target applications and repeatability remain  
**Evidence:** `docs/phase0/results/tier5-exercise-20260818-redmi-note-13-5g.json` (harness 0.0.11)  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  
**Privilege:** Shizuku running, permission granted, identity `shell (uid 2000)` — **not root**  

---

## 1. The result

Eight stages were written to a controller created by this project. All eight produced input, every
event attributed to `dev=22` — that device's own id.

| Stage | Written | Delivered |
| --- | --- | --- |
| Left stick full right | `ABS_X = 32767` | `MOTION X=1.000` |
| Left stick half left | `ABS_X = -16384` | `MOTION X=-0.500` |
| Left stick full up | `ABS_Y = -32768` | `MOTION Y=-1.000` |
| Right stick diagonal | `ABS_Z = 32767`, `ABS_RZ = -32768` | `MOTION Z=1.000  RZ=-1.000` |
| Both triggers full | `ABS_GAS = 255`, `ABS_BRAKE = 255` | `MOTION LTRIGGER=1.000 RTRIGGER=1.000 BRAKE=1.000 GAS=1.000` |
| Right trigger half | `ABS_GAS = 128` | `MOTION RTRIGGER=0.502  GAS=0.502` |
| D-pad right and down | `ABS_HAT0X = 1`, `ABS_HAT0Y = 1` | `MOTION HAT_X=1.000  HAT_Y=1.000` |
| A, B and Y together | three `EV_KEY` presses in one report | three `DOWN` events before any `UP` |

Every stage returned to rest, and every rest was delivered: `MOTION (all axes at rest)` after each.
Nothing was left held.

**The half-deflection stages are the ones that matter most.** `-16384` of a declared `-32768` range
arrived as `-0.500`, and `128` of `255` arrived as `0.502`. The value is being scaled through the
whole path, not saturating at a limit. This is a continuous analog axis, not a switch reported as
one.

Against `docs/PHASE-0.md` §29, the mechanism now satisfies:

| Criterion | Status |
| --- | --- |
| Digital — A/B/X/Y and D-pad | **met** — A, B, Y delivered; D-pad delivered as hat axes and keys |
| Analog — at least one stick works continuously | **met** — both sticks, full and half deflection |
| Triggers | **met** — both, full and half |
| Simultaneous — two independent controls at once | **met** — three buttons, and two stick axes together |
| Hold/release — no stuck inputs | **met** — every control returned to rest and the rest was delivered |
| Lifecycle — state can be reset | **met** — device destroyed on demand, no residue |
| Repeatability | **not met** — one exercise run |
| One emulator and one streaming client | **not met** — no target application has seen it |

Six of eight. The two that remain are the two that need something other than the harness.

## 2. What the platform adds on top of what was written

Every one of these was already recorded from the **physical** controller in Tier 1. Seeing them
again from a created device confirms they are platform behaviour applied to any controller, not
artifacts of how this device is made — which is what makes them safe to build on.

**A held stick synthesises D-pad keys, with auto-repeat.** `ABS_X = 32767` held for one second
produced `KEYCODE_DPAD_RIGHT` at `repeat=1` through `repeat=13`, and a single `UP` when the stick
recentred. Thirteen key events from one axis write. The input layer must not treat a synthesised
D-pad key from a device that also reports stick axes as a separate control, or every stick
deflection becomes a stream of phantom presses.

**Each trigger reports on two axes.** Writing `ABS_GAS` alone produced both `RTRIGGER` and `GAS`
at the same value; `ABS_BRAKE` produced both `LTRIGGER` and `BRAKE`. One physical control, two axis
names, same value. Reading both and summing would double every trigger.

**Buttons with a system meaning are delivered twice.** On the created device the pairs are:

| Button | Also delivered as |
| --- | --- |
| `BUTTON_A` (scan 304) | `KEYCODE_DPAD_CENTER` |
| `BUTTON_B` (scan 305) | `KEYCODE_BACK` |
| `BUTTON_Y` (scan 308) | `KEYCODE_SPACE` |

Same device id, same scan code, one physical press. De-duplicate on `(deviceId, scanCode)`.

**The hat produces both.** `ABS_HAT0X/Y` arrived as motion axes *and* as `DPAD_RIGHT` / `DPAD_DOWN`
key events. A layout that binds both will fire twice for one press.

## 3. Ordering is looser than it looks

Two things in this log break the assumption that events arrive in tidy pairs:

- `BUTTON_Y` arrived `DOWN`, then `DOWN repeat=1`, `DOWN repeat=2` while still held, with its
  duplicate `SPACE` going `DOWN` then `UP` in between.
- The three-button stage interleaved: `A DOWN`, `DPAD_CENTER DOWN`, `B DOWN`, `BACK DOWN`,
  `Y DOWN`, `SPACE DOWN` — the duplicates woven through the real presses.

Button state must be tracked per `(deviceId, scanCode)`, repeats must be recognised as
continuation rather than new presses, and an unmatched release must be idempotent. A state machine
expecting strict down-then-up will desynchronise on real hardware, not just on this one.

## 4. The instrument was operated by the input it was measuring

The most useful finding of the run was not planned.

Partway through, the file picker opened on its own and the activity paused mid-measurement
(`harness paused` at entry 0063, `harness resumed` at 0069). Nothing was touched.

The cause is in the log. The stick's synthesised `DPAD_RIGHT` and `DPAD_DOWN` keys walked focus
across the harness's own buttons; then `BUTTON_A`'s duplicate `DPAD_CENTER` — and `BUTTON_Y`'s
duplicate `SPACE` — activated whatever had focus. The focused control was **Save…**, so the created
controller pressed a button in the application that created it.

Two consequences, and the second is the important one:

**For the harness.** Every control is now locked while a test runs, and Back is held for the same
window, so a run cannot be interrupted or ended by its own stimulus. Events are still recorded
before the guard acts — dispatch sees everything; only the activity's reaction is suppressed.

**For the product.** Kestrel will create a controller and then display its own interface in front
of the user. That controller drives Kestrel's interface too: focus navigation, activation, and
Back all respond to it, because the platform applies them to any device reporting these sources.
An overlay or session UI that ignores this will have its buttons pressed by the controller it is
providing — and `BUTTON_B` reaching an unguarded activity means Back, which means leaving the
session. This is a design requirement for `feature/gaming-session` and the overlay, not a harness
quirk, and it belongs in the input layer's rules before any of that is written.

## 5. A measurement fault, found and fixed

The stage markers in this export are all bunched at the end of the log, after the events they were
supposed to introduce.

The cause was two clocks. The harness handed the whole run to the shell as one scheduled command —
descriptor, sleeps, stages — and then ran a *matching* schedule of its own to write the markers.
Nothing tied them together, and on this run they drifted roughly twenty seconds apart.

The evidence survived only because the events describe themselves: `X=-0.500` is unambiguous
whatever label sits near it. Had a stage produced nothing, the labels would have pointed at the
wrong gap and the conclusion would have been wrong.

Fixed in harness 0.0.12 by removing the second clock. The device is now opened on a named pipe held
open by a sleeping process, and each stage is written by the same thread that writes its marker,
immediately after it. A marker cannot drift from its events because the write happens after it.
That is also the shape a production backend needs: a device that outlives any single command, with
input pushed as it happens rather than scheduled in advance.

Recorded here rather than quietly corrected, because the fault was in the instrument and anyone
reading this export needs to know the markers in it are unreliable.

## 6. What remains

1. **A target application.** `docs/PHASE-0.md` §29 requires one emulator and one streaming client.
   Harness 0.0.12 adds a hold mode that keeps the device open and cycles controls slowly, so a
   binding screen can be reached and watched.
2. **Repeatability.** Across reboots and Shizuku restarts, not consecutive runs in one session.
3. **Latency.** Unmeasured, and not measurable with this instrument.

`ADR-INPUT-001` stays **Pending** until 1 and 2 are done.
