# Phase 0 — Target Applications Accept a Kestrel-Created Controller

**Document:** `docs/phase0/results/tier6-report.md`  
**Status:** Emulators confirmed on one device; streaming client unconfirmed  
**Evidence:** `docs/phase0/results/tier6-20260818-redmi-note-13-5g.json` (harness 0.0.13),
plus operator screenshots of three emulator binding screens  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  
**Privilege:** Shizuku running, permission granted, identity `shell (uid 2000)` — **not root**  

---

## 1. What was tested

Tier 5 proved the platform delivers every control from a device this project creates. That is a
statement about the operating system. It says nothing about whether the applications people
actually play on will treat that device as a controller — an application can enumerate input
devices itself, filter by vendor id, require a specific descriptor, or simply ignore anything it
does not recognise.

Tier 6 asks the applications. The harness opened the device and cycled every control on a slow
schedule while the operator left the harness and opened each target's own controller settings.

**A target's binding screen is the strongest available evidence, because it states in its own words
what it thinks it received.**

## 2. Result — three emulators, independently

### Eden

- Player 1 shows **Connected**, controller type **Pro Controller**.
- The **Input mapping filter** offers exactly two choices: `Any`, and **`Kestrel Virtual
  Controller 0`**. The device is a first-class entry in that application's own device list.
- Auto-mapping bound the full control set:

| Control | Bound to |
| --- | --- |
| A / B / X / Y | `Button 96` / `97` / `99` / `100` |
| Plus / Minus | `Button 108` / `109` |
| L / R | `Button 102` / `103` |
| **ZL / ZR** | **`Axis 17` / `Axis 18`** |
| D-pad up/down/left/right | `-Axis 16` / `Axis 16` / `-Axis 15` / `Axis 15` |
| Left stick up/down/left/right | `Axis 1+` / `Axis 1-` / `Axis 0-` / `Axis 0+` |
| Stick pressed | `Button 107` |

Those numbers are Android's own constants: 96–109 are the `KEYCODE_BUTTON_*` codes, and axes
0, 1, 15, 16, 17, 18 are `AXIS_X`, `AXIS_Y`, `AXIS_HAT_X`, `AXIS_HAT_Y`, `AXIS_LTRIGGER`,
`AXIS_RTRIGGER`. The emulator is reading exactly what the descriptor declared, through the ordinary
platform input path.

**ZL and ZR bound to axes, not buttons.** The emulator classified the triggers as analog controls.

### NetherSX2

- Controller type **DualShock 2**, and **Automatic Mapping** completed with the on-screen message
  "Automatic mapping complete."
- Bindings name the device *and its id*: `Kestrel Virtual Controller[25]/-Axis16`,
  `[25]/+Axis15`, `[25]/Button100` for Triangle, `[25]/Button96` for Cross.

That `[25]` is worth pausing on. The harness's own event log for the same session records every
event as `dev=25`. **The id the emulator wrote into its binding file is the id the platform assigned
to the created device**, independently observed from two applications that know nothing about each
other.

### RetroArch 1.22.2

- Port 1 Controls → **Device Index: `Kestrel Virtual Controller`**, under a description that reads
  "The physical controller as recognised by RetroArch."

RetroArch's own words. It has no category for what this actually is, and it did not need one.

### PPSSPP

Not confirmed. The operator could not locate the relevant settings screen during the run. **Recorded
as untested, not as working** — the other three results do not transfer.

### Artemis (streaming)

Not confirmed. The application exposes no screen listing connected controllers, so there was
nothing to observe. This is a gap in what the test could see, not a negative result about the
device.

**This is the one acceptance criterion still open.** `docs/PHASE-0.md` §29 requires one emulator
**and** one streaming client. Confirming it means streaming to a host and checking that the host
sees a gamepad — the client is a pass-through, so the host is where the answer is.

## 3. What this establishes

Against `docs/COMPATIBILITY.md` §10, this is **Level 4 — virtual gamepad identity**, reached on
stock unrooted hardware: a device created by this project, enumerated by target applications as a
controller in their own device lists, auto-mapped to their full control sets including analog
sticks and analog triggers.

The restriction in `docs/INPUT_BACKENDS.md` on the phrase "true virtual gamepad" required device
testing proving target applications receive controller-style input. For **emulators on this one
device and firmware**, that condition is now met.

Against `docs/PHASE-0.md` §29:

| Criterion | Status |
| --- | --- |
| Digital — A/B/X/Y and D-pad | met, and bound by three emulators |
| Analog — at least one stick continuously | met, bound as axes 0 and 1 |
| Triggers | met, bound as axes 17 and 18 |
| Simultaneous | met |
| Hold/release — no stuck inputs | met |
| Lifecycle — state can be reset | met |
| At least one emulator | **met — three** |
| At least one streaming client | **not met — unconfirmed** |
| Repeatability | partially — repeated across sessions on one device, one firmware |

## 4. Lifecycle observations from the run

**The device survived application switching.** It remained present across several target
applications being opened in turn, which is the behaviour a real session needs — the controller
cannot be tied to whichever application happens to be in the foreground.

**It disappeared partway through.** The operator saw it drop while NetherSX2 was open. The expected
cause is simply the hold schedule ending: the helper runs a fixed cycle of roughly two minutes and
then exits, and the device dies with the process holding it. Nothing in the export contradicts
that, and nothing in it proves it either — **the run has no timestamp showing when the device went
away relative to when the schedule ended**, so this is recorded as unexplained rather than
attributed. A production backend holds the device for the whole session and must be measured
against a clock.

**The duplicate-key behaviour persists into targets.** The export again shows `BUTTON_B` arriving
alongside `KEYCODE_BACK` and `BUTTON_Y` alongside `KEYCODE_SPACE`, on the same device id. In an
emulator this is mostly harmless; in Kestrel's own interface it is not, and §4 of
`tier5-exercise-report.md` covers what that requires.

## 5. What this still does not establish

- **One device, one firmware.** Redmi Note 13 5G, HyperOS 3.0.3, Android 15. Nothing here predicts
  Samsung, Pixel, or older Android versions.
- **No streaming client.** The criterion is unmet, not merely unobserved.
- **PPSSPP untested**, despite being one of the named targets.
- **No gameplay.** Binding screens were observed; nothing was played. Whether input is *usable* —
  latency, dropped reports under load — is a different question this instrument cannot answer.
- **Shizuku throughout.** ADR-003 keeps that optional to the product, so this remains the best
  backend, never the only one.

## 6. Next

1. **The streaming half.** Stream from the Windows host to Artemis and check whether the host sees
   a gamepad. That closes the last §29 criterion.
2. **PPSSPP**, for completeness against the named target list.
3. **Repeatability across a reboot** and a Shizuku restart.
4. Then `ADR-INPUT-001` can be decided rather than left pending.
