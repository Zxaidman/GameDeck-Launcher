# Phase 0 — A Created Controller Can Outlive Everything

**Document:** `docs/phase0/results/tier5-orphan-report.md`  
**Status:** Confirmed on the reference device — harness fixed, architectural requirement recorded  
**Evidence:** `docs/phase0/results/tier5-teardown-20260818-redmi-note-13-5g.json` (harness 0.0.15)  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  
**Severity:** The most serious finding of Phase 0 so far  

---

## 1. What happened

A virtual controller created by the harness could not be stopped. The operator confirmed, in order:

1. **Destroy device** did nothing.
2. **Force stop** did nothing.
3. **Clear data** did nothing.
4. **Uninstalling the harness entirely** did nothing. The device kept existing, and kept delivering
   input — to the home screen, to the browser, to whatever was in front of it — with the
   application that created it no longer installed.
5. Reinstalling confirmed the device was still there, created by an install that no longer existed.
6. **Only a reboot ended it.**

The controller ran its full ten-minute schedule and then stopped itself, because the schedule
ended. Nothing the operator did contributed to that.

## 2. Why

Every stop command in the harness matched on the process being called `uinput`:

```text
pkill -x uinput
pgrep -x uinput || echo NONE
ps -A | grep -i uinput
```

**It is not called that.** The helper is a platform command that runs inside a runtime process with
a different name, so none of those patterns matched anything, ever.

That failure was visible in every transcript from the very first creation run. `processes matching
uinput (raw)` printed `(no output, exit=1)` on runs where the device demonstrably existed for
thirty seconds. It was read as "nothing left running" when what it actually meant was "this search
does not work". The teardown reported success by printing `NONE` — from the same broken search.

Two earlier reports flagged the check as unreliable in one direction or the other, and it was
replaced with raw output and no derived claim. That was the right instinct applied to the wrong
half of the problem: the *reporting* stopped lying, but the *killing* was never fixed, and nothing
ever tested that a stop actually stopped anything. Every apparently successful teardown was a run
whose own sleeps had expired.

## 3. Why the device survives uninstalling

This is the part that matters beyond the harness.

The device belongs to the process holding `/dev/uinput` open. That process is **not a child of the
application**. It was started through the shell-privileged service, so it runs as `shell`, in its
own process tree, with no relationship to the application's lifecycle at all.

The platform therefore has no reason to stop it when the application is force-stopped, cleared, or
uninstalled — from the system's point of view, it is not the application's process. Uninstalling
also runs no code, so an application cannot clean up on its way out even if it wanted to.

An orphaned controller is not inert. It holds a device id, occupies player slot 1, and delivers
whatever its schedule tells it to deliver, into whatever application has focus.

## 4. What the holder actually is

The first version of the fix scanned every process's open file descriptors, which is the correct
question — the device exists exactly as long as something holds the node open. On a real phone it
took longer than ten seconds and was killed by its own timeout, mid-answer. It did get far enough
to print the thing worth knowing:

```text
32267  app_process /system/bin com.android.commands.uinput.Uinput -
```

The holder is `app_process`. There was never any process called `uinput` to find. Teardown now
matches that command line, which is specific, stable, and returns immediately:

```text
pkill -9 -f 'com.android.commands.uinput[.]Uinput'
```

The bracket keeps the pattern from matching the command carrying it, which would otherwise make
every sweep look successful by killing its own shell. Afterwards the same search runs again and its
result is printed: **the report is the state after the attempt, not a claim that the attempt
worked.**

Alongside it:

- **STOP ANY DEVICE** and **What is open?** are always available and never disabled. Recovery has
  to work from a cold start, with nothing running and nothing remembered, on a device created by a
  previous install — because that is exactly the situation this failure produces.
- A **warning banner** appears whenever a Kestrel controller is present in the device list,
  visible without Shizuku and on the first screen, so an orphan announces itself instead of being
  discovered by its effects.
- The helper's process id is recorded at launch and killed first, as a cheaper path before the scan.

A holder owned by root cannot be killed from shell privilege and will still be listed afterwards.
That is correct and deliberate: the vendor's own virtual-input process is one of those, and it must
not be touched.

