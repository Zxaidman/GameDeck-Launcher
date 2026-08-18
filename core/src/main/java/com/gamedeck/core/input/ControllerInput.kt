package com.gamedeck.core.input

/**
 * A controller-semantic input event.
 *
 * The UI and domain layers produce these events. The active InputBackend
 * translates them into Android-specific events.
 */
sealed interface ControllerInput {
    /** A digital button press/release event */
    data class Button(
        val button: GamepadButton,
        val state: ButtonState
    ) : ControllerInput

    /** An analog axis value event. Values are normalized. */
    data class Axis(
        val axis: GamepadAxis,
        val value: Float
    ) : ControllerInput
}