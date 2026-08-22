# Changelog

**Document:** `CHANGELOG.md`  
**Status:** Active — records only what has actually been established  

All notable changes to Kestrel will be documented in this file.

The project is currently in an early architecture and feasibility stage, so this changelog intentionally documents only decisions and artifacts that have actually been established.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/). Semantic versioning will be used once actual releasable software versions exist.

---

## [Unreleased]

### `0.0.29-dev` — Both Renderers Draw The Same Pad

The project owner found the third cause of a symptom that had been chased for three rounds, and
found it from a screenshot: *"buttons size representation in Canva is not aligned with actual gamepad
size."*

**It was not aligned.** The overlay resolves `placement.scaledBy(controlScale)` — 0.85 by default —
and the editor resolved `placement` and nothing else. Every control on the canvas was drawn about
17% larger than the pad draws it, and four controls that fit on the phone at 85% were reported as
leaving the screen at 100%. `BUG-10` was real, `BUG-15` was real, and neither was the whole answer:
the canvas was the wrong shape, then the pad was on the wrong surface, and underneath both the two
renderers were drawing at different sizes. A canvas exists to make that kind of fault visible and
could not, because it had the fault too.

The canvas now resolves at the same scale the pad is showing, and dragging still writes the
**unscaled** number to the file. That distinction matters: the document is the pad at full size and
the setting is applied on top of it, so folding the setting into the file would shrink the layout a
little further with every drag.

**The canvas is now exactly the size of the screen.** The 4% margin was left over from when it
shared the screen with a panel. At no margin, previewing the orientation the phone is in, the canvas
is 1 : 1 with the display — which makes "does the pad match" a question anyone can answer by
looking.

**Long press a control** for the things done to one control: size, shape, copy and paste. Copy takes
size and outline and **not position** — two controls in the same place are two controls, one of which
cannot be pressed. Paste appears only within a family: the sticks and the pad in one, everything
pressed in the other. A face button's size means nothing on a stick, so the option is absent rather
than greyed out.

Also: rotation becomes a fourth floating button and leaves the tools sheet, because turning the phone
is done *while* arranging rather than configured beforehand; and the home page title scrolls with
everything else instead of holding a band of a small screen permanently.

**Six items closed on the device**, including the two oldest bugs on the list: the pad uses the notch
now, and the setting that was supposed to control that finally reaches it. Fourteen items are `done`.

**Decided, not yet built:** a layout will hold a separate arrangement per orientation, one document
with two placement sets. It changes the file format, so it gets its own round and its own test cycle
rather than riding along with an interaction change.


### `0.0.28-dev` — The Pad Takes The Whole Screen

Four items closed on the device, one superseded a round after it was built, and the question the
last two releases kept getting wrong was settled by the project owner rather than by another guess.

**Which screen is the pad on?** `0.0.27-dev` drew the whole display on the canvas and went on
placing the pad in the usable area, so four controls sitting plainly on the screen were reported as
*"outside the usable screen"* and outlined in orange. The screenshots said otherwise, and they were
right: the warning was true of the usable area and false of the phone.

The answer is the whole display, cutout and bars included. `DeviceSurface.forPad` gives one answer
to *what surface is a pad laid out against* and the overlay and the editor both ask it. The overlay's
windows gained `FLAG_LAYOUT_IN_SCREEN` and `FLAG_LAYOUT_NO_LIMITS`, without which the window manager
keeps every window inside the area it hands out — a control the layout puts against the top of the
screen quietly arriving below the status bar is exactly how the pad and the editor came to disagree.

**This closes `BUG-1` and `BUG-2` from the other direction.** The "use the notch area" setting
existed and never reached the overlay: the application obeyed it and the pad — the only thing on
screen while playing — did not. Now the same setting decides both, and the band the system takes is
drawn on the canvas rather than cut out of it. The cost is real and is said once: a control under the
status bar shares that strip with the shade.

**The editor is the canvas now.** No title above it, no margin around it, nothing beside it. Three
buttons float in the middle of the screen — Tools, Save, Exit — which is the one region a pad never
occupies, because controls belong to the corners a thumb reaches and the centre is what a game is
played through. The tools open as a sheet and close again. `FEAT-13`'s three-to-one split lasted one
round and is recorded as superseded rather than deleted: three quarters was better than half and
still an answer to the wrong question. A picture of the whole screen wants the whole screen.

**Previewing the other orientation turns the phone.** It used to draw a small picture of the phone
turned — a strip too narrow to work in, with system bars that were an estimate, because only the
orientation the phone is actually in can be measured. The estimate is gone from the code along with
the feature that needed it. Leaving the editor puts the orientation back to the setting.

Also: tools wrap instead of running off a narrow panel, which is where `⋮ values` had been
disappearing in landscape; grid steps drop the two useless extremes and keep 0.02 to 0.10; and
leaving the editor with unsaved changes asks first, now that the exit button is much easier to press.

**Closed on the device, and written up in `done-list.md`:** `Edit layout` reachable in portrait, the
rotation that no longer discards unsaved work, the numbers dialog that fits and scrolls, and the `±`
buttons. Six items are now `done`.

**Still not measured.** Everything built this round is `testing`. The window flags are documented
platform behaviour rather than something observed here, which is precisely the kind of claim this
project does not treat as settled.

**Not built, and blocked on a decision:** a layout that holds a separate arrangement per
orientation. It is a schema change either way, and the choice between one document with two
placement sets and two documents tied by a profile is the project owner's to make. Recorded as
`FEAT-15` with a recommendation.


### `0.0.27-dev` — The Canvas Becomes The Phone

The first device test of block 1 closed two items and failed on five points. The most important
failure is the one the previous entry claimed to have fixed.

**The canvas was still not the phone.** It drew 2289 × 927 on a 2400 × 1080 screen — the *usable*
area, bars and cutout already subtracted. Those are 2.47 : 1 and 2.22 : 1, which is visibly not the
same shape, so `0.0.26-dev` had corrected the scale of the lie and not the lie. And it is exactly
why the pad still did not match the editor: controls drawn hanging over the canvas edge are pushed
back inside by the window manager on the phone, because a window is laid out within the area the
system gives it.

The canvas now draws the **whole screen** and shades the band the system takes, with the usable area
outlined inside it. `LayoutSurface` already carried insets and `resolve` already placed controls
inside them, so nothing about placement changed — only what is drawn. A control that leaves the
usable area is outlined in orange and counted in the panel, with the reason: the phone will not put
a window there, so the pad will not match.

**The grid moves to the layout's own unit.** *"the button is 0.12 and the grid is 32px both are
different scales."* Steps are now fractions of the shorter side — 0.01 to 0.25 — labelled with the
pixels they come to on this phone, and the selected control is shown in both. It also removes a
limitation that had been written up as a property of the grid and was really a symptom of the wrong
unit: 0.01 is exactly the precision the file stores, so a snapped control lands on a number the file
can hold.

**Turning the phone no longer throws away the work.** The activity handles the configuration change
instead of being rebuilt by it. What that saves is not the navigation — it is every unsaved edit,
which a rotation was discarding.

Also: the canvas takes three quarters of the screen and the tools one quarter; `⋮ values` becomes a
filled button in the row with the rest, having been a text button nobody could see; the numbers
dialog puts two fields to a row and scrolls, so width and height are reachable in landscape; `±`
buttons flip the sign of an offset, because a numeric keyboard cannot be relied on to offer a minus;
and `Edit layout` is reachable in portrait, where it had been off the edge of a row that does not
wrap.

**Closed by the device test, and written up in `done-list.md`:** the square that drew as a rectangle,
and the window editor. Two of the five block-1 items are now `done` rather than `testing` — the
first entries this project has closed through the queue.

**Still not measured.** Everything built this round is `testing`. Build and lint pass; that is not a
claim about a phone.


### `0.0.26-dev` — The Editor Draws The Phone

Block 1 of `todo-list.md`, built in the order the project owner set. Five items, one screen, and one
idea underneath all of them: **an editor that lies about what it shows is worse than a text file**,
because a text file never claimed to be a picture of anything.

**The canvas is the device.** The layout is arranged inside a bordered rectangle with the phone's
own aspect ratio, scaled to fit whole and never scrolled. Before this it was drawn at the shape of
whatever room the screen gave it — close to ultrawide on the reference device — so controls appeared
to overlap that did not, and, worse in the other direction, controls that did overlap could look
clear. `platform/display/DeviceSurface.kt` now answers *what part of this screen can a pad be put
on* once, and both the overlay and the editor ask it rather than each keeping a copy.

**The screen is a dock and a panel.** The canvas is fixed on one side, the tools scroll on the
other, and which side depends on the shape of the editor's own window rather than the shape of the
phone being drawn — two different rectangles that had been conflated. A preview toggle shows the pad
in the orientation the phone is not currently in, because one layout has to work in both and a pad
that fits in landscape and overlaps itself in portrait has shipped here once already.

**A square is a square, in one place.** The rule that a square takes the shorter of its two sides
lived in two copies and they disagreed: the overlay applied it and the editor's preview did not.
`PixelRect.shapedAs` and `LayoutElement.effectiveShape()` are now the single owner, the overlay's
copy is deleted, and the editor hit-tests with the same outline it draws with.

**Dragging states a position rather than accumulating deltas.** `Placement.centeredAt` is the
inverse of `resolve`, and it exists because snapping cannot be written any other way — a snap is a
claim about an absolute position, and a sum of small deltas drifts. A grid from 32 to 256 px and two
snapping modes sit on top of it, with edge snapping winning over the grid per axis: lining up with
the control next door is a statement about this layout, and landing on a grid line is a statement
about the screen.

**A window editor, because the most consequential setting was invisible.** Which controls share a
window is what decides whether a thumb can slide between them — and every pixel of that window that
is not a control is a pixel the game underneath stops receiving touches through. Two grouped
controls in opposite corners make one screen-covering window. That was editable only by hand and
could not be seen at all. It is now a mode on the same screen, with each window drawn, its share of
the screen given as a percentage, and anything past a quarter turned orange.

**Nothing here is measured yet.** The full build passes with lint, the new geometry is unit-tested,
and neither of those is a claim about a phone. All five items sit at `testing` in `todo-list.md`
until the project owner runs them.

Two new documents. `todo-list.md` gained a phase on every entry — `pending`, `building`, `testing`,
`done` — so the state of the queue is readable without asking. `done-list.md` is the receipt: an
item is written there when it is confirmed on the device, with what was asked for, what was built,
how it is known to work, and what it cost.


### Backlog: A Second Round, And Two Answers From The Code

`todo-list.md` grew by seven items and gained the two answers the project owner asked for. Both were
read out of the source rather than recalled, because both decide what gets built next.

**An offset runs to a control's centre**, inwards from its anchor, in fractions of the screen's
shorter side — the same unit as width and height, which is what lets a control and its offsets
scale together instead of drifting apart as the size setting moves.

**A window is the enclosing rectangle of a declared group, and its empty space is dead.** Put a
stick in one corner and its press in the other, and they stay in one window because grouping is
declared rather than inferred — a window covering the screen, with every pixel that is not a control
refusing touches that the platform then does not pass to the game underneath. That answer is the
whole argument for a window editor, and it is why the editor has to show the rectangle rather than
just the group name.

Added: a device-ratio editing canvas as the next thing built (`CRIT-5`), a window editor, a grid
with snapping, and direct numeric entry (`FEAT-10`–`FEAT-12`); three bugs from this round — a
trigger drawing its value twice, a trigger too slow to register, and a square that draws as a
rectangle in the editor but correctly once saved (`BUG-7`–`BUG-9`).

The recommended order was rewritten around the canvas: the editor becomes truthful first, then gains
tools, because all three tools draw on the same surface and doing them first means building it
three times.

No code changed. Nothing is implemented until the order is confirmed.


### A Single Backlog, In The Repository

`todo-list.md` at the repository root. Requested by the project owner, and it changes how work is
chosen here: nothing is built next because it was the last thing discussed — it is built because it
sits highest in a written list that both sides can see.

Six sections, in the order asked for: **critical** work that blocks a release, **errors and bugs**
found by testing, **features** not yet started, **what works now** with the evidence that says so,
**pending scope** from the phase plan, and the project owner's own list as it arrives.

Two conventions the list carries, because a backlog without them rots:

- **Every item is graded by evidence** — Measured, Reported, Reasoned, Unverified. A bug someone hit
  on a device and a bug inferred from reading the code are not the same item, and the list says
  which is which rather than letting both read as fact.
- **Every item is dated and stays.** Items are closed by being marked done, not by disappearing, so
  the file records what was decided against as well as what was built.

The round of testing that produced it is recorded in full: the notch is still not used by the pad
itself, `HOW-TO-EDIT.md` was not written where the project owner looked for it, sensor-portrait is
being dropped as useless, and everything else tested passed unchanged.

No code changed with this entry. Nothing is being implemented until the list is complete and an
order is agreed.


### Phase 3 — A Layout Editor, On Its Own Page

Editing a layout by moving it rather than by typing numbers into a file. Requested first of the
three remaining pieces, and it earns being first: nobody can picture `offsetX: 0.22` on a phone they
are holding, which is the confusion the project owner reported in exactly those words.

**The file stays the truth.** The editor writes the same document a text editor would, and
everything it can do can still be done by hand. What it adds is the one thing a text editor cannot —
seeing where a control is while deciding where it should be.

Three rules it keeps because they are the schema's rather than the screen's:

- **A built-in is never edited.** Pressing Edit on one duplicates it and edits the copy. The rule is
  kept by doing the duplication rather than by refusing the request — somebody who presses Edit
  wants to change their pad, not to learn why they cannot.
- **Nothing is saved until it is saved.** Dragging changes what is on screen; the file changes when
  the button is pressed. An editor that wrote every frame of a drag is an editor with no way to
  change your mind.
- **Dragging moves the control the way the finger went.** An offset is measured inwards from its
  anchor, so a control pinned bottom-right moves left as its offset grows. The sign is flipped
  inside the editor rather than being made the author's problem.

It takes the whole screen, because arranging a pad is a spatial job and a preview squeezed above a
list is a preview of the wrong shape. Select a control, drag it, then size, height, shape and anchor
from the bar underneath. The centre anchor is deliberately not offered: a control anchored to the
middle of the screen is one no thumb can reach while holding a phone.

### A Guide Written Beside The Layouts

`HOW-TO-EDIT.md` is written into `Kestrel/layouts/` whenever a layout is copied there, because JSON
cannot carry a comment and a schema document in the repository does not help somebody holding a
phone with a file manager open.

It answers the thing that was actually confusing — **offsets are measured inwards from an anchor, as
a fraction of the screen's shorter side** — and then every field, what `group` really decides (which
controls a thumb can slide between), and the rules the reader will refuse a file for.

### Kestrel's Own Screen Uses The Space It Asked For

Full screen and the cutout were being applied to the window and then given straight back: the
content was still padded for the bars it had just hidden, leaving a band of screen nobody could use
— visible in the screenshots as a white strip above the interface and the title behind the overlay.
The padding follows the settings now, so turning them on changes Kestrel's own screen and not only
the overlay.

**Orientation, narrowed to the answers that work.** Reverse landscape added, at the project owner's
request; `auto` now honours the phone's rotation lock rather than overriding it. There is no
reverse-portrait option because most phones do not support the orientation at all, and an option
that does nothing on the device in front of you is worse than one that is absent.

### Phase 2 — A Chosen Folder That Went Away, And Three Smaller Faults

**Deleting the chosen folder left Kestrel claiming to use it while every write failed.** The store
was resolved once and remembered forever, so a grant that outlived the folder it pointed at kept
being reported as working. It is re-checked now — at most every few seconds, because the check is an
inter-process call — and when the folder has gone Kestrel falls back to its own directory and
**says so**, naming what happened and what it costs. It cannot recreate the folder: the grant was
for that document, and it died with it.

**A dead privileged service is a lost session, not a log line.** `DeadObjectException` was reported
as one failed write among hundreds, which buried the only fact that mattered. It now stops the
engine and says that Shizuku went away and the session has to be opened again.

**The progress ring on a rectangular trigger was a circle.** The fill had been made shape-aware and
the ring around it had not, so a rounded rectangle wore the outline of a control that was not there.
A non-circular trigger now fills its own edge from the bottom, the same reading in the right shape.

**Every editable field is written to a layout, including the ones at their default.** The opposite
was tried first — omit anything default, so the file says only what it means — and it failed the one
job the file has. The project owner copied a layout, went looking for `shape`, and found nothing:
the control was a circle, so it had not been written. **A field that is absent is a field nobody
knows exists.** `null` is written rather than omitted for optional fields, so every element has the
same shape and what is missing is visible as missing.