## 4a. Persistence is the feature; ungovernable persistence was the fault

The obvious reading of this finding is "stop the device from surviving". That is wrong, and it took
the operator pointing it out to see it.

A controller that dies when you leave the launcher cannot be used to play anything. The whole point
is to open something else and still have a controller. The behaviour that looked like the bug —
running for ten minutes across application switches, surviving the launcher being closed from the
recent list — is the behaviour a session needs.

What was intolerable was not the persistence. It was that **nothing the owner of the device did
could end it.**

So the harness now runs a session rather than a fixed hold:

- A **foreground service with an ongoing notification** keeps the session visible and gives it a
  Stop control reachable from anywhere. A device that exists invisibly is the problem; a device
  with a permanent handle on screen is not.
- The service **renews a lease** — a timestamp in the privileged process — every few seconds.
- A **watchdog** in the privileged process closes the device when that timestamp goes stale, about
  fifteen seconds. It needs no cooperation from the application, which is the entire point:
  force-stop, cleared data and uninstall all end an application without letting it run any code, so
  any teardown that depends on the application running is not a guarantee. A lease that stops being
  renewed is.
- **Pause** stops the input without closing the device, which is why the holder and the feeder are
  separate processes: the holder reads an ordinary file through `tail -f`, so the feeder can stop
  and restart without the holder ever seeing end of input.

The resulting rule is short: **persistence must be governed, not prevented.** A session outlives the
screen that started it and dies on a signal the owner controls — including the signals a user
reaches for when they want something to stop and do not know or care how it works.

## 5. What this requires of the product

`CLAUDE.md` §5 already says a backend that can leave stuck input is not production-ready. This
finding says something stronger, and it is now evidence rather than principle.

**A session must be governed by a lease, not by a schedule.** A device held for a fixed period
cannot be ended early and cannot be extended; a device held by a detached schedule cannot be ended
at all. The lease model — application renews, privileged watchdog enforces — gives both the
persistence a session needs and the certainty that force-stop, cleared data, or uninstall ends it,
without the application having to run anything.

**A session must be visible while it exists.** An ongoing notification with a stop control is not
decoration; it is the only handle a user has on something that otherwise has no interface once they
leave the launcher.

**Recovery must not depend on remembered state.** The device outlives the process that created it,
so it outlives any record of it. Kestrel must be able to find and destroy a controller it has no
memory of creating, by inspecting what holds the node open. That belongs in the platform layer as a
first-class operation, not as a debug affordance.

**Startup must sweep.** Every launch should look for an orphaned controller and offer to remove it,
before anything else. A user who does not know what a virtual input device is will experience this
as a phone that presses its own buttons.

**Teardown must be verified, never assumed.** A stop that reports success without checking is worse
than no stop at all, because it stops anyone looking further. Every teardown path must re-read the
state afterwards and report what it found.

These are requirements on `platform/input/shizuku/` and on the session lifecycle in
`feature/gaming-session`, and they are not optional. An input backend that can strand a controller
on a user's phone until they reboot is not shippable.

## 6. Also observed in the same session

Three further target results, recorded here because they came from the same runs:

- **PPSSPP** binds it. Its mapping screen shows `pad1.Y HAT+`, `pad1.X Axis+`, `pad1.Z Axis+`,
  `pad1.TriggerL+` and `pad1.[A]` against the standard control set — the fourth emulator, and the
  one that was untested in `tier6-report.md`.
- **Dolphin** lists it as `Android/1/Kestrel Virtual Controller` in its device chooser, alongside
  the phone's real input devices. Fifth.
- A **browser gamepad tester** (`hardwaretester.com/gamepad`) shows it through the web Gamepad API:
  `Kestrel Virtual Controller`, `Vendor: 18d1 Product: 4ee0`, `CONNECTED: Yes`, 16 buttons, with
  `AXIS 2 = 1.00000` and `AXIS 3 = -1.00000` moving as the right stick was driven.

That last one is worth more than it looks. The browser is not an emulator and has no controller
mapping heuristics — it reports what the web platform hands it. The device is a controller to code
that was never written with any of this in mind.

**It is still not a streaming result.** The streaming half of `docs/PHASE-0.md` §29 remains unmet.
