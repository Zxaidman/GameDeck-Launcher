# Kestrel Compatibility

**Document:** `docs/COMPATIBILITY.md`  
**Status:** Active — populated as device evidence is produced  

This document records what Kestrel actually supports on real devices and target applications.

It is intended to answer a simple question:

> **What works, where, under which conditions, and how confidently do we know it?**

Compatibility information must be based on testing or documented technical evidence.

Do not mark something as supported merely because:

- it should work in theory
- an API exists
- an AI coding tool said it should work
- it worked on one device but was never recorded
- a similar Android application behaves that way

---

# 1. Purpose

Kestrel operates at several layers of the Android platform:

```text
Android version
      ↓
OEM / device firmware
      ↓
Kestrel
      ↓
Input backend
      ↓
Overlay / display mechanism
      ↓
Target gaming application
      ↓
Game / streaming session
```

A failure at any layer can affect the user experience.

Therefore compatibility is tracked at multiple levels rather than using a single:

> "Compatible / Not Compatible"

classification.

---

# 2. Compatibility Areas

Kestrel compatibility is divided into:

1. Android version
2. Device / OEM
3. Shizuku
4. Input backend
5. Overlay
6. Foreground-app detection
7. Gaming application
8. Controller features
9. Display/layout behavior
10. Community configuration
11. Performance

A device can be compatible with one subsystem and incompatible with another.

Example:

```text
Controller overlay:      Supported
Gamepad injection:       Unsupported
Touch fallback:          Supported
Shizuku:                 Supported
Display resizing:        Limited
```

That is a valid compatibility result.

---

# 3. Status Definitions

Use the following status values.

## Supported

The feature has been tested successfully on the stated environment and meets the project's acceptance criteria.

```text
Status: Supported
```

---

## Supported with Shizuku

The feature works when the required Shizuku capability is available.

```text
Status: Supported with Shizuku
```

The exact privilege level must also be documented where relevant:

```text
Shizuku: ADB / shell
```

or:

```text
Shizuku: root
```

---

## Supported with Fallback

The preferred mechanism is unavailable, but the fallback provides usable functionality.

Example:

```text
Native gamepad backend: unavailable
Touch mapping backend: working

Status: Supported with fallback
```

---

## Limited

The feature works partially or only under specific conditions.

Examples:

- digital buttons work but analog sticks do not
- one target application works but another does not
- only specific Android versions work
- overlay works but display manipulation does not

```text
Status: Limited
```

---

## Experimental

A prototype works, but there is not enough evidence to call it supported.

```text
Status: Experimental
```

This is commonly used during Phase 0.

---

## Untested

The feature may be implemented but has not been tested in the stated environment.

```text
Status: Untested
```

Never interpret `Untested` as `Supported`.

---

## Unknown

There is insufficient information to determine the result.

```text
Status: Unknown
```

---

## Unsupported

The tested environment cannot provide the required capability, or a required prerequisite is intentionally unavailable.

```text
Status: Unsupported
```

---

# 4. Confidence Levels

A compatibility result should also include a confidence level.

## High

Repeated successful tests on real hardware with the target application.

Example:

```text
Confidence: High
```

## Medium

Successful test exists, but testing coverage is limited.

Example:

```text
Confidence: Medium
```

## Low

The result is based on a single test or incomplete evidence.

Example:

```text
Confidence: Low
```

## Unverified

The claim has not yet been validated.

Example:

```text
Confidence: Unverified
```

---

# 4a. Relationship to the Other Status Vocabularies

Three documents describe result quality for different purposes. They are not interchangeable, and a
value from one must never be substituted for a value from another.

| Vocabulary | Defined in | Describes | Scope |
| --- | --- | --- | --- |
| Status + Confidence | this document, §3–§4 | what the project supports, and how well that is known | a device / target-application / backend combination |
| Grade A–E | `docs/PHASE-0.md` §28 | how strong an input mechanism is, technically | one input mechanism during the feasibility phase |
| Claim state | `AI_DEVELOPMENT_GUIDE.md` | how far a specific statement has been verified | any individual claim, in code or documentation |

