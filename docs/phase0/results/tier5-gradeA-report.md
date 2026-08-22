# Phase 0 — A Created Controller Matches a Real One

**Document:** `docs/phase0/results/tier5-gradeA-report.md`  
**Status:** Milestone — device identity achieved; event delivery through it still untested  
**Evidence:** `docs/phase0/results/tier5-gradeA-20260817-redmi-note-13-5g.json`  

---

## 1. Side by side

The left column is the physical controller recorded in the Tier 1 calibration. The right column is
the device Kestrel created, on the same phone, with no computer attached and no root.

| Property | Real controller | Kestrel-created | |
| --- | --- | --- | --- |
| Sources | `KEYBOARD\|GAMEPAD\|JOYSTICK` | `KEYBOARD\|GAMEPAD\|JOYSTICK` | identical |
| Raw source flags | `16778513` | `16778513` | identical |
| Controller number | 1 | 1 | identical |
| Reported as external | yes | yes | identical |
| Classified as a gamepad | yes | yes | identical |
| Axis count | 10 | 10 | identical |
| Axes | X, Y, Z, RZ, HAT_X, HAT_Y, LTRIGGER, RTRIGGER, BRAKE, GAS | same ten | identical |
| Button count | 12 | 10 | **differs** |

The axis list matches exactly, element for element. The system assigned the created device
**controller number 1** — the player slot it gives a real controller — which it does not do for
arbitrary input devices.

The only difference is two buttons: `BUTTON_L2` and `BUTTON_R2`. That is not a platform limitation.
The descriptor simply did not declare their key codes. It declared ten buttons and got ten.

## 2. What this establishes

Against the evidence hierarchy in `docs/PHASE-0.md` §28, this is the **Grade A prerequisite, met**:
the target sees a controller-like input device, created by this project, on stock unrooted hardware.

Reproduced across at least four creations (ids 13, 14, 15 and an earlier pair), and it persisted for
the full 30-second hold — the operator confirmed it visible in the device list for the whole window.

## 3. Device ids increment, and that is correct

The created device received a new id each time: 13, then 14, then 15. This is expected platform
behaviour and not a leak.

The evidence is in the descriptor. Both captured instances carry the **same** descriptor hash:

```text
id 13  ->  8cc7a295a758edbb…
id 14  ->  8cc7a295a758edbb…
```

The id is a per-registration handle, assigned fresh each time any device joins the input stack and
never reused within a boot. A physical controller unplugged and replugged behaves the same way. The
descriptor is derived from the device's identity and is stable across registrations.

The device count returned to 8 after each removal, so nothing accumulated.

**This settles a design rule for the input layer**: identity must be keyed on the descriptor, never
on the numeric id. Any profile, mapping or per-controller setting stored against an id would detach
itself the first time a session restarted. This belongs in `core/input/` when it is written.

## 4. Two harness faults, in opposite directions

The liveness check reported `helper process: NONE` and printed `NOT RUNNING`, while the device
demonstrably existed for the full thirty seconds and was visible in the inventory.

That check has now been wrong twice, in opposite directions: a false positive in 0.0.7, when it
matched the shell that was failing to run the helper, and a false negative here.

It is being replaced with raw process output rather than a derived claim. A measuring instrument
that asserts a conclusion its evidence does not support is worse than one that simply shows what it
saw — an operator can read raw output correctly, but cannot recover the truth from a confident
wrong summary. The harness is meant to be the thing that never does this.

## 5. What is still not proven

**Nothing has been sent through the device.** It exists, it is correctly classified, and it holds a
player slot. Whether events written to it arrive at applications attributed to *it* — rather than to
the system virtual device — is the remaining question, and it is the difference between a device
that looks right and a controller that works.

Also outstanding: no target application has been tested, and triggers, simultaneous input and
repeatability under `docs/PHASE-0.md` §29 are untouched.

`ADR-INPUT-001` therefore remains **Pending**. The candidate is now clear and the evidence is
strong, but a controller that has never delivered a button press is not a proven input backend.

## 6. Changes made in response

- Declare `BUTTON_L2` and `BUTTON_R2`, so the created device matches the reference on all twelve.
- Replace the liveness boolean with raw process output.
- Add an event-injection attempt through the created device, so the final question can be answered:
  press a button on it and see which device the arriving event is attributed to.

Dead zones are deliberately left at zero. `docs/INPUT_BACKENDS.md` places dead zone, sensitivity and
curve handling in the transformation layer rather than in a backend, so the device reports raw values
and the domain layer shapes them. The real controller declared a dead zone of roughly 0.12; that is
the hardware's choice, and Kestrel's equivalent belongs in software where it can be configured.
