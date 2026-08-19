# Phase 0 — A Session That Persists and Still Obeys

**Document:** `docs/phase0/results/tier5-session-report.md`  
**Status:** Verified on the reference device — operator-reported, no export taken  
**Harness:** 0.0.16  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  
**Privilege:** Shizuku at shell (uid 2000) — not root  

---

## 1. What was tested

`tier5-orphan-report.md` recorded a controller that nothing could stop. The fix was not to prevent
it persisting — persistence is what a controller is for — but to put the ending under the owner's
control. This run tested whether both halves hold at once: does the session survive everything it
should, and does it die on every signal that means stop?

## 2. Result

| Expected to survive | Result |
| --- | --- |
| Leaving the harness | Survives |
| Switching between applications | Survives |
| Removing the harness from the recent list | Survives |
| A long session — well beyond the old fixed holds | Survives, no failures observed |

| Expected to end it | Result | Delay |
| --- | --- | --- |
| Stop, from the notification | Ends | immediate |
| Stop, in the application | Ends | immediate |
| Pause / Resume | Input stops and restarts, device stays open | immediate |
| **Force stop** | **Ends** | 10–20 seconds |
| **Uninstall** | **Ends** | 10–20 seconds |

Every target tested during the session — the five emulators and the browser gamepad tester —
recognised the controller as a physical one throughout.

**Both halves hold.** The session outlives the screen that created it, and no reboot is needed to
end it any more.

## 3. On the 10–20 seconds

That delay is the design, not a defect, and the number is worth stating plainly rather than
rounding away.

The application renews a lease every 4 seconds. The watchdog wakes every 3 seconds and acts when
the lease is more than 15 seconds old, so the worst case is roughly 18 seconds and the best is
about 15. The observed 10–20 seconds is that window.

Why not shorter: the watchdog is a **dead-man's switch**, and its threshold is a judgement about
how long a live application may go quiet before it is presumed dead. The platform can freeze an
application briefly for its own reasons — memory pressure, a doze transition, a busy moment — and
a threshold tuned too tightly would tear down a controller mid-session because the phone was busy
for two seconds. Losing a controller during play is a worse failure than a device lingering
fifteen seconds after an uninstall.

Why not longer: fifteen seconds is short enough that a user who force-stops the application sees it
end while they are still looking at the screen.

There is a faster path available, worth recording as an option rather than a decision: the guard
could also watch the privileged service's own process, which ends the moment the application is
uninstalled, using the lease only as a backstop. That would cut the uninstall case to near-instant
while keeping the tolerance for a temporarily frozen application. Not implemented — the current
behaviour meets the requirement, and this is the sort of change that should be made against a
measured need rather than a guess.

## 4. What this establishes

Against `docs/PHASE-0.md` §29, the **lifecycle** criterion — input state can be safely reset — is
now met in a much stronger sense than "the harness can destroy what it created". A controller
created by this mechanism can be ended by every means a user would reach for, including the two
that give an application no chance to run any code.

The evidence for the design rule in `tier5-orphan-report.md` §4a is now on the device rather than
in the reasoning: **persistence must be governed, not prevented.**

For the product, three requirements are now demonstrated rather than proposed:

1. A session is held by a **lease renewed by a visible foreground service**, not by a schedule and
   not by a detached process.
2. The **watchdog is privileged and independent**. It cannot be part of the application, because
   the cases that matter most are the ones where the application no longer exists.
3. **Pause is a separate concern from teardown.** Stopping input and closing the device are
   different operations, and the process holding the device must survive the first.

## 5. Limits of this result

- **Operator-reported, with no export.** The observations are recorded as stated; there is no
  machine-readable evidence file for this run, unlike the earlier tiers. The timings are
  wall-clock estimates by a person, not measurements.
- **One device, one firmware.** HyperOS may treat a foreground service differently from other
  builds. On stricter OEM firmware the service could be killed sooner, which under this design ends
  the session — the safe direction, but it would end sessions users did not want ended.
- **Not tested across a reboot**, and not tested with Shizuku restarted mid-session. If the
  privileged service goes away, renewals fail and the device closes; that is the intended
  behaviour, but it has not been exercised deliberately.
- **No measurement of what happens under memory pressure**, which is exactly the condition where a
  dead-man's switch is most likely to fire when it should not.
