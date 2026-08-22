package io.github.zxaidman.kestrel.core.input

/**
 * What kind of thing a control is, physically, on a controller.
 *
 * Separate from [GamepadControl] itself because it is what validation actually asks about: a layout
 * element that presents a stick must be bound to something that *is* a stick, and the check should
 * not be a list of exceptions that grows every time a control is added.
 */
public enum class ControlForm {
    /** Pressed or not. */
    BUTTON,

    /** Pressed by an amount. */
    TRIGGER,

    /** Deflected in two axes, continuously. */
    STICK,

    /** Eight directions, or none. */
    DPAD,
}

/**
 * A control on a controller, named the way a person naming a controller would name it.
 *
 * This is the vocabulary `CLAUDE.md` §5 requires domain and interface code to speak: `A`, `LEFT_X`,
 * `L2` — never `304`, never `AXIS_BRAKE`. Its absence was a real gap rather than a stylistic one.
 * The overlay was carrying kernel button codes in its own control table, which meant the boundary
 * the rule exists to protect was being crossed in the layer furthest from the kernel.
 *
 * **Nothing here knows how a control is delivered.** The mapping from `A` to whatever a backend
 * sends belongs to that backend, and a second backend mapping it differently changes nothing above
 * this line. That is the point of naming them at all.
 *
 * [wireName] is what appears in a configuration document, and is part of the schema: changing one
 * breaks every layout ever exported, so a rename is a schema migration rather than an edit.
 */
public enum class GamepadControl(
    public val wireName: String,
    public val form: ControlForm,
    /** How a person reads it on screen, before a skin has an opinion. */
    public val defaultLabel: String,
) {
    A("a", ControlForm.BUTTON, "A"),
    B("b", ControlForm.BUTTON, "B"),
    X("x", ControlForm.BUTTON, "X"),
    Y("y", ControlForm.BUTTON, "Y"),

    LEFT_BUMPER("left-bumper", ControlForm.BUTTON, "L1"),
    RIGHT_BUMPER("right-bumper", ControlForm.BUTTON, "R1"),

    /**
     * The stick presses.
     *
     * Buttons rather than part of their sticks, because that is what they are: a layout is free to
     * put `L3` somewhere a thumb can reach without moving the stick, which is exactly what the
     * reference device's layout does.
     */
    LEFT_STICK_PRESS("left-stick-press", ControlForm.BUTTON, "L3"),
    RIGHT_STICK_PRESS("right-stick-press", ControlForm.BUTTON, "R3"),

    START("start", ControlForm.BUTTON, "Start"),
    SELECT("select", ControlForm.BUTTON, "Select"),

    LEFT_TRIGGER("left-trigger", ControlForm.TRIGGER, "L2"),
    RIGHT_TRIGGER("right-trigger", ControlForm.TRIGGER, "R2"),

    LEFT_STICK("left-stick", ControlForm.STICK, "Left stick"),
    RIGHT_STICK("right-stick", ControlForm.STICK, "Right stick"),

    DPAD("dpad", ControlForm.DPAD, "D-pad"),
    ;

    public companion object {
        public fun of(wireName: String): GamepadControl? =
            entries.firstOrNull { it.wireName == wireName }

        /** Every control of a given form, which is what a validation message needs to list. */
        public fun withForm(form: ControlForm): List<GamepadControl> =
            entries.filter { it.form == form }
    }
}
