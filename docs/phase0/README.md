# Phase 0 — Harness and Test Procedure

**Document:** `docs/phase0/README.md`  
**Status:** Experimental — Tiers 0 and 1 complete on the reference device; no injection tier attempted  

This is the operating procedure for the Phase 0 input feasibility experiment defined in
`docs/PHASE-0.md`. That document defines *what* must be proven and how evidence is graded. This one
describes *how* to run it with the harness in `tools/phase0/`.

---

## 1. What the harness is

`tools/phase0/` is a separate application with its own identifier
(`io.github.zxaidman.kestrel.phase0`). It is not part of the product, is not on `:app`'s dependency
graph, and requests no permissions.

**The harness only observes.** It enumerates the input devices Android reports, and logs every key
and motion event its window receives, together with the id and source of the device that produced
each one. It never injects anything.

That separation is deliberate. If the harness both produced and measured the input, a passing result
would prove nothing. Injection candidates are driven from a shell, and the harness reports what — if
anything — actually arrived.

**An empty log after an injection attempt is a result.** Record it.

---

## 2. Target device for this run

| Property | Value |
| --- | --- |
| Device | Redmi Note 13 5G |
| SoC | MediaTek Dimensity 6080 |
| Memory / storage | 6 GB / 128 GB |
| Android | 15 (API 35) |
| Firmware | HyperOS 3.0.3 |
| Root | Not expected |

Record the exact `Build.FINGERPRINT` from the harness export rather than trusting this table — the
export is the evidence, this table is orientation.

Because root is not expected, the realistic ceiling for this device is the shell/ADB privilege tier.
Anything requiring UID 0 is out of scope for this run and must be recorded as untested, not as
failed.

---

## 3. Build and install

```bash
./gradlew :tools:phase0:assembleDebug
adb install -r tools/phase0/build/outputs/apk/debug/phase0-debug.apk
```

Or open the project in Android Studio and run the `phase0` configuration.

The APK name may differ depending on how AGP names the output; check the directory if the path
above does not match.

---

## 4. HyperOS preparation

HyperOS restricts several things this experiment needs. Expect the following; each is standard
Xiaomi behaviour rather than something specific to this project, and each should be confirmed rather
than assumed:

1. **Developer options** — Settings → About phone → tap the OS version repeatedly.
2. **USB debugging** — Developer options → USB debugging.
3. **USB debugging (Security settings)** — a separate toggle on Xiaomi builds. It permits input
   injection and permission granting over ADB, and enabling it commonly requires signing into a Mi
   account and inserting a SIM. **This toggle is likely required for the shell injection tiers
   below.** If it cannot be enabled, record that as an environmental limitation — it constrains
   what this device can prove, and it is exactly the kind of OEM restriction the compatibility
   matrix exists to capture.
4. **Wireless debugging** — needed to start Shizuku without a PC. Developer options → Wireless
   debugging → Pair device with pairing code.
5. **Battery restrictions** — set both the harness and Shizuku to unrestricted battery usage, and
   enable Autostart for Shizuku. HyperOS will otherwise kill the Shizuku service, which looks
   identical to a capability failure but is not one.

---

## 5. Test tiers

Run these in order. Each tier answers a different question, and a failure at one tier does not
invalidate the ones below it.

> **Revised order.** Tiers 0 and 1 are complete on the reference device. The evidence in their
> reports moved Tier 5 to the front: it is the only path to a controller identity, the underlying
> facility was found present and in use on that hardware, and two commands establish whether it is
> reachable at all. Run Tier 5 next, then the rest.

### Tier 0 — Baseline device inventory

Open the harness, **Devices** tab, with nothing connected.

This is Test 1 (Device Discovery) in `docs/PHASE-0.md`. It establishes what the device reports
before any intervention. Export the report and keep it — every later tier is compared against this
baseline.

Expect only built-in devices. Note whether any of them advertise `GAMEPAD` or `JOYSTICK` sources.

### Tier 1 — Instrument calibration — **DONE**

Completed on the target device using a second phone running remote-gamepad software over Bluetooth.
The reference signature and the resulting constraints on the input layer are in
`docs/phase0/results/tier1-report.md`. Read that before writing any input code: it records that
buttons with a system meaning are delivered twice, that each trigger reports on two axes, that the
left stick synthesises D-pad keys, and that the system virtual device aggregates capabilities and
will produce false positives.

#### Repeating the calibration on other hardware

If you have any physical controller, pair it over Bluetooth and open the Devices tab again.

This proves the instrument works, and it is the only way to know what a genuine controller looks
like on this device: its sources, its axes, its vendor and product ids, and which buttons it
advertises. Without this reference, a later "success" cannot be distinguished from a partial one.

Press every control with the **Events** tab open and confirm the log shows `dev=<id>` matching the
controller, `src=GAMEPAD|JOYSTICK`, and real axis values for the sticks and triggers.

If you have no controller, skip this and note that the run has no calibration reference.

### Tier 2 — Normal application, no privileges

An ordinary application cannot inject input into other applications: the permission that would allow
it is signature-level, and `Instrumentation` injection is confined to the injecting application's
own windows.

The expected result is therefore that **standard mode cannot drive a third-party target**, which if
confirmed maps to Grade E for cross-application input in `docs/PHASE-0.md` §28 — while still leaving
touch-based fallback (Grade D) available, since that is a different mechanism.