Approximate mapping, for orientation only:

```text
Grade A  →  may justify   Supported                (once devices confirm it)
Grade B  →  may justify   Supported / Supported with Shizuku
Grade C  →  may justify   Limited
Grade D  →  may justify   Supported with Fallback
Grade E  →                Unsupported
```

A grade is evidence about a mechanism. A status is a claim about the product. Producing a grade in a
feasibility test never by itself upgrades a status here — that requires the evidence in §5 and a
confidence level from §4.

The issue template offers a deliberately reduced set of statuses because a reporter is describing one
observation, not setting project-wide support. A maintainer assigns the final status.

---

# 5. Evidence Requirements

A compatibility entry should ideally include:

- device manufacturer
- device model
- Android version
- security/firmware information when relevant
- Kestrel version or Git commit
- target application
- target application version
- input backend
- Shizuku state
- relevant permissions
- feature tested
- observed result
- test date
- tester or test source where appropriate

Example:

```text
Device:
Google Pixel 8

Android:
15

Kestrel:
commit abc123

Target:
Moonlight 12.x

Input backend:
Shizuku ADB

Result:
Digital + analog input working

Confidence:
Medium
```

---

# 6. Do Not Guess Compatibility

The following are not valid evidence:

```text
"Android is Android, so it should work."
```

```text
"Shizuku gives system access, so it must work."
```

```text
"This API exists, therefore the feature is supported."
```

```text
"It works on my emulator."
```

The repository must distinguish between:

**known**

and

**assumed**.

---

# 7. Android Version Matrix

This table is a living document.

Do not fill unknown values with guesses.

| Android | API | Standard Mode | Shizuku ADB | Shizuku Root | Overlay | Gamepad Backend | Touch Fallback | Notes |
|---|---:|---|---|---|---|---|---|---|
| Android 10 | 29 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 11 | 30 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 12 | 31 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 12L | 32 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 13 | 33 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 14 | 34 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 15 | 35 | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |
| Android 16+ | 36+ | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | |

> Update this table only with evidence from actual testing or carefully documented platform research.

---

# 8. Device / OEM Matrix

The same Android API may behave differently between manufacturers.

Initial manufacturers to investigate:

- Google
- Samsung
- Xiaomi
- OnePlus
- Motorola
- OPPO
- vivo
- ASUS
- Nothing
- Sony
- other manufacturers reported by users

Suggested table:

| Manufacturer | Device | Android | OEM Skin/Firmware | Shizuku ADB | Shizuku Root | Input | Overlay | Display | Overall |
|---|---|---:|---|---|---|---|---|---|---|
| Google | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Samsung | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Xiaomi | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| OnePlus | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Motorola | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| ASUS | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Nothing | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Sony | — | — | — | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |

Add individual device rows as they are actually tested.

Tested devices:

| Manufacturer | Device | Android | OEM Skin/Firmware | Shizuku ADB | Shizuku Root | Input | Overlay | Display | Overall |
|---|---|---:|---|---|---|---|---|---|---|
| Xiaomi | Redmi Note 13 5G (`2312DRAABI`) | 15 | HyperOS 3.0.3 (`OS3.0.3.0.VNQINXM`) | Working | Untested | Experimental | Untested | Untested | Experimental |

```text
Status: Experimental
Confidence: Low
Shizuku: ADB / shell (uid 2000)
Evidence: docs/phase0/results/tier5-exercise-report.md, docs/phase0/results/tier6-report.md
```

Input is Experimental rather than Supported with Shizuku because §29 of `docs/PHASE-0.md` is not
fully satisfied: no streaming client has been confirmed, and repeatability across reboots has not
been established. Confidence is Low because every result comes from one device and one firmware.
Root was never tested — the device is not rooted, so that cell is Untested, never Unsupported.

