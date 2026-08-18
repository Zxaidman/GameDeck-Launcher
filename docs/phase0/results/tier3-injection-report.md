# Phase 0 — Shell Injection Result

**Document:** `docs/phase0/results/tier3-injection-report.md`  
**Status:** Complete — a working input path is established, with a serious caveat  
**Evidence:** `docs/phase0/results/tier3-injection-20260817-redmi-note-13-5g.json`  

---

## 1. Result: controller-grade input works

A shell-privileged process, obtained through Shizuku with no computer attached, delivered input that
arrived carrying **controller semantics**, not keyboard emulation.

| Attempt | Command | Delivered |
| --- | --- | --- |
| A button | `input gamepad keyevent 96` | `KEYCODE_BUTTON_A` DOWN/UP, **`src=GAMEPAD`** |
| D-pad up | `input dpad keyevent 19` | `KEYCODE_DPAD_UP`, **`src=DPAD`** |
| Analog stick | `input joystick motionevent MOVE 0.6 0` | `MOTION X=0.600`, **`src=JOYSTICK`** |
| Analog, other syntax | `input joystick motionevent MOVE --axis X,0.6` | rejected — `IllegalArgumentException` |

The `--axis name,value` form advertised in the tool's own usage text was **not** accepted in this
position. Positional coordinates worked. The usage text is misleading on this build; the working
syntax was found by trying both, which is why both were attempted.

Against the Tier 1 calibration reference this is a genuine result. `src=GAMEPAD` and `src=JOYSTICK`
are the same source flags a real controller produced, and the axis value arrived as a real
continuous float rather than a synthesised key.

**Provisional grade: B** in the scale at `docs/PHASE-0.md` §28 — system-level delivery with correct
controller semantics, but **no persistent device identity**. Every injected event arrived from
`dev=-1`, the system's own virtual device. No new device appeared in the inventory, and the device
count stayed at eight throughout.

That distinction is not academic. A target application that filters by device, or enumerates
attached controllers before enabling controller support, may ignore input that arrives from the
system virtual device. Whether real targets accept it is Tier 6 and is untested.

---

## 2. The serious caveat: an injected axis never returns

This is the most important finding of the run, and it was found by accident.

After `input joystick motionevent MOVE 0.6 0`, the axis **stayed at 0.6**. The system treated the
stick as held to the right and began emitting `KEYCODE_DPAD_RIGHT` repeats continuously:

```text
0015  KEY  KEYCODE_DPAD_RIGHT DOWN repeat=1    dev=-1 src=JOYSTICK
...
0358  KEY  KEYCODE_DPAD_RIGHT DOWN repeat=363  dev=-1 src=JOYSTICK
```

Over 360 repeats, and **still going when the report was exported**. The A-button injection issued
partway through arrived interleaved with the flood, so the stuck axis did not block other input — it
simply never stopped.

Nothing releases it implicitly. There is no paired "up" for a motion event, and the process that
issued it had long since exited. The state lives in the input system, not in the caller.

This is precisely the hazard `ARCHITECTURE.md` §26–§27 and `docs/INPUT_BACKENDS.md` warn about, now
demonstrated rather than anticipated:

> Every backend must release active buttons, reset active axes, stop privileged services, and clean
> up resources when a session ends or becomes invalid. A backend that can leave stuck input is not
> production-ready.

### What this forces on the design

1. **Every axis write needs a matching return-to-centre**, issued by the same component that set it,
   and issued even when the session ends abnormally.
2. **The input engine must track every axis it has moved**, not merely the buttons it has pressed.
   `ARCHITECTURE.md` §27's `activeAxes` is not optional bookkeeping — without it there is no way to
   know what to release.
3. **A crash or a kill is the dangerous case.** The phone was left spamming a direction by a process
   that no longer existed. A production backend must therefore also release on process death, not
   only on an orderly session end.
4. Any watchdog must be able to recentre without knowing what was set, so a blanket "all axes to
   zero" operation has to exist.

The harness now sends an automatic release 1.2 seconds after any axis injection, and carries a
manual **RELEASE ALL** button as an escape hatch.

---

## 3. Also confirmed: the fallback keys, again

The A-button injection delivered `KEYCODE_BUTTON_A` **and** `KEYCODE_DPAD_CENTER`, on the same
stimulus. This is the same duplicate delivery recorded in the Tier 1 calibration, and it now
reproduces through injection as well as through a real controller. The rule stands: match on the
controller keycode and discard the fallback.

---

## 4. What is settled, and what is not

**Settled.** A path exists. Shell privilege via Shizuku, with no computer, delivers digital
controller input and continuous analog axis values with correct source semantics, on this device and
firmware. Provisional Grade B.

**Not settled.**

- **No device identity.** Nothing Kestrel creates appears as a controller in the device list.
- **Tier 5 unanswered.** The virtual-device path could reach Grade A, but the open-for-write result
  was lost: each action overwrote the probe pane before the report was exported. The harness now
  accumulates output instead. This must be re-run.
- **No target application tested.** Whether an emulator or streaming client accepts input from the
  system virtual device is the question that decides whether Grade B is sufficient. Untested.
- **Trigger axes, simultaneous input, hold duration, and repeatability** are all untested.

`ADR-INPUT-001` stays **Pending**. A provisional grade on one device, from one run, with the
identity question open and no target application involved, is not the reproducible evidence the
record requires.

---

## 5. Next

1. Re-run the probe on 0.0.5 and read **ACTUAL OPEN for write** — it survives now. That decides
   whether Grade A is reachable.
2. Test a real target application with Grade B input. If a target accepts it, Grade B may be enough
   and the project can proceed without a virtual device.
3. Exercise triggers and simultaneous input, which the acceptance criteria in `docs/PHASE-0.md` §29
   require and which nothing has touched yet.
