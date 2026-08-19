# Kestrel — Controller Families

**Document:** `docs/CONTROLLER_FAMILIES.md`  
**Status:** Active — what Kestrel presents today, and what the alternatives would cost  
**Scope of evidence:** Redmi Note 13 5G, HyperOS 3.0.3, Android 15. Everything measured is from
that device; everything about other families is reasoning, and is marked as such.  

---

## 1. What Kestrel presents today

**An Xbox-style layout, and this is deliberate rather than incidental.**

The descriptor declares the Linux button codes `BTN_SOUTH`, `BTN_EAST`, `BTN_WEST`, `BTN_NORTH`,
which the platform maps to `BUTTON_A` 96, `BUTTON_B` 97, `BUTTON_X` 99, `BUTTON_Y` 100 — measured on
the reference device and confirmed by five emulators' binding screens
(`docs/phase0/results/tier6-report.md`).

That mapping *is* the Xbox convention: the bottom face button is A, the right is B, the left is X,
the top is Y. It is also what the platform treats as the default arrangement, which is why targets
accept it without configuration.

**In scope, not a limit.** Kestrel is expected to present other families later. What follows is what
that would actually involve, so the decision is made with the costs visible.

---

## 2. What actually differs between the three families

Less than the marketing suggests, and the difference is in three separate places that are easy to
confuse.

### The physical arrangement is nearly identical

All three place four face buttons in a diamond, two shoulder buttons, two triggers, two sticks, a
d-pad, and two or three menu buttons. Nintendo swaps the positions of the A/B pair and the X/Y pair
relative to the others; Sony uses shapes rather than letters. **Nothing about the input protocol
changes.** A press of the bottom face button is the same event on all three.

### The labels differ, and labels are the target's business

| Position | Xbox | PlayStation | Nintendo |
| --- | --- | --- | --- |
| Bottom | A | ✕ | B |
| Right | B | ○ | A |
| Left | X | □ | Y |
| Top | Y | △ | X |

A target application decides what to draw. Some print the raw key code, some recognise a controller
and draw a glyph. **Kestrel cannot make a target draw a glyph it has no asset for**, and nothing in
the descriptor changes what a target chooses to display.

### The identity differs, and that is the only lever Kestrel actually holds

Targets that show family-specific labels do so by recognising the controller's **vendor and product
identifiers**. Declaring the identifiers of a well-known controller would make more targets show
familiar labels. It would also claim to be a device this is not, and that has consequences:

- Targets expect what that device offers — rumble motors, a touchpad, motion sensors, a specific
  button count. Kestrel's device has none of those. A wrong expectation is a worse experience than
  an unfamiliar number.
- The identifiers belong to someone else. Using them is a choice with implications beyond
  compatibility.

**The current decision is to present Kestrel's own identity.** Changing it is an ADR, not an edit.

---

## 3. Where a family belongs in the architecture

Not in the descriptor. A family is a **presentation** concern, and the place it belongs is the
layer that already knows what Kestrel sent.

```text
layout / skin        ← family belongs here: labels, glyphs, arrangement
     ↓
core/input           ← controller semantics: GamepadButton.A, LEFT_X
     ↓
platform/input       ← key codes and axes; one descriptor, one identity
```

Kestrel knows it sent the bottom face button. Its own interface can therefore say **A**, draw **✕**,
or draw **B** depending on the family the user chose, while the descriptor and everything below it
stay unchanged. This is exactly the separation `docs/CONFIGURATION_SCHEMA.md` already draws between
a controller *definition* and a *skin*: the definition says which controls exist, the skin says how
they look.

Consequences of putting it here rather than in the descriptor:

- **One device, many appearances.** Switching family is a configuration change, not a controller
  reconnecting.
- **A layout stays valid across families**, per `ADR-007`. The controls are the same controls.
- **No target has to be re-bound** when a user changes their preferred labels.

What this does *not* solve: a target that draws its own glyphs will keep drawing its own. Nothing on
Kestrel's side reaches into another application's interface.

---

## 4. The Nintendo swap, and why it is not a relabelling

The one case where a family difference is more than cosmetic.

Nintendo's physical A is where Xbox's B sits, and its B is where A sits. So a user with a Nintendo
skin who presses the button *labelled A* expects the target to receive what that target calls A —
which sits in the other position. Two defensible behaviours:

- **Positional**: the bottom button always sends `BUTTON_A`, and the skin merely labels it B. What
  the target receives never changes.
- **Nominal**: the button labelled A sends `BUTTON_A` wherever it sits, so a Nintendo skin swaps
  what the two positions send.

They are different products, not different opinions about the same one. `ADR-007`'s principle points
at **positional** — the product does not silently change what a control sends — but this deserves
its own decision and its own record when a Nintendo skin is actually built, informed by what
emulators of Nintendo hardware expect.

**Untested and undecided.** Recorded so it is a known question rather than a surprise.

---

## 5. What is measured and what is not

| Claim | State |
| --- | --- |
| Xbox-style face mapping is accepted by five emulators | **Measured** — `tier6-report.md` |
| Face codes are 96, 97, 99, 100 and 98 is `BUTTON_C` | **Measured** on the reference device |
| A streaming host receives the controller | **Measured** — `tier6-streaming-report.md` |
| Other families require only presentation changes | **Reasoned** from the protocol; untested |
| Declaring another vendor's identifiers changes target labels | **Reasoned**; untested, and not planned |
| The Nintendo positional/nominal question | **Open**, undecided |

One device, one firmware. Nothing here is a claim about hardware nobody has tested.
