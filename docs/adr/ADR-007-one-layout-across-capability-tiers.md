# ADR-007: One Layout Across Capability Tiers

## Status

**Accepted.**

## Context

Kestrel will have at least two input backends with materially different capabilities: a virtual
controller with sticks, analog triggers and simultaneous input (`ADR-INPUT-001`), and a touch
fallback with none of those (`ADR-006`). A backend can also disappear mid-session when Shizuku
stops.

That raises a question a layout editor cannot avoid: does a user maintain one layout, or one per
capability tier?

Two ways to get it wrong. Keeping separate layouts per tier doubles the editing work, splits the
user's effort across configurations they mostly cannot tell apart, and makes sharing a layout with
someone else ambiguous — whose tier was it built for? Silently reinterpreting one layout for a
lesser backend is worse: a stick quietly becoming a four-way pad is a change to how the thing plays,
made without saying so.

## Decision

**One layout, across every capability tier.**

A layout declares the controls it wants. At session start the active backend reports what it can
provide, and controls the backend cannot deliver are **shown as disabled** rather than removed,
substituted, or silently reinterpreted.

Three rules follow, and they are the substance of the decision:

1. **Never remove.** A disabled control keeps its place in the layout. The user sees the same
   arrangement they designed, with the unavailable parts visibly inert. A control that vanishes
   reads as data loss.
2. **Never substitute.** A stick does not become a d-pad because the backend cannot do analog. If a
   user wants that, they make it a d-pad themselves; the product does not decide it for them.
3. **Never fail silently.** Disabled controls are stated before the session starts, not discovered
   by pressing something that does nothing (`docs/DEGRADED_STATE.md`).

Editing is unrestricted. A user on a limited backend may still place a stick — the layout is a
description of intent, not a description of what today's phone can do. It will be disabled until a
backend that supports it is active, and the editor says so at the point of placement.

## Consequences

- A control in the configuration schema carries the **capability it requires**, so availability is
  computed rather than guessed. `docs/CONFIGURATION_SCHEMA.md` gains that field, with the versioning
  and migration rules that document already requires.
- Capability is a first-class domain concept in `core/input/`, not a platform detail: pure Kotlin,
  unit-testable, with no Android types in it.
- Layout validity never depends on the current backend. A layout is valid or invalid on its own
  terms, and separately, some of it may be unavailable right now. Conflating those would make an
  imported layout look corrupt on a phone that merely lacks a capability.
- A layout shared between users stays meaningful. It describes what the author intended, and each
  recipient's phone determines what of it is live.
- Rendering must show three states, not two: available, disabled-by-capability, and — later —
  unbound. They are different conditions and must not look the same.

## Alternatives considered

- **Separate layouts per tier.** Rejected: double the maintenance, and it makes sharing ambiguous.
- **Automatic substitution** (stick → d-pad on a limited backend). Rejected: it changes how the
  layout plays without telling the user, which is exactly the silent degradation this project keeps
  finding reasons to avoid.
- **Hiding unavailable controls.** Rejected: indistinguishable from having lost them.
