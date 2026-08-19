package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.input.ControlRequirement
import io.github.zxaidman.kestrel.core.input.InputCapability

/**
 * What a control in a layout *is*, and therefore what a backend must provide for it to work.
 *
 * This is the join between the configuration schema and `ADR-007`. A layout element stores its kind;
 * the capability it needs is derived from that rather than stored, so a document written today
 * still means the right thing if the capability model gains a distinction tomorrow. Storing the
 * requirement instead would freeze today's understanding into every file ever exported.
 *
 * Controller semantics only — no key codes, no axis constants (`CLAUDE.md` §5).
 */
public enum class ControlKind(
    public val wireName: String,
    public val requires: Set<InputCapability>,
) {

    /** A face or shoulder button: A, B, X, Y, L1, R1, Start, Select. */
    BUTTON("button", setOf(InputCapability.BUTTONS)),

    /** A directional pad, as four discrete directions. */
    DPAD("dpad", setOf(InputCapability.DPAD)),

    /**
     * A stick reporting continuous deflection.
     *
     * Needs simultaneous input as well as analog: a stick is useless if holding a direction stops
     * a button from registering, which is the ordinary case in anything being played.
     */
    STICK("stick", setOf(InputCapability.ANALOG_STICK, InputCapability.SIMULTANEOUS)),

    /** A trigger reporting how far it is pressed. */
    ANALOG_TRIGGER("analog-trigger", setOf(InputCapability.ANALOG_TRIGGER)),

    /**
     * A trigger treated as a button.
     *
     * Deliberately a separate kind rather than a degraded `ANALOG_TRIGGER`. `ADR-007` forbids the
     * product silently reinterpreting one control as another, but a user is free to *choose* a
     * digital trigger, and then it works on backends where the analog one cannot. The difference is
     * who decided.
     */
    DIGITAL_TRIGGER("digital-trigger", setOf(InputCapability.BUTTONS)),

    /** Artwork or a label. Needs nothing and is never disabled. */
    DECORATION("decoration", emptySet()),
    ;

    /** The requirement for one element of this kind, ready for the availability rules. */
    public fun requirementFor(controlId: String): ControlRequirement =
        ControlRequirement(controlId, requires)

    public companion object {
        public fun of(wireName: String): ControlKind? = entries.firstOrNull { it.wireName == wireName }
    }
}
