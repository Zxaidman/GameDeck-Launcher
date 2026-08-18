# Phase 0 — Tier 1 Calibration Reference

**Document:** `docs/phase0/results/tier1-report.md`  
**Status:** Complete — calibration reference established  
**Evidence:** `docs/phase0/results/tier1-20260817-redmi-note-13-5g-remote-gp.json`  

---

## 1. What was run

A second phone (iQOO Z9 Lite 5G) running remote-gamepad software was paired to the target device
over Bluetooth, so it presented itself as a controller. A wireless controller was also used. The
harness recorded 191 events.

This supplies the calibration reference that Tier 0 lacked: **an observed example of what a genuine
controller looks like on this exact device and firmware.** Every later claim of success now has
something concrete to be measured against.

| Property | Value |
| --- | --- |
| Reported name | `iQOO Z9 Lite 5G` |
| Device id | 8 |
| Sources | `KEYBOARD|GAMEPAD|JOYSTICK` (raw `16778513`) |
| Vendor / product | `0xe0` / `0x0` |
| Controller number | **1** — the system assigned a player slot |
| External | true |
| Axes | 10 |
| Gamepad buttons advertised | 12 |

### The target signature

For Kestrel to claim a Grade A result, a device it creates must look like this:

```text
sources    KEYBOARD | GAMEPAD | JOYSTICK
axes       X, Y, Z, RZ, HAT_X, HAT_Y, LTRIGGER, RTRIGGER, BRAKE, GAS
buttons    A B X Y L1 R1 L2 R2 THUMBL THUMBR START SELECT
number     a controller number assigned by the system
```

Anything advertising fewer sources, no axes, or no controller number is not equivalent, however
convincing its name.

---

## 2. Four findings that constrain the input layer

These came out of the event log and will shape `core/input/` when it is written. Each is recorded
here because assuming otherwise later would be expensive.

### 2.1 Buttons with a system meaning are delivered twice

Every controller button that has a system-level fallback arrives as **two key events sharing one
scan code**:

| Scan | Controller keycode | Also delivered as |
| ---: | --- | --- |
| 305 | `BUTTON_B` | **`BACK`** |
| 307 | `BUTTON_X` | `DEL` |
| 308 | `BUTTON_Y` | `SPACE` |
| 314 | `BUTTON_SELECT` | `MENU` |
| 315 | `BUTTON_START` | `DPAD_CENTER` |
| 317 | `BUTTON_THUMBL` | `DPAD_CENTER` |
| 318 | `BUTTON_THUMBR` | `DPAD_CENTER` |
| 310 | `BUTTON_L1` | *(none)* |
| 311 | `BUTTON_R1` | *(none)* |

Three consequences:

1. **A naive listener double-counts every press.** One physical press, two delivered events.
2. **`BUTTON_B` also means `BACK`.** A target application that does not consume the controller
   keycode may navigate backwards when the user presses B. Kestrel must never treat a `BACK` arriving
   on a controller scan code as a genuine back request.
3. **Three distinct buttons collapse to `DPAD_CENTER`.** START, THUMBL and THUMBR are
   indistinguishable at the fallback level, so the fallback can never be the source of truth.

The rule: **match on the controller keycode and the originating device, and discard the fallback.**

### 2.2 Each trigger reports on two axes at once

A single trigger pull sets two axes simultaneously:

```text
LTRIGGER = 1.000  and  BRAKE = 1.000
RTRIGGER = 1.000  and  GAS   = 1.000
```

The transformation layer must pick one axis per trigger and ignore its twin. Summing them, or
treating them as independent controls, would double the value.

### 2.3 The D-pad arrives as axes and as keys, and so does the left stick

The D-pad is reported both as `HAT_X`/`HAT_Y` at ±1.0 **and** as synthesised `DPAD_*` key events
carrying `scan=0` and `src=JOYSTICK`.

More significantly, **the left analog stick also synthesises D-pad key events** once it passes a
threshold. At event 102 the stick was at `X=-0.741` and a `DPAD_LEFT DOWN` was delivered alongside
the continuing stream of axis values.

So a single stick deflection produces both continuous axis data and discrete directional keys. A
backend that consumes both would move a menu cursor while also driving an analog input.

### 2.4 The system virtual device aggregates capabilities, and will produce false positives

At Tier 0, with nothing connected, the system `Virtual` device (id -1) advertised **4** keys — the
D-pad only. With a controller connected it advertises **all 16**, including the full button set.

It has no axes and is not a controller. Its advertised capability is a union of what the system can
currently deliver.

**Capability detection must therefore skip device id -1 entirely.** Querying it would report a full
controller present on a phone with nothing attached — the same class of false positive as
`uinput-goodix` in Tier 0, arrived at by a different route.

### 2.5 Dead zones are declared by the device, not assumed

The device declares its own flat regions: `0.1176` on the sticks, `0.0588` on the triggers, `0` on
the hats. `docs/INPUT_BACKENDS.md` places dead-zone handling in the transformation layer; this
confirms the layer should **read the declared value per axis** rather than hardcode a constant, and
fall back to a default only when a device declares none.

---

## 3. What this does and does not settle

**Settled.** The receiving half of the problem is not in doubt. This phone, on this firmware,
accepts a controller and delivers complete controller semantics: continuous sticks, dual triggers,
hat directions, twelve buttons, simultaneous presses, and an assigned controller number. Whatever
Kestrel eventually produces has a known target to match.

**Not settled, and this is the important part.** This does **not** show that Kestrel can create a
controller for applications on the *same* phone.

The remote-gamepad software works by making the *second* phone advertise itself as a Bluetooth HID
peripheral to the *first*. Two devices, a host and a peripheral. A single device cannot pair with
itself, so this route cannot be turned inward to solve the on-device problem. It demonstrates the
host side works — which was never the uncertain half.

The core question is unchanged: can a process on the phone create an input device that applications
on that same phone see as a controller? Tier 5 remains the test, and Tier 0 already showed the
underlying facility is present and in use on this hardware.

**Untested:** `BUTTON_A` never appeared in the log, so one button of the twelve has no observed
delivery. Worth a moment's coverage next time.

---

## 4. A real product capability this exposes

Kestrel could implement the peripheral role itself: a spare phone running Kestrel becomes a
controller for the main device, exactly as the software used in this test does.

Android has offered `BluetoothHidDevice` as a public API since API 28, which is within the project's
API 29 baseline. **This has not been verified by experiment and must not be treated as proven** —
but it is a documented public API rather than a hidden one, which puts it in a different and much
safer category than the injection routes.

This is not the core requirement, and it must not be allowed to displace it. It would not help a
user with one phone, which is the user `PRD.md` is written for. It is recorded here because it is a
genuine, standards-based capability discovered by evidence, and it deserves its own decision record
if it is ever pursued.

---

## 5. Recommended next step

Unchanged from the Tier 0 report, and now better supported: **run Tier 5 next.**

```bash
adb shell ls -l /dev/uinput
adb shell uinput -h
```

Two commands decide whether the only path to the signature in §1 is reachable at all. Everything
else in Phase 0 is secondary to that answer.

`ADR-INPUT-001` remains **Pending**. No evidence grade has been assigned, because no Kestrel-created
mechanism has been exercised — a grade describes what the project can produce, and so far the
project has produced nothing. What has been produced is a reliable measuring instrument and a
reference to measure against.
