# Phase 0 — A Streaming Host Sees a Real Controller

**Document:** `docs/phase0/results/tier6-streaming-report.md`  
**Status:** The last outstanding acceptance criterion is met on the reference device  
**Evidence:**
`docs/phase0/results/tier6-streaming-session-20260819-redmi-note-13-5g.json`,
`tier6-streaming-stop-…json`, `tier6-streaming-forcestop-…json` (harness 0.0.16),
plus an operator screenshot of the host's controller panel  
**Client:** Artemis · **Host:** Apollo on Windows, connected over USB tethering  
**Device:** `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys`  

---

## 1. The result

A controller created on the phone, by this project, with shell privilege and no root, was forwarded
by the streaming client and **appeared on the Windows host as a game controller**.

The host's controller panel lists:

```text
Installed game controllers
  Wireless Controller        Status: OK
```

Its properties page shows the standard set — X/Y axes, Z axis, X/Y/Z rotation, thirteen buttons and
a point-of-view hat — and the operator confirmed **the axes and buttons moving on their own** as the
harness cycled controls on the phone. Nothing was touched at either end.

That completes the chain end to end:

```text
Kestrel creates a virtual input device on the phone
        ↓  platform enumerates it as a controller
streaming client reads it as a controller and forwards its state
        ↓  network
host reconstructs it as a controller for the operating system
        ↓
anything on the host that reads a controller
```

The client is a pass-through, so this is the only test that could have answered the question. It
answered it.

## 2. Why the host says "Wireless Controller"

That name is the host's own virtual pad, not ours. A streaming host does not relay a device
identity; it reconstructs a controller locally and feeds it the state the client sends. So the name
on the host says nothing about what the phone created, and everything about the host's own
implementation.

What it does establish is the part that matters: **the client accepted our device as a controller
worth forwarding.** A client that had rejected it, or treated it as a keyboard, would have produced
no controller on the host at all.

## 3. Session behaviour during the test

The three exports record the session working as designed, in the client's presence:

- **Held open across the whole test.** `holding the device: 5531  app_process /system/bin
  com.android.commands.uinput.Uinput -` — one holder, alive throughout, while the operator was in
  the streaming client rather than in the harness.
- **Pause and resume, twice**, without the device closing: `SESSION PAUSE — input stopped, device
  still open`, then `SESSION RESUME`. Device id 18 stayed the same across both.
- **Stop verified rather than assumed.** The teardown export shows the holder present before and
  absent after, with the device count returning to baseline:

  ```text
  holding the device before: 5531  app_process … Uinput -
  holding the device after:  (no output, exit=0)
  input devices now: 8
  ```

  and the harness's own listener recording `DEVICE REMOVED id=18` independently.
- **A later session, id 19**, shows the full control set arriving — `Z=1.000  RZ=-1.000`,
  `LTRIGGER=1.000 RTRIGGER=1.000 BRAKE=1.000 GAS=1.000`, `RTRIGGER=0.502  GAS=0.502` — and then
  `DEVICE REMOVED id=19` when the session ended.

The first export also contains `SESSION: lease renewal failed — the watchdog will close the device`
at entry 0002, **before** the session started. That is the heartbeat running with no privileged
service bound yet, and it is the correct message for that state: no service, no renewal. It is
noise at that moment rather than a fault, and the wording should be narrowed to fire only while a
session is actually open.

## 4. Acceptance criteria — `docs/PHASE-0.md` §29

| Criterion | State | Evidence |
| --- | --- | --- |
| Digital — A/B/X/Y and D-pad | **Met** | `tier5-exercise-report.md`, five emulators |
| Analog — one stick continuously | **Met** | half-deflection `-0.500`, not saturated |
| Triggers | **Met** | analog, `0.502` at half |
| Simultaneous — two independent controls | **Met** | three buttons, two axes together |
| Hold/release — no stuck inputs | **Met** | every control returns to rest |
| Lifecycle — state can be safely reset | **Met** | `tier5-session-report.md` |
| At least one emulator | **Met** | Eden, NetherSX2, RetroArch, PPSSPP, Dolphin |
| **At least one streaming application** | **Met** | this report |
| Repeatability — succeeds repeatedly, not once by chance | **Met on this device** | many sessions across several days and harness versions |

**Every criterion in §29 is satisfied on the reference device.**

## 5. What is still not established

The criteria are met. That is not the same as the mechanism being proven, and the difference should
be stated before anyone builds on it.

- **One device, one firmware, one OEM.** Redmi Note 13 5G, HyperOS 3.0.3, Android 15. §29 does not
  require more; the product does. Nothing here predicts Samsung, Pixel, or Android 10–14.
- **Latency is unmeasured**, and this test could not measure it: the input was generated by a
  schedule, not by a person, so there was nothing to feel. Over a stream there are three places
  latency can accumulate and none of them were quantified.
- **Nobody has played anything.** Binding screens and a host controller panel prove input arrives.
  They prove nothing about whether it is usable under load, during a scene change, or with a
  wireless connection under contention.
- **USB tethering, not Wi-Fi.** The operator connected the phone to the host by cable. Wi-Fi is the
  realistic case and it is untested; a wireless link is where jitter and loss would appear.
- **Shizuku throughout.** ADR-003 keeps that optional to the product, so this remains the best
  backend and never the only one.
- **No fallback path has been tested at all.** Everything measured in Phase 0 is the privileged
  path. What a user without Shizuku gets is still entirely unknown, and that is a larger gap than
  anything on this list.

## 6. What follows

`docs/PHASE-0.md` §29 is satisfied, so `ADR-INPUT-001` — pending since the project began — can be
decided rather than deferred. The record is ready for that decision and the decision belongs to the
project owner; the evidence table in the ADR names what supports it and what does not.

`docs/PHASE-0.md` §30 defines a **Strong Pass** against a specific target list, which is not the
same bar and is not claimed here.
