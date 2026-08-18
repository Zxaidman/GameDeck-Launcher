package com.gamedeck.core.input

/**
 * Capabilities that an InputBackend may provide.
 *
 * Backend selection is capability-driven rather than assumption-driven.
 */
enum class InputCapability {
    /** Digital buttons (A/B/X/Y, shoulders, start/back) */
    DIGITAL_BUTTONS,

    /** D-pad directional input */
    DPAD,

    /** Analog stick axes */
    ANALOG_AXES,

    /** Analog trigger axes */
    ANALOG_TRIGGERS,

    /** Multiple simultaneous inputs */
    SIMULTANEOUS_INPUT,

    /** System-level input event injection */
    SYSTEM_INPUT_INJECTION,

    /** A virtual input device is registered */
    VIRTUAL_DEVICE,

    /** The input appears as a game controller device */
    GAMEPAD_DEVICE_IDENTITY,

    /** Known compatibility with target applications */
    TARGET_APP_COMPATIBILITY,

    /** Touch/gesture fallback simulation */
    TOUCH_FALLBACK
}