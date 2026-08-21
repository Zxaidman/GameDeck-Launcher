# Kestrel — To-do List

**Document:** `todo-list.md`  
**Status:** Active — the single work queue. Nothing is started that is not on it.  
**Owner of priority:** the project owner. This document orders and describes; it does not decide.  
**Last updated:** build `0.0.25-dev`

---

## How to read this

Six sections, in the order the project owner asked for them.

| § | Section | What belongs in it |
| --- | --- | --- |
| 1 | **Critical** | Blocks the `v0.1.0` release. Nothing ships until every one is closed or deliberately deferred. |
| 2 | **Errors and bugs** | Something is wrong now. Each carries how it was found and whether it is reproduced. |
| 3 | **Features** | New capability. Each is separable and can be scheduled on its own. |
| 4 | **Working now** | What is built and verified, so nobody rebuilds it and nobody claims more than was measured. |
| 5 | **Pending scope** | Agreed direction, not yet started. |
| 6 | **Owner's list** | Reserved for what the project owner sends next, and everything after. |

**Every entry has an ID** (`CRIT-1`, `BUG-3`, `FEAT-7`) so it can be referred to in one word.

**Evidence vocabulary**, used strictly and matching `AI_DEVELOPMENT_GUIDE.md`:

- **Measured** — observed on the reference device, with the result recorded.
- **Reported** — the project owner saw it; not yet reproduced or diagnosed here.
- **Reasoned** — follows from documented platform behaviour; not observed.
- **Unverified** — believed, with nothing behind it.

**Reference device for every "measured" claim:** Redmi Note 13 5G, HyperOS 3.0.3, Android 15,
Shizuku shell (uid 2000), no root. One device, one firmware. Nothing here is a claim about other
hardware.

---

## 1. Critical — blocks `v0.1.0`

### `CRIT-1` — A release signing key that is not in this repository

**Why it blocks.** The key currently signing every build is committed, and its password is public.
Anyone can sign an APK with it, and the platform will install that APK straight over a user's
Kestrel, inheriting their permissions and their data folder. A signature is the only thing that
makes an update *an update* rather than a different application, and a public key protects nothing.

Acceptable while people are testing builds they fetched themselves from a CI page. **Not acceptable
the moment a build is published as a release**, which is exactly what `v0.1.0` is.

**What it needs.** A key generated once and kept offline, given to CI as encrypted repository
secrets and assembled at build time. The testing key stays for testing. `signing/README.md` carries
the reasoning; the key itself has to be generated and held by the project owner, because if it is
lost no future version can ever update an installed Kestrel.

**Depends on:** the project owner generating and storing the key. Cannot be done unilaterally.

---

### `CRIT-2` — A home screen, and navigation

**Why it blocks.** What opens today is a diagnostics harness: every developer control, every raw
number, one long scroll. It was the right thing while the question was "does any of this work". It
cannot be the first thing a user sees.

**What it needs.** A home screen that says what Kestrel is and what state it is in; navigation
between home, controller, layouts, settings and diagnostics; and developer tools moved behind a
deliberate door rather than presented as the product.

**Explicitly requested** by the project owner: *"we can't have our dev build homepage as release
build … hide unnecessary options from the homepage and good navigation setting"*.

**Ordered after** `FEAT-2` (test ground) at the project owner's request.

---

### `CRIT-3` — Modular architecture, as `PROJECT_STRUCTURE.md` already describes it

**Why it blocks.** Every line of product code lives in `:app`. `PROJECT_STRUCTURE.md` has described
`feature/`, `platform/` and `data/` modules since before any of it was written, and the gap between
the document and the tree grows with every screen. `CLAUDE.md` §4 allows the packages to be
physically grouped early — it does not allow the boundary to stop meaning anything.

The one boundary that **is** enforced is `:core` being Kotlin/JVM, which makes an illegal import a
compile error rather than a review comment. That is the pattern to extend, not abandon.

**What it needs.** `feature/` for screens, `platform/` for Android-specific implementations, `data/`
for packaged configuration. The input backend behind an interface (`ADR-002`), so a second backend
is possible without the rest of the system noticing.

**Ordered with** `CRIT-2`: doing the navigation first and the modules afterwards would mean moving
the same code twice.

---

### `CRIT-4` — Decide what `v0.1.0` contains

**Stated by the project owner:** release and tag `v0.1.0` once **overlay, controller editor, gaming
session and Shizuku** are complete, then push to `main`.

Three of the four are done or nearly so. **Gaming session (Phase 4) is roughly a third built** — it
holds a controller and survives leaving the application, and it cannot launch a target, load a
profile, or notice which target is in front. That is the gap between here and the tag.

