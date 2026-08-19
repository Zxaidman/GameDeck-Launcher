# Phase 1 — Controls Cannot Live in an Ordinary Window

**Document:** `docs/phase0/results/tier6-focus-report.md`  
**Status:** Measured on the reference device — architectural consequence recorded  
**Evidence:** `docs/phase0/results/app-stick-focus-20260819-redmi-note-13-5g.json` (Kestrel 0.0.6-dev)  

---

## 1. What was observed, and what the export says instead

The operator reported that on-screen face buttons bound and worked inside an emulator, while the
on-screen stick did nothing — and that the stick appeared in Kestrel's own readout as coming from
`touch pad (this screen)`, which reads like the stick never reached the controller at all.

The export says something different, and it is the more useful answer:

```json
"lastInput": {
  "source": "Kestrel Virtual Controller (id 14)",
  "events": 2005,
  "lastButton": "DPAD_RIGHT"
}
```

**The stick worked.** Two thousand events arrived *from the created controller*, and `DPAD_RIGHT` is
the directional key the platform synthesises from a held stick — it cannot appear unless an axis was
actually deflected on the device.

They arrived at **Kestrel**. That is the finding.

## 2. Why

The platform delivers a controller's events to the **focused window**. Touching a control inside an
ordinary activity makes that activity the focused window. So:

```text
finger touches Kestrel's stick
        ↓  Kestrel becomes the focused window
Kestrel writes axis values to the controller
        ↓  the controller moves — this part always worked
platform delivers the events to the focused window
        ↓
Kestrel receives its own input; the target receives nothing
```

The buttons appeared to work for the same reason in reverse: the operator found they only reached
the target "if the emulator has focus before the button goes down". That was not a quirk of buttons.
It was the same rule, and the stick simply cannot satisfy it — a drag *has* to begin with a touch on
Kestrel, so Kestrel always has focus by the time the axis moves.

**Nothing about the controller, the write path, or the transformation was wrong.** Every part
measured correct and the arrangement was still unusable, which is a failure mode worth naming: a
pipeline can be correct end to end and still deliver to the wrong place.

## 3. What follows

The controls must be in a window that **accepts touches and never takes focus** —
`TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE`. Then the target keeps focus, and the
controller's events go where the player is looking.

This is what `PRD.md` and `ARCHITECTURE.md` always described as the overlay, and what
`PROJECT_STRUCTURE.md` reserves `platform/overlay/` for. What is new is that it is now a
**requirement with a measurement behind it** rather than a design preference: without a
non-focusable window there is no arrangement of an ordinary activity that works, on any device.

`FLAG_NOT_TOUCH_MODAL` goes with it, so touches outside the controls reach whatever is underneath
and the rest of the screen stays usable.

## 4. Consequences beyond the overlay

- **Kestrel's own interface will be driven by the controller Kestrel creates** whenever Kestrel is
  focused — already recorded in `tier5-exercise-report.md` §4, and this is the same rule from the
  other side.
- **A layout editor cannot be tested by playing through it.** Editing happens in a focused window,
  so a control pressed while editing goes to the editor. Previewing a layout means previewing it in
  the overlay.
- **Requiring an overlay permission is not optional to the product.** It is the second permission a
  user must grant, after Shizuku, and `docs/DEGRADED_STATE.md` should treat its absence as a state
  to report rather than an error to raise.

## 5. Limits

One device, one firmware. The focus rule is platform behaviour rather than a device property, so it
is expected to hold everywhere, but that expectation is untested. Nothing here says the overlay
*works* — only that the arrangement it replaces cannot.
