package com.gamedeck.core.input

/**
 * Semantic gamepad axis identifiers used throughout GameDeck.
 *
 * Axis values are normalized to -1.0 ... +1.0 for sticks and
 * 0.0 ... +1.0 for triggers.
 */
enum class GamepadAxis {
    /** Left analog stick horizontal axis */
    LEFT_X,

    /** Left analog stick vertical axis */
    LEFT_Y,

    /** Right analog stick horizontal axis */
    RIGHT_X,

    /** Right analog stick vertical axis */
    RIGHT_Y,

    /** Left trigger analog axis */
    LEFT_TRIGGER,

    /** Right trigger analog axis */
    RIGHT_TRIGGER
}