And a wart found by a test rather than a person: `DocumentHeader` treated everything outside its own
four fields as unknown, so a layout's header carried a second and wrong copy of the whole body —
enough to stop a document comparing equal to itself after a round trip. The layout keeps the
document's unknown fields; the header keeps none.

### Phase 1 — Kestrel Takes The Whole Screen, And Faces The Right Way

Three settings, all of them defaulting on, all of them applied the moment they change rather than at
the next launch.

**Full screen.** A pad drawn under a status bar loses the space to it, and a notification sliding in
over a control mid-play is worse than not seeing the time. The bars stay reachable by swiping,
because hiding something is not the same as taking it away.

**Drawing under the cutout.** This is what makes a phone with a notch the same shape as a phone
without: refuse it and the platform letterboxes the whole application below the notch, which on a
wide screen is a black band and less room for controls. Some people would rather have the band than
a control beside the camera, so it can be turned off.

**Orientation**, with six answers: auto, landscape, portrait, sensor landscape, sensor portrait, and
whatever the phone's own rotation lock says. Landscape is the default because a handheld is held one
way — and it is a setting because a phone is not a handheld, and somebody arranging a layout on a
sofa should not have to turn the room.

All three live in `settings.json` under `display`, so they travel with the folder like everything
else.

### Phase 2 — Positioned The Way The Window Manager Thinks

The overlay computed absolute screen coordinates from the display and handed them to the window
manager, which places an overlay inside **what is left after the system bars**. With a status bar
showing, every control moved down by its height — the bottom row ran off the screen and the pad
overlapped itself. Reported from the device, and visible in a screenshot with the bar up.

Two changes, and each fixes half of it.

**Clusters hang from an edge rather than sitting at a coordinate.** Gravity and a margin from the
nearest edge mean the same thing in both coordinate spaces, which is what an anchor was always
supposed to say: a layout that says *bottom right* means it whatever the usable area turns out to
be.

**The surface is the area the controls actually have.** `currentWindowMetrics` minus the system
bars, taken with `getInsetsIgnoringVisibility` — **whether or not the bars are showing**. A status
bar can appear at any moment, and controls that move when it does are controls a thumb has to find
again mid-play.

The layout tests now include the same phone with the bars taking their share, alongside the full
display, at every size and both orientations. A layout that only fits the whole screen is a layout
that overlaps itself the moment a notification arrives.

### Phase 2 — Controls Have Shapes

`shape` on a layout element: `circle`, `square` or `rectangle`. Asked for by the project owner, and
the pad had been round-only because nothing had ever said otherwise.

Separate from the kind, and the separation is the point: a kind says what a control **does**, a shape
says what it **looks like**. A shoulder button is a rectangle on most pads and a circle on some, and
nothing about which changes what it sends.

**The shape decides where the control can be pressed, not only how it is drawn.** A rectangle
hit-tested as a circle would have corners that look pressable and are not — a fault a player feels
and cannot describe. `square` is stated rather than left to equal width and height, so a control
stays square when a hand-edited file makes them slightly uneven.

Sticks and d-pads stay round whatever the shape says. Deflection is a distance from a centre, and a
rectangular stick would reach further along its diagonal than along its sides.

The shipped layout is unchanged — every control is still a circle, because that is the arrangement
that was tested and approved. The option is there for the copy in the user's own folder.

### Phase 2 — The Pad Is A File You Can Edit

Four faults from the first data-driven build, and one of them was the point of the exercise.

**The layout was scaled twice.** Its numbers were measured from the pad on the reference device at
65%, and then the size setting multiplied them again — so every control came out at 42% of what the
same setting used to give. The document now describes the pad at **full size**, the default setting
brings it back to what a hand settled on, and `BuiltInLayoutsTest` checks every control against
every size on the slider in both orientations rather than at one size in one orientation. The
maximum is now the largest arrangement that actually fits: a setting that can produce an overlapping
pad is a setting that will produce one.

**Which controls share a window is declared, not inferred.** It had been derived from how close two
controls were drawn, and that failed on the very layout it was written for — the gap that had to
mean *together* and the gap that had to mean *apart* were fifteen pixels apart, so the answer
flipped with rounding and with the size setting. **A gesture that works at one size and not another
is worse than one that never worked.** Each element now carries a `group`, and a test asserts the
grouping is identical at every size and both orientations. Select and Start are back in their
shoulder rows, where they were when people played with them.

**Resizing moves the windows instead of replacing them.** A slider produces a change every frame,
and removing and re-adding eight windows that often left visible trails and lagged behind the thumb.
Since grouping is declared, a size change cannot alter it — so the same windows are re-measured and
the controls inside told where they now are. Anything held stays held.

**Turning the phone rebuilds the pad.** Every position is a fraction of a surface and rotating
replaces the surface, so the controls had been staying where the old screen put them until the user
hid and showed them again. The session service hears the configuration change and refreshes.

**And the point of all of it: `Copy layout to my folder`.** It duplicates the shipped layout into
`Kestrel/layouts/` as a user copy, points the settings at it, and redraws. From then on the file in
the user's own folder is what the pad is — edit it in a text editor, press **Reload layout**, and the
controls move. That is also the built-in → duplicate → user copy step `docs/CONFIGURATION_SCHEMA.md`
requires, made visible rather than described: the shipped layout is never edited because it cannot
be.

Numbers written to any document are rounded to two decimals, and the sliders snap to the same. A
drag produces `0.34827995`, and `settings.json` is a file the user is invited to open; the precision
discarded is far below what a thumb can set or an eye can see.

### Setup Is A Page

Requested by the project owner, and it earns the screen. On a fresh install every step is missing
and the diagnostics screen behind it cannot do anything, so a card would have been a small box above
something useless. **Skip for now** still hands over the whole application, because a wizard that
will not let you past it traps anyone whose phone answers a question differently from expected — and
it returns next launch, because what it was hiding is still true.

The folder picker now opens at **`Kestrel` itself** rather than the top of storage. Where that folder
already exists the whole interaction is one tap on *Use this folder*.

**Kestrel still cannot create that folder itself, and this is a limit rather than an oversight.**
Making a directory at the top of shared storage needs `MANAGE_EXTERNAL_STORAGE` — access to every
file on the phone. Declaring a permission of that class is exactly what got Kestrel blocked by Play
Protect when the accessibility service was declared, measured in `ADR-006`. The picker costs one tap
once; the permission would cost every user their install.

### Project Definition

- Established the Kestrel product vision.
- Defined Kestrel as a gaming-focused Android launcher and virtual-controller environment.
- Defined Android phones running Android 10 or newer as the initial platform target.
- Deferred tablet and foldable support until the phone experience is sufficiently stable.
- Defined the initial application scope as:
  - emulators
  - game-streaming applications
  - cloud-gaming applications
- Deferred broad support for ordinary Android applications to a future version.
- Added manual application addition as a required fallback when automatic detection fails.

### Product Experience

- Defined the intended handheld layouts:
  - landscape with controller areas on both sides
  - portrait with controller area below the game
  - future dynamic layouts
- Defined scaling modes:
  - Fit
  - Fill
  - Stretch
- Defined initial aspect-ratio presets:
  - 4:3
  - 16:9
  - 18:9
  - 19.5:9
  - 20:9
  - 21:9
- Defined a future custom-aspect-ratio capability.
- Defined dynamic controller-space sizing.
- Defined skins as a separate visual layer from controller layouts.

### Controller System

- Defined proper gamepad-style input as the primary long-term input objective.
- Defined a capability-based input architecture.
- Defined fallback input for environments where the preferred backend is unavailable.
- Defined Xbox-style, PlayStation-style, Nintendo-style, generic, and emulator-oriented controller templates as planned initial families.
- Defined built-in controller templates as immutable.
- Defined user customization through duplication of built-in layouts rather than direct editing.
- Defined fully editable user-created layouts.

### Shizuku

- Defined Shizuku as an optional enhancement rather than a mandatory dependency.
- Defined separate capability handling for:
  - no Shizuku
  - Shizuku unavailable/stopped
  - Shizuku with ADB/shell privileges
  - Shizuku with root privileges
- Defined a requirement to validate Shizuku-based input capabilities before making them part of the production controller architecture.

### Configuration

- Chosen technology direction:
  - Native Kotlin
  - Jetpack Compose
- Chosen Android minimum:
  - Android 10 / API 29
- Chosen configuration direction:
  - JSON-first
  - data-driven
  - exportable/importable
  - schema-versioned
- Defined built-in configuration as immutable.
- Defined user configuration as duplicated/editable data.
- Defined application profiles.

### Community

- Defined an open-source, community-first direction.
- Chosen license:
  - GNU GPLv3
- Defined GitHub repositories as the initial community distribution mechanism for:
  - controller layouts
  - skins
  - profiles
  - compatibility metadata
- Deferred a proprietary cloud backend.
- Defined community content as untrusted declarative data.
- Defined validation requirements for imported/downloaded community content.

### Documentation

- Established:
  - `README.md`
  - `PRD.md`
  - `ARCHITECTURE.md`
  - `CONTRIBUTING.md`
  - `SECURITY.md`
  - `CODE_OF_CONDUCT.md`
  - `CHANGELOG.md`
  - `LICENSE`
- Established the intention to maintain architecture decision records.
- Established AI-assisted development and review principles.

### Engineering Direction

- Defined modular, capability-driven architecture.
- Defined separation between:
  - presentation
  - application/features
  - domain/core
  - Android/platform implementations
- Defined abstraction of input backends so experimental Android mechanisms can be replaced without rewriting the controller UI.
- Defined the requirement for real-device testing of Android-specific behavior.
- Defined Phase 0 as a technical feasibility gate before large-scale application development.

### Phase 0

- Defined `docs/PHASE-0.md`.
- Defined initial input-feasibility targets:
  - PPSSPP
  - Dolphin
  - RetroArch
  - Moonlight
  - Steam Link
- Defined testing across:
  - normal Android
  - Shizuku + ADB
  - Shizuku + root
  - other technically appropriate input mechanisms
  - touch/gesture fallback
- Defined testing requirements for:
  - digital buttons
  - analog axes
  - triggers
  - simultaneous input
  - hold/release behavior
  - lifecycle interruptions
  - controller/device recognition
  - repeatability
- Defined the requirement to distinguish touch simulation, key-event injection, axis/event injection, and true virtual gamepad/HID identity.
- Defined `ADR-INPUT-001` as the intended decision record for the production input strategy.

### Build Foundation Established

- Added a Gradle build: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and the
  Gradle wrapper pinned to 8.14.3.
- Added `gradle/libs.versions.toml` as the single declaration point for plugin and dependency
  versions, per `DEVELOPMENT.md`.
- Added two modules, keeping the module count small per `PROJECT_STRUCTURE.md` §24:
  - `:app` — Android assembly layer, containing a manifest, a single activity, and a placeholder
    screen. No feature, input, or configuration logic.
  - `:core` — Kotlin/JVM module, so that the dependency rule in `PROJECT_STRUCTURE.md` §21 is
    enforced by the compiler: Compose, Android UI, and Shizuku cannot resolve there.
- Added `core/common/Outcome.kt` — the typed success/failure result that domain code returns instead
  of throwing for expected failures, as required by `docs/CONFIGURATION_SCHEMA.md`.
- Pinned Android 10 / API 29 as `minSdk`, per ADR-004.

Verified, with the Android SDK installed:

- `./gradlew build` completes successfully — both modules compile, lint reports no errors, and the
  domain tests pass.
- `./gradlew :core:test` — 9 tests, all passing, on JDK 21 with Gradle 8.14.3.
- Every pinned version in `gradle/libs.versions.toml` resolved. AGP 8.13.2, Kotlin 2.2.21, Compose
  BOM 2026.05.01 and `compileSdk`/`targetSdk` 36 are confirmed mutually compatible.
- `app-debug.apk` builds with identity `io.github.zxaidman.kestrel`, label Kestrel.

Confirmed on hardware afterwards:

- Both APKs install and launch on a Redmi Note 13 5G running HyperOS 3.0.3, side by side, with no
  security warning shown during installation.

Not verified:

- No layout, skin, profile, input backend, overlay, or session behaviour exists.

### Phase 0 — Tier 0 Executed on Hardware

First real device evidence. See `docs/phase0/results/tier0-report.md` and the raw export beside it.

- Ran the harness on a Redmi Note 13 5G (Dimensity 6080, Android 15 / API 35, HyperOS 3.0.3).
- Recorded the baseline input inventory: eight devices, none of them a usable controller, as
  expected with nothing attached.
- Found that two devices on this stock unrooted phone are created through the kernel virtual-input
  facility by vendor components. This is the first evidence bearing on the highest-value tier: the
  mechanism exists and is in use on this hardware. It does not establish that an ordinary
  application or a shell-privileged process can reach it.
- Found that one of those devices advertises the gamepad source while advertising zero gamepad
  buttons and zero axes. A capability check based on source flags alone would report a controller
  present on this phone. This is why capability must be read from advertised keys and axes, and the
  harness records all three.
- Confirmed the volume keys originate from two separate hardware devices, so a backend must not
  assume one device covers a logical group of controls.
- Test 13 has a partial result: the harness survives backgrounding and re-registers its listener.

Not verified:

- No injection tier has been attempted. No evidence grade applies, since a grade describes a
  mechanism and no mechanism has been exercised. `ADR-INPUT-001` remains Pending.
- No physical controller has been attached, so there is no calibration reference for what a genuine
  controller looks like on this device.

### Phase 0 — Tier 1 Calibration Executed on Hardware

A second phone running remote-gamepad software was paired over Bluetooth to act as a controller,
supplying the calibration reference Tier 0 lacked. See `docs/phase0/results/tier1-report.md`.

- Recorded the signature of a genuine controller on the reference device: sources
  `KEYBOARD|GAMEPAD|JOYSTICK`, ten axes, twelve buttons, and a system-assigned controller number.
  Anything Kestrel creates must match this to claim an equivalent result.
- Found that every button carrying a system meaning is delivered twice on one scan code — notably
  `BUTTON_B` also arrives as `BACK`, and `START`, `THUMBL` and `THUMBR` all collapse to
  `DPAD_CENTER`. Input handling must match on the controller keycode and originating device, and
  discard the fallback, or it will double-count every press.
- Found that each trigger reports on two axes simultaneously, so the transformation layer must pick
  one per trigger rather than treat them independently.
- Found that the D-pad arrives both as hat axes and as synthesised key events, and that the left
  stick also synthesises directional keys past a threshold.
- Found that the system virtual device aggregates the capabilities of connected devices: it
  advertised four keys with nothing attached and sixteen with a controller attached. Capability
  detection must skip it, or it will report a controller present on a bare phone.
- Confirmed that dead zones are declared per axis by the device, so the transformation layer should
  read the declared value rather than hardcode one.

Not verified:

- This exercises the receiving half only. It does not show that Kestrel can create a controller for
  applications on the same phone, because the software used works by making a second device
  advertise itself as a Bluetooth peripheral to the first. The core question is unchanged and
  `ADR-INPUT-001` remains Pending.
- `BUTTON_A` was not pressed during the run, so one button has no observed delivery.

Noted for possible future work:

- The same mechanism suggests Kestrel could implement the peripheral role itself, turning a spare
  phone into a controller for a main device. `BluetoothHidDevice` has been public since API 28,
  within the project baseline. This is unverified, is not the core requirement, and would not help a
  user with a single phone; it would need its own decision record if pursued.

### Phase 0 Harness — Privilege Probe

- Added a Probe tab to the harness that reports the privilege state and runs read-only checks
  through a shell-privileged service, using Shizuku. This makes the virtual-device tier runnable
  from the phone alone, with no computer and no typed commands, which matters because the project
  owner is not a developer.
- The privilege state is reported as four separate facts — service running, permission granted,
  identity actually obtained, and version — implementing the model in `ARCHITECTURE.md` §14 and
  testing its central claim that none of those facts implies another.
- The probe reads only: device node existence, permissions and owning group, readability and
  writability from the obtained identity, presence of the helper command, and enforcement mode. It
  creates no device and emits no event, so the harness still cannot manufacture the result it
  measures.
- Probe output and privilege state are included in the export, so they become evidence.
- Added `dev.rikka.shizuku:api` and `dev.rikka.shizuku:provider` 13.1.5 to `tools/phase0` only, with
  the justification recorded in the module's build script and the entries added to
  `THIRD_PARTY_LICENSES.md`.

Verified:

- `./gradlew build` succeeds with lint clean.
- The product's runtime classpath was inspected and contains no Shizuku artifact, so the boundary
  required by ADR-003 and `PROJECT_STRUCTURE.md` §21 holds.

Not verified:

- The probe has never been run. Whether Shizuku binds, whether the service starts, and what the
  device node permissions actually are on this firmware are all unknown until it runs on hardware.

