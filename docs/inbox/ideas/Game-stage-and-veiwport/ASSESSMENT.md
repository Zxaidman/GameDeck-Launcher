# Assessment — Game Stage & Game Viewport

**Assessed:** 2026-08-20, against `0.0.20-dev`  
**Spec:** `Game_Stage_Game_Viewport_Feature_Spec.md`, 769 lines  
**Verdict:** Valuable, and **one hard conflict** that the spec itself asks to be flagged  

---

## REMAKE REQUIRED — §18 flag

The spec's §18 asks for this exact form, so here it is.

**1. Existing decision.** `ARCHITECTURE.md` §22 (Activity Embedding) and `CLAUDE.md` §5 (Display):
Kestrel must never claim an external application was resized because Kestrel changed its own layout.
Cross-application activity embedding is Android 13+, carries trust and opt-in restrictions, and must
not be a dependency of the Android 10+ architecture that `ADR-004` fixes.

**2. Proposed requirement.** §3.3: *"The Game Viewport is the actual game/application display
rectangle inside the Game Stage."* The stage sizes and positions the game's picture; scaling modes,
alignment and letterboxing all act on it.

**3. Exact conflict.** For an **external** target — every emulator and streaming client Kestrel
launches — Kestrel cannot place that application's output inside a rectangle it controls. The
target's window is drawn by the platform at the size the platform gives it. Nothing public on
Android 10 lets one application host another's window, and where it exists at all it is 13+ and
requires the target to co-operate.

**4. Why the conflict exists.** The spec is written from the frontend conventions of desktop
launchers, where the frontend owns the surface the game draws into. On Android the launcher does
not own that surface and cannot be given it.

**5. Minimal change.** Invert what the stage describes. Instead of *a container that positions the
game*, make it *a description of where the picture is expected to be, and therefore where Kestrel's
own controls and art go around it.* Everything else in the spec survives that inversion:

- Aspect ratio, scaling mode and alignment become the way Kestrel **predicts** the letterboxed
  rectangle a target will produce — most emulators letterbox to a console's aspect, and that is
  computable rather than controllable.
- Stage background and letterbox presentation become Kestrel's **overlay** drawn in the bands beside
  the picture, which Kestrel genuinely does own.
- Per-target and global defaults, §14 and §15, work unchanged.
- §12's controller separation is already how the overlay is built.

What does not survive: Fill, Stretch and Integer Scaling as things Kestrel *applies*. Those are the
target's own settings, and most emulators already expose them. Kestrel can say what it expects and
let the user correct it; it cannot impose it.

**6. Migration.** None — nothing is built yet.

---

## What is good in it, and should be kept

- The separation of **stage** from **viewport** is the right distinction even after the inversion,
  and there is no equivalent in the architecture today.
- §18's instruction to check for overlaps before building, and to flag rather than rewrite, is
  exactly the discipline `CLAUDE.md` asks for. It is why this document exists.
- §17's out-of-scope list correctly refuses emulator behaviour, dual-screen handling and ROM
  management.
- Arbitrary aspect ratios rather than a fixed 16:9 or 4:3 is correct and is not assumed anywhere in
  the current architecture, which currently assumes nothing at all.

## Where it lands

Phase 4 (Gaming Session) and the display architecture, `ARCHITECTURE.md` §21. It depends on layout
and skin being data — which as of `0.0.19-dev` layout is — and on a session that launches a target,
which does not exist yet.

**Nothing is being built from it now**, per the project owner. It stays in the inbox, this
assessment stays with it, and the inversion above is what an ADR would have to record before any of
it becomes code.
