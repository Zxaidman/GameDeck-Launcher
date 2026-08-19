# Kestrel — Degraded State Contract

**Document:** `docs/DEGRADED_STATE.md`  
**Status:** Active — decided, not yet implemented  
**Authoritative for:** what the user sees and can do when the preferred input backend is
unavailable, and what happens when it goes away mid-session  

---

## 1. Why this document exists

Kestrel's preferred backend needs Shizuku, and Shizuku stops on every reboot. This is not an edge
case affecting a minority of users — **it is the normal state of every user's phone after every
restart**, including users who have Shizuku installed and working.

So the reduced state is not an error path bolted on at the end. It is a state the product spends
real time in, and the decisions below are made once, here, so that every screen answers them the
same way.

The rule underneath all of them: **the user always knows what they have, before they need it.**

---

## 2. Capability states

Four, and they are separate facts rather than points on a line. `ARCHITECTURE.md` §14 already
requires Shizuku's state to be reported as separate facts for the same reason: none of them implies
another.

| State | Meaning | What works |
| --- | --- | --- |
| **Full** | Preferred backend active — virtual controller open | Everything |
| **Ready** | Preferred backend available but no session open | Everything, once started |
| **Reduced** | Preferred backend unavailable; fallback available | Fallback capabilities only (`ADR-006`) |
| **Configure only** | No input backend available | Everything except playing |

**Configure only is a fully usable state, not a locked door.** The launcher opens, targets are
detected and can be added by hand, layouts and skins can be created and edited, settings are
available. The one thing that cannot happen is a session.

That is the decision: **the application never refuses to start, and never hides its own features,
because input is unavailable.**

---

## 3. On the home screen

The current state is shown on the home screen, permanently, not behind a menu.

In **Full** or **Ready**, it is a quiet confirmation. In **Reduced** or **Configure only**, it is a
prominent banner that says three things:

1. what is unavailable,
2. what to do about it, in one action,
3. what still works.

The third is not optional politeness. A user told only that something is broken assumes everything
is.

Example wording for Configure only, as a shape rather than final copy:

> **Controller unavailable — Shizuku is not running.**
> Start Shizuku to use a controller. You can still browse, add, and configure everything here.
> `[ Open Shizuku ]  [ How this works ]`

### On recommending a Shizuku build

Some Shizuku builds keep themselves running better than others across reboots and battery
management. Where a distribution is known to survive better on a given firmware, Kestrel may say so
— as a **recommendation with its reason stated**, never as a requirement, and never presented as
official. Kestrel does not require any particular build, and a user on any of them gets the same
product.

**Kestrel cannot keep Shizuku alive.** It is a separate application; its lifetime is not ours to
manage, and no permission we hold changes that. Anything the product says about this must be honest
about where the limit is.

---

## 4. When the backend goes away mid-session

This is the case that most needs to be handled well, because it happens while the user is playing.

When the privileged service stops, the lease stops being renewed and the watchdog closes the
controller within about fifteen seconds (`docs/phase0/results/tier5-session-report.md`). From the
user's side, a controller simply stops working.

The contract:

1. **Say it immediately**, in the session notification and on screen if Kestrel is visible. Never
   let a controller stop silently.
2. **Name the cause** — Shizuku stopped — rather than reporting a generic failure.
3. **Offer one action to recover.** A single re-arm control that starts a new session once the
   privileged service is back, without the user reconstructing anything.
4. **Keep the session's configuration.** The controller is gone; the layout, profile and target
   selection are not, and the user must not have to set them up again.
5. **Never fall back automatically.** Dropping to simulated touch without being told is worse than
   stopping: the user keeps playing, the input behaves differently, and the product looks broken
   rather than degraded. Offer it; do not assume it.

---

## 5. Before launching a target

Every target in the launcher shows what it is expected to do **in the current state**, before it is
opened.

Expectation is derived from the compatibility record (`docs/COMPATIBILITY.md`) plus the active
capability state, and is shown as one of:

- **Should work** — the combination is recorded as working
- **Limited** — recorded as working with named restrictions, which are shown
- **Not expected to work** — recorded as failing, or requires capabilities the current state lacks
- **Untested** — no evidence either way

**Untested is displayed as Untested**, never optimistically rounded up. It is the honest answer for
most combinations and the user is better served by knowing that than by a guess dressed as a claim.

A target is never blocked on this basis. The user may launch anything; they simply are not
surprised by it.

---

## 6. Failure and success are always stated

A session start either succeeds visibly or fails with a reason. There is no third outcome where
something silently does not happen.

- **Success**: the session notification appears and the controller is confirmed present.
- **Failure**: a message naming what failed and what to do next.

Specifically prohibited, because each of these was observed to be actively misleading during Phase 0
and cost real time to diagnose:

- reporting success from a check that cannot detect failure,
- reporting a state that was never verified,
- doing nothing at all in response to a control being pressed.

---

## 7. What this contract binds

- `feature/launcher` — home screen state display, per-target expectation, the recommendation.
- `feature/gaming-session` — mid-session loss, re-arm, configuration retention.
- `feature/controller-editor` — disabled controls per `ADR-007`, and saying so at the point of
  placement.
- `core/input` — capability as a domain concept, computed rather than assumed.
- `platform/shizuku` — the four separate facts, never collapsed into one boolean.

Nothing in this document is implemented yet. It is a decision, recorded before the code exists so
the code can be built to it.
