package com.gamedeck.core.input

/**
 * State of a digital gamepad button.
 */
enum class ButtonState {
    /** Button is not pressed */
    UP,

    /** Button was just pressed */
    DOWN,

    /** Button is being held down */
    HELD
}