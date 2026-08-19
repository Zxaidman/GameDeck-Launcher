package io.github.zxaidman.kestrel.core.input

/**
 * Whether one control in a layout can actually be used right now.
 *
 * `ADR-007` keeps a single layout across every capability tier: a control the active backend cannot
 * deliver is **shown and disabled**, never removed, never substituted, never silently reinterpreted.
 * This type is that rule expressed once, where it can be tested, instead of re-derived by each
 * screen that draws a control.
 */
public enum class ControlAvailability {

    /** Usable now. */
    AVAILABLE,

    /**
     * Present in the layout, drawn in place, and inert.
     *
     * The user designed it and it stays where they put it. A control that disappears reads as lost
     * data, which is why removal is not one of the options here.
     */
    DISABLED_BY_CAPABILITY,
    ;

    public val isUsable: Boolean
        get() = this == AVAILABLE
}

/**
 * A control's requirement, in controller terms.
 *
 * A layout stores the requirement rather than a computed availability, so the same layout means the
 * same thing on any phone and its meaning does not change when a backend does.
 */
public data class ControlRequirement(
    /** Stable identifier of the control within its layout. */
    public val controlId: String,
    /** What a backend must provide for this control to work. Empty means it always works. */
    public val requires: Set<InputCapability>,
)

/** Whether a control can be used, given what the active backend provides. */
public fun availabilityOf(
    requirement: ControlRequirement,
    available: Set<InputCapability>,
): ControlAvailability = if (available.containsAll(requirement.requires)) {
    ControlAvailability.AVAILABLE
} else {
    ControlAvailability.DISABLED_BY_CAPABILITY
}

/**
 * What the user must be told before a session starts.
 *
 * `docs/DEGRADED_STATE.md` §6 forbids finding out by pressing something that does nothing, so the
 * disabled controls are computed up front and stated. An empty result means everything in the
 * layout works, and is the only case where nothing needs saying.
 */
public fun disabledControls(
    requirements: List<ControlRequirement>,
    available: Set<InputCapability>,
): List<ControlRequirement> = requirements.filter { !availabilityOf(it, available).isUsable }

/**
 * The capabilities a layout wants that the backend does not provide.
 *
 * Useful for explaining *why* rather than only *what*: "no analog sticks" is a better thing to tell
 * someone than a list of six control names they now have to interpret.
 */
public fun missingCapabilities(
    requirements: List<ControlRequirement>,
    available: Set<InputCapability>,
): Set<InputCapability> = requirements
    .flatMap { it.requires }
    .filterNot { it in available }
    .toSet()
