# Game Stage & Game Viewport — Feature Specification

**Status:** Proposed feature specification  
**Purpose:** Provide a self-contained specification for adding configurable game presentation/layout behavior to the existing game launcher/frontend without replacing, duplicating, or restructuring already-approved product requirements or architecture.

---

## 1. Agent Instructions

Before implementing this feature:

1. Read the existing PRD, architecture documents, design specifications, and current implementation.
2. Identify existing modules responsible for:
   - Game launching
   - Game/app window or display handling
   - Frontend/layout rendering
   - Controller UI/layout
   - Themes/skins
   - Per-game configuration/profile data
3. **Do not create duplicate systems** if an equivalent capability already exists.
4. Reuse existing abstractions, naming conventions, configuration stores, rendering components, and services wherever possible.
5. If this specification conflicts with an already-approved PRD or architecture decision:
   - **Do not silently override the existing decision.**
   - Mark the conflict as `REMAKE REQUIRED`.
   - Explain exactly what overlaps/conflicts.
   - Propose the smallest architectural change required.
   - Wait for product/architecture approval before making a breaking change.
6. If an existing component can support this feature with a small extension, extend it rather than creating a parallel implementation.
7. Preserve backward compatibility with existing game-launch and controller behavior.

---

# 2. Feature Goal

Introduce a **Game Stage** and **Game Viewport** abstraction for the launcher/frontend.

The feature allows the frontend to present games/apps inside a configurable stage while preserving the configured viewport aspect ratio and providing flexible positioning, scaling, skins, and unused-space presentation.

The system must support arbitrary aspect ratios.

Examples include:

- 16:9
- 4:3
- 5:3
- 21:9
- Other custom ratios

The system must **not** assume that all games are 16:9 or 4:3.

The application is primarily a **game launcher/frontend**, not an emulator framework. Therefore, emulator-specific multi-screen behavior is explicitly out of scope.

---

# 3. Core Concepts

## 3.1 Physical Display

The physical Android device may have an ultra-wide landscape display.

Example target device:

```text
2400 × 1080
20.5:9
```

The physical display is not the Game Stage.

---

## 3.2 Game Stage

The **Game Stage** is a configurable presentation container inside the frontend.

It defines the area in which the Game Viewport is displayed.

Example:

```text
Game Stage
1600 × 1080
```

The Game Stage may contain:

- Game Viewport
- Letterbox/pillarbox area
- Background
- Skin/artwork
- Decorative elements

The Game Stage must not modify the game's configured aspect ratio merely to fill the stage.

---

## 3.3 Game Viewport

The **Game Viewport** is the actual game/application display rectangle inside the Game Stage.

The viewport defines:

- Aspect ratio
- Scaling mode
- Alignment
- Position
- Size calculated from the stage and scaling mode

The viewport must remain independent from controller UI logic.

---

# 4. Target Layout Model

For a 2400 × 1080 device:

