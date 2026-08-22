# Kestrel — Done List

**Document:** `done-list.md`  
**Status:** Active — the record of work that is finished, and what finished means for each item  
**Companion to:** `todo-list.md`, which holds everything not yet finished  

---

## How to read this

`todo-list.md` is the queue. This is the receipt.

An item moves here when it reaches the phase `done` — which means **confirmed on the reference
device by the project owner**, not "written and it compiles". Until then it stays in the queue at
`pending`, `building` or `testing`.

Each entry says four things, in this order:

1. **What was asked for**, in the words it was asked in where they exist.
2. **What was actually built** — enough that somebody reading it a year later knows what to look for
   in the code.
3. **How it is known to work** — Measured, Reported, Reasoned or Unverified, the same vocabulary
   `todo-list.md` uses.
4. **What it cost**, where it cost something: a limit, an assumption, a thing deliberately not done.

**Nothing is written here on the strength of a build succeeding.** A compiler proves that the code
is well-formed. It proves nothing about a phone.

**Reference device for every "Measured" claim:** Redmi Note 13 5G, HyperOS 3.0.3, Android 15,
Shizuku shell (uid 2000), no root. One device, one firmware, one person testing.

---

## Closed items

### `BUG-9` — A square drew as a rectangle in the editor — **closed `0.0.26-dev`**

**Reported.** `width 0.24`, `height 0.12`, shape `square`: a rectangle in the editor, a correct
square once saved and running.

**Built.** The rule — *a square is sized by the shorter of its two sides, a circle by its inscribed
radius* — existed in two places and the two disagreed. It is now one function in the domain,
`PixelRect.shapedAs(shape)`, with `LayoutElement.effectiveShape()` beside it for the related rule
that a stick and a pad are round whatever the document says. The overlay's private copy was deleted
and it calls these; the editor's preview and its hit-testing call them too, so a control is
*selected* by the same outline it is *drawn* with.

**How it is known.** **Measured** on the reference device — the project owner set those exact
numbers and the editor drew a square. Unit tests cover both rules.

**Cost.** None. This removed code.

---

### `FEAT-10` — The window editor — **closed `0.0.26-dev`**

**Asked for.** *"Whatever the case i also want the controller window editor also. on same screen as
layout editor with one toggle to greyout the buttons editor to window editor."*

**Built.** A **Controls / Windows** toggle at the top of the tool panel. The tools of the inactive
mode are **greyed out rather than hidden**, so it stays visible that the other mode exists. In
Windows mode the canvas draws each window as a translucent box around its group, the selected
control's window highlighted, and any window past a quarter of the screen drawn in orange; the panel
lists every window with its share of the screen as a percentage. `◀` and `▶` step a control through
*own window*, every group that exists, and a fresh `group-N` — chosen rather than typed, because
group names follow the same rules as element ids.

**Why it needed a screen at all.** A window is the enclosing rectangle of everything sharing a
group. A finger can slide between controls that share one — that is what makes rolling across face
buttons work, and what lets a thumb hold `L3` and then move the stick — but **every pixel of that
rectangle that is not a control is dead**. A touch there is refused, and, measured on the reference
device, a refused touch is *not* passed to the application underneath. Two grouped controls in
opposite corners make one screen-covering window and the game stops receiving touches. It was
editable only by hand and there was no way to see it.

**How it is known.** **Measured** — the project owner ran tests 9, 10 and 11: greying out, changing
a control's window, and the screen-covering warning all behaved. The platform behaviour underneath
is measured and recorded in `Clustering.kt`.

**Cost.** The editor does not stop a user making a window that covers the screen; it shows the
percentage and turns it orange. `ADR-007`'s spirit: say what is true, do not overrule the person.

---

## Built, awaiting confirmation — `0.0.27-dev`

The first test of block 1 closed two items and failed on five points. This is what was done about
them, plus the two items from block 1 that are still not finished.

### `BUG-10` — The canvas is the phone now, bands and all