### Phase 0 — Tier 5 Privilege Probe Executed on Hardware

See `docs/phase0/results/tier5-probe-report.md`.

- The privilege chain works end to end: Shizuku bound, permission was granted, and the identity
  actually obtained was `shell`, uid 2000. Root was neither obtained nor expected.
- `/dev/uinput` is `crw-rw----`, owned by `system`, group `net_bt_admin`, and the shell identity is
  a member of that group. Both permission tests reported the node readable and writable.
- `/system/bin/uinput` is present on this build.
- **This is not yet a yes.** `test -w` calls access(2), which consults only the classic permission
  bits and is blind to SELinux. SELinux is Enforcing, the node is labelled `uhid_device`, and no
  actual open has been attempted. Policy is decided at open, and policy is where this usually fails.
- Found a second candidate path that was not previously believed available: the platform `input`
  command on this build advertises `gamepad`, `joystick` and `dpad` as injection sources, and
  accepts named motion axes via `--axis`. If it behaves as advertised, a shell-privileged process
  could deliver controller semantics with continuous axis values without creating a virtual device
  at all.
- Two paths now exist, both reachable from the phone alone: creating a virtual device, which could
  carry a real device identity, and injecting through `input`, which could not. No evidence grade
  applies to either yet and `ADR-INPUT-001` remains Pending.

### Phase 0 Harness — Actual Access Tests and User-Chosen Export

- Added an actual open-for-write test against the virtual-input node, because the permission-bit
  test cannot see SELinux and would otherwise have been mistaken for a positive result.
- Added injection attempts issued through the platform's own `input` tool in the shell-privileged
  process, covering the gamepad and dpad sources and both candidate analog-axis syntaxes.
- The command issued is written into the event log immediately before it runs and its result
  immediately after, so the log interleaves stimulus and response and a delivered event can always
  be traced to what caused it. The harness still does not synthesise events into its own window.
- Export now opens the system file picker so the destination is chosen by the user, replacing the
  previous write into the application's private directory, which was not reachable through an
  ordinary file manager. Sharing is now a separate action.
- Captured the full `input` usage text rather than a truncated head, which is what surfaced the
  gamepad and axis support above.

- Documented how to obtain an installable build without any toolchain: build artifacts are attached
  to every workflow run, and releases are published by tagging. Tag pushes must be done by the
  repository owner, since the development environment's git proxy refuses them.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: none of the new tests has been run on hardware.

### Phase 0 — Virtual-Input Access Confirmed

The decisive question is answered. See `docs/phase0/results/tier5-open-report.md`.

- A shell-privileged process obtained through Shizuku, with no computer attached, **opened the
  kernel virtual-input node for writing** on a stock unrooted device with SELinux Enforcing. The
  kernel denial log was empty, so policy permits it outright rather than permitting-and-auditing.
- This was the prerequisite most likely to fail, and it did not. The path that could produce a
  device with its own controller identity is open on this hardware. Creating and recognising such a
  device is still unproven.
- The full `input` usage text, captured intact, establishes a hard ceiling on the shell path:
  `motionevent` accepts only `x` and `y`, and the `--axis` option belongs to `scroll` alone. The
  shell path can therefore drive buttons, the D-pad and one analog stick, but **cannot address the
  right stick or the triggers**. Against `docs/PHASE-0.md` §29, which requires a working trigger, it
  cannot pass on its own. It is a fallback and a comparison baseline, not a candidate answer.
- The release mechanism works: the axis returned to rest and the repeat flood stopped. Two further
  repeats arrived after the release was issued, so a release must be issued early and confirmed,
  not assumed effective the moment it is sent. Measured repeat rate is about 15 per second.

### Phase 0 Harness — Virtual Device Creation Attempts

- Added attempts to create a virtual controller through the platform `uinput` helper, holding the
  device open for five seconds so it can be observed in the inventory and by the hot-plug listener.
- Two descriptor schemas are attempted, because the helper's accepted schema is undocumented
  on-device and its help output is empty. A rejection is informative: the error states what the
  schema requires.
- Button and axis numbers are Linux input-event constants, which are stable kernel ABI rather than
  values invented for this project.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: no creation attempt has been run. Whether the helper accepts either schema, whether a
device appears, and whether it carries controller semantics are all unknown.

### Phase 0 — A Virtual Controller Was Created

Milestone. See `docs/phase0/results/tier5-create-report.md`.

- The `uinput` helper accepted the descriptor and **a device named Kestrel Virtual Controller was
  registered with the input stack** on a stock unrooted phone, observed by an ordinary application
  through the standard hot-plug callback. Repeated across two runs, ids 9 through 12. Both the
  numeric and the named descriptor schema were accepted.
- This is the prerequisite for a device identity, which the shell-injection path can never provide.
- **It is not yet a pass.** The device was removed immediately in every case, well inside the window
  it was meant to be held for, and nothing about it was captured: the hot-plug callback recorded
  only its name, so its sources, axes and buttons — the properties that decide whether it is a
  controller at all — were never read. A device that exists momentarily and is never characterised
  is a strong signal, not evidence. `ADR-INPUT-001` remains Pending.
- Established a design consequence regardless of the outcome: the device lives exactly as long as
  the process holding its file descriptor. A production backend must own a long-lived process for
  the duration of a session, since losing that process loses the controller mid-session. This
  argues for a foreground service and must be reflected in `ARCHITECTURE.md` when the input backend
  is designed.

### Phase 0 Harness — Capture Devices as They Appear

- Input devices are now described **inside the hot-plug callback**, at the instant they appear, and
  the descriptions are kept in the export. This was the flaw that made the creation run inconclusive
  rather than decisive: the device was seen to exist but never measured.
- The helper is now started as a background process holding the device for 30 seconds, so it can be
  inspected in the inventory, and its liveness is reported — which distinguishes "the helper exited"
  from "the system rejected the device".
- Added a destroy action so a virtual device is never left behind.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: none of this has run on hardware.

### Phase 0 Harness — Quoting Regression, Found and Fixed

The 0.0.7 creation run failed for a reason entirely of this project's making, recorded here because
the evidence trail must show why a run produced nothing.

- 0.0.7 wrapped the helper invocation in a second `sh -c "..."` layer. The descriptor contains
  double quotes, so the shell broke apart inside the device name and the helper never ran. The
  device reported it plainly: `Virtual: no closing quote`. 0.0.6, which used a single level of
  quoting, had created devices successfully.
- The liveness check compounded it. Matching any command line containing "uinput" matched the very
  shell that was failing to run it, so the harness reported `helper alive=true` while nothing was
  running — a false positive that would have made a real failure look like a partial success.
- Fixed by writing the descriptor to a file using only single quotes, which the JSON never contains,
  and never nesting shells. The helper is launched from a single unnested command.
- The liveness check now matches the process name exactly, and reports the descriptor's size and
  first bytes so a malformed descriptor is visible rather than inferred.
- Destroy now matches exactly too, and re-checks after stopping.

The lesson is recorded rather than merely fixed: a harness that reports success without confirming
it is worse than one that reports nothing, because it converts a null result into a false one.

### One Signing Key, So An Install Is An Update

Reinstalling meant granting every permission again and losing every setting, and the cause was not
the settings — it was the **signature**. Android treats a signature as an application's identity, so
two builds signed by different keys are two different applications and the second cannot install
over the first. Gradle's default debug config generates a keystore on the machine that builds, and a
CI runner is a fresh machine every time, so **every build was signed by a different key**. The
workflow's own release notes had been saying so: *"signed with a per-machine debug key, so a newer
build may not count as an update."*

`signing/kestrel-testing.p12` is committed, and both modules use it. Verified rather than assumed —
`apksigner` reports the same certificate for both APKs:

```text
Signer #1 certificate DN: CN=Kestrel Testing Key, OU=Testing, O=Kestrel
SHA-256: 69d5958dc3fda5d46a53280b7365ea6e74c085f88703f63acab7b8515d7a4f95
```

**The key is public and must never sign a release.** Anyone can use it to sign an application that
installs *as an update* over a user's Kestrel — acceptable for builds people are testing
deliberately, unacceptable for builds people are trusting. `signing/README.md` carries the reasoning
and the steps for a real key; `docs/RELEASE.md` makes it a gate.

**One more uninstall is needed.** The key changed, so this build cannot install over one signed by
the old per-machine key. From the next one onwards, installs are updates.

### Files Live Where The User Can Reach Them

**Everything Kestrel keeps now goes in a folder the user chooses, at the top level of shared storage
— beside `Android`, not inside it.** It survives uninstalling Kestrel, opens in a file manager, and
copies to a computer like any other folder. `docs/STORAGE.md` is the decision.

The alternative was `MANAGE_EXTERNAL_STORAGE`: access to every file on the phone, a restricted
permission, and an unknown install-time cost. Having just watched a manifest declaration turn
Kestrel into an application Play Protect blocks outright (`ADR-006`), a second restricted
declaration is not something to add on a hunch. **The Storage Access Framework grants one folder,
declares nothing, and asks a question whose answer is exactly the folder the user wanted.**

One consequence stated plainly: on Android 11 and later the picker will not allow selecting the root
of shared storage itself. A folder inside it — `Kestrel` — is selectable, which is what was wanted.

**Never required.** With no folder chosen Kestrel keeps working from its own directory and says on
screen that what it writes there dies with an uninstall — `docs/DEGRADED_STATE.md` §2, the
application does not refuse to start because something is unavailable. Choosing a folder **copies
what is already there into it**, because starting somebody's settings again from defaults as a
reward for answering a question would be a punishment for answering it.

`core/storage/DocumentStore` is the interface, with a memory implementation that the tests describe
the promises against, and two platform implementations behind it. Folder names are a **fixed list**
rather than paths — a security property, since an imported document that could choose its own path
could choose one outside the folder. Document names are validated before reaching a filesystem: no
separators, no `..`, nothing over 96 characters, and none of the names Windows reserves, because
copying the folder to a computer is supported and a file that cannot be copied is one that quietly
does not get backed up.

Two faults were designed out rather than found later, both specific to the framework: `createFile`
invents a new name when one is taken — `settings (1).json` — so a blind create would leave the real
file untouched while appearing to succeed; and an output stream opened without `"wt"` leaves the
tail of a longer document behind a shorter one, producing a file that parses as neither.

### Settings Are A Document, And Therefore Survive

Kestrel had never kept a setting. Control size and stick shaping were adjusted, used, and lost when
the process ended — which is why every test run began by setting the same things up again.

`settings.json` is a configuration document with a schema version, in the chosen folder, beside the
layouts. Readable, editable by hand, copyable to another phone, and kept when Kestrel is
uninstalled. **Settings that can only be changed from inside the application are settings that
disappear with the application.**

Three behaviours that are decisions:

- **A file that cannot be read is left alone.** Kestrel runs on defaults, says so, and refuses to
  save over it. A file that failed to parse may be one the user can fix; replacing it with defaults
  would destroy the only copy while looking like recovery.
- **Fields Kestrel does not recognise are written back**, so a settings file from a newer build read
  and saved by an older one keeps what the older one did not understand.
- **Writing is deliberate.** A slider produces a value every frame; state changes as it is dragged
  and is persisted when the drag ends, so one decision is one write.

`:core` tests: **181, all passing.** All of it is untested on a device — the picker, the grant, and
whether the folder survives an uninstall are the next things to find out.

### The Game Stage Spec, Assessed

`docs/inbox/ideas/Game-stage-and-veiwport/ASSESSMENT.md`. The spec asks in its §18 for conflicts to
be flagged in a specific form rather than worked around, and there is one.

**Kestrel cannot place an external application's picture inside a rectangle it controls.** The
spec's viewport is *"the actual game/application display rectangle inside the Game Stage"*, and for
every target Kestrel launches, that window is drawn by the platform at the size the platform gives
it. `ARCHITECTURE.md` §22 and `CLAUDE.md` §5 already say so; the spec is written from desktop
frontend conventions, where the frontend owns the surface the game draws into. On Android it does
not and cannot be given it.

The minimal change is an **inversion**, and most of the spec survives it: instead of a container
that positions the game, the stage becomes a description of where the picture is expected to be, and
therefore where Kestrel's own controls and art go around it. Aspect ratio and alignment become a
prediction of the letterbox a target will produce rather than something imposed on it. What does not
survive is Fill, Stretch and Integer Scaling as things Kestrel applies — those belong to the target,
and most emulators already expose them.

Nothing is being built from it. It lands in Phase 4 and needs a session that can launch a target,
which does not exist.

### Release Criteria Recorded

`docs/RELEASE.md`. The project owner's gate for `v0.1.0`: overlay, controller editor, gaming session
and Shizuku finished, CI green, no known defect outstanding. Tagging `v*` already publishes a
release with both APKs attached, so the mechanism exists.

Three things are recorded as also required, because they are the difference between a build for one
person and a build for anyone: a release key that is **not** in this repository, release notes that
say `ADR-INPUT-001` is scoped to one device, and a compatibility document reflecting what was tested
rather than what is expected to work.

### Phase 2 — The Overlay Draws The Document

The controls are no longer a Kotlin file. `ControllerOverlay` reads a `ControllerLayout`, resolves
every element against this screen, and turns the result into windows. Every position, size and
binding on screen now comes from `builtin.xbox.default.json`, and editing that file changes the pad.

**Which controls share a window is derived, not declared.** Two measured facts pull against each
other: a finger cannot move between windows, so sliding a thumb from one control to the next only
works inside one; and a window is dead everywhere its controls are not, so every spare pixel of
window is a pixel nothing can be touched through. One window for everything gives perfect sliding
and covers the screen; one window per control covers nothing and makes sliding impossible.

`core/layout/Clustering` splits the difference: **controls close enough to slide between share a
window, controls far enough apart to be deliberate get their own.** It is derived from the layout,
so a user who drags two buttons together in the editor gets sliding between them without knowing the
concept exists. Grouping is transitive, because a row of buttons is one row, and distance is
measured between inscribed circles rather than bounding boxes — four round face buttons in a diamond
have overlapping boxes and touch nowhere, so boxes would collapse every layout into one window.

Tests hold it to the arrangement people have actually played with: a stick shares its window with
its own press, the face buttons share theirs, the pad stays alone, no cluster covers a quarter of
the screen, and the windows together cover under 35% of it.

**One touch model for every kind of control**, in one view, because the interesting behaviour is
between controls rather than inside them. A stick or a pad belongs to one finger until it lifts. A
button follows its finger onto another button, which is what makes rolling across a diamond press
each in turn. **A button does not release when its finger slides onto nothing or onto a stick** —
that is how a thumb holds `L3` and then moves the stick, and it also means a press is not lost by
drifting a few pixels. Lifting is what releases.

**Scaling shrinks the arrangement towards its anchors**, offsets and sizes together. Scaling only
the controls would leave a smaller pad sitting further from the corner rather than nearer it, which
is the opposite of what someone reaching for a smaller control wants.

`platform/input/GamepadCodes` is now the only place a control's name becomes a number. The overlay
used to carry `304` and `ABS_BRAKE` in its own tables — kernel constants in the layer furthest from
the kernel, and the reason a second backend could never have mapped them differently.

### Phase 2 — A Layout Repository, And Immutability That Cannot Be Bypassed

`core/layout/LayoutRepository` reads a layout by id from wherever that id lives: built-ins from what
Kestrel ships, user layouts from the user's own folder, both through the same reader and the same
rules.

**Built-in immutability is enforced here rather than by a disabled button.** `save` and `delete`
refuse a `builtin.` id outright, so there is no code path that overwrites a shipped layout and no
interface can accidentally offer one. `duplicate` is the only way to get an editable copy, which
makes Built-in → Duplicate → User copy → Edit the only path there is instead of the path people are
asked to follow. A duplicate takes a new identity and keeps everything else, because an id
travelling with a copy would leave two documents claiming to be the same thing.

**A layout that cannot be loaded falls back to the default and says why.** Leaving someone with no
controls at all is a worse answer than the wrong pad, and a corrupt file, a deleted layout and a
forgotten folder all reach the same place.

`ControllerLayoutWriter` completes the round trip, and the property that matters is that **what was
read comes back out** — including fields this build has never heard of, written back where they were
found. Optional fields that carry nothing are left out, because a file full of `"label": null` is
harder to hand-edit, and hand-editing is a thing this project's own owner does.

### Setup Asks Once, Every Time It Needs To

There is no single moment when setup happens. A fresh install has nothing; clearing data is back to
nothing with the application still installed; a permission revoked in system settings takes one
thing away and leaves the rest. Asking at first launch handles only the first of those.

So Kestrel asks **whenever the state is incomplete**: notifications, drawing over other
applications, a data folder, and Shizuku, each with what it is for and one button that does it.
Skipping is a real answer and lasts until the process ends — not persisted, deliberately, because
what it hides is a fact about the phone that the user can undo from outside Kestrel at any time, and
remembering the dismissal is the one way to guarantee they never see it again.

