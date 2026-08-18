# Phase 0 — A Created Controller Delivered Its Own Input

**Document:** `docs/phase0/results/tier5-press-report.md`  
**Status:** Grade A mechanism demonstrated on one device — acceptance criteria not yet met in full  
**Evidence:** `docs/phase0/results/tier5-press-20260817-redmi-note-13-5g.json`  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  
**Privilege:** Shizuku running, permission granted, identity `shell (uid 2000)` — **not root**  

---

## 1. The result

`tier5-gradeA-report.md` established that a device created by this project is indistinguishable
from a real controller in every property the platform reports. It left one question open: whether
events written to that device arrive **attributed to it**, or are absorbed by the system's own
virtual device.

They arrive attributed to it. Verbatim from the event log:

```text
0004  NOTE  CREATE+PRESS [numeric schema] — register, then press BUTTON_A three times
0005  NOTE  DEVICE ADDED   id=17 Kestrel Virtual Controller
      sources=KEYBOARD|GAMEPAD|JOYSTICK axes=10 gamepad=true
0006  KEY   KEYCODE_BUTTON_A DOWN
      dev=17 src=KEYBOARD|GAMEPAD scan=304
0007  KEY   KEYCODE_BUTTON_A UP
      dev=17 src=KEYBOARD|GAMEPAD scan=304
...
0017  NOTE  DEVICE REMOVED id=17
```

`dev=17` is the id the platform assigned to the device this project created, seconds earlier, in
the same run. Every one of the six key events carries it. `scan=304` is `BTN_SOUTH`, the exact key
code the descriptor declared.

Nothing about this is emulation of a keyboard press. The chain is: the harness asks a
shell-privileged process to register a virtual input device; the kernel creates it; the platform
enumerates it, classifies it as a gamepad and assigns it player slot 1; the same process writes
button reports to it; the platform delivers them to an ordinary unprivileged window as controller
input from that device.

The three unprivileged requirements in `docs/phase0/README.md` §5 Tier 5 now all hold:

1. it appears in the Devices tab via the hot-plug listener — yes, `DEVICE ADDED id=17`
2. it advertises `GAMEPAD`/`JOYSTICK` sources with real axes — yes, ten axes
3. events from it arrive carrying that device's id — **yes, this run**

## 2. What the created device looked like

Captured at the moment it appeared, so the record survives its removal:

| Property | Value |
| --- | --- |
| Name | `Kestrel Virtual Controller` |
| Id | 17 |
| Descriptor | `8cc7a295a758edbbada3044903f0f8fb0c1157f1` |
| Sources | `KEYBOARD\|GAMEPAD\|JOYSTICK` (raw `16778513`) |
| Controller number | 1 |
| External | yes |
| Classified as gamepad | yes |
| Axes | 10 — X, Y, Z, RZ, HAT_X, HAT_Y, LTRIGGER, RTRIGGER, BRAKE, GAS |
| Buttons | 12 — A, B, X, Y, L1, R1, **L2, R2**, SELECT, START, THUMBL, THUMBR |

The two buttons missing from the previous run are present. That confirms the earlier gap was a
descriptor omission and not a platform limit: declare the key code, get the button.

The axis ranges arrive **normalised** — sticks `-1…+1`, triggers `0…+1` — despite the descriptor
declaring raw kernel ranges of ±32768 and 0–255. The platform performs that normalisation itself.
`ARCHITECTURE.md`'s requirement that the input layer work in normalised units is therefore not an
extra conversion step on this path; it is what the platform already hands over.

## 3. Constraints this run confirms

**Duplicate delivery is inherent, not a defect of injection.** Every `BUTTON_A` was accompanied by
`KEYCODE_DPAD_CENTER` on the same device id and the same scan code. Tier 1 recorded exactly this
behaviour from a *physical* controller: buttons carrying a system-navigation meaning are delivered
twice. A created device inherits it because the platform applies the mapping, not the device. The
input layer must therefore de-duplicate on `(deviceId, scanCode)` rather than trusting key codes,
and this is now confirmed on both a real and a created device.

**Event ordering is not guaranteed to pair cleanly.** Entry `0008` is a `DPAD_CENTER` **UP** with no
preceding `DOWN` in the log, and the later pairs interleave. Any state machine that assumes a
strict down-then-up sequence per key code will desynchronise. Track button state per
`(deviceId, scanCode)` and treat an unmatched release as idempotent.

**Identity is the descriptor, never the id.** Id 17 follows ids 13, 14 and 15 from earlier runs.
The id is a per-registration handle and increments on every create; the descriptor
`8cc7a295…` is stable across all of them. A profile, a binding, or a remembered layout must key on
the descriptor. This was already argued in `tier5-gradeA-report.md` §3; this run is the fourth
independent confirmation.

**The device dies with the process holding it.** `DEVICE REMOVED id=17` follows the helper exiting.
A production backend is therefore a long-lived process for the duration of a session, with an
explicit teardown — which is what `CLAUDE.md` §5 already requires of every backend.

## 4. What this does *not* establish

Stated plainly, because the temptation to over-read this result is the whole reason the evidence
grades exist:

- **Only one button was pressed.** Analog axes, triggers, D-pad, and simultaneous input were not
  driven through the created device in this run. The device *declares* them; nothing yet proves
  values written to them arrive.
- **No target application has seen it.** Tier 6 is untouched. Until an emulator or streaming
  client's own binding screen recognises this device, the phrase "true virtual gamepad" stays
  barred by `docs/INPUT_BACKENDS.md`.
- **One device, one firmware.** Redmi Note 13 5G on HyperOS 3.0.3. A HyperOS result is a HyperOS
  result.
- **Shizuku was required.** The whole path depends on shell privilege. Per ADR-003 that privilege
  is optional to the product, so this mechanism can only ever be the *best* backend, never the
  only one.
- **Latency is unmeasured.** The harness timestamps arrival, not the interval from intent to
  delivery.
- **Repeatability of this specific test is n=1.** Device creation is reproduced across four runs;
  delivery through it, once.

## 5. Effect on the acceptance criteria

Against `docs/PHASE-0.md` §29, this run satisfies the identity and digital-delivery parts of the
Grade A criteria and leaves the analog and target-application parts open. `ADR-INPUT-001` therefore
stays **Pending** — one press is not a pass, and §29 requires repeatable evidence including analog
axes and a real target.

What has changed is the *shape* of the remaining work. Before this run it was an open question
whether the highest tier was reachable at all on unrooted stock hardware. It is. What remains is
extending a demonstrated mechanism, not searching for one.

## 6. Next

1. Write axis and trigger values through the created device and confirm the delivered values.
2. Press several buttons at once and confirm they arrive as simultaneous state, not a queue.
3. Repeat the whole sequence across reboots and Shizuku restarts.
4. Then, and only then, Tier 6: a real target application's binding screen.