---

# 9. Input Backend Compatibility

This is the most important compatibility area.

Current planned input backend categories:

```text
Native / Virtual Gamepad
Shizuku-based backend
System-level input injection
Touch / gesture fallback
Future backend(s)
```

Each backend must be evaluated independently.

Suggested matrix:

| Backend | Normal Android | Shizuku ADB | Shizuku Root | Buttons | Analog | Triggers | Simultaneous | Device Identity |
|---|---|---|---|---|---|---|---|---|
| Native/Gamepad | Unsupported | Experimental | Untested | Working | Working | Working | Working | Yes |
| Shizuku | N/A | Limited | Untested | Working | Limited | Unsupported | Unknown | No |
| System Injection | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown | Unknown |
| Touch Fallback | Unknown | N/A | N/A | Unknown | Unknown | Unknown | Unknown | No |

```text
Status: Experimental
Confidence: Low
Tested on: Xiaomi Redmi Note 13 5G, Android 15, HyperOS 3.0.3
Evidence: docs/phase0/results/tier5-exercise-report.md, docs/phase0/results/tier6-report.md
```

Row by row, and only from what was actually observed on that one device:

- **Native/Gamepad** here means a kernel virtual input device created through the platform's own
  helper. It needs shell privilege, which an ordinary application cannot obtain, so the normal
  column is Unsupported — that is a fact about privilege, not about the mechanism. With shell
  privilege it produced a device the platform classifies as a controller, delivering buttons,
  both sticks, analog triggers and simultaneous input under its own device id.
- **Shizuku** here means the platform's `input` command run with shell privilege. It delivers
  buttons, but `input motionevent` accepts only two coordinates, so the right stick and the
  triggers are unreachable, and an axis it sets is never released implicitly. It has no device
  identity of its own.
- The remaining rows have not been tested and stay Unknown.

The exact final backend names should match `docs/INPUT_BACKENDS.md` once that document exists.

---

# 10. True Gamepad Requirement

Kestrel distinguishes between several kinds of input.

## Level 1 — Touch simulation

```text
button
  ↓
screen gesture
```

This is a fallback.

It is not classified as a true virtual gamepad.

---

## Level 2 — Event injection

```text
button
  ↓
system input event
```

This may be useful, but it does not automatically mean Android exposes a virtual controller device.

---

## Level 3 — Gamepad-style axis/button delivery

```text
button / axis
  ↓
target application
```

This may qualify for certain backend classifications depending on how the target receives the events.

---

## Level 4 — Virtual gamepad identity

```text
virtual controller
       ↓
Android input subsystem
       ↓
target application
```

This is the preferred long-term result.

Compatibility reports must state which level was actually achieved.

---

# 11. Controller Feature Matrix

Controller features must be tested independently.

| Feature | PPSSPP | Dolphin | RetroArch | Moonlight | Steam Link |
|---|---|---|---|---|---|
| A/B/X/Y | Untested | Unknown | Bound | Unknown | Unknown |
| D-pad | Untested | Unknown | Bound | Unknown | Unknown |
| Left Stick | Untested | Unknown | Bound | Unknown | Unknown |
| Right Stick | Untested | Unknown | Untested | Unknown | Unknown |
| LB/RB | Untested | Unknown | Bound | Unknown | Unknown |
| LT/RT | Untested | Unknown | Untested | Unknown | Unknown |
| Start | Untested | Unknown | Untested | Unknown | Unknown |
| Back | Untested | Unknown | Untested | Unknown | Unknown |
| Simultaneous Inputs | Untested | Unknown | Untested | Unknown | Unknown |
| Hold/Release | Untested | Unknown | Untested | Unknown | Unknown |

Additional applications should be added as testing begins.

Emulators tested with a Kestrel-created controller, on the device in §8:

| Feature | Eden | NetherSX2 | RetroArch 1.22.2 | PPSSPP | Dolphin |
|---|---|---|---|---|---|
| Listed as a connected controller | Yes, by name | Yes, by name and id | Yes, by name | Yes, as `pad1` | Yes, as `Android/1/Kestrel Virtual Controller` |
| Auto-mapping | Full control set | Completed | Device selected as Port 1 | Bound through its mapping screen | Listed for selection |
| A/B/X/Y | Bound (`Button 96/97/99/100`) | Bound (`Button96`, `Button100`) | Bound | Bound (`pad1.[A]`) | Untested |
| D-pad | Bound (`±Axis 15/16`) | Bound (`±Axis15/16`) | Bound | Bound (`pad1.Y HAT+`) | Untested |
| Left Stick | Bound (`Axis 0/1`) | Untested | Bound | Bound (`pad1.X Axis+`) | Untested |
| Right Stick | Untested | Untested | Untested | Bound (`pad1.Z Axis+`) | Untested |
| LB/RB | Bound (`Button 102/103`) | Untested | Bound | Untested | Untested |
| **LT/RT** | **Bound as axes** (`Axis 17/18`) | Untested | Untested | Bound (`pad1.TriggerL+`) | Untested |
| Simultaneous Inputs | Untested in-application | Untested | Untested | Untested | Untested |
| Hold/Release | Untested in-application | Untested | Untested | Untested | Untested |

One further observation, from outside the emulator category: a **browser gamepad tester** page
reports the device through the web Gamepad API as `Kestrel Virtual Controller`, vendor `18d1`,
product `4ee0`, connected, with sixteen buttons and live axis values. The browser has no controller
heuristics of its own — it reports what the web platform hands it — so this is a target that was
never written with any of this in mind treating the device as an ordinary controller.

```text
Status: Experimental
Confidence: Low
Level achieved: Level 4 — virtual gamepad identity (see §10)
Evidence: docs/phase0/results/tier6-report.md
```

**Lifecycle hazard, found and addressed on this device.** A created controller is held by a process
that is not the application's, so it initially survived force-stop, clearing data, and
**uninstalling the application**, and kept delivering input with nothing installed — only a reboot
ended it (`docs/phase0/results/tier5-orphan-report.md`).

The mechanism is not the fault, and removing the persistence would remove the feature: a controller
that dies when the user leaves the launcher cannot be used to play anything. What was missing was a
way for the owner to end it. A session is now held by a lease renewed by a visible foreground
service, with a privileged watchdog that closes the device when renewals stop. Verified on this
device: the session survives leaving the application and switching between applications, ends
immediately on Stop, and ends within 10–20 seconds on force stop or uninstall
(`docs/phase0/results/tier5-session-report.md`).

Any backend built on this mechanism inherits the requirement: **persistence must be governed, not
prevented** — held by a lease rather than a schedule, watched by something that outlives the
application, and visible while it exists.

"Bound" records what the application's own binding screen displayed after auto-mapping. It is
strong evidence that the application enumerated the device and accepted its controls, and it is
**not** evidence that gameplay works: nothing was played, and latency was not measured. Entries
left Untested were not exercised — they must never be read across from the ones that were.

---

# 12. Target Application Compatibility

Kestrel initially targets gaming applications rather than arbitrary Android applications.

Application compatibility should include:

- installation
- automatic detection
- manual addition
- launch
- profile selection
- controller display
- input
- orientation
- scaling
- foreground monitoring
- session termination

Suggested format:

```text
Application:
PPSSPP

Category:
Emulator

Package:
<package>

Detection:
Supported

Manual Add:
Supported

Launch:
Supported

Gamepad:
Experimental

Touch fallback:
Supported

Landscape:
Supported

Display scaling:
Limited

Profile:
Supported
```

---

# 13. Emulator Compatibility

Initial emulator targets:

## PPSSPP

Primary goals:

- controller detection
- digital input
- analog input
- triggers where relevant
- profile switching
- landscape mode

## Dolphin