Nothing blocks. The rest of the screen stays usable, because a wizard that will not let you past it
traps anyone whose phone answers a question differently from how it was expected to.

The permission request that used to fire on launch is gone with it: a dialog shown before the user
has seen the application is a dialog answered without knowing what it is for.

### Kestrel Makes Its Own Folder

Choosing a folder now puts Kestrel's files in a `Kestrel` folder **inside** whatever was picked,
creating it if it is not there and using it directly if the user picked one already called that. The
picker also opens at the top level of internal storage rather than wherever it was last. Someone who
selects the whole of Documents has not agreed to have `settings.json` dropped among their documents.

**Kestrel cannot create that folder at the top of storage by itself**, and the reason is worth
recording rather than rediscovering: making a directory there needs `MANAGE_EXTERNAL_STORAGE`, which
is access to every file on the phone — and declaring a permission of that class is exactly what got
Kestrel blocked by Play Protect when the accessibility service was declared (`ADR-006`). One tap in
the picker is the price of not paying that again.

### Phase 2 — A Layout Is A Document Now

The arrangement of controls lived in a Kotlin file, and that one fact was blocking three phases of
`PRD.md` at once: there was nothing for a layout editor to edit, nothing for a skin to dress, and
nothing for a profile to select. They were all waiting on the same missing noun.

**`core/layout/ControllerLayout`** is that noun. A layout is a validated document — exportable,
importable, versioned, shareable — which is what `ADR-001` chose JSON for in the first place.
Nothing in it draws anything or knows a pixel; `Placement` turns an element into a rectangle only
once a surface is known.

**`core/input/GamepadControl`** is the vocabulary the layout speaks: `A`, `LEFT_TRIGGER`,
`LEFT_STICK`. `CLAUDE.md` §5 has always required domain and interface code to use controller
semantics rather than key codes, and the overlay had been carrying `304` and `ABS_BRAKE` in its own
control table — the boundary being crossed in the layer furthest from the kernel. Nothing in the new
enum knows how a control is delivered, which is the whole point of naming them.

Three rules in the reader are worth stating, because each exists to catch a specific failure:

- **What an element *is* and what it *drives* must agree.** A stick bound to `A` draws correctly and
  does nothing, which is the hardest kind of fault to see from outside. It is now a message naming
  the field and listing what would have been valid.
- **A decoration must not bind.** Artwork that sends input is a control that was mislabelled.
- **Duplicate element ids are refused, not resolved.** Picking one silently would make a layout
  behave differently from the file that describes it.

Both trigger kinds bind to a trigger, because presenting an analog trigger as a button is a choice
the user is entitled to make (`ADR-007`) rather than a different control.

### Phase 2 — The Built-In Layout Is A Shipped File, Not Code

`builtin.xbox.default` is a JSON resource, parsed by **the same reader that parses an imported
layout**. That is the point rather than an implementation detail: a built-in defined in Kotlin would
be the one layout in the product that never goes through validation, so the schema could drift from
what the application renders and the drift would surface first for a user importing a file. This way
each is a continuous test of the other. It is also immutable by construction — the source is a
read-only resource — rather than by a disabled button.

Its numbers are not invented. **Every offset and size is the arrangement measured on the reference
device at the default control scale**, converted to fractions of the shorter side.

Ten tests check the shipped file rather than a fixture, and two of them failed on the first run and
were right to: a first attempt at the geometry put the right stick off the top of a landscape screen
and overlapped the pad with a shoulder button. A third caught the left stick's press sitting inside
the stick. The tests now assert that every control resolves inside the screen in **both**
orientations, that no two overlap, that every control the pad declares is bound exactly once, and
that nothing is anchored where a thumb cannot reach it.

Controls are compared as circles rather than as bounding boxes, because a diamond of four round face
buttons has overlapping boxes by construction and no overlapping buttons at all.

### Phase 2 — `core` Reads JSON Without A Dependency

`core/configuration/Json` is a small, strict reader producing `ConfigNode`.

**Why not a dependency.** `core` is plain Kotlin so that the rules are testable without a device.
Every configuration rule in it operates on `ConfigNode`, so with nothing to produce one from text,
the only place a document could be read was `platform/` and the only place it could be *tested* was
a phone — putting the validation this project most depends on behind its slowest feedback loop. A
reader that fits in one file buys the fast loop back and costs nothing.

**Strict on purpose**, because an imported document is untrusted input: no comments, no trailing
commas, no unquoted keys, no single quotes, no trailing content, no leading zeros, no `Infinity` or
`NaN`, no raw control characters inside text, and a repeated key is refused rather than resolved —
JSON does not say which wins, so any choice would be this reader's opinion silently overriding the
author's. Nesting is capped, because recursive descent on untrusted input without a cap is a stack
overflow waiting for a file full of open brackets, and a crash is not a typed error.

`ConfigurationError.MalformedDocument` is deliberately separate from every other error: the rest
describe a document that parsed and then failed a rule, which a user can act on by editing a named
field. This one says the file never became a document, and reports where reading stopped.

**Not yet wired to anything.** The overlay still draws its hardcoded arrangement; rendering from the
document is next. `:core` tests: **144, all passing**.

### Artwork Cleared, And One Inbox Instead Of Three Places

**The skin pack's licence is CC0**, and that was the item blocking it. It is *Xelu's Free Controller
Prompts* by Nicolae "Xelu" Berbece; the licence file shipped with the pack is stored beside the
artwork at `docs/inbox/skins/LICENSE.txt`. A public-domain dedication means no copyleft conflict
with GPLv3, no notice requirement, and no attribution obligation.

**Kestrel credits the author anyway.** The licence permits taking the work without naming him, and
the author says he does not mind — neither makes it the right thing to do. Recorded in
`THIRD_PARTY_LICENSES.md`, which gains a *Bundled Assets* section, because the project now carries
material that is not code and the same rule applies to it: what ships has terms on the record.

**Still separate, still open:** the trademark question. A licence on the files says nothing about
what the shapes depict. The practical exposure is small because `ADR-INPUT-001` already decided
Kestrel presents its own device identity rather than another vendor's — a skin drawing familiar
glyphs is not a device claiming to be someone else's hardware — and the position is written down
rather than assumed.

**Status: cleared for use**, and still not adopted. Per `docs/SKIN_ASSETS.md` §2a the skin format
comes from building Kestrel's own skin first, so the pack moves into `data/` when there is a format
to receive it and not before.

### One Inbox

`docs/inbox/` replaces `docs/phase0/results/inbox/`, which was named for a phase that has finished
and had been collecting artwork and reports it was never meant to hold. Three folders — `reports/`,
`ideas/`, `skins/` — and its README says what the inbox is *not*: not a distribution path, and not a
queue. Nothing ships from it; material moves out only by being adopted into `docs/`, `docs/adr/` or
`data/`, which is what keeps unassessed or unlicensed content out of the product by construction.

The old path keeps a README pointing at the new one rather than disappearing.

### Confirmed: The Play Protect Block Was The Accessibility Declaration

Measured in both directions now. `0.0.15-dev` added the service declaration and was blocked;
`0.0.18-dev` removes the service and permission declarations, changes nothing else about how Kestrel
installs, and scans as *"This app looks safe"*. The block appeared with the declaration and
disappeared with it, so the attribution in `ADR-006` is measured rather than inferred.

### Fallback — Measured, Works, Rejected

`ADR-006` is decided. The mechanism was built, measured on the reference device, and **rejected on
product grounds rather than technical ones** — this is not a record of something that failed, it is
a record of something that succeeded at the wrong thing.

**The gate was restricted settings, and it is manual.** With **Allow restricted settings** off, the
accessibility toggle was greyed out and both programmatic enable routes wrote nothing while
reporting success. With it on, the Shizuku route worked immediately — so the setting had never been
the obstacle. No permission substitutes for that one manual step. The first enable needs a hand;
everything after it does not.

**Then everything measured, and it is good.**

| | Shizuku running | Shizuku stopped |
| --- | --- | --- |
| Taps landed | 12 of 12 | 12 of 12 |
| Latency (best / median / worst) | 3 / **4** / 7 ms | 3 / **5** / 20 ms |
| Drag | 97 movements in 400 ms — **243 a second** | 96 in 398 ms — **241 a second** |

Kestrel enabled the service itself with Shizuku stopped, using the `WRITE_SECURE_SETTINGS` grant.
Every number was taken against an overlay window of Kestrel's, so injection and the overlay
demonstrably coexist.

Two of those **beat what `ADR-006` predicted**. It expected sticks to "degrade to digital regions at
best" — 242 movements a second is above display refresh, so a simulated stick would have been
smooth. It expected latency to be "worse by an unmeasured amount" — 4 ms is below a frame.

**Rejected anyway, and the reasons are not in the numbers.** An accessibility service dispatches
touches. It cannot create a device and has no key-injection API. So the product is not "Kestrel
without Shizuku" — it is Kestrel's controls **puppeting a target's own on-screen buttons**: our A
becomes a tap at the coordinates of theirs. That needs the target to draw touch controls, keep them
visible and still, and be calibrated per layout and per screen size — and a user could already touch
those buttons directly. On top of which, declaring the service **made Play Protect block Kestrel's
install for every user**, including everyone who would never enable it, because a manifest
declaration is visible at install time whether the code runs or not.

A working mechanism, a weaker product, per-target calibration Kestrel would own, paid for by every
install. Not worth shipping.

**What changed as a result.**

- **Kestrel is Shizuku-only for input.** `ADR-003` still holds: Shizuku is not required for the
  application, only for a session.
- The **Reduced** capability state is gone from `docs/DEGRADED_STATE.md`. Three remain — Full,
  Ready, Configure only — and the document says why the fourth was removed, because "we dropped the
  fallback" otherwise invites the assumption that it did not work.
- `ARCHITECTURE.md` §15 records that the backend ordering now has one real entry, and §16's
  evaluation checklist is answered item by item with the distribution-policy line marked decisive.
- The probe's code is **deleted rather than left dormant**, and the manifest declarations with it,
  which restores the ordinary sideload path. Its design is `docs/FALLBACK_PROBE.md` and its
  implementation is in history at `0.0.17-dev`.
- `ADR-006` records what would reopen the question. Any of it would be a new record, not an
  amendment.

No JSON was captured for the final run; the numbers are transcribed from the screen and are marked
as weaker evidence than an export, though the conclusion does not turn on a millisecond.

### Fallback — First Results, And A Cost That Is Not About The Fallback

Reference device, `0.0.16-dev`. Three measurements, one of them about how Kestrel installs rather
than about input.

**The permission grant works.** `pm grant` through the Shizuku shell succeeded, `dumpsys` reports
`WRITE_SECURE_SETTINGS: granted=true`, and Kestrel reads it as held in the same session with no
restart. The project owner's own suggestion — a one-time grant so the fallback can be enabled later
with Shizuku not running — is **confirmed**.

**Writing the accessibility list does not take, by either route.** Through the shell, the script's
own read-back reported `list is now: null`. Through Kestrel's granted permission,
`Settings.Secure.putString` reported success and the list stayed empty. Both routes claim success
and neither changes anything, which is the failure mode that looks most like working code.

It is not the script this time: it was exercised against a stand-in `settings` over six cases before
shipping, and a mis-formed write would read back as empty rather than as `null`. `null` means the
key is absent, so the write was reverted or refused. Two candidates — the platform's *restricted
settings* block on sideloaded applications, or the OEM layer protecting this particular setting —
and **neither is confirmed**. Enabling the service by hand is the test that separates them, and it
has not been run.

**Nothing was measured.** The service never connected, so latency, drag resolution, and behaviour
under Kestrel's overlay all remain unknown.

**And the finding that is not about the fallback: declaring the service changed how Kestrel
installs.** Up to `0.0.14-dev`, sideloading gave the ordinary unknown-source warning and proceeded.
`0.0.15-dev` — which added the accessibility service and nothing else relevant — is **blocked by
Play Protect**: *"App blocked to protect your device."* Installing now requires turning that
protection off.

The attribution is clean: the service arrived in `0.0.15-dev`, the permission in `0.0.16-dev`, and
the block began with the former. This is the distribution cost `ARCHITECTURE.md` §16 asked to be
evaluated, arriving as a measurement rather than a prediction — and it is paid by **every** user,
including everyone who never enables the fallback, because a manifest declaration is visible at
install time whether or not the code ever runs.

**The ceiling, recorded next to the results because it bounds what a good outcome could have been.**
Without a privileged shell there is no way for an application to deliver controller input to another
application: creating a device needs shell or root, `injectInputEvent` needs a signature permission,
and an accessibility service dispatches touches and nothing else. The best imaginable result was
never "Kestrel works without Shizuku" — it was "Kestrel's layout can press an emulator's own
on-screen buttons", a puppet layer over controls the target already draws.

### Fallback — Two Faults In The Probe, Found On Its First Run

Neither was a finding about the fallback. Both were the probe being wrong, which is worth recording
because a probe that reports its own fault as the subject's fault is worse than no probe.

**`pm grant` succeeded and granted nothing.** `WRITE_SECURE_SETTINGS` is
`signature|privileged|development`, and the `development` flag is what lets a shell hand it over —
**but only to an application that has asked for it.** Kestrel never declared it, so the grant had
nothing to act on: it exited zero, printed nothing, and the permission stayed absent, which read
exactly like the platform refusing. It is declared now, and the grant reports the platform's own
answer from `dumpsys` alongside Kestrel's, because a permission granted to a running process can be
cached and the two disagreeing is itself worth seeing.

**A value formatted for a person was parsed as data.** The enable route read the accessibility list
into Kotlin, edited it and wrote it back — and `exec` returns human-readable text, so an empty
setting came back as the literal string `(no output, exit=0)`, which went into the next command and
produced `sh: syntax error: unexpected '('`.

The fix is not to parse that string more carefully. **The shell now reads, decides and writes
without the value ever crossing back into the application.** That removes the class of fault rather
than the instance, and the same shape does the disable. `paste` was avoided in favour of `tr` and
`sed`: the platform's shell is toybox, and reaching for a tool that might not be there would fail at
the moment a user is trying to undo this.

Both scripts were exercised against a stand-in `settings` before shipping, over every case that
matters — an empty list, a repeat that must not duplicate, a list already holding someone else's
service, removal from the middle of three, removal when Kestrel is the only entry, and removal when
it is already absent. Other services survive all six.

### Fallback — A Probe Before A Backend

`ADR-006` chose touch simulation through an accessibility service as the direction for a user
without Shizuku, and accepted it **untested**. Three documents already assume it works:
`docs/DEGRADED_STATE.md` describes what such a user is told and offered, and `ADR-007` promises one
layout across capability tiers. Finding out the direction is not viable after a launcher, an editor
and a skin system have been built on it is the mistake Phase 0 existed to prevent.

So: a probe, not a backend. `docs/FALLBACK_PROBE.md` is the procedure. Four questions, and they are
separate because a yes to one says nothing about the others:

1. **Can it be enabled without sending the user hunting through settings?** Three routes are
   reported apart — through the privileged shell, by granting `WRITE_SECURE_SETTINGS` once so
   Kestrel can write the setting itself later, and by hand. The middle one is the project owner's
   own suggestion and the one that matters: a grant that survives means the fallback can be turned
   on with Shizuku **not running at all**.
2. **How long does an injected touch take to arrive?** Measured end to end with no human in the
   loop: a tap is aimed at a window Kestrel owns, and the time from asking to landing is the number.
3. **How finely can a movement be drawn?** A drag is dispatched and the movements it produces are
   counted. This is the question most likely to decide the answer — a stick is continuous, and a
   drag arriving as a handful of points cannot simulate one however low its latency is.
4. **Does it work with Kestrel's overlay up?** The measurement target *is* an overlay window, so
   every number above is already taken under that condition.

Two things are deliberate about the shape of it.

**The service can inject and cannot observe.** No `canRetrieveWindowContent`, no event types beyond
the one the platform requires, nothing read from the screen. An accessibility service is a large
thing to ask a user to enable, and a probe that can only inject is a smaller thing to trust than one
that can also watch.

**Both enable routes append to the accessibility list rather than replacing it.** The setting is
shared, and writing only Kestrel into it would silently switch off every service the user actually
depends on. A diagnostic that does that is unacceptable regardless of what it measures.

Nothing in product code refers to any of it, which is `ARCHITECTURE.md` §16's requirement that
accessibility be removable without affecting the rest of the system.

**What it cannot decide, stated before any result is read:** whether Kestrel works without Shizuku.
It cannot — nothing here creates a device. A good result means a target's own on-screen touch
controls can be driven, which is a real product for an emulator that draws them and nothing at all
for a target that does not.

