# GameDeck Android — Phase 0 Input Feasibility Specification

**Document:** `docs/PHASE-0.md`  
**Status:** Required before full application implementation  
**Objective:** Determine whether GameDeck can provide usable gamepad-style input to target applications on Android 10+.

---

# 1. Purpose

Phase 0 is a technical feasibility experiment.

It is NOT an attempt to build the GameDeck application.

It exists to answer one question:

> Can a touch-controlled Android application generate input that target emulators and game-streaming applications recognize and use as game-controller input?

The experiment must compare several privilege/input paths.

---

# 2. Why This Phase Is Mandatory

A normal Android application can observe available input devices through Android's public input APIs, but that is not equivalent to registering a new virtual gamepad. Android's public `InputManager` APIs include input-device discovery/listening, while arbitrary input injection is exposed through `UiAutomation` rather than as a normal application-level virtual-device API.

Shizuku changes the privilege context, but does not automatically guarantee a virtual HID device. Shizuku UserService can operate under shell or root identity, with non-SDK API access, but it is subject to the actual identity and platform behavior.

Therefore Phase 0 must establish facts experimentally.

---

# 3. Hard Requirement

The preferred result is:

```text
GameDeck Touch
      ↓
Input Backend
      ↓
Android recognizes controller-style input
      ↓
Target application receives it
```

The strongest possible result is:

```text
GameDeck virtual controller
        ↓
Android input subsystem
        ↓
Target application
```

where the target application observes controller-like input through its normal controller APIs.

---

# 4. Things Phase 0 Must Distinguish

The experiment must NOT treat all successful input as equivalent.

There are four separate outcomes:

### A. Touch simulation

Example:

```text
GameDeck button
      ↓
screen tap
      ↓
game reacts
```

Useful fallback.

Not a true virtual gamepad.

---

### B. Key-event injection

Example:

```text
GameDeck button
      ↓
Android key event
      ↓
game reacts
```

Potentially useful.

Still not automatically equivalent to a gamepad device.

---

### C. Motion/axis event injection

Example:

```text
GameDeck analog stick
      ↓
axis event
      ↓
game reacts
```

More promising.

Still needs verification of how the target application identifies the source.

---

### D. Virtual gamepad/HID identity

Example:

```text
GameDeck
      ↓
virtual input device
      ↓
Android input device list
      ↓
game sees controller device
```

This is the preferred end state.

---

# 5. Test Environments

Phase 0 must test at least these modes.

## Test A — Normal Android application

No Shizuku.

```text
Android 10+
GameDeck prototype
```

Purpose:

Determine the baseline capabilities of an ordinary application.

---

## Test B — Shizuku + ADB

```text
GameDeck
   ↓
Shizuku UserService
   ↓
shell UID
```

Shizuku documentation identifies the ADB-backed UserService identity as shell UID 2000.

Purpose:

Determine whether shell-level access is enough to provide useful gamepad-style input.

---

## Test C — Shizuku + root

```text
GameDeck
   ↓
Shizuku UserService
   ↓
root UID
```

Shizuku documentation identifies the root-backed UserService identity as UID 0.

Purpose:

Determine what becomes possible with root while keeping the main GameDeck process unrooted.

Root is an experimental capability here, not an initial product requirement.

---

# 6. Test Devices

At least one physical Android 10+ phone is required.

Preferably Phase 0 should eventually be repeated on:

```text
Android 10
Android 11
Android 12
Android 13
Android 14
Android 15+
```

The first prototype does not need all versions simultaneously.

The first goal is identifying a working mechanism.

---

# 7. Target Applications

Initial mandatory targets:

### Emulator

PPSSPP

### Emulator

Dolphin

### Emulator

RetroArch

### Streaming

Moonlight

### Streaming

Steam Link

These represent different application implementations and therefore provide better evidence than testing only one emulator.

---

# 8. Test Controller Definition

The prototype should expose a minimal standard controller:

```text
D-pad
A
B
X
Y

LB
RB
LT
RT

Left Stick X/Y
Right Stick X/Y

Start
Back
```

Do not build the full GameDeck controller editor yet.

A simple test UI is sufficient.

---

# 9. Test UI

The prototype should have a minimal screen:

