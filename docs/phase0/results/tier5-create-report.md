# Phase 0 — A Virtual Controller Was Created

**Document:** `docs/phase0/results/tier5-create-report.md`  
**Status:** Milestone — creation succeeded; the device did not persist and was not characterised  
**Evidence:** `docs/phase0/results/tier5-create-20260817-redmi-note-13-5g.json`  

---

## 1. The result

```text
0036  CREATE ATTEMPT [numeric schema] — holding device open for 5s
0037  DEVICE ADDED   id=9  Kestrel Virtual Controller
0038  DEVICE REMOVED id=9
0040  CREATE ATTEMPT [named schema]
0041  DEVICE ADDED   id=10 Kestrel Virtual Controller
0042  DEVICE REMOVED id=10
```

Repeated on a second run, producing ids 11 and 12.

**A device of the project's own making was registered with the system's input stack on a stock,
unrooted phone**, named as specified, and observed by an ordinary application through the standard
hot-plug callback. This is the prerequisite for Grade A in `docs/PHASE-0.md` §28 — the one thing the
shell-injection path can never provide, because injected events always arrive from the system's own
virtual device with no identity of their own.

Both descriptor schemas were accepted, so the helper tolerates numeric and named forms alike. The
helper printed nothing and exited zero in both cases.

## 2. What is still missing, and why the run cannot claim Grade A

**The device did not stay.** Each `DEVICE ADDED` was followed immediately by `DEVICE REMOVED`,
inside the five-second window the invocation was supposed to hold open. The device count before and
after each attempt was 8 and 8: by the time the inventory was sampled, the device was already gone.

**Nothing about the device was captured.** The hot-plug callback recorded only the name. Its
sources, axes and buttons — the properties that decide whether it is a controller or merely a
device — were never read, because the only code that reads them runs against the *current*
inventory, and the device had vanished by then.

So the honest position is:

| Question | Answer |
| --- | --- |
| Can Kestrel create an input device the system accepts? | **Yes** |
| Does it appear to ordinary applications? | **Yes**, via the standard hot-plug callback |
| Does it advertise controller sources and axes? | **Unknown** — never read |
| Does it persist? | **No**, not as invoked here |
| Can it deliver events? | Untested |

A device that exists for a moment and is never characterised is a strong signal, not a pass.
`ADR-INPUT-001` remains **Pending**.

## 3. Why it probably did not persist

The invocation was `(echo '<descriptor>'; sleep 5) | uinput -`. The intent was that the pipe stay
open for five seconds and the device live that long.

Two candidate explanations, neither yet tested:

1. **The helper finished its work and exited** rather than waiting for further commands, destroying
   the device with it. The helper is designed to read a stream of commands — `register`, then
   further instructions — and may treat a lone `register` as a complete script.
2. **The system removed it** because the descriptor, while accepted by the helper, described a
   device the input stack classified as uninteresting and dropped.

These are distinguishable: if the helper is still running, the first is wrong. The next run should
therefore keep the helper alive as a background process rather than tying it to the lifetime of a
single command, and should check whether that process still exists.

This also settles a design question for the eventual implementation. The device lives exactly as
long as the process holding the file descriptor. A production backend cannot create a controller and
walk away — it must own a long-lived process for the duration of a session, and losing that process
means losing the controller mid-session. That is a strong argument for a foreground service, and it
must be reflected in `ARCHITECTURE.md` when the input backend is designed.

## 4. Changes made in response

- **Device properties are now captured inside the hot-plug callback**, at the instant the device
  appears, and kept in the export. A device that lives for 200 milliseconds is now fully described.
  This was the flaw that made this run inconclusive rather than decisive.
- **The helper is started as a background process** holding the device for 30 seconds, so it can be
  inspected in the inventory rather than being gone before the tool returns.
- **A destroy action** stops any running helper, so a device is never left behind.
- **A liveness check** reports whether the helper process still exists, which distinguishes the two
  explanations above.

## 5. Follow-up run, and a self-inflicted failure

The next run produced nothing, for a reason internal to the harness rather than to the device.

The invocation had been wrapped in a second shell layer; the descriptor contains double quotes, so
the shell broke apart inside the device name and the helper never ran. The device said so:
`Virtual: no closing quote`. Worse, the liveness check matched any command line containing the word
"uinput", which matched the failing shell itself, so the harness reported the helper as alive while
nothing ran.

Both are fixed: the descriptor is written to a file using only single quotes, no shell is nested,
and liveness matches the process name exactly. Recorded here because an evidence trail that omits
why a run produced nothing is not an evidence trail.

Note the ordering: the earlier run, with simpler quoting, **did** create devices. The mechanism is
not in doubt; the tooling around it was.

## 6. Next

1. Create the device again and read its captured properties. If they show `GAMEPAD`/`JOYSTICK`
   sources with axes and buttons, this becomes a Grade A candidate on evidence rather than on
   inference.
2. If it persists, exercise it: send events through the helper and confirm they arrive attributed to
   the new device id rather than to `dev=-1`. That attribution is the whole difference between
   Grade A and Grade B.
3. Only then is a target application worth testing.