### Phase 1 — Two Faults The Trail Found On Its First Run

The report added in the entry below was used once and immediately earned itself. Both of these were
invisible in every export before it.

**Every controller button was arriving twice.** `BUTTON_A` with `DPAD_CENTER`, `BUTTON_X` with
`DEL`, `BUTTON_Y` with `SPACE` — and `BUTTON_B` with **`BACK`**. The second of each pair is a
platform fallback key, generated for a gamepad button that **nothing handled**. Kestrel's own screen
was observing keys without handling them, so pressing `B` on Kestrel's controls was asking Kestrel
to navigate back. A controller's keys are now consumed, which stops the fallbacks being generated at
all; everything else, the back gesture included, still goes where it was going.

**The trigger ramp was tied to the display.** It stepped a fixed amount per frame, which is half a
second only at 60 Hz — the reference device runs at 120, and the trail measured a press intended to
take 0.50 s taking **0.31 s**. Elapsed time is now what advances it, so the ramp is the same
everywhere, and a frame delayed by a stall is capped rather than jumping the value.

The trail also confirmed what it was built to confirm: every press in the run had its matching
release, a slide from `Y` to `X` released one and pressed the other in the same millisecond, the
eight-way pad walked all eight directions and returned to centre, and the trigger reported
thirty-four intermediate values on the way up rather than 0 and 1.

### Phase 1 — A Report That Shows A Sequence

The exports every conclusion in this project rests on carried the **most recent** value of each
field and nothing else. That is enough to answer "did anything arrive" and no other question. It
cannot show a press that never got its release, two controls firing when one was touched, or a value
climbing while a thumb sat still — and those are the failures that have actually cost time here.
Each of them is a sequence, and a moment cannot contain one.

`core/diagnostics/InputTrail` is a bounded, oldest-first record of what happened, with two of them
in every report:

- **`sent`** — what Kestrel wrote to the virtual device.
- **`received`** — what the platform delivered back.

Together and in order they answer the question that actually gets asked when something is wrong:
**whether the fault is above the virtual device or below it.** Neither half alone can.

Three details that are decisions rather than defaults:

- **Bounded, keeping the newest.** A stick held still writes sixty positions a second, so an
  unbounded log is a memory leak with extra steps — and a bounded one keeping the *oldest* entries
  would fill with the moments before the interesting one and never reach it. What was dropped is
  counted and reported even when it is zero, so a quiet trail is distinguishable from a truncated
  one.
- **Analog values are coalesced** past a movement threshold. Sixty copies of one value say nothing
  the first said and crowd out every press around them.
- **Key releases are recorded**, though only presses update the screen. The release is the half that
  matters when a control is stuck, and it was the half being discarded.

The `received` trail carries a note saying it only fills while Kestrel's own screen has focus, so an
empty one during play in another application is not misread as nothing having arrived. A **Clear
trail** button starts a test clean.

Five tests cover the trail's ordering, its wrap behaviour, its dropped count, and the coalescing
threshold.

### Phase 1 — Measured: Refusing A Touch Does Not Pass It On

A cluster window is a rectangle and its controls are circles, so the space between them was
swallowing touches — reaching neither a control nor the application underneath. The previous build
made those touches **refused** rather than consumed, as an experiment, with the outcome stated in
advance as either "the gap becomes transparent" or "the gap stays dead".

**It stays dead.** On the reference device a touch in the gap reached nothing: the application below
did not scroll and no control fired. The window is chosen before the view hierarchy is consulted, so
a view returning "not handled" wastes the touch rather than forwarding it.

What follows from that, recorded so it is not re-attempted:

- The platform's own remedy is an **irregular touchable region**, which is not public API. `CLAUDE.md`
  §8 forbids building on hidden APIs, so it is not available here.
- The remaining public option is **one window per control**, which would trade away sliding a thumb
  from one control to the next — a thing that works and was asked for.
- The gaps therefore stay inert. The refusal stays too, because it costs nothing and is the correct
  expression of what the window is for; it is simply not claimed to do anything it does not.

Also: the stick knob now lights while a thumb is on it. It was the one control on the overlay that
gave no sign of being touched, so a thumb resting at centre looked the same as a thumb that missed.

### Phase 1 — The Stick Press Lives On The Stick

The project owner asked for something that sounds like a layout preference and is not: **hold `L3`,
slide onto the stick, and have both stay live**, because some titles need a stick press held while
the stick is moving.

It could not be done with `L3` in its own window, and the reason is worth recording rather than
rediscovering. **A pointer belongs to the window that received its touch-down and stays there for
the life of the gesture.** `FLAG_SPLIT_TOUCH` lets a *new* finger reach a *different* window; it
does not hand an existing finger over. So a press button in a window of its own can be held, or the
stick can be moved, and never both by one thumb — no amount of care in the touch handler changes
that, because the second window never sees the finger at all.

**The press therefore shares the stick's window**, in a strip on the inner side, and two rules
follow:

- **The press latches to its finger, not to its area.** Sliding off does not release it; only
  lifting does. Releasing on slide-off is right for a face button and wrong here, since sliding off
  is the entire point.
- **A press finger may take over the stick** if no other finger already holds it, and goes on
  holding the press while it does.

`Select` and `Start` moved under their own bumpers at the same time. The four of them — with the
stick presses — had shared one narrow strip across the bottom of the screen, which made every one of
them the smallest thing on screen and put two of them where a thumb has no reason to be. The strip
is gone.

The right stick is now the same size as the left. It was smaller to save room, which cost it half
its precision and its press a third of its radius for no reason a hand could feel; both fit at full
size in either orientation, verified against the window rectangles rather than by eye.

**One experiment rides along.** A cluster window is a rectangle and its controls are not, so the
space between them was being swallowed — a touch there reached neither the control nor whatever is
underneath. Those touches are now **refused** rather than consumed. Whether refusing them lets them
through to the application below is a property of the platform's input dispatch that has not been
measured here, and the two possible outcomes are "the gap becomes transparent" and "the gap stays
dead, as it already was". Recorded as an experiment because it is one. *(Since measured: it stays
dead. See the entry above.)*

### Phase 1 — Measured: Diagonals Work In Play

The open question from the previous entry is closed. A title running under Eden was played with the
eight-way pad and **the character moves diagonally**; titles are playable there generally.

This settles what the binding screens could not. PPSSPP and Eden each capture only the nearer of the
two axes when a diagonal is held, which is what a binding screen is built to do — it asks for one
control and picks the dominant one. It was never evidence about play, and play now says the pad is
read as a pad. `docs/COMPATIBILITY.md` gains nothing new about the backend; what changed is that a
Phase 1 control behaviour is measured rather than reasoned.

### Phase 1 — Overlay Geometry Solved Rather Than Assumed

Device feedback on the previous build: multi-touch, sliding presses, the cross, the diagonals and
visibility all worked; two things did not, and both were arithmetic.

**The face buttons overlapped each other and were clipped by their own window.** The radius came
from a fixed divisor and each control was then placed a full half-window from centre, which put the
outer edge of every control exactly on the window boundary — so half of each outline was cut away —
while leaving adjacent buttons overlapping by about a third of their width. The radius is now
**solved for** by bisection: the largest value at which no two controls come within a gap of each
other and every control, outline included, still fits inside its window. Every cluster uses the same
solution, so the shoulders and the menu strip stopped being unreadable dots at the same time — the
trigger buttons roughly doubled in radius.

**The trigger ramp was a ramp on paper and a switch in the hand.** 0.2 s to full could not be felt
on the reference device and the fill was gone before it could be read. Full press is now about half
a second and release about a third, and the level is drawn twice — as a fill rising inside the
button and as a ring closing around its inner edge, because the inside of a small circle is exactly
the part a thumb is covering.

**The controls sit on plates now.** The first answer to "invisible on a white screen" was a heavy
dark ring around a pale shape; it worked and it looked like a diagram. The arrangement the project
owner asked for, and the one commercial pads on this platform use, is the other way round: a dark
translucent plate carries the cluster and the controls sit on it in a lighter grey. The plate makes
the whole cluster legible over a white page, so no individual control needs a ring heavy enough to
do that alone.

Window placement is now described once and used by both the first layout and every resize, rather
than written out twice — two copies of a layout drifting apart is how a resize puts a control
somewhere the first layout never did.

### Phase 1 — Measured: The Platform Synthesises Diagonal D-pad Keys

Three targets were asked what the eight-way pad produces, and the answers differ in a way worth
recording, because two of them look like a failure and are not.

A gamepad testing tool reported **`268`, `269`, `270`, `271`** for up-left, down-left, up-right and
down-right, alongside `96`, `97`, `99`, `100` for the face buttons. Those are the platform's
dedicated diagonal d-pad key codes, and their appearance is direct evidence that **both hat axes are
being delivered and the platform is deriving true diagonals from them**.

PPSSPP lists the control as `Pad1.Hat.-/+X` and `-/+Y`, and Eden as axes `-/+15` and `16`; both
capture only the nearer of the two axes when a diagonal is pressed. That is what a **binding** screen
does — it asks which single control to bind and picks the dominant one — and it is not evidence
about what those targets read during play. (Since confirmed in play: see the entry above.)

Recorded because the distinction is easy to get backwards: a binding screen showing one axis is not
a pad sending one axis.

### Phase 1 — An Overlay That Can Actually Be Played

Five faults reported from the reference device, four of them from a single mistake: a control was
decided once, when a finger landed, and never reconsidered.

**Touches now split across the overlay's windows.** `FLAG_SPLIT_TOUCH` was missing, and without it
the first window to see a finger owns the whole gesture — so holding the stick stopped every other
control responding **and stopped the phone underneath responding**. Separate small windows were
never sufficient on their own; this flag is what makes them independent. The symptom looked like
"no multi-touch" and the cause was a single missing flag, not the drawing.

**Every finger is read on every event.** Sliding from one face button into its neighbour used to
keep the first held and never press the second. The set of controls under the fingers is recomputed
on each move and the difference applied, so a thumb rolling across two buttons presses both.

**The d-pad is one cross that reports eight directions**, not four circles that report one. Four
circles could only ever report the one a finger landed in, so rolling from up into the corner gave
up, then nothing, then right — never up-and-right. A diagonal is now a place on the pad rather than
two presses to be timed. First finger down owns it until it lifts; a second touch no longer
overrides the direction being held.

**L2 and R2 ramp instead of switching.** They sent 0 or 1 with nothing between, which is not what
those controls are on a pad. Holding raises the value over about a fifth of a second, releasing
drains it slightly faster, and the button fills from the bottom as it goes — so the level is visible
on the control rather than only inside the game.

**Every shape carries a dark outline and every label is drawn twice**, dark stroke then light fill.
Pale shapes with pale labels are legible over a dark game and invisible over a white one, and no
amount of translucency fixes that: a control has to be defined by its edge, not its fill.

Sticks also claim a single pointer now, for the same reason the d-pad does.

### Documentation — Skin Assets Assessed

`docs/SKIN_ASSETS.md` records what the 233-file artwork pack in `docs/phase0/results/inbox/Skins/`
actually contains — every file inspected, not sampled — and what it implies.

The pack is **button prompts, not pad artwork**: one control drawn in isolation per file, 256×256
RGBA, five sets covering Xbox, PlayStation, Switch, and keyboard/mouse in two tones. What it does
not contain matters as much: no pressed state, no stick-press art, no diagonal d-pad, and no body or
background.

Two conventions follow from it. The filenames are sequence numbers carrying no meaning, so **the
control-to-file mapping belongs in a per-family manifest** rather than in filenames — which is also
where the drawn extent belongs, since square files hold non-square subjects. And **PNG stays
primary**, per the owner's decision; the whole pack is 1.4 MB, which is not worth trading a
lossless format for.

The pack's origin is a Reddit post, *FREE Keyboard and controllers prompts pack*. "Free" in a post
title is not a licence, and the licence question is separate again from the trademark question the
drawn shapes raise; neither answers the other, so the artwork stays in the inbox.

**Decided: build Kestrel's own skin first, then judge packs against what it needed.** A skin format
derived from one pack encodes that pack's accidents — 256 px squares, one image per control, no
pressed state — as if they were requirements, and then fits exactly one set of artwork. Deriving the
format from what the renderer needs produces a specification a pack either meets or does not. It
also makes the licence question answerable rather than urgent, because nothing has been built around
artwork that may turn out to be unusable.

### Phase 1 — A Whole Pad, and a Stick That Fits Its Own Window

**The thumb was clipped, and there were two geometry faults behind it.** The knob's centre was moved
the full radius of the window, so at full deflection half of it sat outside and was sliced flat
against the edge — visible in the screenshot. A drawn knob cannot travel further out than its own
radius from the edge, and now it does not. The second fault was in the same three lines: each axis
was clamped to ±1 **separately**, so a diagonal reached 1.41 from centre — outside the ring the user
can see, and a deflection no real stick can produce. Clamped as a circle now.

**Every control a standard pad has is on the overlay**, so what a target does with each can be
tested rather than assumed: both sticks, the d-pad, L1/R1, L2/R2, Select, Start, L3/R3. Each cluster
is its own small window, laid out as a controller is — sticks and d-pad left, faces right, shoulders
along the top edge, menu buttons centred.

Two decisions inside that are worth stating:

- **The d-pad sends hat axes, not four keys.** A real pad reports a hat, and Phase 0 measured the
  platform synthesising `DPAD_*` keys from one — so sending the hat produces both, while sending
  keys produces only the keys.
- **L2 and R2 send analog trigger values, not buttons**, for the same reason: a target reading the
  axis sees it, and one reading the button still sees the key the platform derives.

The right stick is coalesced separately from the left, because a player aiming while moving would
otherwise have one overwrite the other.

### Documentation — Controller Families

`docs/CONTROLLER_FAMILIES.md` records what Kestrel presents and what the alternatives cost.

Kestrel presents an **Xbox-style layout deliberately**: the descriptor's `BTN_SOUTH/EAST/WEST/NORTH`
map to `BUTTON_A/B/X/Y`, which is both the Xbox convention and the platform's default arrangement,
which is why targets accept it without configuration.

The three families differ in three separate places that are easy to confuse: the **physical
arrangement** is nearly identical and the input protocol does not change at all; the **labels**
differ and are the target's business to draw; the **identity** differs and is the only lever Kestrel
holds. Declaring another vendor's identifiers would make more targets show familiar labels and would
also claim to be a device this is not — one with rumble, a touchpad, motion sensors — so the current
decision is Kestrel's own identity, and changing it is an ADR rather than an edit.

**A family belongs in the layout and skin layer, not the descriptor.** Kestrel knows which control
it sent, so its own interface can say A, draw ✕ or draw B while the descriptor stays unchanged. One
device, many appearances; a layout stays valid across families per `ADR-007`; no target needs
re-binding when a user changes labels.

One open question recorded rather than decided: Nintendo's A and B sit in swapped positions, so a
Nintendo skin must choose between **positional** (the bottom button always sends `BUTTON_A`) and
**nominal** (the button labelled A sends `BUTTON_A` wherever it sits). Those are different products.
`ADR-007`'s principle points at positional, but it deserves its own record when a Nintendo skin is
actually built.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: the full control set has not been run on a device.

### Phase 1 — Control Size Is a Hand's Judgement

The first size was chosen by arithmetic — a fraction of the short side that seemed thumb-sized — and
looked too large on the reference device in both orientations. That is the kind of number only a
hand can settle, so it is now a setting with a default, and the default is what that hand asked for:
**65% of the original**, adjustable from 35% to 130%.

Resizing updates the windows already on screen rather than taking them down and putting them back,
because removing a window drops whatever control was being held at that moment — a size change
mid-play would leave a button stuck down.

**The toggle does not scale.** It is the way out, and a way out that shrinks with a setting is one
someone can make too small to use.

### Phase 1 — An Overlay That Covers Only Itself

The first overlay locked the phone. It was one window the size of the screen whose touch handler
reported every touch as handled, so it consumed **every touch on the device** — home screen, recent
list, settings, notification shade, all of it. Nothing could be operated by finger and only a reboot
recovered it.

That is not a bug to be patched by reporting touches as unhandled more carefully. It is a reason not
to put a window there at all. **Each control cluster now has its own window, sized to itself**: the
stick bottom-left, the face buttons bottom-right. Everywhere the controls are not, there is no
window of ours, so nothing of ours can intercept anything.

Three safety rules go with it, each answering something that happened:

- **A small toggle appears first and alone**, at the top of the screen, and shows and hides the
  controls. It comes up before them on purpose: it is the way out, and a user who cannot make the
  controls go away has lost their phone until they reboot it.
- **The notification gains Hide**, so the controls can be removed without touching the screen at
  all.
- **Every path out of the session removes the controls**, including the service being destroyed. A
  window put up by a service outlives the screen that asked for it.