**The fault, as measured.** The canvas drew **2289 × 927**; the screen is **2400 × 1080**. Those are
2.47 : 1 and 2.22 : 1 — not the same shape. `CRIT-5` had fixed the *size* of the lie and not the
lie. And it is why the pad still did not match: controls drawn hanging over the canvas edge are, on
the phone, pushed back inside by the window manager, because a window is laid out within the area
the system gives it.

**Built.** `DeviceSurface.screen()` returns the **whole** screen with the bars and the cutout carried
as insets rather than subtracted. The canvas draws that rectangle, shades the band the system takes,
outlines the usable area inside it, and arranges the pad within the band — which `LayoutSurface` and
`resolve` already supported, so nothing about placement changed, only what is drawn. A control that
leaves the usable area is now **outlined in orange**, and the panel says how many have, and why it
matters: *"the phone will not put a window there, so the pad will not match this."*

**How it is known.** Unverified on the device. The geometry is the same code the overlay uses.

**Cost.** A layout can still put a control outside the usable area — that is a real design for a
shoulder button, and `ADR-007`'s spirit says show it rather than forbid it. What is no longer
possible is doing it *by accident and invisibly*.

---

### `BUG-11` — `Edit layout` is reachable in portrait

Three buttons in one non-wrapping row put the third off the edge in portrait, with nothing to scroll
sideways: the editor could not be opened with the phone upright. Two rows now, with `Edit layout`
first and alone with `Reload layout`. Unverified on the device.

---

### `BUG-12` — Turning the phone keeps you in the editor

`MainActivity` declares `configChanges` for orientation and the sizes that come with it, so a
rotation re-lays-out instead of rebuilding the activity. What this really saves is not the
navigation — it is **every unsaved edit**, which was being thrown away by turning the phone.

Unverified on the device. **The risk worth naming:** handling a configuration change means Kestrel
is now responsible for anything that should change with it. Compose re-reads `LocalConfiguration`
and the editor re-measures the screen from it, which covers what this screen needs; a future screen
that depends on configuration-specific resources will have to be checked rather than assumed.

---

### `BUG-13` — The numbers dialog fits and scrolls

Two fields to a row, and the body scrolls. Four stacked fields in a dialog on a landscape phone put
width and height below the fold with no way to reach them. Unverified on the device.

---

### `BUG-14` — A minus sign without a minus key

`± offsetX` and `± offsetY` buttons in the dialog flip the sign of whatever is in the field,
including a half-typed one. This does not depend on which keyboard someone has, which is the part
that could not be relied on. Unverified on the device, and the underlying question — whether that
keyboard offers a decimal point but no minus — is still not confirmed either way.

---

### `FEAT-13` — Three to one, and the values button where the hand is

The canvas takes three quarters and the tools one quarter, in both orientations. `⋮ values` is a
filled button in the same row as `−`, `+`, `taller`, `shorter`, `shape` and `anchor` — it was a bare
text button beside a row of filled ones, which is what a control that looks like a label gets.

Unverified on the device. **Not fixed by this:** previewing *portrait* while the editor itself is in
*landscape* still leaves a tall narrow strip, because the canvas can only be as tall as the dock.
Three quarters of the width does not help a rectangle limited by height. Editing a portrait pad is
best done with the phone in portrait — which `BUG-12` now makes possible.

---

### `FEAT-14` — The grid in the layout's own unit

*"the button is 0.12 and the grid is 32px both are different scales."* Correct, and the fix was to
move the grid rather than the control. Steps are now **0.01, 0.02, 0.05, 0.10 and 0.25 of the
shorter side**, labelled with the pixel equivalent for this phone — `0.05 · 46 px` — and the
selected control's size is shown in both units for the same reason.

It also removes a limitation that was written up as a property of `FEAT-11` and was really a symptom
of the wrong unit: a step of `0.01` is exactly the precision the file stores, so a snapped control
now lands on a number the file can hold. The pixel grid could not promise that on any screen.

Unverified on the device.