```text
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   Controller       GAME STAGE              Controller        │
│                    1600 × 1080                              │
│                                                              │
│              ┌──────────────────────┐                        │
│              │    GAME VIEWPORT     │                        │
│              │                      │                        │
│              └──────────────────────┘                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

The exact controller dimensions must remain controlled by the existing controller/layout system.

**Do not hard-code 400 px controller regions.**

The `1600 × 1080` example is a default/example configuration, not a mandatory device-wide layout.

---

# 5. Game Stage Configuration

The user must be able to configure the Game Stage.

## Required properties

### Width

User-configurable.

Example:

```text
1600 px
```

The implementation should avoid assuming that 1600 px is universally optimal.

### Height / Aspect Ratio

The stage should support a configurable aspect ratio.

The implementation may represent this as either:

```text
Stage width + stage aspect ratio
```

or:

```text
Stage width + stage height
```

depending on existing architecture.

Prefer the representation already used by the project.

### Skin / Background

The Game Stage should support existing theme/skin infrastructure if available.

Potential presentation modes:

- Solid color
- Black
- Image/artwork
- Custom skin
- Gradient
- Other existing background systems

Do **not** create a second skin/theme engine if one already exists.

---

# 6. Game Viewport Configuration

## 6.1 Aspect Ratio

The viewport must support:

```text
Auto
16:9
4:3
5:3
Custom
```

The final implementation should allow arbitrary aspect ratios rather than maintaining a hard-coded list.

Examples:

```text
16:9
4:3
5:3
21:9
1:1
Custom: 17:10
```

If the existing launcher can obtain reliable native/application aspect-ratio information, `Auto` may use that information.

Do not invent aspect-ratio detection if the existing platform/window architecture does not provide reliable information.

---

# 7. Scaling Modes

The Game Viewport must support four conceptual scaling modes.

## 7.1 Fit

Preserve aspect ratio.

The complete viewport remains visible.

Unused Game Stage space becomes letterbox/pillarbox space.

Example:

```text
Stage:    1600 × 1080
Viewport: 16:9

Result:
1600 × 900
```

No distortion.

---

## 7.2 Fill

Preserve aspect ratio.

The viewport expands until the Game Stage is completely covered.

Cropping may occur.

No distortion.

---

## 7.3 Stretch

The viewport fills the available target rectangle without preserving aspect ratio.

Distortion is allowed.

This mode must be explicitly selected by the user.

It must never be the implicit default.

---

## 7.4 Integer Scaling

Scale the source viewport using whole-number scale factors.

Example:

```text
1×
2×
3×
4×
```

The implementation should preserve the source aspect ratio and avoid fractional scaling where possible.

If integer scaling cannot fill the Game Stage, unused space must remain available for the stage background.

---

# 8. Viewport Alignment

The viewport must support a 3 × 3 alignment grid.

```text
┌─────────────┬─────────────┬─────────────┐
│ Top Left    │ Top Center  │ Top Right   │
├─────────────┼─────────────┼─────────────┤
│ Middle Left │   Center    │ Middle Right│
├─────────────┼─────────────┼─────────────┤
│ Bottom Left │Bottom Center│Bottom Right │
└─────────────┴─────────────┴─────────────┘
```

Required alignment values:

```text
top-left
top-center
top-right

middle-left
middle-center
middle-right

bottom-left
bottom-center
bottom-right
```

Default:

```text
middle-center
```

The implementation should preferably use an enum/value already consistent with the project's layout system.

---

# 9. Aspect-Ratio Examples

## 9.1 16:9

For:

```text
Game Stage = 1600 × 1080
```

Maximum Fit viewport:

```text
1600 × 900
```

Unused vertical space:

```text
90 px top
90 px bottom
```

---

## 9.2 4:3

Maximum Fit viewport:

```text
1440 × 1080
```

Unused horizontal space:

```text
80 px left
80 px right
```

---

## 9.3 5:3

For a 1600 × 1080 stage:

```text
1600 × 960
```

Unused vertical space:

```text
60 px top
60 px bottom
```

---

# 10. General Viewport Calculation

Do not implement special-case calculations only for 16:9, 4:3, and 5:3.

The renderer/layout system should calculate the maximum rectangle that satisfies the configured viewport aspect ratio within the Game Stage.

Conceptually:

```text
Given:

Stage width  = SW
Stage height = SH
Viewport ratio = R