Hiding the controls also centres the stick and releases every held button, because a control that
disappears mid-press leaves nothing behind able to release it.

Two behaviours the operator saw are **not** faults and are recorded as such: the stick moving the
home screen's selection is a controller doing what a controller does, and `BUTTON_B` closing an
application is the platform's own mapping of B to Back, measured on a physical controller in Tier 1
and inherited by any created device. Neither is caused by the overlay, and neither is Kestrel's to
override — though a session will want to suppress Back reaching the launcher itself.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: the reshaped overlay has not been run on a device. The failure it fixes was severe
enough that the first test should be the recovery path — put the controls up, then take them down
with the toggle — before anything else is tried.

### Phase 1 — Controls Cannot Live in an Ordinary Window

The on-screen stick appeared to do nothing in an emulator. The export says otherwise, and the real
answer is more useful than the reported one:

```json
"source": "Kestrel Virtual Controller (id 14)", "events": 2005, "lastButton": "DPAD_RIGHT"
```

**The stick worked.** Two thousand events arrived from the created controller, and `DPAD_RIGHT` is
the key the platform synthesises from a *held stick* — it cannot appear unless an axis moved. They
arrived at **Kestrel**.

The platform delivers a controller's events to the **focused window**, and touching a control inside
an ordinary activity makes that activity focused. So Kestrel wrote to the controller, the controller
moved, and the platform handed the result back to Kestrel. The operator had already found the same
rule from the other side without naming it: buttons only reached the target "if the emulator has
focus before the button goes down". The stick can never satisfy that, because a drag has to begin
with a touch on Kestrel.

**Nothing was wrong with the controller, the write path or the transformation.** Every part measured
correct and the arrangement was still unusable — a pipeline can be right end to end and deliver to
the wrong place.

`platform/overlay/` now exists: the stick and four face buttons in a `TYPE_APPLICATION_OVERLAY`
window with `FLAG_NOT_FOCUSABLE`, so the target keeps focus and the controller's events go where the
player is looking. `FLAG_NOT_TOUCH_MODAL` lets touches outside the controls through to what is
underneath. Multi-touch is tracked by pointer id, because holding a direction while pressing a
button is the ordinary case rather than an advanced one.

Drawn with a plain `View` rather than Compose: a window put up by a service has no lifecycle owner,
and giving it one is more machinery than a stick and four buttons justify.

Recorded in `docs/phase0/results/tier6-focus-report.md`, with what it implies beyond the overlay —
a layout editor cannot be tested by playing through it, since editing happens in a focused window,
and the overlay permission is now a second thing a user must grant rather than an optional extra.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: the overlay has not been run on a device. It is a designed answer to a measured
problem, and only the problem is measured so far.

### Phase 1 — The Step That Was Missing: Controls Reach the Controller

On-screen controls did nothing in an emulator, and the export explains why in one line:
`"source": "touch pad (this screen)", "events": 4533`. The pad produced four and a half thousand
events **into the application's own state**. Nothing ever wrote them to the device.

**The path from a control to the controller had never been built.** Its absence was invisible in the
way that matters most: the stick moved, the numbers moved, the controller existed with all ten axes
and a matching descriptor, and five emulators recognised it — so everything looked right and nothing
arrived. A target saw a controller that never moved.

`InputEngine` is that path — the middle of `UI → InputEngine → backend → platform`. Three decisions
in it are worth stating:

- **The stream is held open.** Sending a control through a shell command would spawn a process per
  event, which at the rate a thumb moves a stick is hundreds a second. The privileged service
  already runs as shell, so it opens the stream once and writes to it. That is a design difference,
  not a tuning one.
- **Stick positions are coalesced, buttons are not.** Only the newest position matters — an old
  stick position is not partial information, it is wrong information — so a writer runs at about
  sixty a second and discards the rest. A press is a moment rather than a position and goes
  immediately.
- **Releasing centres the stick on the device, not only on screen**, and stopping a session
  releases everything. A control left deflected keeps the platform emitting directional keys, which
  Phase 0 measured at over 360 repeats.

On-screen A, B, X and Y buttons went in alongside, pressing on touch down and releasing on touch
up. Not an `onClick`: a click is reported after the finger lifts, so press and release would arrive
together and holding a control would be impossible.

`SessionState.engine` is null when there is no session, and the screen says so rather than
accepting input with nowhere to send it.

Evidence: `docs/phase0/results/app-session-20260819-redmi-note-13-5g.json`, the first export from
Kestrel itself rather than the harness — device id 9, ten distinct axes, descriptor
`8cc7a295…` matching every controller the harness ever made, holder pid 24298.

Also confirmed on that run: the screen refreshes in place, stop works from the notification and
in-application, force-stop and uninstall both end the session, and **the transformation is smooth
past the dead zone under a thumb** — the question the harness's cycled values could never answer.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: nothing in this entry has been run on a device.

### Phase 1 — Six Faults From One Device Run

Every item here came from the first run of the merged application on the reference device. Two were
serious, and one of those is the reason the other four were hard to see.

**The watchdog matched itself, so it never fired.** It checked whether the owner was alive with
`pgrep -f <package>` — and **`pgrep`'s own command line contains the package name it is searching
for**, so it always found itself, the owner always looked alive, and the guard never ran. Force
stop did nothing. Uninstalling did nothing. Only a reboot ended the controller — the exact failure
`docs/phase0/results/tier5-orphan-report.md` was written about, reintroduced by a different route.
Now `pidof`, which matches a process by name and cannot match the command running it, with a
bracketed pattern as the fallback for the same reason.

**Stop gave up silently when the binder was gone.** `ShizukuCapability.shell() ?: return` — so after
the application's process restarted, stop did nothing and *looked like it had worked*. Swiping the
application away is enough to lose that binder, and swiping it away was exactly what the operator
had to keep doing because of the next fault. Stop now reconnects before acting.

**Nothing on the screen ever refreshed.** The privilege state and the device list were read as plain
function calls in a composable — not snapshot state — so nothing recomposed and the screen only
changed when it was recreated from scratch. That is why every check needed the application clearing
from recents first, which in turn broke stop. Polled once a second now.

**Close escalates and reports what it found.** A pattern kill is a command that may match nothing;
it is not evidence. Holders are read, killed by pattern, any survivors killed by process id, and the
state is read again. If anything still holds the device the text says so in as many words, because a
controller that cannot be closed is the most serious failure this project has.

**The touch pad kept its position to itself**, so the readouts stayed at zero while the dot moved —
it worked and appeared not to. Its values now go where a controller's do, with the source named so
the two can be told apart.

**`axes=30`, explained.** A motion range is reported per source, so a device carrying three sources
lists the same ten axes three times. Both numbers are now shown — `axes=10 (ranges=30)` — rather
than one being quietly chosen. Nothing is wrong with the device.

**Report export**, saving to a folder of the user's choosing or sharing as a file, carrying the
build fingerprint, the four privilege facts, the session state and every input device with its
descriptor. Recorded observations have decided every question in this project so far; descriptions
of what someone saw have not.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: none of these fixes has been run on a device. The watchdog fault in particular was
found by reading rather than by testing, and reading is how it got in.

### Phase 1 — One Application, and a Watchdog Watching the Right Thing

**The watchdog was watching the wrong signal.** On the reference device a session died twice in the
background while its notification stayed on screen. The cause is now understood: the platform froze
the application, the heartbeat stopped, and the watchdog did exactly what it had been told — a
frozen application looked identical to a dead one.

That was the wrong question. A frozen application is alive and its session should survive; only a
dead or removed one should end it. The watchdog now checks two things directly, neither of which
needs the application to run any code:

- **its process still exists** — force-stop removes it, so force-stop still ends the session
- **its package is still installed** — uninstalling removes it, so uninstalling still ends it

The safety property from `docs/phase0/results/tier5-orphan-report.md` is intact and the false
positive is gone. Freezing an application no longer destroys a controller the user is using.

**Kestrel now holds its own session.** `platform/shizuku/` reaches shell privilege behind one
capability boundary, reporting the four facts separately as `ARCHITECTURE.md` §14 requires;
`platform/input/virtual/` creates the controller `ADR-INPUT-001` selected; `platform/session/`
keeps it visible and stoppable. **Rebuilt behind the platform layer rather than promoted from
`tools/phase0/`**, as `PROJECT_STRUCTURE.md` §27 requires — what carried over is the evidence, not
the code. The harness stays what it always was: the instrument that produced that evidence, kept so
the evidence can be reproduced.

The Shizuku dependency now appears in `:app`, confined to `platform/shizuku/`. The note in the
harness build that said it must never appear there predated the product needing it and has been
corrected: `:core` is still forbidden it, and no Composable may touch it.

**A touch pad, because a created controller cannot answer the question.** The first device test of
the analog transformation reported a jump past the dead zone. The two exports taken during that
session — `docs/phase0/results/tier5-session-20260819-…json` and `tier5-session-long-…json` — show
why that reading cannot be trusted either way: every motion event in them is a fixed value,
`X=1.000`, `Y=-1.000`, or all axes at rest. The harness cycles full deflection and rest with
nothing in between, so **there was no slow push in that test for a jump to appear in**.

The preview now has a stick driven by a finger, with the dead zone drawn where it actually is and
the raw and transformed positions shown together. Whether the jump is real is **still unknown**,
and this is what will settle it. The transformation's own tests assert the property directly, so if
a finger disagrees with them the fault is somewhere between them and the screen — which is worth
knowing and is exactly why this is being checked rather than assumed.

Verified: `./gradlew build` succeeds with lint clean, 92 tests passing.
Not verified: nothing in this entry has been run on a device. Every claim about the new watchdog is
a design intention until it is.

### Phase 1 — Analog Transformation and Profile Matching, in `core/`

**Analog transformation** — the shaping `CLAUDE.md` §5 requires to live outside every backend, pure
and unit-tested.

- **The dead zone rescales rather than filters.** Simply ignoring everything below the threshold
  leaves a jump: at 0.099 the stick is at rest and at 0.101 it is already a tenth of the way over,
  so a slow push snaps into motion. Rescaling means the first movement past the dead zone is the
  smallest possible movement, and there is a test for exactly that.
- **The dead zone is radial for a stick**, not per-axis. Per-axis produces a cross-shaped dead area:
  a diagonal push clearly past the threshold is swallowed on both axes, and pushing along one axis
  lets the other through unfiltered so aim drifts.
- **Direction is preserved exactly; only distance from centre is reshaped.** Anything else changes
  where the player is aiming rather than how fast they get there.
- An `outerLimit` lets a worn stick that no longer reaches its corners still report full deflection.
- Order is fixed so no caller can vary it: dead zone, curve, sensitivity, clamp, invert. Output
  never leaves the unit circle whatever the sensitivity, asserted across the whole input square.
- Inversion is ignored for triggers rather than producing one that rests fully pressed.
- A test pins the values Phase 0 measured — a half trigger at `0.502`, a half stick at `-0.500` —
  passing through unchanged when no shaping is asked for.

**Profile matching** — which profile applies when a target is launched, and **why**.

- Precedence: a user's **pin** beats an exact target match, which beats a family match, which beats
  the default. A pin outranks everything because it is the user overruling the product on purpose,
  and nothing automatic may quietly replace a deliberate choice.
- **Every answer carries its reason**, so the launcher can say why rather than choosing silently —
  `docs/DEGRADED_STATE.md` §6.
- **Ties break by identifier, alphabetically**: arbitrary, and chosen because it is. Breaking ties
  by "most recently edited" would mean opening the editor changes which layout appears next launch,
  and a launcher that behaves differently depending on invisible history cannot be trusted or
  debugged. Tests assert the answer is independent of the order profiles arrive in.
- Disabled profiles are skipped rather than chosen and then ignored, so one can never shadow a
  working profile. `candidateProfiles` returns everything applicable in the same order, because
  telling a user which profile will be used is worth little if they cannot see the alternatives.

31 new tests, 92 in the module, no failures.

### Phase 1 — A Screen You Can Install

`app/` gains a **diagnostic screen** over `core/`, in its own package until `feature/` exists —
allowed by `CLAUDE.md` §4 while the package boundary is real.

It reads whatever controller the phone already has, including one created by the Phase 0 harness,
and shows raw against transformed values live, with dead zone, curve, sensitivity and invert as
sliders. **It creates no input**: Kestrel still has no input backend, and the screen says so rather
than implying otherwise.

The reason it exists: the transformation is arithmetic and the tests prove the arithmetic. Whether
a curve *feels* right is a question only a thumb can answer, and until now nothing in `core/` could
be put in front of one.

Verified: `./gradlew build` succeeds with lint clean, `:core:test` 92 tests passing.
Not verified: the screen has not been run on a device.

### Phase 1 — Layout Geometry, in `core/`

The arithmetic a layout editor and an overlay both depend on, pure and testable.

- **Position and size are normalised differently, on purpose.** Position is an offset from one of
  nine **anchors**, so a control pinned to a bottom corner stays where a thumb rests when the screen
  shape changes; size is measured against the **shorter side only**, so a round button stays round
  and rotating the phone resizes nothing. Normalising position against full width and height moves
  thumb controls towards the middle of a wider screen; normalising size against both axes turns a
  circle into an ellipse. Both failures are avoided by construction and both have tests.
- Offsets apply **inwards** from the anchor, so an author never writes a negative number to move a
  right-hand control away from the right edge.
- **Insets** — cutouts, gesture areas — are subtracted by the surface rather than encoded in the
  layout, so one layout lands correctly on a phone with a cutout and one without.
- A control outside the usable area is **reported, not corrected**. Running a control off an edge
  can be deliberate, and the same principle as `ADR-007` applies.
- **Hit testing is exact under rotation**: the touch point is rotated back around the control's
  centre rather than the bounding box being tested. Overlapping rotated controls would otherwise
  answer for each other's touches.

**A defect found by a failing test rather than papered over.** Bounds ignored rotation entirely, so
a turned control was not merely approximated — it was wrong in both directions, reported as clear of
a neighbour it visibly overlaps and as fitting inside a surface it hangs out of. Bounds are now
rotation-aware, with a regression test that fails on the old behaviour. Two of the rotation tests
had also asserted the wrong diagonal, which was confirmed against the rotation matrix before the
code was touched — screen coordinates grow downwards, so a clockwise turn sends a long axis
down-left.

17 new tests, 61 in the module, no failures. `docs/CONFIGURATION_SCHEMA.md` gains the normalisation
rules, the inset rule and the rotation rule.

### Phase 1 — The Configuration Schema, in `core/`

Validation, identifiers and the document header, in plain Kotlin with no parser and no dependency.

- **`ConfigNode`** is the seam between reading and judging. Reading bytes is I/O and belongs to
  `data/`; deciding whether what was read is valid is domain logic. A parser produces this tree and
  every rule in `docs/CONFIGURATION_SCHEMA.md` is expressed against it, so `core/` needs no JSON
  library — and unknown fields survive, because validation reads the tree rather than consuming it.
- **`ConfigurationError`** — one sealed hierarchy for everything that can be wrong, each error
  naming the field it concerns. Being told a file is invalid leaves a user nowhere; being told
  `elements[3].opacity` is 1.4 and must be between 0 and 1 gives them something to do.
- **`ConfigurationId`** — namespaced, lowercase, dot-separated. Mixed case is refused so that an
  identifier cannot mean one thing on one filesystem and another elsewhere. `builtin.` is
  recognised by namespace rather than by a flag inside the file, and **`requireEditable` is the one
  place immutability is enforced** — in the domain, not by a disabled button.
- **`DocumentHeader`** — checks **version first**, because a document from a future schema is not
  malformed, this build is simply older, and telling the user to fix a good file is worse than
  telling them to update; then **type**, so reading a skin as a layout says exactly that instead of
  failing later on a field that was never going to be there.
- **`ControlKind`** joins the schema to `ADR-007`: an element stores what it *is*, and what it
  requires is derived. Storing the requirement would freeze today's capability model into every
  exported file. `digital-trigger` is a separate kind on purpose — a user may choose one, and it
  works where an analog trigger cannot, but the product never makes that substitution for them.

23 new tests, 44 in the module, no failures. `docs/CONFIGURATION_SCHEMA.md` gains the `control`
capability rule, the validation ordering rules, and what "preserved" means for unknown fields.

### Phase 1 — The Capability Model, in `core/`

First product code. Pure Kotlin in `core/input/`, no Android types, unit-tested — which means it is
verifiable in a container with no SDK, unlike everything Phase 0 produced.

- `InputCapability` — what a backend can do, in controller terms: buttons, d-pad, analog stick,
  analog trigger, simultaneous input, device identity, vibration. Two named sets go with it: what
  the preferred backend **measured** on the reference device, and what the touch fallback is
  **expected** to provide, labelled as an expectation because nothing about it has been tested.
