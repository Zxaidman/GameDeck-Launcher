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

### `BUG-11` — `Edit layout` reachable in portrait — **closed `0.0.27-dev`**

Three buttons in one non-wrapping row put the third off the edge in portrait, with nothing to scroll
sideways: the editor could not be opened with the phone upright. Split into two rows, `Edit layout`
first. **Measured** — the project owner confirmed it on the device.

**What it did not fix, and cost a round to learn:** the same fault existed in the editor's own tool
row, where `⋮ values` was the seventh button in a panel a quarter of a landscape screen wide. One
non-wrapping row was fixed and the next was left standing. `BUG-16` wraps them properly.

---

### `BUG-12` — Turning the phone keeps you in the editor — **closed `0.0.27-dev`**

`MainActivity` declares `configChanges` for orientation and the sizes that come with it, so a
rotation re-lays-out instead of rebuilding the activity. What this really saves is not the
navigation — it is **every unsaved edit**, which was being thrown away by turning the phone.

**Measured** on the device. It is also what made `FEAT-17` possible: an editor that survives a
rotation can ask for one.

**The standing obligation it creates:** handling a configuration change means Kestrel is now
responsible for anything that should change with it. Compose re-reads `LocalConfiguration` and the
editor re-measures from it, which covers what exists today; a future screen that depends on
configuration-specific resources has to be checked rather than assumed.

---

### `BUG-13` — The numbers dialog fits and scrolls — **closed `0.0.27-dev`**

Two fields to a row and a scrolling body. Four stacked fields in a dialog on a landscape phone put
width and height below the fold with no way to reach them — a feature that worked and could not be
used. **Measured** on the device.

---

### `BUG-14` — A minus sign without a minus key — **closed `0.0.27-dev`**

`± offsetX` and `± offsetY` flip the sign of whatever is in the field, including a half-typed one.
It does not depend on which keyboard someone has, which was the part that could not be relied on.
**Measured** on the device.

The underlying question — whether that keyboard offers a decimal point but no minus — is still not
answered, and no longer needs to be.

---

### `FEAT-13` — Three parts canvas to one part tools — **superseded `0.0.28-dev`**

Built in `0.0.27-dev`, confirmed working on the device, and replaced one round later by `FEAT-16`.

It is recorded rather than deleted because the lesson is worth keeping: three quarters of the screen
was better than half and still an answer to the wrong question. The canvas does not want *most* of
the screen — it is a picture of the screen, so it wants the screen. A permanent panel of any width
is that much of the picture missing.

What survived from it: `⋮ values` as a filled button among the others rather than a text button
nobody could see.

---

---

## Built, awaiting confirmation — `0.0.28-dev`

### `BUG-15` — The pad uses the whole screen, and so does the canvas

**The decision.** *"i want the gamepad and it's window to always use the whole screen 2400x1080."*

**What was wrong before it.** `BUG-10` made the canvas the whole display and left the pad in the
usable area. So four controls sitting plainly on the screen were reported as *"outside the usable
screen"* and outlined in orange — true of the usable area, false of the phone, and the project
owner's screenshots said so. A warning that fires on correct work is worse than no warning.

**Built.** `DeviceSurface.forPad` gives one answer to *what surface is a pad laid out against*, and
the overlay and the editor both ask it. The overlay's windows gained `FLAG_LAYOUT_IN_SCREEN` and
`FLAG_LAYOUT_NO_LIMITS` — without those the window manager keeps every window inside the area it
hands out, so a control the layout puts against the top of the screen quietly arrives below the
status bar — and a cutout mode of `ALWAYS` on API 30 and above, `SHORT_EDGES` on 29.

**This closes `BUG-1` and `BUG-2` from the other direction.** The "use the notch area" setting
existed and never reached the overlay: the application obeyed it and the pad — the only thing on
screen while playing — did not. It does now, and the same setting decides what the canvas draws.

**How it is known.** Unverified on the device. The window flags are documented platform behaviour,
not measured here, and that is exactly the kind of claim this project does not treat as settled.

**The cost, stated once.** A control under the status bar shares that strip with the system: a swipe
from the top edge will sometimes open the shade instead of pressing the control. The band stays
drawn on the canvas so that is visible while arranging rather than discovered while playing.

---

### `BUG-16` — Tools wrap instead of running off the edge

The control and window tools are `FlowRow`s. Seven buttons in a fixed row lost the last one off a
narrow panel, which is how `⋮ values` came to exist in portrait and not in landscape. Unverified on
the device.

---

### `FEAT-16` — The editor is the canvas, with three buttons floating on it

**Asked for.** *"instead of 3:1 canvas whole screen display the Canva and with three floating
button, options … save, exit. in both orientation."*

**Built.** The canvas is the entire screen — no title bar above it, no margin around it, nothing
beside it. Three buttons float on it: **Tools**, **Save**, **Exit**. Everything that was a side
panel is now a sheet that opens over the canvas and closes again, down one edge in landscape and up
from the bottom in portrait.

They float **in the middle of the screen**, which is the one region a pad never occupies: controls
belong to the corners and edges a thumb reaches, and the centre is what a game is played through.
Any other position would have put them on top of the thing being arranged.

Under them, on a dark plate so they stay readable over whatever is drawn behind: the layout's name,
whether there are unsaved changes, the selected control in both units, and any warning.

**Added without being asked, and it should be flagged:** leaving with unsaved changes now asks
first. "Nothing is saved until it is saved" is a rule that makes an accidental exit expensive, and
the exit button became a great deal easier to press.

**How it is known.** Unverified on the device.

---

### `FEAT-17` — The preview turns the phone

**Asked for.** *"pressing portrait preview should rotate the app to portrait view for editing period
only once outside app should follow chosen orientation."*

**Built.** The orientation buttons ask the activity to turn. The editor then measures the
orientation it is really in, and closing the editor calls `applyDisplayPreferences`, which puts the
orientation back to whatever the display settings say.

**What this removes is better than what it adds.** The old toggle drew a small picture of the phone
turned — a strip too narrow to work in — and the system bars in that picture were an estimate,
because only the orientation the phone is in can be measured. That estimate is gone from the code
along with the feature that needed it.

**How it is known.** Unverified on the device.

---

### `FEAT-14` — Grid steps narrowed

`0.01` was too fine to see and `0.25` too coarse to place anything with. The steps are now **0.02,
0.04, 0.06 and 0.10** of the shorter side, still labelled with the pixels they come to on this
phone. Unverified on the device; the rest of `FEAT-14` was confirmed working.

---

### `CRIT-5`, `BUG-10`, `FEAT-11`, `FEAT-12` — still `testing`

`CRIT-5` and `BUG-10` do not close until the pad on the phone matches what the editor drew — that is
the test that has failed twice. `FEAT-11` and `FEAT-12` are open only on the small changes above.

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