Calculate the largest viewport rectangle
that fits inside SW × SH while preserving R.
```

For `Fit`:

```text
viewport_width  <= stage_width
viewport_height <= stage_height
viewport_width / viewport_height = R
```

For `Fill`:

The same ratio must be preserved, but the viewport may exceed one stage dimension and be cropped.

For `Stretch`:

The viewport may use the available target rectangle without ratio preservation.

For `Integer`:

Choose the largest valid whole-number scale that fits.

Use the project's existing coordinate/rendering system where possible.

---

# 11. Stage Background / Letterboxing

Unused Game Stage space must be treated as part of the Game Stage rather than simply being considered an error or empty space.

Possible presentation:

```text
Black
Custom color
Existing skin
Image
Gradient
Blurred background
```

The exact available options should reuse existing theme/skin functionality.

If a skin system already exists, integrate with it.

**Do not build a parallel skin manager.**

---

# 12. Controller Separation

This feature must not take ownership of controller functionality.

The conceptual architecture is:

```text
Frontend
│
├── Game Stage
│   ├── Stage configuration
│   ├── Viewport
│   ├── Scaling
│   ├── Alignment
│   └── Background/Skin
│
└── Existing Controller System
    ├── Buttons
    ├── Sticks
    ├── Triggers
    ├── Mapping
    └── Controller appearance
```

The Game Stage determines the game presentation area.

The existing controller system determines controller UI and input behavior.

---

# 13. Recommended Integration Point

The preferred integration point is the existing **game/frontend display or per-game layout/profile configuration**.

Potential structure:

```text
Game Profile / Game Configuration
│
├── Existing Game Settings
├── Existing Launch Settings
├── Existing Controller Settings
│
└── Display / Game Presentation
    ├── Game Stage
    └── Game Viewport
```

However:

**Do not create this hierarchy if an equivalent configuration hierarchy already exists.**

Use the existing project's structure.

---

# 14. Per-Game Configuration

The feature should preferably support per-game configuration if the existing application already supports per-game profiles/settings.

Example:

```text
Game A
  Stage width: 1600
  Stage ratio: 40:27
  Viewport ratio: 16:9
  Scaling: Fit
  Alignment: Center
  Skin: Default

Game B
  Stage width: 1600
  Stage ratio: 40:27
  Viewport ratio: 4:3
  Scaling: Integer
  Alignment: Center
  Skin: Retro
```

Do not introduce a new profile database if an existing game/profile configuration system already exists.

Extend the existing configuration model instead.

---

# 15. Global Defaults

The system should support defaults so the user does not need to configure every game manually.

Recommended defaults:

```text
Game Stage:
  Width: 1600 px
  Aspect Ratio: project/device default
  Background: Black

Game Viewport:
  Aspect Ratio: Auto
  Scaling: Fit
  Alignment: Middle Center
```

These values are recommendations only.

The existing PRD/configuration architecture takes precedence.

---

# 16. Responsive Behavior

The implementation must not assume that every Android device is:

```text
2400 × 1080
20.5:9
```

The target device is an important design case, but the system should calculate layout from the actual available display dimensions.

Example:

```text
Available display
       ↓
Game Stage configuration
       ↓
Actual Stage rectangle
       ↓
Viewport calculation
       ↓