```text
INPUT FEASIBILITY

Backend:
[ Normal ]

Connection:
[ Not Tested ]

Buttons:
[A] [B] [X] [Y]

[DPad]

[LS] [RS]

[LB] [RB]
[LT] [RT]

[Start] [Back]

Run Automated Test
Run Target-App Test
Export Diagnostics
```

The UI exists only to generate reproducible input.

---

# 10. Test 1 — Device Discovery

Determine whether the generated device/input path produces an observable Android input device.

Record:

```text
Device ID
Name
Vendor ID
Product ID
Sources
Motion ranges
Keyboard/button capabilities
```

Android exposes `InputDevice` information and listeners through `InputManager`.

### Success

A new controller-like input device appears.

### Partial success

Input reaches applications but no distinct device identity appears.

### Failure

No useful input reaches the target.

---

# 11. Test 2 — Digital Buttons

Test:

```text
A
B
X
Y
DPad Up
DPad Down
DPad Left
DPad Right
LB
RB
Start
Back
```

For each:

```text
DOWN
HOLD
UP
```

Verify that the target application receives a logically correct sequence.

---

# 12. Test 3 — Analog Sticks

Test:

```text
Left X
Left Y
Right X
Right Y
```

Values:

```text
-1.0
-0.5
0.0
+0.5
+1.0
```

Also test:

```text
center
small movement
large movement
rapid direction changes
```

---

# 13. Test 4 — Triggers

Test:

```text
LT = 0.0
LT = 0.25
LT = 0.5
LT = 0.75
LT = 1.0

RT = same
```

Determine whether targets see:

- Digital trigger
- Analog trigger
- Both
- Neither

---

# 14. Test 5 — Simultaneous Input

Verify:

```text
Left Stick + A
Right Stick + B
LT + RT
DPad + button
Two buttons + analog stick
```

This is important because many games and streaming applications require simultaneous controls.

---

# 15. Test 6 — Hold Duration

Test:

```text
50 ms
100 ms
250 ms
500 ms
1 second
5 seconds
```

Confirm:

- no stuck buttons
- no missing releases
- no duplicate presses
- no input drift

---

# 16. Test 7 — Rapid Input

Run repeated:

```text
A DOWN
A UP
A DOWN
A UP
...
```

at increasing frequencies.

The purpose is to detect:

- dropped events
- reordered events
- duplicated events
- growing latency
- crashes

---

# 17. Test 8 — Analog Stability

Hold:

```text
Left Stick X = 0
Left Stick Y = 0
```

for several minutes.

Verify no drift.

Then:

```text
X = 0.5
```

and check whether the target sees a stable value.

---

# 18. Test 9 — Target Recognition

For each target application, determine:

```text
Does it recognize a controller?
Does it show the input source as a game controller?
Does its internal controller configuration detect buttons?
Does analog input work?
Do triggers work?
Does simultaneous input work?
```

---

# 19. Test 10 — Gamepad Device Identity

This test is particularly important.

Determine whether the target sees an identifiable controller-like input device.

Record:

```text
Device name
Sources
Vendor/Product if available
Controller-related source flags
Axes
Keys
```

A result such as:

```text
"the screen reacted"
```

is NOT sufficient to classify the backend as a gamepad backend.

---

# 20. Test 11 — Application Diversity

Repeat the same controller test across:

```text
PPSSPP
Dolphin
RetroArch
Moonlight
Steam Link
```

A backend that works only on one application is classified as:

```text
Limited Compatibility
```

rather than:

```text
General Gamepad Backend
```

---

# 21. Test 12 — Foreground-App Transition

Start:

```text
GameDeck
```

then:

```text
PPSSPP
```

then:

```text
Home
```

then:

```text
PPSSPP again
```

Check whether the backend:

- remains stable
- stops safely
- reconnects
- releases held inputs

---

# 22. Test 13 — Lifecycle Interruption

During a held button:

1. Lock screen.
2. Unlock.
3. Open another app.
4. Return to target.
5. Kill GameDeck.
6. Restart GameDeck.

Verify that no button remains permanently pressed.

---

# 23. Test 14 — Rotation

Test:

```text
Portrait
↓
Landscape
↓
Portrait
```

The controller state must be reset safely.

The experiment is not yet testing the final UI layout.

It is testing whether the input backend survives lifecycle changes.

