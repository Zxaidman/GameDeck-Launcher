package io.github.zxaidman.kestrel.platform.input

import io.github.zxaidman.kestrel.core.input.GamepadControl

/**
 * Where a control's name becomes a number, and the only place it does.
 *
 * `CLAUDE.md` §5 requires domain and interface code to speak controller semantics — `A`, not `304`.
 * That rule only means anything if there is exactly one place the translation happens, and this is
 * it. The overlay used to carry these numbers in its own control table, which put kernel constants
 * in the layer furthest from the kernel and meant a second backend could never map them differently.
 *
 * The values are Linux input event codes, a stable kernel ABI, matching the descriptor
 * `VirtualControllerBackend` declares. They are not invented here and they are not Android key
 * codes: the platform derives its own `BUTTON_A` and friends from these, which Phase 0 measured.
 */
public object GamepadCodes {

    /**
     * The button code for a control, or null when the control is not a button.
     *
     * Null rather than a throw, because "this is a stick, not a button" is a question the caller is
     * expected to ask and answer, not an error.
     */
    public fun buttonCode(control: GamepadControl): Int? = when (control) {
        GamepadControl.A -> BTN_SOUTH
        GamepadControl.B -> BTN_EAST
        GamepadControl.X -> BTN_WEST
        GamepadControl.Y -> BTN_NORTH
        GamepadControl.LEFT_BUMPER -> BTN_TL
        GamepadControl.RIGHT_BUMPER -> BTN_TR
        GamepadControl.SELECT -> BTN_SELECT
        GamepadControl.START -> BTN_START
        GamepadControl.LEFT_STICK_PRESS -> BTN_THUMBL
        GamepadControl.RIGHT_STICK_PRESS -> BTN_THUMBR
        GamepadControl.LEFT_TRIGGER,
        GamepadControl.RIGHT_TRIGGER,
        GamepadControl.LEFT_STICK,
        GamepadControl.RIGHT_STICK,
        GamepadControl.DPAD,
        -> null
    }

    /** Whether this control is the right-hand one of its pair, which is what the engine asks. */
    public fun isRight(control: GamepadControl): Boolean =
        control == GamepadControl.RIGHT_STICK || control == GamepadControl.RIGHT_TRIGGER

    // Linux input event codes. Stable kernel ABI, not values chosen here.
    private const val BTN_SOUTH = 304
    private const val BTN_EAST = 305
    private const val BTN_WEST = 307
    private const val BTN_NORTH = 308
    private const val BTN_TL = 310
    private const val BTN_TR = 311
    private const val BTN_SELECT = 314
    private const val BTN_START = 315
    private const val BTN_THUMBL = 317
    private const val BTN_THUMBR = 318
}
