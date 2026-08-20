# Kestrel — Skin Assets

**Document:** `docs/SKIN_ASSETS.md`  
**Status:** Active — assessment of the artwork now in the repository, and the conventions it implies  
**Source material:** `docs/inbox/skins/` (233 files, pushed by the project owner)  

---

## 1. What was delivered

Every file was inspected, not sampled: 233 PNGs, all **256 × 256**, all 8-bit RGBA with a
transparent background. Five sets, numbered as one continuous run from 0001 to 0150 and then split
into folders, so the numbers are unique across the whole pack rather than per family.

| Set | Files | Numbers | Bytes |
| --- | --- | --- | --- |
| `Xbox/` | 20 | 0001–0020 | 188 KB |
| `Playstation/` | 22 | 0021–0042 | 227 KB |
| `Switch/` | 25 | 0043–0067 | 187 KB |
| `Keyboard Light/` | 83 | 0068–0150 | 394 KB |
| `Keyboard Dark/` | 83 | 0068–0150 | 398 KB |

The two keyboard sets are the same 83 subjects in two tones, so they are one set with a light and a
dark variant rather than two sets.

**These are button prompts, not pad artwork.** Each file is one control drawn in isolation — a face
button, a stick, a shoulder, a d-pad. There is no frame, no body, no background, and nothing that
describes where a control sits. That matters for what they can and cannot be used for, below.

### What each set covers

- **Xbox** — A, B, X, Y; both sticks from above and from the side; d-pad neutral and three
  directions; LT, RT, LB, RB; view, menu, share.
- **PlayStation** — ○, △, □, ✕; both sticks; d-pad neutral and three directions; L1, L2, R1, R2;
  three pill-shaped menu buttons, one of them shown with a press flourish; the touchpad.
- **Switch** — A, B, X, Y; four filled direction triangles; both sticks from above and from the
  side; d-pad neutral and four directions; L, R, ZL, ZR; home, capture, minus, plus.
- **Keyboard / mouse** — nine mouse states including wheel and each button; the letters, digits,
  punctuation, arrows, and the named keys (Esc, Shift, Ctrl, Alt, Tab, Caps Lock, Backspace, Enter,
  Space, Ins, Del, Home, End, Page Up, Page Down), plus a WASD cluster and both platform keys.

### What no set covers

- **A pressed state.** Nothing in the pack is a lit or depressed version of a control. The only
  state art present is directional: the d-pad in each family has a neutral drawing plus one per
  direction. So a press has to be drawn by Kestrel — a tint, a scale, or an overlay — and cannot be
  supplied as a second image.
- **L3 / R3.** The stick art is one drawing per stick; nothing marks a stick press.
- **A diagonal d-pad.** Each family draws one direction at a time. `ADR-007` and the eight-way pad
  Kestrel now sends both mean a diagonal is a real state that has to be shown, so it will have to be
  composed from two singles or drawn.
- **Any body, bezel, or background.** A "skin" in `docs/CONFIGURATION_SCHEMA.md` terms is currently
  satisfied only at the per-control level by this pack.

---

## 2. What has to be decided before any of it can be loaded

### The filenames carry no meaning

`Xbox0011.png` is the d-pad with right pressed, and nothing in the name says so. A renderer cannot
map a control to a file by naming convention here, and renaming 233 files by hand is both
error-prone and destroys the correspondence with whatever produced them.

**The mapping belongs in a manifest, not in the filenames.** One JSON document per family, listing
control identifier against file, which is also where the things a filename could never express
belong: the aspect the art is really drawn at, whether it is a stick or a shoulder, and which of the
d-pad singles compose a given diagonal. This follows the rule the schema already sets — a
*definition* says which controls exist, a *skin* says how they look — and it means the artwork is
never edited to fit the code.

### Square files, non-square subjects

Every file is 256 × 256, but a shoulder button, a stick seen from the side, and a Space bar are not
square subjects; they are drawn into a square canvas with transparent padding. Placing them by their
file bounds would leave a shoulder button floating in the middle of its slot. The manifest therefore
needs the drawn extent, or the renderer needs to compute it from the alpha channel on load.

### 256 px is enough, but only just

The controls as currently drawn measure roughly 150–400 px on the reference device at the default
scale. 256 px is comfortable for a face button and marginal for a d-pad drawn at full cluster size
on a large display. Worth knowing before the pack is treated as final: it is adequate, not generous.

### Format

PNG is the delivered format and, per the owner's decision, the primary one. WebP stays the
alternative rather than a conversion target — the whole pack is 1.4 MB, which is not a size worth
trading a lossless, universally-inspectable format for. Recorded so nobody "optimises" it later
without a reason.