Primary goals:

- GameCube-style controller
- digital input
- analog sticks
- triggers
- profile switching
- landscape mode

## RetroArch

Primary goals:

- generic controller
- D-pad
- analog
- per-core compatibility where necessary

RetroArch should not be treated as a single compatibility environment forever. Different cores and configurations may have different behavior.

## Other emulators

Future emulator entries should be added after actual testing.

---

# 14. Streaming and Cloud-Gaming Compatibility

```text
Status: Untested
Confidence: Unverified
```

A first attempt with Artemis produced no observation at all: the client exposes no screen listing
connected controllers, so there was nothing to read. That is a limit of what could be seen, not a
negative result. A streaming client is a pass-through — the question it answers is whether the
**host** sees a gamepad, so confirming this requires streaming to a host and checking there. Until
that is done, the streaming half of `docs/PHASE-0.md` §29 is unmet and `ADR-INPUT-001` stays
pending.

Initial streaming targets include:

- Moonlight
- Steam Link
- Xbox Cloud Gaming
- GeForce NOW
- other controller-oriented streaming clients

Streaming applications can behave differently from emulators because input may be translated, serialized, or forwarded to another system.

Therefore testing should measure:

```text
Kestrel
   ↓
Android target
   ↓
Streaming client
   ↓
Remote game
```

rather than only checking whether the local application appears to respond.

---

# 15. Cloud-Gaming Caveat

A local controller test does not automatically prove cloud-gaming compatibility.

A valid cloud-gaming test should verify the complete path.

For example:

```text
Virtual button
      ↓
Cloud-gaming client
      ↓
Remote session
      ↓
Game
      ↓
Expected action
```

This distinction should be recorded in compatibility reports.

---

# 16. Display Compatibility

Display behavior is tracked separately from input.

Potential capabilities include:

- controller overlay
- landscape mode
- portrait mode
- configurable controller area
- aspect-ratio selection
- Fit
- Fill
- Stretch
- custom game area
- external activity/window resizing
- activity embedding where available
- virtual-display approaches where applicable

A device may have:

```text
Overlay: Supported
Scaling: Supported
External window resize: Unsupported
```

That should be reported as separate capabilities.

---

# 17. Controller Overlay Compatibility

Test at minimum:

- appears above target application
- remains interactive
- does not unexpectedly disappear
- survives configuration changes
- behaves correctly after foreground application changes
- cleans itself up after a gaming session

Record failures such as:

- overlay disappears
- touch input blocked
- overlay offset incorrect
- safe-area overlap
- manufacturer-specific restrictions

---

# 18. Orientation Compatibility

Track:

- portrait
- landscape
- forced landscape
- orientation changes
- application-requested orientation
- lifecycle behavior after rotation

A target application's own orientation behavior may override Kestrel preferences.

Do not claim orientation control merely because Kestrel itself rotates.

---

# 19. Scaling Compatibility

Scaling should be tested against actual visual output.

Example:

```text
4:3 + Fit
4:3 + Fill
4:3 + Stretch

16:9 + Fit
16:9 + Fill
16:9 + Stretch
```

Record:

- aspect preservation
- cropping
- distortion
- black bars
- controller overlap
- performance impact

---

# 20. Performance Compatibility

Compatibility includes more than "it works."

Measure where practical:

- input latency
- frame stability
- CPU usage
- GPU impact
- memory usage
- battery impact
- overlay rendering stability

A feature that technically works but causes severe frame drops may be classified as:

```text
Limited
```

rather than:

```text
Supported
```

---

# 21. Input Latency

Controller input should eventually have a measurable latency target.

Phase 0 should establish baseline measurements before the project commits to a numeric production requirement.

When measuring latency, document:

- device
- backend
- target application
- measurement method
- sampling rate
- whether the measurement is local or end-to-end

Do not invent latency numbers from subjective impressions.

---

# 22. Battery Behavior

