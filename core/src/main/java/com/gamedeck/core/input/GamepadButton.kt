package com.gamedeck.core.input

/**
 * Semantic gamepad button identifiers used throughout GameDeck.
 *
 * These are controller-semantic and are translated to Android-specific
 * key codes or other backend representations by the active InputBackend.
 */
enum class GamepadButton {
    /** South face button (Xbox A / PlayStation Cross) */
    A,

    /** East face button (Xbox B / PlayStation Circle) */
    B,

    /** West face button (Xbox X / PlayStation Square) */
    X,

    /** North face button (Xbox Y / PlayStation Triangle) */
    Y,

    /** Left shoulder button */
    LB,

    /** Right shoulder button */
    RB,

    /** Left analog trigger */
    LT,

    /** Right analog trigger */
    RT,

    /** D-pad up */
    DPAD_UP,

    /** D-pad down */
    DPAD_DOWN,

    /** D-pad left */
    DPAD_LEFT,

    /** D-pad right */
    DPAD_RIGHT,

    /** Left analog stick click */
    LEFT_STICK_CLICK,

    /** Right analog stick click */
    RIGHT_STICK_CLICK,

    /** Start / Options button */
    START,

    /** Select / Back / Share button */
    BACK,

    /** Guide / Home-style button */
    GUIDE,

    /** Touchpad click where supported */
    TOUCHPAD_CLICK
}