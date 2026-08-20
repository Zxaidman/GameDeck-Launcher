# Kestrel — Fallback Probe

**Document:** `docs/FALLBACK_PROBE.md`  
**Status:** Active — procedure for the experiment that decides `ADR-006`  
**Subject:** `ADR-006` — touch fallback via an accessibility service and an overlay,
**Accepted as direction, untested**  

---

## 1. Why this exists before the backend does

`ADR-006` chose a direction without evidence, and said so. Every document downstream of it assumes
that direction works: `docs/DEGRADED_STATE.md` describes what a user without Shizuku is told and
offered, and `ADR-007` promises **one layout across capability tiers** with unavailable controls
disabled rather than removed. If touch simulation turns out not to be viable, all three need
rewriting — and finding that out after a launcher, a layout editor and a skin system have been built
on top of them is the mistake Phase 0 existed to prevent.

So this is a probe, not a backend. It is the smallest thing that can produce evidence, it is
self-contained, and no product code refers to it.

## 2. What it can and cannot decide

**It cannot make Kestrel work without Shizuku.** Nothing in it creates an input device. A target
that reads only controller input will see nothing from it, however well it measures. Saying
otherwise would be the exact confusion `CLAUDE.md` §5 warns about — touch simulation, key-event
injection, motion injection and virtual HID identity are four different capabilities.

What a good result would mean, precisely: **a target's own on-screen touch controls can be driven by
Kestrel's layout.** That is a real product for emulators that draw their own touch pad, and it is
nothing at all for a target that does not.

## 3. The four questions

| # | Question | How it is answered |
| --- | --- | --- |
| 1 | Can the service be enabled without sending the user hunting through settings? | Three routes, reported separately — see §4 |
| 2 | How long does an injected touch take to arrive? | A tap is aimed at a window Kestrel owns and the time from asking to landing is measured, twelve times |
| 3 | How finely can a movement be drawn? | One drag is dispatched and the movements it produces are counted |
| 4 | Does any of it work while Kestrel's overlay is up? | The measurement target **is** an overlay window, so every number above is already taken under that condition |

Question 3 is the one most likely to decide the answer. A stick is continuous; a drag that arrives
as a handful of points cannot simulate one however low its latency is. The number to look at is
**movements per second**, which the screen prints directly.

## 4. The three ways in

Reported apart, because they are different promises to a user.

- **Enable via Shizuku** — writes the platform's accessibility list through the privileged shell.
  Expected to work; requires Shizuku running at that moment.
- **Grant permission** — grants Kestrel `WRITE_SECURE_SETTINGS` once, through the shell. **This is
  the interesting one.** A grant that survives means every later enable can be done by Kestrel
  itself, with Shizuku not running at all, which is what a fallback for a user without Shizuku
  actually needs.
- **Enable without shell** — writes the list using Kestrel's own permission, with no shell involved.
  This is the test of whether the grant above really bought anything.

Both writes **append** to the list rather than replacing it. The setting is shared, and writing only
Kestrel into it would silently switch off every accessibility service the user depends on. A
diagnostic that does that is not acceptable regardless of what it measures.

## 4a. Two faults in the probe itself, found on its first run

Recorded because both are the kind that recur, and because a probe that reports a fault of its own
as a fault of the thing it measures is worse than no probe.

**`pm grant` succeeded and granted nothing.** `WRITE_SECURE_SETTINGS` is
`signature|privileged|development`, and the `development` flag is what lets a shell hand it over —
**but only to an application that has asked for it.** Kestrel had never declared it, so the grant
had nothing to act on, exited zero, printed nothing, and the permission stayed absent. It is
declared in the manifest now. Declaring it costs nothing while it is ungranted: the platform does
not give it to an ordinary installation.

**A value formatted for a person was parsed as data.** The enable route read the current
accessibility list into Kotlin, edited it and wrote it back — and `exec` returns human-readable
text, so an empty setting came back as the literal string `(no output, exit=0)`, which was written
into the next command and produced `sh: syntax error: unexpected '('`.

The fix is not to parse that string more carefully. **The shell now reads, decides and writes
without the value ever crossing back into the application**, which removes the whole class of fault
rather than this instance of it. The same script shape is used to disable.

Both scripts were exercised against a stand-in `settings` before shipping, over the cases that
matter: an empty list, a repeat run that must not duplicate, a list that already contains somebody
else's service, removing Kestrel from the middle of three, removing it when it is the only one, and
removing it when it is already absent. The other services survive every one.

## 5. Procedure

Nothing here needs a target application. Everything is measured against Kestrel's own window.

1. Install the build. Open Kestrel. Bind Shizuku as usual.
2. Grant the draw-over-other-apps permission if it has not been granted — the measurement aims a
   touch at an overlay window.
3. In **Fallback probe**, press **Grant permission**. It now reports the **platform's** answer as
   well as Kestrel's, from `dumpsys`. If the platform lists the permission as granted and Kestrel
   still says *not held*, close Kestrel from the recent list and open it again — a permission
   granted to a running process can be cached — then check the line again.
4. Press **Enable via Shizuku**. Check the two status lines: *in the setting list* and *service
   connected*. Both should become yes within a second or two.
5. Press **Measure**. A box appears in the middle of the screen. **Do not touch it** — the point is
   that Kestrel touches it. It disappears when the run ends.