The controller engine should not consume excessive battery merely because an overlay is visible.

Test:

- idle gaming launcher
- active controller
- analog stick held/moving
- streaming session
- background behavior

Potential problem:

```text
continuous polling
        ↓
high CPU usage
        ↓
battery drain
```

Where possible, prefer event-driven behavior.

---

# 23. Memory Stability

Test long sessions.

Suggested scenarios:

- 30-minute emulator session
- 1-hour session
- streaming session
- repeated application launches
- repeated profile switching

Look for:

- memory growth
- overlay leaks
- service leaks
- controller-state accumulation
- crashes

---

# 24. Shizuku Compatibility

Shizuku must be treated as its own compatibility axis.

Test:

### Shizuku unavailable

Kestrel should clearly indicate unavailable capabilities.

### Shizuku running

Verify initialization.

### Permission not granted

Verify graceful failure.

### ADB-backed Shizuku

Test capabilities.

### Root-backed Shizuku

Test capabilities.

### Shizuku restarts

Verify Kestrel detects capability changes.

### Kestrel restarts

Verify the application recovers.

---

# 25. Shizuku Compatibility Reporting

Every Shizuku result should identify:

```text
Shizuku state:
Running / stopped

Privilege:
ADB / root

Kestrel:
version/commit

Android:
version/API

Device:
manufacturer/model

Capability:
what was tested

Result:
supported/limited/unsupported

Notes:
observed limitations
```

Do not record "Shizuku supported" without specifying the actual capability.

---

# 26. OEM-Specific Behavior

OEM-specific restrictions may involve:

- battery optimization
- background service policies
- overlay behavior
- permission management
- app startup restrictions
- aggressive process termination
- accessibility restrictions
- display behavior

When a behavior is OEM-specific, document the exact device and firmware rather than generalizing to the entire manufacturer.

Prefer:

> "Observed on Redmi Note X with firmware Y"

over:

> "Xiaomi does not support this."

unless broader evidence exists.

---

# 27. Firmware Updates

A previously supported device may change behavior after an OEM firmware update.

Compatibility entries should therefore optionally record:

- firmware/build number
- One UI / MIUI / HyperOS / OxygenOS / etc. version where relevant
- security patch date when useful

When a major update changes compatibility, add a new test entry rather than silently rewriting history.

---

# 28. Regression Testing

A compatibility result should not be considered permanent.

Important regression checkpoints:

- new Kestrel release
- major Android update
- major OEM firmware update
- major target-application update
- input backend change
- overlay architecture change
- Shizuku integration change

A previously passing environment should be retested after significant changes.

---

# 29. Compatibility Test Records

Store detailed test records separately from this summary document when practical.

Suggested structure:

```text
docs/
└── compatibility/
    ├── devices/
    ├── applications/
    ├── input/
    └── reports/
```

Example filename:

```text
pixel8-android15-moonlight-shizuku-adb.md
```

Each report should contain enough information to reproduce the test.

---

# 30. Recommended Test Record

Use this template:

```markdown
# Compatibility Test

## Environment

- Device:
- Manufacturer:
- Android:
- API:
- Firmware:
- Kestrel:
- Commit:
- Shizuku:
- Privilege:
- Target application:
- Target version:

## Feature

- Input backend:
- Controller layout:
- Scaling:
- Aspect ratio:
- Orientation:

## Tests

- Digital buttons:
- D-pad:
- Left stick:
- Right stick:
- Triggers:
- Simultaneous input:
- Hold/release:
- Lifecycle:
- Foreground-app transition:

## Result

- Status:
- Confidence:

## Observations

Describe what actually happened.

## Limitations

Document known problems.

## Evidence

Links to logs, screenshots, recordings, or test reports where appropriate.
```

---

# 31. Compatibility Evidence

Where practical, maintain evidence such as:

- screenshots
- screen recordings
- diagnostic JSON
- logs
- test reports
- benchmark results

Evidence should not contain private user information.

