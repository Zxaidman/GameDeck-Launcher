# Phase 0 — Tier 0 Baseline Report

**Document:** `docs/phase0/results/tier0-report.md`  
**Status:** Complete — baseline recorded, no injection tier attempted yet  
**Evidence:** `docs/phase0/results/tier0-20260817-redmi-note-13-5g.json`  

---

## 1. Environment

| Property | Value |
| --- | --- |
| Device | Redmi 2312DRAABI (Redmi Note 13 5G), codename `gold` |
| SoC | `mt6833` — MediaTek Dimensity 6080 |
| Android | 15, API 35 |
| Firmware | `OS3.0.3.0.VNQINXM` (HyperOS 3.0.3) |
| Security patch | 2026-06-01 |
| Fingerprint | `Redmi/gold_in_global/gold:15/AP3A.240905.015.A2/OS3.0.3.0.VNQINXM:user/release-keys` |
| Harness | `phase0-0.0.1` |
| Physical controller attached | None |

---

## 2. Result

Tier 0 is complete. Eight input devices were reported. **None of them is a usable controller**, which
is the expected baseline for a phone with nothing attached.

| id | Name | Sources | Axes | Gamepad keys | Notes |
| ---: | --- | --- | ---: | ---: | --- |
| -1 | `Virtual` | KEYBOARD, DPAD | 0 | 4 D-pad | System virtual device; delivers the back gesture |
| 0 | `uinput-goodix` | KEYBOARD, **GAMEPAD** | 0 | **0** | Advertises the gamepad source but has no buttons and no axes |
| 2 | `mtk-kpd` | KEYBOARD | 0 | 0 | Physical key matrix — delivers volume down |
| 3 | `mtk-pmic-keys` | KEYBOARD | 0 | 0 | Power-management keys — delivers volume up |
| 4 | `mt6833-mt6359 Headset Jack` | KEYBOARD | 0 | 0 | Headset media buttons |
| 5 | `swtp` | KEYBOARD | 0 | 0 | Vendor device |
| 6 | `goodix_ts` | KEYBOARD, TOUCHSCREEN | 8 | 0 | The touchscreen; the only device reporting real axes |
| 7 | `uinput_nav` | KEYBOARD | 0 | 4 D-pad | Vendor virtual device, marked external |

---

## 3. The finding that matters

**Two of the eight devices were created through `uinput`, by the vendor, on a stock unrooted phone.**

`uinput-goodix` (id 0) and `uinput_nav` (id 7) share vendor `0x666` and product `0x888` — a
vendor-assigned pair, not real USB identifiers. Their names say plainly how they were made.

This is the first real evidence bearing on Tier 5, which is the only tier that could produce a
Grade A result. It establishes that the kernel's virtual-input facility is present and actively used
on this hardware and firmware. Kestrel's preferred architecture depends on something equivalent
being reachable.

It proves the mechanism **exists**. It does **not** prove Kestrel can use it:

- These devices were created by vendor components, which run with privileges an ordinary
  application does not have.
- Nothing here shows whether `/dev/uinput` is writable from the shell UID that Shizuku would
  provide. That is exactly what Tier 5 must determine, and it remains untested.

Encouraging, not conclusive. It raises the value of running Tier 5 before the easier tiers.

## 4. The finding that would have misled us

`uinput-goodix` reports `SOURCE_GAMEPAD`. A naive capability check — "does any device advertise the
gamepad source?" — would answer **yes** on this phone and conclude a controller is present.

It advertises **zero** gamepad buttons and **zero** axes. It cannot deliver controller input of any
kind.

This is the concrete justification for a rule already written into `docs/INPUT_BACKENDS.md` and
`CLAUDE.md`: capability must be determined from what a device actually advertises — keys and axes —
never from a source flag or a name. The harness records all three precisely so this class of false
positive is visible rather than assumed away. It cost nothing to catch here; it would have been
expensive to discover after a backend had been built on the assumption.

---

## 5. Event evidence

Physical keys were exercised. No controller was available, so this is not a calibration reference —
it only confirms the instrument records what it should.

```text
KEYCODE_VOLUME_DOWN  DOWN/UP   dev=2  src=KEYBOARD  scan=114
KEYCODE_VOLUME_UP    DOWN/UP   dev=3  src=KEYBOARD  scan=115
KEYCODE_BACK         DOWN/UP   dev=-1 src=KEYBOARD  scan=0
```

Three observations:

1. **The volume keys come from two different devices** — down from `mtk-kpd`, up from
   `mtk-pmic-keys`. They are wired to separate hardware blocks. A backend must never assume one
   device covers a logical group of controls.
2. **Every event arrived as `KEYBOARD`.** Nothing on this phone delivers `GAMEPAD` semantics
   unprompted.
3. **The back gesture arrives through the virtual device** (id -1), not a physical one.

Lifecycle notes were recorded across two pause/resume cycles, so Test 13 has a partial result: the
harness survives backgrounding and correctly re-registers its device listener.

---

## 6. What this does and does not settle

**Settled:** the baseline inventory (Test 1) for this device and firmware. The instrument works and
its output is trustworthy.

**Not settled:** everything about injection. No tier from 2 to 6 has been attempted. No evidence
grade applies, because a grade describes an input mechanism and no mechanism has been exercised.
`ADR-INPUT-001` remains **Pending**.

**Missing:** a calibration reference. Without a physical controller having been attached once, there
is no observed example of what a genuine controller looks like on this device — which sources, which
axes, which buttons. Any later claim of success would have nothing to be compared against. This is
worth doing before Tier 3, not after.

---

## 7. Recommended next step

Run **Tier 5 before Tiers 3 and 4**, contrary to the original numbering.

The reasoning: Tier 5 is the only path to a controller with its own identity, this report shows the
underlying mechanism is present and in use on the device, and a single command establishes whether
it is reachable at all:

```bash
adb shell ls -l /dev/uinput
adb shell uinput -h
```

If the shell UID cannot reach it, that closes the highest-value question immediately and the
remaining tiers can be run knowing the ceiling. If it can, that is the most important result Phase 0
could produce, and everything else becomes secondary.

Nothing in `docs/COMPATIBILITY.md` changes as a result of this report. A baseline inventory is not a
support status.
