# Phase 0 — Virtual-Input Access Confirmed, and the Ceiling of the Shell Path

**Document:** `docs/phase0/results/tier5-open-report.md`  
**Status:** Complete — the decisive access question is answered  
**Evidence:** `docs/phase0/results/tier5-open-20260817-redmi-note-13-5g.json`  

---

## 1. The answer

```text
── ACTUAL OPEN for write
$ (exec 9>/dev/uinput) 2>&1 && echo 'OPEN SUCCEEDED' || echo 'OPEN DENIED'
OPEN SUCCEEDED
```

A shell-privileged process, obtained through Shizuku with no computer attached, **opened the kernel
virtual-input node for writing on a stock, unrooted, SELinux-Enforcing device.**

The check for kernel denials came back empty, so this was not a permitted-then-audited case. Policy
allows it outright.

This was the single question Phase 0 existed to answer. The path that can produce a device with its
own controller identity — the Grade A path in `docs/PHASE-0.md` §28 — is **open on this device**.

It remains unproven that a device can actually be *created* and *recognised*; opening the node is a
prerequisite, not the whole job. But the prerequisite that was most likely to fail has not failed.

---

## 2. The shell path has a hard ceiling

The full usage text, captured intact this time, settles what the `input` command can and cannot do:

```text
motionevent <DOWN|UP|MOVE|CANCEL> <x> <y>   (Default: touchscreen)
scroll <x> <y> [axis_value]                 Axis options: SCROLL, HSCROLL, VSCROLL
```

Two consequences, and the second is decisive:

1. **`--axis` belongs to `scroll`, not to `motionevent`.** That is why the earlier attempt was
   rejected. The usage preamble describes `--axis` generically, which reads as though it applies
   everywhere; it does not. The tool's own documentation is misleading on exactly this point.

2. **`motionevent` accepts only `x` and `y`.** There is no way to address any other axis. That means
   the shell path can drive **one analog stick and nothing more**:

| Control | Shell path | Notes |
| --- | --- | --- |
| Buttons, D-pad | yes | `src=GAMEPAD` / `src=DPAD`, confirmed |
| Left stick | yes | `X` and `Y`, continuous, confirmed |
| Right stick | **no** | `Z`/`RZ` not addressable |
| Triggers | **no** | `LTRIGGER`/`RTRIGGER`/`BRAKE`/`GAS` not addressable |

Against the acceptance criteria in `docs/PHASE-0.md` §29, which require a working trigger where
supported, **the shell path cannot pass on its own**. It is a genuine Grade B for what it covers,
and it covers roughly half a controller.

This reframes the two paths. The shell path is no longer a plausible final answer; it is a fallback
for devices where the virtual-device path proves unavailable, and a useful comparison baseline.

---

## 3. The release mechanism works

The stuck-axis hazard found in the previous run is now demonstrably controllable:

```text
0009  INJECT [Stick right]: input joystick motionevent MOVE 0.6 0
0010  MOTION X=0.600
0012  KEY DPAD_RIGHT DOWN repeat=1
...   (flooding)
0027  KEY DPAD_RIGHT DOWN repeat=16
0028  AUTO-RELEASE: input joystick motionevent MOVE 0 0
0029  KEY DPAD_RIGHT DOWN repeat=17
0030  KEY DPAD_RIGHT DOWN repeat=18
0031  MOTION (all axes at rest)
0033  KEY DPAD_RIGHT UP
```

Three things worth keeping:

- **The release works.** The axis returned to rest and the repeat flood stopped.
- **The repeat rate is about 15 per second**, from 18 repeats across the 1.2-second hold.
- **The release is not instantaneous.** Two further repeats arrived after the command was issued,
  and the `DPAD_RIGHT UP` came after the axis reached rest. A release therefore needs to be *issued
  early and confirmed*, not assumed to have taken effect the moment it is sent — which matters for
  a session-end path that may be racing a process teardown.

---

## 4. Status

**Settled on this device and firmware:**

| Question | Answer |
| --- | --- |
| Can a shell-privileged process be obtained without a computer? | Yes, via Shizuku, identity `shell` |
| Can it open the virtual-input node for writing under Enforcing SELinux? | **Yes** |
| Can shell injection deliver controller semantics? | Yes — `GAMEPAD`, `DPAD`, `JOYSTICK` sources |
| Can shell injection deliver analog values? | Yes, but only `X` and `Y` |
| Can shell injection drive triggers or a right stick? | **No** |
| Does injected input carry a device identity? | No — everything arrives from `dev=-1` |
| Can a held axis be released? | Yes, and it must be explicit |

**Not settled:** whether a virtual device can actually be created, whether it is recognised with
controller semantics and an identity, and whether any real target application accepts input from
either path. The trigger, simultaneous-input and repeatability criteria remain untouched.

`ADR-INPUT-001` stays **Pending**. The evidence now points clearly at one candidate, but a pointer
is not a proof.

---

## 5. Next

Attempt to **create** a virtual device through the `uinput` helper and watch for it to appear in the
device inventory. The harness's hot-plug listener already logs devices appearing and disappearing, so
a creation that succeeds even briefly will be visible.

One structural detail matters for the attempt: the helper reads its description from standard input
and tears the device down when that input closes. A one-shot invocation would create and immediately
destroy the device. Holding the input open keeps it alive, which is also how a production
implementation would have to work — the device lives exactly as long as the process that owns it.