---

# 32. Compatibility Labels in the Application

The eventual Kestrel UI may show labels such as:

```text
✓ Supported
✓ Supported with Shizuku
~ Limited
⚗ Experimental
? Untested
✕ Unsupported
```

The application should avoid implying certainty where testing does not justify it.

---

# 33. User-Reported Compatibility

Users will eventually be able to report compatibility.

User reports should initially be treated as:

```text
Community Report
```

not automatically promoted to:

```text
Officially Supported
```

A maintainer or designated tester should verify important claims before changing official compatibility status.

---

# 34. Community Compatibility Data

Community-provided compatibility information may eventually be distributed through GitHub.

It should use machine-readable data where practical.

Example concept:

```json
{
  "schemaVersion": 1,
  "device": {
    "manufacturer": "Example",
    "model": "Example"
  },
  "android": {
    "version": "15",
    "api": 35
  },
  "target": {
    "package": "example.package"
  },
  "result": {
    "status": "supported",
    "confidence": "medium"
  }
}
```

The exact schema belongs in the configuration-schema documentation.

---

# 35. Compatibility Claims

Use careful language.

Preferred:

> "Tested successfully on Pixel 8 running Android 15."

Avoid:

> "Works on all Pixel phones."

Preferred:

> "Moonlight digital and analog input tested successfully with Shizuku ADB."

Avoid:

> "Shizuku makes Moonlight compatible."

---

# 36. Minimum Evidence for Official Support

Before marking a feature or environment `Supported`, there should be:

1. A real-device test.
2. A reproducible test procedure.
3. Successful results for the required feature set.
4. No known critical failure in the tested scenario.
5. A recorded environment.
6. A documented confidence level.

For high-risk system features such as privileged input, stronger evidence may be required.

---

# 37. Compatibility Is Not a Guarantee

Even a `Supported` result means:

> "This configuration has been tested successfully under the recorded conditions."

It does not mean:

> "This will work on every future version of Android, firmware, or target application."

Android and third-party applications change independently.

---

# 38. Priority of Compatibility Testing

Testing priority should generally follow:

### Priority 1

Input backend feasibility.

### Priority 2

Primary emulator targets.

### Priority 3

Primary streaming targets.

### Priority 4

Shizuku variants.

### Priority 5

Android version coverage.

### Priority 6

OEM/device coverage.

### Priority 7

Display/scaling edge cases.

This prevents the project from spending large amounts of time testing UI variations before the core input mechanism is proven.

---

# 39. Initial Compatibility Targets

The first meaningful target set is:

```text
Android:
10+

Applications:
PPSSPP
Dolphin
RetroArch
Moonlight
Steam Link

Input:
Gamepad-style backend
Shizuku-backed backend
Fallback backend

Layouts:
Xbox
PlayStation
Nintendo
Generic / emulator-specific
```

The project should expand the matrix only after the core path is working reliably.

---

# 40. Phase 0 Relationship

Phase 0 is the source of truth for the initial input compatibility decision.

See:

[`docs/PHASE-0.md`](PHASE-0.md)

Phase 0 should produce:

- device test results
- backend results
- target-application results
- input capability results
- Shizuku results
- limitations
- `ADR-INPUT-001`

After Phase 0, the compatibility matrix in this document should be updated using those results.

---

# 41. Future Compatibility Documents

As the project grows, this document may be split into:

```text
docs/compatibility/
├── android.md
├── devices.md
├── emulators.md
├── streaming.md
├── input-backends.md
└── test-reports/
```

Until the database becomes large enough to justify splitting it, this file remains the high-level source of truth.

---

# 42. Final Rule

The most important compatibility rule is:

> **Do not confuse hope, theory, or a single successful experiment with support.**

Kestrel should be known for telling users honestly:

- what works
- what partially works
- what requires Shizuku
- what requires a specific privilege level
- what is experimental
- what has not been tested
- what does not work

That honesty is part of the product.