The CI workflow already publishes a release with both APKs attached on any `v*` tag, so tagging is
one action once the contents are agreed — and once `CRIT-1` is done.

---

## 2. Errors and bugs

### `BUG-1` — The overlay does not draw into the cutout area

**Reported**, `0.0.25-dev`, with a screenshot. Kestrel's own screen now uses the notch area
correctly; **the controller overlay does not**. On the reference device the left-hand controls stop
short of the notch, wasting the space the setting was turned on to claim.

**Likely cause, reasoned and not yet confirmed:** the overlay's windows are separate from the
activity's, and `layoutInDisplayCutoutMode` was applied only to the activity. An overlay window has
its own attributes and its own insets, and the surface it resolves against subtracts the cutout
whether or not the user asked it to.

**Also:** `BUG-2` is the same fault seen from the other side.

---

### `BUG-2` — The "use the notch area" setting does not reach the overlay

**Reported**, `0.0.25-dev`: *"yes, except for gamepad"*. Toggling the setting changes Kestrel's own
screen and leaves the controls where they were. A setting that works in one place and silently does
nothing in another is worse than one that is absent, because it teaches the user it did nothing at
all.

**Fix is shared with `BUG-1`.** The overlay must read the same preference and set its own windows'
cutout mode and insets accordingly.

---

### `BUG-3` — `HOW-TO-EDIT.md` is not written

**Reported**, `0.0.25-dev`: *"no, `HOW-TO-EDIT.md` found"* — meaning it was not found.

**Diagnosis, reasoned:** the guide is written by **Copy layout to my folder** only. The project
owner reached the editor through **Edit layout**, which duplicates a built-in by a different path
and never calls the writer. So the code is right and it is on the wrong path.

**What it needs:** the guide written whenever a layout is written into the user's folder, by any
route — and a check that the file is actually there rather than an assumption that the call
succeeded.

---

### `BUG-4` — `sensor-portrait` does nothing and should go

**Reported**, `0.0.25-dev`: *"sensor portrait is useless just like reverse portrait discard it"*.

Most phones do not support reverse portrait at all, so `sensor-portrait` behaves exactly like
`portrait` on the device in front of the user. **An option that does nothing is worse than one that
is absent** — the same reasoning already used to leave reverse-portrait out.

**What it needs:** remove `SENSOR_PORTRAIT`, leaving `auto`, `landscape`, `reverse-landscape`,
`sensor-landscape`, `portrait`. A settings file naming the removed value must keep loading, falling
back rather than being refused.

---

### `BUG-5` — A `"shape": "round"` was seen somewhere

**Reported**, `0.0.24-dev`, and **not reproduced**. Kestrel only ever writes `circle`, `square` or
`rectangle`, and the reader refuses anything else with the allowed values listed. The only
`round`-adjacent value in any Kestrel document is `deadzoneShape` in `settings.json`, which is
`radial` or `axial`.

**Open question rather than a known fault.** If a layout genuinely contains `"shape": "round"`,
**Reload layout** should have refused the file — so either the file was not the one being read, or
something writes a value this project does not know about. Needs the file, or the sighting
withdrawn.

**Priority:** low, and it stays on the list because an unexplained value in a validated document is
not something to shrug at.

---

### `BUG-6` — Kestrel cannot create its own data folder

**Measured** and **not fixable within the current permission set**, recorded so it is not raised as
a bug repeatedly.

Creating a directory at the top of shared storage needs `MANAGE_EXTERNAL_STORAGE` — access to every
file on the phone. Declaring a permission of that class is exactly what got Kestrel **blocked by
Play Protect** when the accessibility service was declared, measured in `ADR-006` and confirmed in
both directions. One tap in the picker, once, versus every user's install.

**Status: accepted limitation.** Reopen only if the project owner decides the Play Protect cost is
worth paying, which would be a measured experiment rather than a code change.

---

## 3. Features

### `FEAT-1` — Face buttons as one cluster, like the d-pad

**Requested**, with a reference image: four face buttons on a **shared round plate**, read as one
group rather than four independent circles.

The project owner's words: *"on DPAD yes I mean it like option a … but i like the sound of option b
also"*. Option (a) is this entry; option (b) is `FEAT-2`.

**What it means concretely.** The buttons already share a window, so this is presentation plus
grouping: a plate drawn behind the diamond, the cluster editable and movable as one thing, and the
individual buttons still separately pressable.

**Both styles must remain available**, and each needs its own editing — the project owner asked for
the choice rather than a replacement.

---

