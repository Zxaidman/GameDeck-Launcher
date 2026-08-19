# Changelog

**Document:** `CHANGELOG.md`  
**Status:** Active — records only what has actually been established  

All notable changes to Kestrel will be documented in this file.

The project is currently in an early architecture and feasibility stage, so this changelog intentionally documents only decisions and artifacts that have actually been established.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/). Semantic versioning will be used once actual releasable software versions exist.

---

## [Unreleased]

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