- `CapabilityState` — Full, Ready, Reduced, Configure only, per `docs/DEGRADED_STATE.md`. Carries
  the two questions every screen asks: can a session start, and does the user need to be told
  something.
- `ControlAvailability` — `ADR-007` expressed once, where it can be tested. A control is available
  or disabled; **removal and substitution are not representable**, which is the decision rather
  than an omission. `disabledControls` and `missingCapabilities` compute what to say before a
  session starts, so nothing is discovered by pressing something inert.

12 tests, all passing, each encoding a decision rather than a mechanism, so changing the behaviour
means confronting the decision.

Verified: `./gradlew :core:test` — 21 tests across the module, no failures. `./gradlew build`
succeeds with lint clean, with the SDK installed.

### ADR-INPUT-001 Accepted — Scoped to the Reference Device

Decided by the project owner on the Phase 0 evidence. The record has been pending since the project
began; it is now Accepted, and **the scope is part of the decision rather than a caveat attached to
it**, so the conclusion cannot be quoted without its boundary.

**Decision.** The preferred production input backend is a kernel virtual input device, created
through the platform's own helper with Shizuku-provided shell privilege, and held for the length of
a session by a lease that a privileged watchdog enforces.

**Scope.** Xiaomi Redmi Note 13 5G, Android 15, HyperOS 3.0.3, unrooted, Shizuku at shell (uid
2000). Valid there. Everywhere else it is the project's working assumption, and an assumption is
not a result — `docs/COMPATIBILITY.md` keeps other devices at Untested until each has its own
evidence. Further OEMs and firmware will be tested as hardware becomes available.

**Explicitly not decided, and not implied:** latency, which has never been measured by any test;
behaviour while actually playing; wireless streaming, since the streaming test used a cable; any
other OEM or Android version; and **every fallback path** — nothing has been tested for a user
without Shizuku, which is the largest remaining gap in the project and the natural subject of the
next input record.

**Binding on any implementation**, because both were measured rather than reasoned:

- **Persistence must be governed, not prevented.** A session is held by a lease so that force-stop,
  cleared data and uninstall end it without the application running any code. A backend that holds
  a device without one can strand a controller on a user's phone until they reboot.
- **Identity keys on the device descriptor, never the numeric id**, which changes on every
  registration.

The record also names what would reopen it: a second device failing to reproduce the mechanism, a
platform change restricting shell access to the virtual-input facility, unacceptable measured
latency, or a fallback that needs a different primary design to stay coherent.

Propagated to `CLAUDE.md`, `README.md`, `ARCHITECTURE.md`, `PROJECT_STRUCTURE.md`,
`CONTRIBUTING.md`, `docs/INPUT_BACKENDS.md` and `docs/phase0/README.md`, each of which previously
described the selection as pending. ADR-002 is untouched and the backend abstraction stays: it is
what makes this decision revisable, and accepting a preferred backend is not a reason to collapse
it.

### Phase 0 — A Streaming Host Sees a Real Controller

The last outstanding acceptance criterion is met. See `docs/phase0/results/tier6-streaming-report.md`.

- A controller created on the phone was forwarded by Artemis and **appeared on the Windows host as
  a game controller**, listed with status OK, with axes and buttons moving as the phone drove them.
  Nothing was touched at either end.
- The name the host shows is its own virtual pad — a host reconstructs a controller locally rather
  than relaying a device identity — so it says nothing about the phone. What it establishes is that
  **the client accepted the device as a controller worth forwarding**.
- The session behaved as designed throughout: one holder alive across the whole test while the
  operator was inside the client, pause and resume twice without the device closing, and a stop
  verified by re-reading the state — holder present before, absent after, device count back to
  baseline, with the harness's own listener recording the removal independently.

**Every criterion in `docs/PHASE-0.md` §29 is now satisfied on the reference device**: digital,
analog, triggers, simultaneous, hold/release, lifecycle, five emulators, one streaming client, and
repeatability across many sessions and harness versions.

Not established, and the reason a decision must state its scope: one device, one firmware, one OEM;
**latency never measured**; nobody has played anything; the streaming test used a cable rather than
Wi-Fi; and **no fallback path has been tested at all**, so what a user without Shizuku gets is still
entirely unknown. That last gap is larger than everything else on this list.

`ADR-INPUT-001` is now **ready to be decided** rather than deferred, and its evidence table records
what supports a decision and what does not. It remains Pending until the project owner decides.

### Phase 0 Harness — Say It Only When It Means Something

- The heartbeat reported `lease renewal failed` before any session existed, which is the true state
  of an empty session rather than a fault, and it put an alarming line at the top of a log for a run
  that went on to work perfectly. It now speaks only while a session is open.
- The flag that gates it records what this process asked for, never what exists. A device can
  outlive the process that opened it, so the only honest answer to "is one open" stays the same:
  ask what holds the node open.

### Phase 0 — The Session Model Verified on Hardware

Operator-reported on the reference device with harness 0.0.16; no export was taken, so this is
recorded as observation rather than as a machine-readable evidence file. See
`docs/phase0/results/tier5-session-report.md`.

- **Survives** leaving the harness, switching applications, and removing the harness from the
  recent list, for as long as it was left running. Every target tested during the session — five
  emulators and the browser gamepad tester — recognised the controller as a physical one
  throughout.
- **Ends immediately** on Stop, from the notification or in the application. Pause and Resume stop
  and restart input without closing the device, from either place.
- **Ends within 10–20 seconds** on force stop and on uninstall. No reboot is needed any more.
- That window is the design: renewal every 4 seconds, the watchdog waking every 3 and acting on a
  lease older than 15, so the worst case is about 18. It is a dead-man's switch, and the threshold
  is a judgement — tuned tighter it would tear down a controller mid-session because the platform
  froze the application for a moment, and losing a controller during play is a worse failure than
  a device lingering fifteen seconds after an uninstall.

This makes the **lifecycle** criterion in `docs/PHASE-0.md` §29 met in a stronger sense than "the
harness can destroy what it created": the device can be ended by every means a user would reach
for, including the two that give an application no chance to run any code.

Recorded as an available option, not a decision: the guard could also watch the privileged
service's own process, which ends the instant the application is uninstalled, using the lease as a
backstop. Not implemented — the current behaviour meets the requirement, and that change should be
made against a measured need rather than a guess.

Not established: one device and one firmware; timings are wall-clock estimates by a person, not
measurements; behaviour across a reboot, with Shizuku restarted mid-session, and under memory
pressure is untested — the last of these being exactly where a dead-man's switch is most likely to
fire when it should not.

### Phase 0 — Sessions: Persistence That the Owner Can End

The orphan finding had an obvious reading — stop the device surviving — and it was wrong. A
controller that dies when you leave the launcher cannot be used to play anything; the persistence
is the feature. What was intolerable is that **nothing the owner did could end it**.

The harness now runs a session instead of a fixed-length hold:

- **A foreground service with an ongoing notification**, carrying Pause, Resume and Stop. A device
  that exists invisibly is the problem; a device with a permanent handle on screen is not.
- **A lease.** The service renews a timestamp in the privileged process every few seconds, and a
  **watchdog** there closes the device about fifteen seconds after renewals stop. It needs no
  cooperation from the application — which is the whole point, because force-stop, cleared data and
  uninstall all end an application without letting it run any code. A teardown that depends on the
  application running is not a guarantee; a lease that stops being renewed is.
- **Pause stops input without closing the device.** Holder and feeder are separate processes: the
  holder reads an ordinary file through `tail -f`, so the feeder can stop and restart without the
  holder ever seeing end of input.
- No timer to outlast and none to wait out. The device exists while the notification does.

Recorded as a product rule in `docs/phase0/results/tier5-orphan-report.md` §4a: **persistence must
be governed, not prevented.**

### Phase 0 Harness — The Holder Names Itself

- The `/proc` scan added in the previous version was the right question and the wrong instrument:
  on the reference device it took longer than ten seconds and was killed by its own timeout,
  mid-answer. It did get far enough to print what mattered —
  `32267  app_process /system/bin com.android.commands.uinput.Uinput -`.
- **The holder is `app_process`.** There was never a process called `uinput` for any of the earlier
  sweeps to find. Teardown now matches that command line, which is specific, stable and returns
  immediately, and re-reads the state afterwards.
- Evidence: `docs/phase0/results/tier5-teardown-20260818-redmi-note-13-5g.json`. In it the sweep
  times out while listing, and `input devices now: 8` records the device count returning to
  baseline — the kill worked, the listing was what could not finish.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.16 has not been run on a device. The lease timeout, the watchdog, and
every claim about force-stop and uninstall behaviour are **untested** until it is.

### Phase 0 — A Created Controller Can Outlive Everything

The most serious finding so far, and the reason teardown is now an architectural requirement rather
than a detail. See `docs/phase0/results/tier5-orphan-report.md`.

- A created controller could not be stopped by **Destroy device, force stop, clearing data, or
  uninstalling the harness**. It kept delivering input to the home screen and the browser with the
  application no longer installed, and only a reboot ended it. It stopped when its own ten-minute
  schedule ran out — nothing the operator did contributed.
- **Cause: every stop command matched on the process being called `uinput`, and it is not.** The
  helper runs inside a runtime process with a different name, so `pkill -x uinput` killed nothing,
  ever, and `pgrep -x uinput || echo NONE` reported success from the same broken search. This was
  visible in every transcript from the first creation run — `(no output, exit=1)` on runs where the
  device demonstrably existed — and was read as "nothing running" rather than "this search does not
  work". Earlier changes fixed the *reporting* and never tested that a stop actually stopped
  anything.
- **Why it survives uninstalling:** the device belongs to whichever process holds `/dev/uinput`
  open, and that process is not a child of the application. It was started through the privileged
  service, runs as `shell`, and has no relationship to the application's lifecycle. Uninstalling
  also runs no code, so an application cannot clean up on its way out.

Fixed in the harness:

- Teardown now asks **which processes have the node open**, by scanning `/proc/*/fd`, and kills
  those whatever they are called. The same scan runs again afterwards and its result is printed:
  the report is the state after the attempt, not a claim that the attempt worked.
- **STOP ANY DEVICE** and **What is open?** are always available and never disabled — recovery must
  work from a cold start on a device created by a previous install, because that is exactly what
  this failure produces.
- A warning banner appears whenever a Kestrel controller is present, on the first screen and
  without Shizuku, so an orphan announces itself instead of being discovered by its effects.

Required of the product, recorded now because the evidence exists now:

- A production backend must hold the descriptor **inside a process the platform reclaims with the
  application** — the Shizuku user service bound to the application's lifetime — never a detached
  shell schedule.
- Recovery must not depend on remembered state: Kestrel must find and destroy a controller it has
  no record of creating.
- Startup must sweep for an orphan before doing anything else.
- Every teardown must re-read the state and report what it found. A stop that reports success
  without checking is worse than no stop, because it stops anyone looking further.

### Phase 0 — Two More Emulators, and a Browser

- **PPSSPP** binds it — `pad1.Y HAT+`, `pad1.X Axis+`, `pad1.Z Axis+`, `pad1.TriggerL+`, `pad1.[A]`
  — closing the gap left in `tier6-report.md`. Fourth emulator.
- **Dolphin** lists it as `Android/1/Kestrel Virtual Controller` in its device chooser, beside the
  phone's real input devices. Fifth.
- A **browser gamepad tester** reports it through the web Gamepad API: name, vendor `18d1`, product
  `4ee0`, connected, sixteen buttons, live axis values. The browser has no controller heuristics of
  its own, so this is a target written with none of this in mind treating the device as an ordinary
  controller.
- Still not a streaming result. The streaming half of `docs/PHASE-0.md` §29 remains unmet.

### Phase 0 Harness — A Hold Long Enough to Set Up a Stream

- Added a ten-minute hold alongside the two-minute one. Two minutes is enough to open a target's
  binding screen and enough to explain why the device disappeared partway through the last run; it
  is not enough to pair a client with a host, start a stream, and then look at what the host sees.
- The hold length is now one parameter rather than a fixed count, so the schedule and the message
  describing it cannot disagree.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.14 has not been run on a device.

### Phase 0 — Emulators Accept a Kestrel-Created Controller

Tier 6, on the reference device. See `docs/phase0/results/tier6-report.md`.

- **Eden** lists `Kestrel Virtual Controller 0` in its own input-device filter, shows Player 1 as
  Connected with type Pro Controller, and auto-mapped the full control set — face buttons to
  `Button 96–100`, shoulders to `102/103`, d-pad to `±Axis 15/16`, left stick to `Axis 0/1`, and
  **ZL/ZR to `Axis 17/18`**, meaning it classified the triggers as analog controls.
- **NetherSX2** completed Automatic Mapping and wrote bindings naming the device *and its id*:
  `Kestrel Virtual Controller[25]/Button96`, `[25]/-Axis16`. The harness's own log for the same
  session records every event as `dev=25` — **the id the emulator stored is the id the platform
  assigned, observed independently by two applications that know nothing about each other.**
- **RetroArch 1.22.2** selected it as Port 1's Device Index, under its own description "The physical
  controller as recognised by RetroArch."
- Against `docs/COMPATIBILITY.md` §10 this is **Level 4 — virtual gamepad identity**, reached on
  stock unrooted hardware. The restriction in `docs/INPUT_BACKENDS.md` on the phrase "true virtual
  gamepad" is satisfied for emulators, on this one device and firmware.
- The device survived several target applications being opened in turn, which is what a real
  session needs. It disappeared partway through; the expected cause is the two-minute hold schedule
  ending, but the export carries no timestamp proving that, so it is recorded as unexplained rather
  than attributed.

Not established, and stated because these are the reasons Phase 0 is still open:

- **No streaming client confirmed.** Artemis exposes no screen listing connected controllers, so
  the attempt produced no observation at all. A client is a pass-through; the question is whether
  the host sees a gamepad, which means testing against a host.
- **PPSSPP untested** — its settings screen was not located during the run. Recorded as untested,
  never inferred from the three that worked.
- One device, one firmware, no gameplay, no latency measurement.

`docs/COMPATIBILITY.md` now carries the device row, the input-backend matrix, and the emulator
feature matrix, all at Status Experimental / Confidence Low. `ADR-INPUT-001` gains an evidence
table naming the four items still missing, and stays **Pending** — §29 requires a streaming client
and repeatability, and neither is done.

### Phase 0 Harness — An Instrument That Hangs Is Worse Than One That Fails

The first Tier 6 attempt produced nothing. The harness froze on pressing the hold button, before
any device was created: every control stayed locked, no device appeared in any target, and the
session ended with no evidence at all. Recorded as a harness fault, not a device result — nothing
was learned about the phone.

Three things were wrong, and all three are fixed:

- **A shell call could block forever.** The privileged service read its child's output to end of
  file and waited for it without a limit. A backgrounded child keeps that output open after its
  parent exits, so the read never ended. Every call is now bounded: output is drained on a separate
  thread, the process is killed if it overruns, and the result says it timed out. A reading that
  says "timed out" is a result; a frozen instrument is not.
- **The named pipe was the wrong mechanism.** Opening a pipe waits for the other end, so any step
  of that handshake that does not complete stops the thread. The stream is now an ordinary file,
  appended to, followed by `tail -f`. Appending to a file never waits for a reader. The property
  it was introduced for is kept: each stage is still written by the thread that writes its marker.
- **The lock had no way out.** A run that wedged left every control disabled with no recovery.
  There is now a RESET control that is never disabled — it unlocks the interface, stops any helper,
  and reports what it found. Tab switching is no longer locked either: it cannot damage a
  measurement, and locking it left the operator unable to watch the log being written.

Target-application holding was also moved back onto the plain pipeline, the mechanism that has
already delivered every control on this hardware. The appended stream exists to keep log markers
aligned with events, and when the operator is in another application there are no markers to align.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.13 has not been run on a device. Tier 6 remains untested — the first
attempt produced no measurement of any kind.

### Phase 0 — Every Control Delivered Through a Created Controller

Six of the eight acceptance criteria in `docs/PHASE-0.md` §29 are now met by the mechanism. See
`docs/phase0/results/tier5-exercise-report.md`.

- Both sticks, both triggers, the d-pad and three simultaneous buttons were driven through a
  created controller. **All eight stages produced input, every event attributed to that device's
  own id**, and every control returned to rest with the rest delivered.
- **Analog is real, not saturated.** A stick written at half of its declared range arrived as
  `-0.500`, and a trigger at half arrived as `0.502`. The value is scaled through the whole path.
- Digital, analog, triggers, simultaneous, hold/release and lifecycle are met. Repeatability and a
  target application are not, and those are the two that need something other than the harness.