Controller/layout system uses remaining space
```

Avoid hard-coded pixel assumptions wherever possible.

---

# 17. Explicitly Out of Scope

This feature does **not** implement:

- Emulator functionality
- Emulator-specific screen layouts
- NDS dual-screen handling
- 3DS dual-screen handling
- Game emulation
- Input mapping changes
- Controller hardware communication
- Game launching logic
- ROM management
- Game library management
- A new skin/theme engine
- A new profile database

These should remain owned by their existing systems.

---

# 18. Potential Architecture Overlaps — MUST CHECK

Before implementation, inspect the existing architecture for the following.

| Potential overlap | Required action |
|---|---|
| Existing display/window manager | Extend it instead of creating another |
| Existing frontend layout system | Integrate Game Stage into it |
| Existing game profile/config system | Add fields there |
| Existing skin/theme system | Reuse it |
| Existing responsive layout engine | Use it for dimensions |
| Existing game viewport/window abstraction | Extend it |
| Existing scaling utility | Reuse it |
| Existing alignment/anchor system | Reuse it |
| Existing controller layout system | Keep separate |
| Existing rendering pipeline | Integrate into it |
| Existing per-game settings | Extend them |
| Existing global settings | Add defaults there |

If any row conflicts with an approved architectural decision, flag:

```text
REMAKE REQUIRED
```

and document:

1. Existing decision
2. Proposed feature requirement
3. Exact conflict
4. Why the conflict exists
5. Minimal change required
6. Whether migration/backward compatibility is required

Do not proceed with an architectural rewrite without approval.

---

# 19. Acceptance Criteria

The feature is considered implemented when:

- [ ] Game Stage exists using the project's existing layout architecture.
- [ ] Stage width is configurable.
- [ ] Stage aspect ratio is configurable.
- [ ] Stage background/skin integrates with the existing skin/theme system.
- [ ] Game Viewport aspect ratio is configurable.
- [ ] Arbitrary aspect ratios are supported.
- [ ] Fit preserves the viewport aspect ratio.
- [ ] Fill preserves the viewport aspect ratio.
- [ ] Stretch is explicitly selectable.
- [ ] Integer scaling is supported where technically applicable.
- [ ] All 9 viewport alignments are supported.
- [ ] Default alignment is middle-center.
- [ ] 16:9 works correctly.
- [ ] 4:3 works correctly.
- [ ] 5:3 works correctly.
- [ ] Other/custom aspect ratios work without special-case implementation.
- [ ] No game stretching occurs when Fit/Fill/Integer mode is selected.
- [ ] Unused stage space can display the configured background/skin.
- [ ] Existing controller behavior remains unchanged.
- [ ] Existing launcher behavior remains unchanged.
- [ ] Existing profiles/settings remain backward compatible.
- [ ] No duplicate skin/theme/configuration system has been introduced.
- [ ] No emulator-specific functionality has been added.
- [ ] Existing PRD and architecture remain authoritative unless an approved `REMAKE REQUIRED` change is made.

---

# 20. Implementation Principle

The most important architectural principle is:

> **Game Stage is the presentation container. Game Viewport is the game display. Controller UI remains a separate existing system.**

Do not build separate modes such as:

```text
16:9 Mode
4:3 Mode
5:3 Mode
Retro Mode
Wide Mode
```

Instead build one generic system:

```text
Game Stage
    ↓
Game Viewport
    ↓
Aspect Ratio + Scaling + Alignment
```

This allows the launcher/frontend to support arbitrary game/application display ratios without continually adding new layout modes.

---

# 21. Recommended Implementation Sequence

1. Audit existing PRD and architecture.
2. Identify existing display/layout/configuration abstractions.
3. Produce an overlap report.
4. Mark any conflict as `REMAKE REQUIRED`.
5. Extend existing abstractions where possible.
6. Implement Game Stage configuration.
7. Implement generic Game Viewport calculation.
8. Implement scaling modes.
9. Implement 3 × 3 alignment.
10. Integrate existing skins/themes.
11. Integrate with existing per-game/global configuration.
12. Test multiple aspect ratios.
13. Test multiple device resolutions.
14. Verify controller behavior is unchanged.
15. Verify launcher behavior is unchanged.
16. Run existing tests/regression suite.

---

# 22. Final Design Summary

```text
                    ANDROID DISPLAY
                           │
                           ▼
                    ┌─────────────┐
                    │ GAME STAGE  │
                    │             │
                    │ Width       │
                    │ Aspect Ratio│
                    │ Skin        │
                    │ Background  │
                    │             │
                    │ ┌─────────┐ │
                    │ │ VIEWPORT│ │
                    │ │         │ │
                    │ │ Ratio   │ │
                    │ │ Scaling │ │
                    │ │ Align   │ │
                    │ └─────────┘ │
                    └─────────────┘

             Existing Controller System
                       remains
                     independent
```

The implementation should therefore add a **generic display-layout capability** to the existing launcher/frontend, rather than creating a separate game-rendering architecture.