Confirm rather than assume, and record the observation.

### Tier 3 — Shell injection over ADB

With the harness open on the **Events** tab, from a connected computer:

```bash
adb shell input keyevent 96     # BUTTON_A
adb shell input keyevent 97     # BUTTON_B
adb shell input keyevent 99     # BUTTON_X
adb shell input keyevent 100    # BUTTON_Y
adb shell input keyevent 19     # DPAD_UP
```

Read the harness log and record, for each:

- did an event arrive at all?
- what `dev=` id produced it, and does that id appear in the Devices tab?
- what `src=` did it carry — `KEYBOARD`, or `GAMEPAD`?

**The source is the whole question.** An event that arrives as `KEYBOARD` is key-event emulation
(Grade C), not controller input. Only `GAMEPAD`/`JOYSTICK` semantics with a corresponding device
identity would approach Grade B or A.

Some builds accept a source qualifier — try `adb shell input gamepad keyevent 96` and record
whether the command is accepted and whether the delivered source changes. Do not assume this
subcommand exists on this build; the output of a plain `adb shell input` lists what it supports.

Analog axes are the harder half. Record whether any shell mechanism can produce a continuous axis
value at all, since digital-only injection cannot satisfy the acceptance criteria in
`docs/PHASE-0.md` §29.

### Tier 4 — Shizuku, no computer attached

Install Shizuku, start it via wireless debugging, then use its shell (`rish`) to run the same
commands as Tier 3.

The question here is not whether the commands work — Tier 3 already answered that — but whether the
same capability is reachable **without a PC attached**, since that is the only form usable by a real
person on a real phone. Record the privilege level Shizuku reports (`ADB_SHELL`, not `ROOT`, on this
device).

### Tier 5 — Virtual input device — **now runnable from the phone alone**

The harness has a **Probe** tab that asks these questions directly, using Shizuku for the shell
privilege. No computer, no typing commands.

1. Start Shizuku (it stops on every reboot and must be restarted).
2. Open the harness, **Probe** tab, press **Refresh status**.
3. Press **Grant permission** if the status says permission is not granted, and approve the Shizuku
   prompt.
4. Press **Run probe**, then **Export** — the probe output is included in the export.

The privilege state is reported as four separate facts, because none of them implies another:
service running, permission granted, the identity actually obtained (`shell` uid 2000 or `root`
uid 0), and the Shizuku version. `ARCHITECTURE.md` §14 requires exactly this separation; the Probe
tab is where it is first tested against real hardware.

The probe reads only. It checks whether the virtual-input device node exists, its permissions and
owning group, whether it is readable and writable from that identity, whether the helper command is
present, and what the enforcement mode is. It creates no device and produces no event, so a result
shown there cannot have been manufactured by the harness.

**What the answers mean.** If the node is writable from the shell identity, the highest-value path
is open and the next step is attempting to create a device. If it is not, that is a firm ceiling for
this device and firmware, and the remaining tiers can be run knowing it.

#### The manual route, for reference

### Tier 5 — Virtual input device

This is the only tier that could yield Grade A, because it is the only one that would create a
device with its own controller identity rather than injecting events into an existing stream.

First establish whether the mechanism exists on this build at all:

```bash
adb shell which uinput
adb shell uinput -h
adb shell ls -l /dev/uinput
```

Record the output verbatim. If the command or the device node is absent or unreadable from the shell
UID, that is the answer for this device and this tier ends there.

If it is present, the test is whether a virtual controller can be created such that:

1. it appears in the harness **Devices** tab, added via the hot-plug listener, and
2. it advertises `GAMEPAD`/`JOYSTICK` sources with real axes, and
3. events from it arrive carrying that device's id.

Do not describe any result from this tier as a "true virtual gamepad" unless all three hold and a
real target application also responds. That restriction is in `docs/INPUT_BACKENDS.md` and it is not
negotiable.

### Tier 6 — Real target applications

Only for tiers that produced controller-shaped input. Install at least one emulator and one
streaming client from the list in `docs/PHASE-0.md` §7, open each one's control-mapping screen, and
check whether it recognises the input as a controller binding.

A target application's own binding screen is better evidence than gameplay, because it states
explicitly what it thinks it received.

---

## 6. Recording results

Export from the harness after each tier — the export contains the device fingerprint, the full input
device inventory, and the event log, which together form the evidence record required by
`docs/COMPATIBILITY.md` §5.

Save exports as:

```text
docs/phase0/results/<tier>-<yyyymmdd>-redmi-note-13-5g.json
```

Then summarise into a short report per `docs/PHASE-0.md` §27, and only afterwards touch
`docs/COMPATIBILITY.md`. Remember §4a there: an evidence grade describes a mechanism, and never by
itself sets a support status.

`ADR-INPUT-001` stays **Pending** until a tier passes the acceptance criteria in `docs/PHASE-0.md`
§29 with repeatable evidence. One successful press is not a pass.

---

## 7. What this harness cannot tell you

- Nothing about a rooted privilege tier, which this device is not expected to provide.
- Nothing about latency; the harness timestamps arrival, not the interval from a physical action.
- Nothing about behaviour on other OEM firmware. A HyperOS result is a HyperOS result.
- Nothing about whether a target application *plays well* — only whether input is received.