---

# 24. Test 15 — Shizuku State Changes

Run:

```text
Shizuku stopped
Shizuku started
permission revoked
permission granted
GameDeck restarted
Shizuku restarted
```

GameDeck must identify capability changes without crashing.

---

# 25. Input Backend Candidates

Phase 0 should investigate, in order:

### Candidate 1

Supported system/gamepad input mechanism available to the application.

### Candidate 2

Shizuku shell-level system/API access.

### Candidate 3

Shizuku root-level system/API access.

### Candidate 4

Virtual device/HID/uinput route where technically available.

### Candidate 5

Touch/gesture fallback.

These are experimental candidates, not guaranteed implementations.

---

# 26. Important Constraint: Do Not Fake Results

The following do NOT qualify as proving gamepad support:

```text
A visible button was pressed.
```

```text
A touch event happened.
```

```text
An API returned true.
```

```text
An emulator reacted to something.
```

The test must show what input source the target application actually received.

---

# 27. Evidence Collection

Every test run should generate:

```text
phase0-report.json
```

Example:

```json
{
  "device": {
    "manufacturer": "Example",
    "model": "Example Phone",
    "androidApi": 35
  },
  "backend": {
    "id": "shizuku-adb",
    "privilege": "ADB_SHELL"
  },
  "target": {
    "package": "example.package",
    "name": "PPSSPP"
  },
  "tests": {
    "deviceDetected": true,
    "buttons": true,
    "axes": true,
    "triggers": true,
    "simultaneousInput": true
  }
}
```

The exact schema should be finalized when implementing the prototype.

---

# 28. Evidence Hierarchy

Results should be graded.

## Grade A — Native virtual controller

Target sees a controller-like input device and controller semantics work.

```text
★★★★★
```

Preferred.

---

## Grade B — System-level gamepad-style injection

Input is delivered through a system mechanism and behaves sufficiently like controller input, but no persistent virtual device identity exists.

```text
★★★★☆
```

Potentially acceptable depending on compatibility.

---

## Grade C — Input/key event emulation

Some controller functions can be reproduced but analog/controller semantics are incomplete.

```text
★★★☆☆
```

Potential fallback.

---

## Grade D — Touch mapping

Works by simulating touches/gestures.

```text
★★☆☆☆
```

Fallback only.

---

## Grade E — Unsupported

Cannot reliably control target applications.

```text
★☆☆☆☆
```

---

# 29. Acceptance Criteria

Phase 0 passes only if at least one backend can satisfy all of the following for at least:

- One emulator
- One streaming application

### Digital

A/B/X/Y and D-pad work.

### Analog

At least one analog stick works continuously.

### Triggers

At least one trigger works correctly where supported.

### Simultaneous

At least two independent controls work simultaneously.

### Hold/release

No stuck inputs.

### Lifecycle

Input state can be safely reset.

### Repeatability

The test succeeds repeatedly rather than once by chance.

---

# 30. Strong Pass

Phase 0 receives a **Strong Pass** if one backend provides:

```text
Controller identity
+
Buttons
+
Analog axes
+
Triggers
+
Simultaneous input
+
Lifecycle stability
```

on:

```text
PPSSPP
Dolphin
RetroArch
Moonlight
Steam Link
```

or a sufficiently broad subset documented by testing.

---

# 31. Conditional Pass

Phase 0 receives a **Conditional Pass** if:

- A true gamepad backend works on some devices or privilege levels.
- Another backend is needed for some devices.
- Touch fallback covers remaining devices.

In this case the architecture remains:

```text
Native/Gamepad
      ↓
Shizuku
      ↓
Fallback
```

and compatibility is reported per device/backend.

---

# 32. Failure

Phase 0 fails the primary objective if no practical method can produce reliable controller-style input.

The project should NOT pretend that touch injection is equivalent.

Instead:

```text
Primary gamepad architecture = unresolved
```

and the research phase continues.

---

# 33. Android Version Matrix

Record results as:

| Android | Normal | Shizuku ADB | Shizuku Root | Best Backend |
|---|---|---|---|---|
| 10 | ? | ? | ? | ? |
| 11 | ? | ? | ? | ? |
| 12 | ? | ? | ? | ? |
| 13 | ? | ? | ? | ? |
| 14 | ? | ? | ? | ? |
| 15+ | ? | ? | ? | ? |