6. Read the result: how many landed, best/median/worst latency, and the drag's movements per second.
7. Press **Disable**. Then **stop Shizuku entirely** and press **Enable without shell**. This is
   question 1's real test — whether the fallback can be turned on with no privilege available at
   that moment.
8. With Shizuku still stopped, press **Measure** again. Compare.
9. Save the report and push the JSON.

### Then, once, with a target

The probe cannot tell whether an emulator reacts. That needs a person:

10. Open an emulator that draws its own on-screen controls. Note whether Kestrel's overlay and the
    emulator's own controls can both be touched.
11. Record what happens — this is an observation, not a measurement, and is recorded as such.

## 6. What to record

`docs/COMPATIBILITY.md` gets device, firmware, Kestrel version and result. `ADR-006` gets its status
changed from *untested* to whatever the evidence supports, with the numbers quoted rather than
summarised. If the answer is that the direction does not work, that is a result and it is recorded
as one — `CHANGELOG.md` says a failed experiment is still valuable documentation.

## 6a. First results — reference device, 2026-08-20

Redmi Note 13 5G, HyperOS 3.0.3, Android 15, Kestrel `0.0.16-dev`.

**The permission grant works.** `pm grant` through the Shizuku shell succeeded, `dumpsys` reports
`android.permission.WRITE_SECURE_SETTINGS: granted=true`, and Kestrel reads it as held in the same
session with no restart. Question 1's middle route — a one-time grant that lets Kestrel write the
setting itself later — is **confirmed on this device**.

**Writing the accessibility list does not take, by either route.**

- Through the shell: the script's own read-back reported `list is now: null`. `settings put` exited
  without complaint and wrote nothing that survived. Nothing appeared on stderr, which `exec`
  captures.
- Through the app's own permission, with the grant held: "Written without a shell" — and the list
  still reads empty. `Settings.Secure.putString` succeeded and had no effect.

Both routes therefore report success and neither changes anything, which is the failure mode that
looks most like working code. It is not Kestrel's script: the same script was exercised against a
stand-in `settings` over six cases before shipping, and a value that failed to write would read back
as empty rather than as `null` — `null` means the key is absent, so the write was reverted or
refused rather than mis-formed.

Two candidate causes, and the manual route distinguishes them. **Neither is confirmed.**

- **Restricted settings.** Android 13 introduced a block on enabling an accessibility service for an
  application installed from outside a store, lifted only by the user through App info → Allow
  restricted settings. If this is it, the manual route shows a disabled toggle and says so.
- **OEM policy.** Xiaomi's layer is reported to protect this particular setting against shell
  writes. If this is it, the manual route works normally and only the programmatic routes fail.

**Nothing was measured**, because the service never connected. Latency, drag resolution and
behaviour under Kestrel's overlay all remain unknown.

### And a finding that is not about the fallback at all

**Declaring the accessibility service changed how Kestrel installs.** Up to and including
`0.0.14-dev`, sideloading produced the ordinary unknown-source warning and proceeded. `0.0.15-dev`,
which added the service and nothing else relevant, is **blocked by Play Protect**: *"App blocked to
protect your device — this app can request access to sensitive data."* Installing now requires the
user to turn that protection off.

The attribution is clean, because `0.0.15-dev` added the service declaration and `0.0.16-dev` added
the permission declaration, and the block began with the former.

This is the cost `ARCHITECTURE.md` §16 asked to be evaluated, arriving as a measurement rather than
a prediction, and it is paid by **every** user — including every user who never enables the fallback
— because a manifest declaration is visible at install time whether or not the code ever runs.

## 7. State of each claim

| Claim | State |
| --- | --- |
| `WRITE_SECURE_SETTINGS` can be granted through the shell | **Measured — yes**, reference device |
| A granted permission is visible to Kestrel without a restart | **Measured — yes** |
| The accessibility list can be written through the shell | **Measured — no**, silently ineffective |
| The accessibility list can be written with the granted permission | **Measured — no**, silently ineffective |
| Why the writes do not take | **Open** — restricted settings, or OEM policy |
| The service can be enabled by hand | **Untested** — the one test that distinguishes the two |
| The service can dispatch a gesture at all | **Unverified** — it never connected |
| Injected touch latency | **Unmeasured** |
| A drag delivers enough points to simulate a stick | **Unmeasured** |
| Declaring the service triggers a Play Protect install block | **Measured — yes**, from `0.0.15-dev` |
| Any of this drives a target that reads only controller input | **False** — nothing here makes a device |

## 8. The ceiling, which no measurement can raise

Worth stating plainly next to the results, because it bounds what a good outcome could ever have
been.

Without a privileged shell there is **no way for an application to deliver controller input to
another application**. Creating a device needs shell or root; `injectInputEvent` needs a signature
permission; an accessibility service dispatches **touches** and nothing else. That is not a gap in
this probe — it is the shape of the platform.

So the best imaginable result here was never "Kestrel works without Shizuku". It was: *Kestrel's
layout can press an emulator's own on-screen buttons* — a puppet layer over controls the target
already draws, requiring those controls to be visible and to stay where they are. A stick would be a
drag aimed at the emulator's stick; a d-pad, taps on its d-pad. Whether that is worth having is a
product judgement, and it is a different product from the one `ADR-INPUT-001` delivers.
