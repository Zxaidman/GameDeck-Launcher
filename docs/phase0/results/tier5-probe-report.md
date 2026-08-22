# Phase 0 — Tier 5 Privilege Probe

**Document:** `docs/phase0/results/tier5-probe-report.md`  
**Status:** Complete — permissions established, actual access not yet attempted  
**Evidence:** `docs/phase0/results/tier5-probe-20260817-redmi-note-13-5g.json`  

---

## 1. The privilege chain works

| Fact | Result |
| --- | --- |
| Shizuku service running | yes |
| Permission granted to the harness | yes |
| Identity actually obtained | **`shell`, uid 2000** |
| Shizuku version | 13 |

The four facts `ARCHITECTURE.md` §14 insists on separating were each measured separately, and on this
device all four line up. The privileged service bound, started, and executed commands. Root was not
obtained and was never expected.

---

## 2. The virtual-input device node is reachable — on paper

```text
crw-rw---- 1 system net_bt_admin u:object_r:uhid_device:s0  10, 223  /dev/uinput
```

The node is owned by `system`, its group is `net_bt_admin` (gid 3001), and the group has read and
write permission.

The shell identity's group list:

```text
2000(shell) 1004(input) 1007(log) 1011(adb) 1015(sdcard_rw) 1028(sdcard_r)
1078(ext_data_rw) 1079(ext_obb_rw) 3001(net_bt_admin) 3002(net_bt) 3003(inet)
3006(net_bw_stats) 3009(readproc) 3011(uhid) 3012(readtracefs)
```

**`shell` is a member of `net_bt_admin`.** So the group permission bits apply, and the kernel's
ordinary permission check should allow opening the node for writing. Both explicit tests agreed:

```text
test -r /dev/uinput  ->  READABLE
test -w /dev/uinput  ->  WRITABLE
```

Also present: `/system/bin/uinput`, the helper that creates virtual devices from a description.

Worth noting: `shell` is also in `uhid` (3011), and the node carries the SELinux label
`uhid_device`.

### Why this is not yet a yes

`test -r` and `test -w` call `access(2)`, which consults **only** the classic owner/group/other
permission bits. It does not consult SELinux.

SELinux is **Enforcing** on this device. The node is labelled `u:object_r:uhid_device:s0` and the
shell identity runs in `u:r:shell:s0`. Whether policy permits that domain to open that file is a
separate question, decided at `open(2)`, and **no open has been attempted**.

So the honest statement is: *the ordinary permission check passes, and the remaining question is
whether policy allows it.* Anyone reporting this as "we can create a virtual controller" would be
overstating the evidence by exactly one step — and it is the step that most often fails on a modern
device.

---

## 3. The unexpected second path

The probe captured the shell `input` command's own usage text, which lists the sources it accepts:

```text
touchnavigation  touchscreen  joystick  stylus  touchpad
gamepad  dpad  mouse  keyboard  trackball  rotaryencoder
```

and an option for motion axes:

```text
--axis <axis_name>,<axis_value>
   where <axis_name> is the name of the axis as defined in
   MotionEvent without the AXIS_ prefix (e.g. SCROLL, X)
```

This matters more than it looks. The original Tier 3 plan assumed shell injection could only produce
keyboard-style key events — which `docs/PHASE-0.md` §28 grades as C, insufficient on its own. But
this build advertises **`gamepad` and `joystick` as injection sources, and named axis values**.

If those work as advertised, a shell-privileged process could deliver events carrying controller
semantics *and* continuous axis values, without creating a virtual device at all. That is plausibly
Grade B territory: correct semantics, no persistent device identity.

Two viable candidate paths now exist, both reachable from the phone alone through Shizuku:

| Path | Ceiling | Blocking question |
| --- | --- | --- |
| **A** — create a virtual device via `uinput` | Grade A: a real device identity | Does SELinux policy permit the open? |
| **B** — inject with `input gamepad` / `--axis` | Grade B: correct semantics, no identity | Do the events arrive with `GAMEPAD`/`JOYSTICK` source and real axis values? |

Path B was not previously believed to be available. It is now the cheaper of the two to test and it
may be sufficient for the product's acceptance criteria.

---

## 4. What is settled and what is not

**Settled.** Shizuku yields the shell identity on this device; that identity holds group membership
granting ordinary write permission to the virtual-input node; the `uinput` helper binary exists; and
the `input` command advertises controller sources and axis values.

**Not settled.** Nothing has been opened, created, or injected. No event has been produced by
Kestrel. No evidence grade applies, and `ADR-INPUT-001` remains **Pending**.

The single most likely way this fails from here is an SELinux denial on the actual open, which the
permission bits give no warning of.

---

## 5. Next step

Two concrete tests, both now runnable from the phone:

1. **Actually open the node for writing** and report success or the exact failure. This converts
   "permitted on paper" into a fact either way.
2. **Inject through `input` using the `gamepad` and `joystick` sources with `--axis`** and read what
   the harness's own event log captured — checking the delivered `src=`, which is the whole question
   per the Tier 1 findings.

The harness records the command issued into the same event log as the events that follow, so the
stimulus and the response are interleaved in one record and cannot be confused for one another.
