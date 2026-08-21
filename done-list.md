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

Nothing has been closed through the queue yet. `CRIT-5`, `BUG-9`, `FEAT-10`, `FEAT-11` and `FEAT-12`
are built and sitting at `testing` in `0.0.26-dev`; they are described below under **Built, awaiting
confirmation**, and they move up into this section when the project owner has run them.

---

## Built, awaiting confirmation — `0.0.26-dev`

### `CRIT-5` — The editor draws the phone, not the page

**Asked for.** *"Layout editor editing area, look to ultrawide with control overlapping while actual
is not, so according to device aspect ratio create one rectangle border with blank canvas showcasing
Android device… Scale that rectangle area as such so it can appear without scrolling needed in any
orientation. maybe make it for rectangle area is fixed dock like panel on one side of screen while
other side work as side panel with scrollable area and editing tool for editor."*

**Built.**

- A new `platform/display/DeviceSurface.kt` answers one question — *what part of this phone's screen
  can a pad be put on* — and both the overlay and the editor now ask it. The overlay's own copy of
  that calculation is gone; it delegates. Insets are subtracted with `getInsetsIgnoringVisibility`,
  so a status bar appearing later does not move a pad that was arranged without it.
- The editor draws a bordered rectangle at that exact aspect ratio, filled dark, and arranges the
  layout inside it. It is scaled to fit whole with a small margin, so it never scrolls and never
  crops, in either orientation of the editor.
- The screen is a dock and a panel. Wide editor window: canvas fixed on the left at 58% of the
  width, tools scrolling on the right. Tall editor window: canvas fixed on top at 52% of the height,
  tools scrolling underneath. The split is chosen from the shape of the *editor's* window, and the
  canvas keeps the shape of the *phone* either way — they are two different rectangles and were
  being conflated.
- A **preview toggle** for landscape and portrait, so a pad can be checked in the orientation the
  phone is not currently in. One layout has to work in both, and a pad that fits in landscape and
  overlaps itself in portrait has shipped here once already.

**How it is known.** Unverified on the device. It compiles, the full `./gradlew build` passes
including lint, and the geometry it relies on is covered by unit tests — none of which is a claim
about how it looks in the hand.

**Cost.** The canvas uses *this* phone's ratio, because that is the only one Kestrel can measure
rather than assume. Editing a layout meant for a differently-shaped phone is not offered and is not
solved by this.

---

### `BUG-9` — A square drew as a rectangle in the editor

**Reported.** `width 0.24`, `height 0.12`, shape `square`: a rectangle in the editor, a correct
square once saved and running.

**Built.** The rule — *a square is sized by the shorter of its two sides, and a circle by its
inscribed radius* — existed in two places and the two disagreed. It is now one function in the
domain, `PixelRect.shapedAs(shape)`, with `LayoutElement.effectiveShape()` beside it for the related
rule that a stick and a pad are round whatever the document says. The overlay's private copy was
deleted and it calls these; the editor's preview and its hit-testing now call them too, so a control
is *selected* by the same outline it is *drawn* with.

**How it is known.** Reasoned, plus unit tests for both rules, plus the fact that the overlay's
behaviour is unchanged because it is the copy that was already right. Unverified on the device.

**Cost.** None. This removed code.

---

### `FEAT-11` — A grid, and snapping

**Asked for.** *"add grid with the drop-down option 32x32px to 256x256px with one checkbox snapping
on for grid and another checkbox for gamepad edge snapping."*

**Built.** A drop-down offering 32, 64, 128 and 256 px, drawn faintly on the canvas; two checkboxes,
one for grid snapping and one for edge snapping. Both start off, so the editor behaves exactly as
before until they are turned on.

Edge snapping lines a control's **left edge, centre or right edge** up with any other control's
left, centre or right, and with the screen's own edges and midlines — whichever is nearest, within
about 2% of the short side. A yellow guide line shows what it caught, and disappears when the finger
lifts.

**When both could apply, edge snapping wins**, per axis. Lining up with the control next to it is a
statement about *this layout*; landing on a grid line is a statement about *the screen*, and the
first is nearly always what a hand dragging a control is after. A control can line up with a
neighbour horizontally and sit on the grid vertically.

**How it is known.** Unverified on the device.

**Cost, and it is stated in the interface rather than hidden.** The grid is measured in this phone's
pixels; the file stores fractions of the short side rounded to two decimals. A snapped control can
therefore land a pixel off the line on a different screen. That trade is deliberate — a file a
person can read and hand-edit is worth more than an exact grid — but a user should be told rather
than discover it.

---

### `FEAT-12` — Typing the numbers

**Asked for.** *"Three dot option in layout editor where we can input offset and size value
directly."*

**Built.** A `⋮` button beside the selected control opens a dialog with the four numbers — offsetX,
offsetY, width, height — as editable fields on a decimal keyboard. Apply validates through the same
`Placement.of` the file reader uses, so a bad number is reported with the field that was wrong and
the range it had to be in, rather than being silently clamped. Values are rounded to the two
decimals the file gets, so what is typed is what is written.

The dialog also **states the units**, which were reported as confusing: an offset runs from the
named anchor to the control's **centre**, inwards, and all four numbers are fractions of the
screen's **shorter side**.

**How it is known.** Unverified on the device. The validation path is the one already under test.

**Cost.** The anchor is not editable in this dialog — it is a button in the panel, and mixing a
cycling control into a form of typed numbers made the dialog worse rather than better.

---

### `FEAT-10` — The window editor

**Asked for.** *"Whatever the case i also want the controller window editor also. on same screen as
layout editor with one toggle to greyout the buttons editor to window editor."*

**Built.** A **Controls / Windows** toggle at the top of the tool panel. The tools belonging to the
mode that is not active are **greyed out rather than hidden**, so it stays visible that the other
mode exists and what it holds. Dragging is disabled in Windows mode; selecting still works.

In Windows mode the canvas draws each window as a translucent box around its group, the selected
control's window highlighted, and any window past a quarter of the screen drawn in orange. The panel
lists every window with its **share of the screen as a percentage** and the controls it holds.

Editing is of each control's `group`: `◀` and `▶` step through *own window*, every group that
already exists, and a fresh `group-N`. A name is chosen rather than typed — group names follow the
same rules as element ids, and a keyboard is a way to break that rule when all the name has to do is
be different from the others.

**Why this needed a screen at all**, which is the answer to the project owner's question: a window
is the enclosing rectangle of everything sharing a group. A finger can slide between controls that
share one — that is what makes rolling across face buttons work, and what lets a thumb hold `L3` and
then move the stick — but **every pixel of that rectangle that is not a control is dead**. A touch
landing there is refused, and, measured on the reference device, a refused touch is *not* passed to
the application underneath. Two grouped controls in opposite corners therefore make one
screen-covering window and the game stops receiving touches. It was editable only by hand, and there
was no way to see it.

**How it is known.** The platform behaviour behind it is **Measured** on the reference device and
recorded in `Clustering.kt`. The screen itself is Unverified.

**Cost.** Grouping is still declared rather than inferred, and the editor does not stop a user
making a window that covers the screen — it shows the percentage and turns it orange. `ADR-007`'s
spirit: say what is true, do not overrule the person.

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
