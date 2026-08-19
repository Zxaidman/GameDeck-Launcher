# Kestrel — Skin Assets

**Document:** `docs/SKIN_ASSETS.md`  
**Status:** Active — assessment of the artwork now in the repository, and the conventions it implies  
**Source material:** `docs/phase0/results/inbox/Skins/` (233 files, pushed by the project owner)  

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

### Provenance and licence — open, and blocking

**This is the one item that must be answered before any of this artwork ships inside the
application.** Kestrel is GPLv3 (`ADR-005`) and `THIRD_PARTY_LICENSES.md` records what everything
included is under. The pack arrived without a licence file or a stated origin, and it draws control
shapes strongly associated with three hardware vendors.

Two separate questions, and they have different answers:

- **The licence of these files.** If they came from an asset pack, its terms decide whether they can
  be redistributed under GPLv3 at all. Unknown provenance is not a licence.
- **The shapes themselves.** Drawing a recognisable ✕/○/□/△ or a Switch face layout is a trademark
  question, not a copyright one, and it is not resolved by the files being freely licensed.

Until the first is answered the pack stays where it is — in `docs/phase0/results/inbox/`, which is a
drop zone, not a distribution path. Nothing here blocks *building* the skin layer against it
locally; it blocks shipping it in `data/`.

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
| The licence and provenance of the pack | **Unknown** — blocking for `data/`, not for building against |
| Trademark position on the drawn shapes | **Open** — separate question, not answered by a licence |