- Three platform behaviours recorded from a physical controller in Tier 1 reappeared on the created
  one, confirming they are applied to any controller rather than being artifacts of how this device
  is made: a held stick synthesises d-pad keys with auto-repeat (thirteen from one axis write),
  each trigger reports on two axis names at the same value, and buttons with a system meaning are
  delivered twice — `BUTTON_A` also as `DPAD_CENTER`, `BUTTON_B` also as `BACK`, `BUTTON_Y` also as
  `SPACE`.
- Event ordering is looser than pairs: presses repeat while held and duplicates interleave with
  real presses. Button state must be tracked per `(deviceId, scanCode)`, with repeats read as
  continuation and unmatched releases idempotent.

**The created controller operated the application that created it.** Mid-run, the stick's
synthesised d-pad keys walked focus onto a harness button and `BUTTON_A`'s `DPAD_CENTER` duplicate
activated it, opening the file picker and pausing the measurement. This is a **product design
requirement, not a harness quirk**: Kestrel will create a controller and then show its own
interface in front of the user, and that interface will be driven by that controller — including
`BUTTON_B`, which reaches an activity as Back. `feature/gaming-session` and the overlay must be
built for this.

### Phase 0 Harness — One Clock, and an Instrument Its Own Stimulus Cannot Drive

- **Removed a measurement fault.** The run was scheduled as one shell command while the harness ran
  a matching schedule of its own to label the log. Two clocks, nothing tying them together: they
  drifted about twenty seconds apart and every stage marker landed after the events it introduced.
  The evidence survived only because the events describe themselves. The device is now opened on a
  named pipe held open by a sleeping process, and each stage is written by the same thread that
  writes its marker, immediately after it — a marker cannot drift from its events.
- That is also the shape a production backend needs: a device outliving any single command, with
  input pushed as it happens rather than scheduled in advance.
- **Controls are locked while a test runs, and Back is held for the same window.** Events are still
  recorded before the guard acts; only the activity's reaction to them is suppressed. An instrument
  its own stimulus can operate is measuring itself.
- Added a hold mode for Tier 6: the device stays open and cycles one control every few seconds for
  about two minutes, with the schedule handed to the privileged process so it continues while the
  harness is in the background and a target application's binding screen is open.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.12 has not been run on a device.

### Phase 0 — Delivery Through a Created Controller, Repeated

- The create-and-press test was re-run on a later harness build in a separate session on the same
  phone. Identical outcome: device created as **id 21**, all six `BUTTON_A` events arrived carrying
  `dev=21`, `src=KEYBOARD|GAMEPAD`, `scan=304`, with the same `DPAD_CENTER` duplicate on each.
- **Six registrations have now produced six different ids and one unchanging descriptor**
  (`8cc7a295…`). Keying identity on the descriptor rather than the id is demonstrated, not argued.
- Twelve buttons and ten axes present again, so the descriptor fix holds across builds.
- Recorded as `docs/phase0/results/tier5-press-repeat-20260818-redmi-note-13-5g.json` and folded
  into `tier5-press-report.md` §4a. Delivery is now n=2, which is a repeat, not yet repeatability:
  `docs/PHASE-0.md` §29 wants the sequence surviving reboots and privilege restarts.

### Phase 0 Harness — Exercise Every Control, Not One Button

- Added a test that drives **both sticks, both triggers, the d-pad and three buttons at once**
  through the created device, each held for a second and then returned to rest. One button proved
  the device can deliver its own input; it did not prove it can deliver a *controller's* input, and
  §29 names all of these.
- Included **half-deflection stages** for a stick and a trigger. The descriptor declares raw kernel
  ranges while the platform reports axes normalised, so a half value is the only way to distinguish
  a real conversion from a value that saturates at 1.0.
- Every stage returns its control to rest, and the device outlives its last release. A stuck axis
  makes the platform emit directional keys without stopping — measured earlier at over 360 repeats
  from a process that had already exited.
- Stage markers go into the event log as the helper reaches them, so a control that produces
  nothing is visible as a gap rather than lost in an undifferentiated stream.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.11 has not been run on a device.

### Phase 0 — A Created Controller Delivered Its Own Input

The last open question at Tier 5 is answered. See `docs/phase0/results/tier5-press-report.md`.

- A virtual controller created by Kestrel on a stock unrooted phone, with no computer attached, was
  sent three `BUTTON_A` press/release pairs. **All six key events arrived at an ordinary
  unprivileged window carrying `dev=17` — the id the platform had assigned to that device seconds
  earlier — with `src=KEYBOARD|GAMEPAD` and `scan=304`, the exact key code the descriptor
  declared.** The device is delivering its own input, not routing it through the system virtual
  device.
- All three unprivileged Tier 5 requirements now hold on this hardware: the device appears via
  hot-plug, it advertises gamepad and joystick sources with ten real axes, and events from it carry
  its own id.
- `BUTTON_L2` and `BUTTON_R2` are present on the created device, confirming the earlier gap was a
  descriptor omission and not a platform limit.
- Axis ranges arrive **normalised** — sticks `-1…+1`, triggers `0…+1` — although the descriptor
  declares raw kernel ranges. The platform performs that conversion itself.
- Every `BUTTON_A` was accompanied by a `KEYCODE_DPAD_CENTER` on the same device id and scan code,
  matching what Tier 1 recorded from a *physical* controller. Duplicate delivery is a platform
  mapping, not an artifact of injection: the input layer must de-duplicate on
  `(deviceId, scanCode)`.
- The log also contains a `DPAD_CENTER` release with no preceding press. Button state tracking must
  tolerate unmatched releases rather than assuming strict down-then-up ordering.

Still not proven, and the reason this is still not a pass:

- One button. No analog axis, trigger, D-pad or simultaneous input has been driven through the
  created device.
- No target application has seen it — Tier 6 is untouched, so `docs/INPUT_BACKENDS.md` still bars
  the phrase "true virtual gamepad".
- One device, one firmware, and delivery demonstrated once. Latency unmeasured. Shizuku required
  throughout, so per ADR-003 this can only ever be the best backend, never the only one.
- `ADR-INPUT-001` remains **Pending**. What has changed is the shape of the remaining work: it is
  now extending a demonstrated mechanism rather than searching for one.

### Phase 0 Harness — Share a File, and Report While Working

- **Share now sends an actual `.json` file** through a non-exported `FileProvider`, rather than
  pasting the report into a message body where it had to be copied back out and could be silently
  truncated. The receiving application gets a read grant for that one file.
- **Long-running actions report each step as it happens.** Results were previously assembled into
  one string and shown only when the whole action finished, so the decisive create-and-press test
  looked frozen: nothing appeared on screen until the device had already been created, pressed and
  removed. Registration, press, and teardown now each report as they pass.
- Added `docs/phase0/results/inbox/` as a drop-off point for raw exports, so evidence can be pushed
  to the repository directly instead of re-uploaded through a chat window every run. It is a
  staging area — files are renamed to the convention in `docs/phase0/README.md` §6 and moved out.

Verified: `./gradlew build` succeeds with lint clean, with the SDK installed.
Not verified: harness 0.0.10 has not been run on a device.

### Phase 0 — A Created Controller Matches a Real One

The Grade A prerequisite is met. See `docs/phase0/results/tier5-gradeA-report.md`.

- A device created by Kestrel on a stock unrooted phone, with no computer attached, was compared
  property by property against the physical controller recorded in the Tier 1 calibration on the
  same phone. **Sources, raw source flags, controller number, external flag, gamepad
  classification, axis count and the full axis list are identical.** The system assigned it
  controller number 1 — the player slot it gives a real controller.
- The only difference was two buttons, `BUTTON_L2` and `BUTTON_R2`, which the descriptor had simply
  never declared. Now declared.
- Reproduced across at least four creations, and it persisted for the full 30-second hold.
- Device ids increment on each registration, which is correct: ids are per-registration handles,
  never reused within a boot, and a physical controller replugged behaves the same way. Both
  captured instances carry the same descriptor hash, and the device count returned to its baseline
  after each removal, so nothing accumulated. **This settles a design rule: identity must be keyed
  on the descriptor, never on the numeric id**, or per-controller settings would detach themselves
  whenever a session restarted. It belongs in `core/input/`.

Still not proven, and the reason this is not yet a pass:

- **Nothing has been sent through the device.** It exists and is classified correctly; whether
  events written to it arrive attributed to it rather than to the system virtual device is the
  difference between a device that looks right and a controller that works.
- No target application has been tested. Triggers, simultaneous input and repeatability are
  untouched. `ADR-INPUT-001` remains Pending.

### Phase 0 Harness — Stop Asserting What the Evidence Does Not Support

- The helper liveness check reported `NOT RUNNING` while the device demonstrably existed for its
  full thirty seconds. The same check had reported a false positive one version earlier. It is
  replaced with raw process listing output and no derived claim.
- Recorded as a principle, not just a fix: an instrument that asserts a conclusion its evidence does
  not support is worse than one that shows what it saw. An operator can read raw output correctly;
  nobody can recover the truth from a confident wrong summary. This harness exists precisely to not
  do that.
- Added an event-injection attempt through the created device, so the remaining question can be
  answered: press a button on it and read which device the arriving event is attributed to.

Verified: `./gradlew build` succeeds with lint clean.
Not verified: the press test has not been run.

### Fixed After First Device Run

- The on-screen event counter was a plain integer, invisible to composition, so it stopped matching
  the log it was counting. It is now snapshot-backed.
- Neither screen applied window insets, so content drew underneath the status bar on Android 15,
  which draws edge to edge by default at this target level. Both now inset for system bars.

### Product Identity

- Added an adaptive launcher icon to both applications: a double-chevron mark reading as swept wings
  and ascent, drawn as vector art so it stays sharp at every density with no bitmap assets.
- Earlier illustrative attempts at a falcon silhouette were rendered and inspected before being
  rejected: at icon size they read as an aircraft or an insect. The geometric mark survives being
  reduced to 48dp, which is the size that actually matters.
- The harness carries the same mark in neutral steel rather than the product's colours, so the
  experimental build is never mistaken for the product on a home screen.

### Continuous Builds

- Added `.github/workflows/build.yml`. Every push and pull request compiles, lints, tests, and
  attaches both APKs as downloadable artifacts; a tag beginning with `v` additionally publishes a
  release with both APKs attached, giving a link that can be opened directly on a phone.
- This removes the need for a local toolchain to obtain an installable build.
- Both APKs are debug-signed. Release signing requires a keystore in repository secrets and is
  deliberately not set up; a debug-signed build must not be treated as distributable.

Verified:

- The workflow's first run completed successfully on GitHub-hosted runners, building, linting and
  testing both modules and attaching both APKs as artifacts. No change to the workflow was needed.

### Phase 0 Harness Established

- Added `tools/phase0/` — the input feasibility harness, as its own application with its own
  identifier (`io.github.zxaidman.kestrel.phase0`), no permissions, and no dependency on `:app` or
  `:core`. Labelled experimental per `PROJECT_STRUCTURE.md` §27.
- The harness observes only: it enumerates reported input devices, listens for device hot-plug, and
  logs every key and motion event its window receives with the id and source of the originating
  device. It injects nothing, so that a measured result cannot be produced by the instrument.
- Added `docs/phase0/README.md` — the test procedure, structured as six tiers from baseline
  inventory through to real target applications, with the OEM preparation steps the target device
  requires.
- Added `docs/phase0/results/` for exported evidence.

Fixed during first compilation:

- `IntArray` has no `mapNotNull`; device enumeration used `map` and `filterNotNull` instead.
- The harness consumed key events, including BACK, which would have trapped the user on the screen.
  It now records each event and passes it on untouched — an observer must not swallow what it
  measures.
- Rumble detection used an API deprecated from API 31; it now selects the API by version.
- Removed redundant manifest labels and a mis-declared composable.

Verified:

- `./gradlew :tools:phase0:assembleDebug` produces `phase0-debug.apk` with identity
  `io.github.zxaidman.kestrel.phase0`, label Kestrel Phase 0, installable alongside the product.
- Lint reports no errors for the module.

Not verified:

- The harness has never been installed or launched on a device.
- No tier has been executed and no evidence has been recorded. `ADR-INPUT-001` remains Pending.

### Setup Documentation

- Added `docs/SETUP.md` — a build and install guide for contributors who are not software
  developers, using the command-line tools and a code editor rather than the full IDE. The Linux
  path in it was executed end to end; the Windows and macOS paths were not, and say so.

### Rebranding

- Renamed the project to Kestrel, for a distinctive mark.
- Set the package identity to `io.github.zxaidman.kestrel`.

### Documentation Artifacts Established

These files exist in the repository.

- Established the root documentation set: `README.md`, `PRD.md`, `ARCHITECTURE.md`,
  `PROJECT_STRUCTURE.md`, `DEVELOPMENT.md`, `AI_DEVELOPMENT_GUIDE.md`, `CLAUDE.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`,
  `THIRD_PARTY_LICENSES.md`, `LICENSE`.
- Established the supporting documentation set under `docs/`: `PHASE-0.md`, `COMPATIBILITY.md`,
  `INPUT_BACKENDS.md`, `CONFIGURATION_SCHEMA.md`.
- Established the accepted decision records under `docs/adr/`:
  - `ADR-001-json-first-config.md` — JSON-first configuration
  - `ADR-002-input-backend-abstraction.md` — input backend abstraction
  - `ADR-003-shizuku-optional.md` — Shizuku is optional
  - `ADR-004-android-10-baseline.md` — Android 10 / API 29 baseline, phones only
  - `ADR-005-gplv3.md` — GPLv3 for original project code
- Recorded `ADR-INPUT-001.md` as pending, awaiting Phase 0 evidence.
- Established contribution infrastructure under `.github/`: pull request template and issue
  templates for bug reports, feature requests, and compatibility reports.
- Designated `PROJECT_STRUCTURE.md` as canonical for folder organization, and corrected the
  repository tree in `ARCHITECTURE.md` §4 to match it.
- Defined a single decision-record naming convention in `CONTRIBUTING.md` §57.
- Documented how the compatibility statuses, Phase-0 evidence grades, and claim-verification states
  relate to one another in `docs/COMPATIBILITY.md` §4a.

---

## Versioning Policy

Before the first meaningful release, changes remain under:

`[Unreleased]`

When a release is created, entries should be moved into a versioned section such as:

```text
## [0.1.0] - YYYY-MM-DD
```

The project should avoid inventing release numbers for prototypes that were never actually distributed as meaningful software releases.

---

## Changelog Categories

Use these categories when applicable:

### Added

New functionality.

### Changed

Changes to existing behavior.

### Deprecated

Features that remain available but are planned for removal.

### Removed

Removed functionality.

### Fixed

Bug fixes.

### Security

Security-related fixes or changes.

### Internal

Important architectural or developer-tooling changes that do not directly affect users.

---

## What Belongs Here

The changelog should record meaningful project changes, such as:

- new input backends
- new Android-version support
- new controller templates
- major launcher functionality
- configuration schema changes
- compatibility changes
- security fixes
- breaking behavior changes
- significant performance improvements

The changelog should not become a copy of every Git commit.

---

## What Usually Does Not Belong Here

Avoid listing every:

- typo fix
- variable rename
- formatting-only change
- internal refactor with no relevant behavior change
- temporary debugging change
- failed local experiment

Those belong in Git history or development documentation where appropriate.

---

## Breaking Changes

Breaking changes should be clearly identified.

Examples include:

- incompatible JSON schema changes
- removal of a public configuration format
- changed profile semantics
- changed controller mappings
- removal of supported Android versions

When possible, migration instructions should accompany breaking changes.

---

## Security Changes

Security fixes should be documented here when disclosure is appropriate.

Do not include sensitive exploit details merely for completeness.

Refer to [`SECURITY.md`](SECURITY.md) for security reporting and disclosure policy.

---

## Compatibility Changes

Android compatibility changes should identify the relevant environment when useful.

Example:

```text
- Fixed Shizuku capability detection on Android 14 devices.
- Added compatibility information for a specific emulator version.
```

Device/OEM-specific findings should also be recorded in compatibility documentation.

---

## Development-Stage Notes

Kestrel is currently an early-stage project.

The current goal is not to create a long changelog full of artificial version numbers.

The goal is to preserve a trustworthy record of how the project evolves.

Failed experiments may be better recorded in:

```text
docs/
docs/adr/
docs/phase0/
```

A failed experiment can still be valuable documentation.

---

## Links

Version comparison links are added once the first release exists. Until then only the repository
link below is meaningful, because there is no tag to compare against.

```text
[Unreleased]: https://github.com/Zxaidman/Kestrel/commits/main
```

After the first release, the pattern becomes:

```text
[Unreleased]: https://github.com/Zxaidman/Kestrel/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Zxaidman/Kestrel/releases/tag/v0.1.0
```