---

### `CRIT-5` — still `testing`

The canvas is now the phone (`BUG-10`) and the proportions are as asked (`FEAT-13`), but the item
does not close until the pad on the phone matches what the editor drew. That is test 12, and it
failed last round.

### `FEAT-11` — still `testing`

Grid and edge snapping both worked on the device. It stays open until the unit change (`FEAT-14`) is
confirmed useful rather than merely different.

### `FEAT-12` — still `testing`

Validation worked on the device. It stays open on `BUG-13` and `BUG-14`.

---

## Before this list existed

The work below closed before `todo-list.md` and this file were created. It has no IDs because none
existed, and it is recorded descriptively so that nothing is rebuilt by accident.

### Phase 0 — input feasibility, and what it cost to find out

**Established, and it is the foundation everything else stands on:** a virtual input device created
through Shizuku's shell privilege delivers real controller input to ordinary target applications on
the reference device. `ADR-INPUT-001` is Accepted with that scope written into it. Evidence is in
`docs/phase0/results/`.

Two results are **binding on any implementation**, because they were measured rather than reasoned:

- **Persistence must be governed, not prevented.** A session is held by a lease that a privileged
  watchdog enforces, so force-stop and uninstall end it without Kestrel running any code. A backend
  that holds a device without one can strand a controller until the phone is rebooted — which
  happened once.
- **Identity keys on the device descriptor, never the numeric id**, which changes on every
  registration.

**`ADR-006` — the accessibility fallback — was measured and then rejected.** It worked: median 4 ms,
about 242 drag movements a second. It was rejected on product grounds, and the second reason is the
harder one: declaring an accessibility service made Play Protect block installation *for every
user*, confirmed in both directions. A failed experiment is a result and is written down as one.
Kestrel is Shizuku-only for input as a consequence.

### The overlay

A pad on screen, in windows the layout decides. What was learned building it:

- **A pointer belongs to the window that received its DOWN for the life of the gesture.** Sliding
  between controls only works inside one window. This is why grouping exists.
- **A view returning "not handled" does not pass the touch to the application below**, and irregular
  touchable regions are not public API. This is why windows are kept small.
- **Grouping by proximity does not work.** It was tried. On the shipped layout the gap that had to
  mean "together" and the gap that had to mean "apart" were fifteen pixels apart, so the answer
  flipped with rounding and with the size setting. Declared groups replaced it.
- **Anchors and edge margins, not absolute coordinates.** Coordinates computed against the display
  but placed by the window manager inside the usable area moved everything down when the status bar
  appeared, and the pad overlapped itself.
- **Windows are repositioned in place**, not destroyed and recreated, or resizing leaves trails.
- **Controller keys are consumed.** Observing them without handling them made the platform generate
  its own fallback keys, and `B` arrived twice — once as itself and once as Back.

### Layout as a document

`ControllerLayout` with a strict hand-written JSON reader and writer in `:core`. Placement is an
anchor plus inward offsets, sizes are fractions of the **shorter side** so a control keeps its shape
on any screen, and the writer emits **every editable field including nulls and defaults** — a file a
user is invited to edit should show them what there is to edit. Numbers are two decimals.

Built-ins are immutable and the editor duplicates rather than refusing. Storage re-validates the
chosen folder every few seconds and falls back to private storage with an explanation, after a
deleted folder was cached forever.

### Build and toolchain

Gradle with a version catalogue, three modules, CI on every push producing both APKs as artifacts,
lint failing the build. `:core` is Kotlin/JVM on purpose, which makes the architecture boundary a
compile error rather than a review comment.

### Rejected, and why

- **`MANAGE_EXTERNAL_STORAGE`** — measured Play Protect block. Storage access is SAF.
- **`ADR-006` accessibility fallback** — above.
- **Proximity-based grouping** — above.
- **Reverse portrait and sensor portrait** — reverse portrait does not work on the platform;
  sensor portrait is reported useless. `BUG-4` removes what remains.