### `FEAT-2` — An eight-way face pad

**Requested as a nice-to-have.** A single face control read like the cross: one thumb, eight
directions, a diagonal pressing two buttons at once.

The project owner's own caveat, recorded because it sets the priority: *"no game i know use that
except for few. but it is nice to have"*.

**What it needs:** a new control kind — one element binding four controls — which is a schema
addition and its own editing. Not a variation on an existing kind.

---

### `FEAT-3` — A test ground for every control

**Requested, and the project owner's stated next priority**, with a reference image: a screen
showing the whole pad with every control lighting as it is pressed, every axis printing its value
live, so each one can be proven in one place instead of inside a target application.

**What makes it worth building rather than nice:** every input fault this project has found was
found by a person pressing something and reading a number. This is that loop, in the product, on one
screen — and it is also what turns "it works" into something a second person can check.

Should show: every button lit on press, both sticks with their live values, both triggers with their
analog value, the pad's eight directions, and what the platform reports back.

---

### `FEAT-4` — Skins

Artwork licensed and cleared (**CC0**, *Xelu's Free Controller Prompts*), 233 files assessed, format
not started. `docs/SKIN_ASSETS.md` carries the assessment and the open questions.

**Decided already:** the skin format comes from building Kestrel's own skin first and then judging
packs against what it needed — not from the shape of the pack that happens to be in the inbox.

**Blocked on** nothing technical; scheduled after the editor and the home screen.

---

### `FEAT-5` — Target discovery and launching

Phase 1 and Phase 4 of `PRD.md`, and the largest single gap between what exists and the MVP flow.
Kestrel cannot list an installed target, add one by hand, or launch one.

---

### `FEAT-6` — Profiles: a layout per target

A gaming profile selects a layout, a controller definition and a display mode for a named target.
`core/profile/ProfileMatching.kt` exists and nothing uses it.

---

### `FEAT-7` — Haptics

Listed in `PRD.md` Phase 2 and never started. Small, and worth doing while the controller engine is
still fresh.

---

### `FEAT-8` — The input backend behind an interface

`ADR-002` requires it and `ADR-006`'s rejection removed the only second backend that was planned. So
there is exactly one implementation and no interface, which is honest — but the interface is what
lets a future backend arrive without the rest of the system noticing, and it is cheaper to add while
there is one implementation than when there are two.

---

### `FEAT-9` — Community system

`PRD.md` Phase 7. Not started, and correctly last: it distributes what the earlier phases produce.

---

## 4. Working now

Everything below is **measured on the reference device** unless marked otherwise. This section
exists so nothing here is rebuilt, and so nothing is claimed beyond what was observed.

### Input, the hard part

- A **virtual controller** created through the Shizuku shell, recognised by five emulators, a
  browser's gamepad API, and a Windows host through Artemis/Apollo. `ADR-INPUT-001`, Accepted,
  scoped to this device.
- **Face buttons** arrive as 96, 97, 99, 100.
- **The pad's diagonals work in play** — the platform derives key codes 268–271 from the hat, and a
  character moves diagonally in a running title. Binding screens showing one axis are a property of
  binding screens, not of the pad.
- **Analog triggers** send intermediate values; 34 steps measured on the way up.
- **Latency indistinguishable from a real controller** in play, on this device.
- **A session survives leaving Kestrel**, and ends on force-stop, clear-data and uninstall within
  10–20 seconds, enforced by a privileged watchdog.

### The overlay

- Drawn from a **layout document**, not from code.
- **Multi-touch across windows** — `FLAG_SPLIT_TOUCH`. Without it, holding the stick froze the
  phone.
- **Sliding between controls** in one window; **holding `L3` and then moving the stick**.
- **Eight-way pad** with real diagonals.
- **Shapes**: circle, square, rectangle, deciding where a control can be pressed and not only how it
  is drawn.
- **Correct placement** with the system bars up, in both orientations, at every size on the slider.
- **Resizing moves the windows** rather than replacing them; nothing held is dropped.
- **Rotation rebuilds the pad** without hiding and showing it.

### Files and settings

- Everything lives in a **folder the user chooses**, beside `Android` rather than inside it, and
  **survives uninstalling Kestrel**.
- A deleted folder is **noticed and reported**, with a fallback to Kestrel's own directory, and
  recovers when the folder returns.
- `settings.json` and layouts are readable and editable by hand; numbers are two decimals.
- **Install-over-the-top works** — one signing key for every build.

### Editing

- **A layout editor on its own page**: select, drag, size, height, shape, anchor, save.
- A built-in is duplicated rather than edited.
- **Copy layout to my folder** and **Reload layout** — edit the file in a text editor, see the pad
  change.

### Diagnostics

- A report carrying **what was sent and what was received**, in order, so a fault can be placed
  above or below the virtual device.
- 212 `:core` tests, all passing; `./gradlew build` green; CI builds both APKs on every push.

### Setup

- A **setup page** listing what is missing with one action each, skippable, returning when the state
  is still incomplete.
- Full screen, cutout and orientation as settings — **except for the overlay**, see `BUG-1`/`BUG-2`.

---

## 5. Pending scope

Agreed direction, nothing started, no blockers other than order.

| Item | Where it is written down |
| --- | --- |
| Application shell, Compose navigation, launcher | `PRD.md` Phase 1 |
| Target discovery, manual target addition | `PRD.md` Phase 1, `ARCHITECTURE.md` §18 |
| Controller definitions as documents | `PRD.md` Phase 2, `docs/CONFIGURATION_SCHEMA.md` |
| Gaming session: launch, profile, orientation, display mode | `PRD.md` Phase 4 |
| Foreground-target monitoring | `ARCHITECTURE.md` §17 |
| Skin format, selector, import/export | `PRD.md` Phase 6, `docs/SKIN_ASSETS.md` |
| Community repository, manifests, checksums | `PRD.md` Phase 7 |
| Compatibility registry filled from real runs | `docs/COMPATIBILITY.md` |
| Second device, second firmware, second OEM | Everything measured is one device |

**Standing constraints that shape all of it**, so they are not rediscovered:

- **Input needs Shizuku.** `ADR-006` is Rejected — the fallback worked and was not worth shipping.
  Without Shizuku, Kestrel is a launcher, an editor and a skin manager that says so plainly.
- **Configuration is data, never executable.** No shell, no code, no downloaded plugins.
- **Built-ins are immutable**, enforced in the domain rather than by hiding a button.
- **One layout across capability tiers** (`ADR-007`); unavailable controls are disabled, never
  removed or substituted.
- **A window is dead everywhere its controls are not**, and the platform's remedy is not public API.
  Keep windows small.

---

## 6. Owner's list

Reserved for the list the project owner is sending next, and everything after it.

Entries added here get an ID in the same scheme and are then sorted into §1–§3 by kind, with the
original wording kept alongside so nothing is lost in the paraphrase.

### Round `0.0.25-dev` — test results, recorded

| # | Result |
| --- | --- |
| 1 | Main application interface correct. **Overlay still not using the notch area** → `BUG-1` |
| 2 | Notch toggle works, **except for the overlay** → `BUG-2` |
| 3 | Orientation works. **`sensor-portrait` useless, discard** → `BUG-4` |
| 4 | Editor: tap and drag a control — **working** |
| 5 | Editor: size, taller, shorter, shape, anchor — **working** |
| 6 | Editor: save, and the pad matches — **working** |
| 7 | `HOW-TO-EDIT.md` — **not found** → `BUG-3` |
| 8 | Regression: sliding, diagonals, `L3` onto stick, trigger fill — **working** |

### Decisions taken this round

- **Face buttons:** option (a), a shared plate like the pad → `FEAT-1`. Option (b), an eight-way
  face pad, wanted as well but lower → `FEAT-2`.
- **Order for what follows:** layout editor *(done)* → test ground (`FEAT-3`) → home screen
  (`CRIT-2`) and modules (`CRIT-3`).

### Awaiting

- The project owner's own list of issues and features, to be added here and sorted.
- A decision on which entry is worked first.

---

## Proposed order, for the project owner to accept or change

Not a decision — a recommendation, with the reasoning visible.

1. **`BUG-1` + `BUG-2`** — one fault, small, and it makes a shipped setting honest.
2. **`BUG-3`, `BUG-4`** — both small, both reported this round.
3. **`FEAT-3` test ground** — the project owner's stated next priority, and it makes every later
   change checkable in one place.
4. **`FEAT-1` face cluster** — asked for, self-contained, and it exercises the editor.
5. **`CRIT-2` + `CRIT-3`** — home screen and modules together, because doing either first means
   moving the same code twice.
6. **`FEAT-5` discovery and launch** — the gap between here and the MVP flow, and the bulk of
   `CRIT-4`.
7. **`CRIT-1` release key** — last in build order, first in importance, and it needs the project
   owner rather than the agent.

`FEAT-2`, `FEAT-4`, `FEAT-6`, `FEAT-7`, `FEAT-8`, `FEAT-9` and `BUG-5` sit after `v0.1.0` unless the
project owner pulls one forward.