Do not fill unsupported cells with guesses.

---

# 34. Device/OEM Matrix

Eventually test:

| Manufacturer | Model | Android | Shizuku | Backend | Result |
|---|---|---:|---|---|---|
| Samsung | — | — | — | — | — |
| Xiaomi | — | — | — | — | — |
| OnePlus | — | — | — | — | — |
| Google | — | — | — | — | — |
| Other | — | — | — | — | — |

OEM differences must be treated as first-class compatibility data.

---

# 35. What Phase 0 Must Produce

At completion, the repository should contain:

```text
docs/
├── PHASE-0.md
├── INPUT_BACKENDS.md
└── phase0/
    ├── results/
    ├── reports/
    ├── logs/
    └── screenshots/
```

Also:

```text
phase0-report.json
```

for each tested device/backend combination.

---

# 36. Phase 0 Deliverables

### Deliverable 1

Minimal input-test application.

### Deliverable 2

Input backend experiments.

### Deliverable 3

Automated button/axis test suite.

### Deliverable 4

Target application compatibility results.

### Deliverable 5

Shizuku capability results.

### Deliverable 6

Device/Android compatibility matrix.

### Deliverable 7

Architecture Decision Record:

```text
ADR-INPUT-001
```

This ADR determines the production input strategy.

---

# 37. ADR-INPUT-001 Must Answer

The final Phase 0 decision must explicitly answer:

1. Can GameDeck create a true virtual gamepad?
2. On which Android versions?
3. Does it require Shizuku?
4. Does it require root?
5. Does it require both?
6. Is the device visible as a controller?
7. Do emulators recognize it?
8. Do streaming applications recognize it?
9. What is the latency?
10. What is the fallback?
11. What percentage of tested devices work?
12. What known OEM restrictions exist?

---

# 38. Production Decision Examples

### Example A

```text
TRUE VIRTUAL GAMEPAD
Shizuku + root required
```

Production:

```text
GamepadBackend
     ↓
Root/Shizuku
```

Standard users receive fallback functionality.

---

### Example B

```text
GAMEPAD-STYLE SYSTEM INJECTION
Shizuku ADB sufficient
```

Production:

```text
ShizukuGamepadBackend
```

No root dependency required.

---

### Example C

```text
NO ACCEPTABLE SYSTEM GAMEPAD PATH
```

Production:

```text
GamepadBackend = blocked
TouchBackend = fallback
```

The product team then decides whether the remaining user experience is acceptable.

---

# 39. Do Not Build Yet

Until Phase 0 passes, do NOT spend significant development time on:

- Full launcher UI
- Community system
- Skins marketplace
- Advanced layout editor
- Cloud sync
- Premium features
- Large profile databases
- Complex display management

The controller/input capability is the project risk.

---

# 40. Phase 0 Completion Definition

Phase 0 is complete when:

```text
Hypothesis
   ↓
Prototype
   ↓
Measured results
   ↓
Compatibility matrix
   ↓
Input backend decision
   ↓
ADR-INPUT-001
```

has been completed.

At that point the project can move confidently into the full GameDeck implementation.

---

# 41. Important Note About Display Embedding

Phase 0 should also include a **small secondary experiment** for the desired handheld display arrangement.

The target concept is:

```text
┌──────────┬──────────────────────┬──────────┐
│ LEFT     │                      │ RIGHT    │
│ CONTROL  │       GAME           │ CONTROL  │
│          │                      │          │
└──────────┴──────────────────────┴──────────┘
```

Do not assume this requires embedding the game inside GameDeck.

Test separately:

```text
Overlay approach
Window-management approach
Supported activity-embedding approach
Virtual-display approach
```

Android 13+ provides cross-application activity embedding under specific conditions, but the system has trust/opt-in and task-ownership restrictions. It therefore cannot be treated as a universal Android 10+ solution.

This display experiment should produce a second small report:

```text
docs/phase0/display-layout-report.md
```

The display experiment must not block the input experiment from proceeding.

---

# 42. Final Phase 0 Principle

The purpose of Phase 0 is not to prove that the original architecture was correct.

It is to discover what Android actually permits.

The production architecture must then be built around the evidence.