### Provenance and licence — resolved

**The licence is CC0.** The pack is *Xelu's Free Controller Prompts* by **Nicolae "Xelu" Berbece**
(Those Awesome Guys). The licence file shipped with it is stored alongside the artwork at
`docs/inbox/skins/LICENSE.txt`, and states: *"You can use all these assets in any project you want
to (be it commercial or not). All of the assets are in the public domain under Creative Commons 0
(CC0)."*

That answers the question that was blocking. **CC0 is a public-domain dedication, so redistribution
inside a GPLv3 application is unencumbered** — there is no copyleft conflict, no notice requirement,
and no attribution obligation. The author asks to be credited and explicitly says he does not mind
if he is not; Kestrel will credit him regardless, because taking work and not naming its author is a
choice about the project rather than about the licence.

Recorded in `THIRD_PARTY_LICENSES.md`.

**What CC0 does not answer.** The trademark question is separate and a licence on the files does not
touch it: drawing a recognisable ✕/○/□/△ or a Switch face arrangement is about marks belonging to
hardware vendors, not about who owns the drawing. Two things are worth stating precisely.

- The licence resolves **redistribution**. It does not resolve **what the shapes depict**.
- The pack is used in a long list of commercially released titles, named in its own licence file.
  That is **context, not a guarantee**, and it is recorded as context.

The practical consequence is small, because `ADR-INPUT-001` already decided Kestrel presents its own
identity rather than claiming another vendor's (`docs/CONTROLLER_FAMILIES.md` §2). A skin that draws
familiar glyphs while the device says "Kestrel" is a different thing from a device that claims to be
someone else's hardware.

**Status: cleared for use.** The pack may move into `data/` once a skin format exists to receive it
— which, per §2a, comes from building Kestrel's own skin first rather than from this pack's shape.

---

## 2a. The decision: build a skin before adopting a pack

**Decided by the project owner:** do not build the skin layer around this pack. Build Kestrel's own
skin first, discover from that what a skin actually has to supply, and write the requirements down.
Only then judge any pack against them.

This is the right way round, and it is worth stating why rather than only that it was decided.

A skin format derived from one pack encodes that pack's accidents — its 256 px squares, its one
image per control, its missing pressed state — as if they were requirements. The format would then
fit exactly one set of artwork and quietly fail every other. Deriving the format from what the
**renderer** needs instead produces a specification a pack either meets or does not, which is also
what makes the licence question answerable rather than urgent: nothing has been built around
artwork that may turn out to be unusable.

What building Kestrel's own skin is expected to settle, and what this document should be updated
with once it has:

- Which controls need art at all, and which are drawn well enough as primitives.
- Which states each control needs beyond "at rest" — pressed, and for the pad, eight directions.
- Whether a cluster needs a plate image or whether the plate is the renderer's job.
- What a background is: one image, a colour, or a layout property.
- The anchor and aspect a non-square control needs, and whether that is per-asset data or derived
  from the alpha channel.
- Resolution: what size is enough at `MAX_SCALE` on the largest supported display.

Until that list has answers, the pack in the inbox is a **reference for what such artwork looks
like** and a check on any format proposed — not the input to the format.

---

## 3. Where this lands in the architecture

Nothing about this changes the layers. A family is presentation, as `docs/CONTROLLER_FAMILIES.md`
§3 already sets out, and this pack is the artwork that section assumed would exist:

```text
data/skins/<family>/          ← these files, plus one manifest each
     ↓
layout / skin                 ← which control is drawn where, and in which family
     ↓
core/input                    ← controller semantics: GamepadButton.A, LEFT_X
     ↓
platform/input                ← one descriptor, one identity, unchanged by any of this
```

The descriptor does not change because a skin changed. Switching family stays a configuration
change, not a controller reconnecting.

---

## 4. State of each claim

| Claim | State |
| --- | --- |
| 233 files, all 256×256 RGBA, five sets as tabulated | **Measured** — every file read |
| Set contents as listed in §1 | **Measured** — every file rendered and inspected |
| No pressed state, no L3/R3, no diagonal d-pad art | **Measured** — by absence across all 233 |
| 256 px is adequate at the current control sizes | **Reasoned** from the drawn sizes on the reference device |
| Provenance: *Xelu's Free Controller Prompts*, Nicolae "Xelu" Berbece | **Confirmed** — licence file in the pack |
| The licence is CC0, so redistribution under GPLv3 is unencumbered | **Confirmed** — `docs/inbox/skins/LICENSE.txt` |
| Trademark position on the drawn shapes | **Open** — separate question, not answered by a licence |
| That the skin format is derived from Kestrel's own skin, not from this pack | **Decided** — §2a |
