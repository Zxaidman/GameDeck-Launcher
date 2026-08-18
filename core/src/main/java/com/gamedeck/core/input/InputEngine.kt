package com.gamedeck.core.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central input coordination engine.
 *
 * The controller UI sends semantic ControllerInput events to this engine.
 * The engine forwards them to the currently selected InputBackend.
 *
 * The UI and domain layers never directly call Android input APIs.
 */
class InputEngine(
    private val backendProvider: BackendProvider
) {
    private var activeBackend: InputBackend? = null
    private val activeBackendId = MutableStateFlow<String?>(null)

    /** Currently active backend identifier, or null if none */
    val backendId: StateFlow<String?> = activeBackendId.asStateFlow()

    /** Set of buttons currently pressed */
    private val pressedButtons = mutableSetOf<GamepadButton>()

    /** Current axis values */
    private val axisValues = mutableMapOf<GamepadAxis, Float>()

    /**
     * Initialize and activate the best available backend.
     */
    suspend fun initialize(): BackendResult {
        shutdown()

        val backend = backendProvider.selectBackend()
        val result = backend.initialize()
        if (result is BackendResult.Success) {
            activeBackend = backend
            activeBackendId.value = backend.id
        }
        return result
    }

    /**
     * Send a button event to the active backend.
     */
    suspend fun sendButton(button: GamepadButton, state: ButtonState): InputResult {
        val backend = activeBackend ?: return InputResult.Failure("No active input backend")

        when (state) {
            ButtonState.DOWN -> pressedButtons.add(button)
            ButtonState.UP -> pressedButtons.remove(button)
            ButtonState.HELD -> Unit
        }

        return backend.sendButton(button, state)
    }

    /**
     * Send an axis value to the active backend.
     */
    suspend fun sendAxis(axis: GamepadAxis, value: Float): InputResult {
        val backend = activeBackend ?: return InputResult.Failure("No active input backend")
        axisValues[axis] = value
        return backend.sendAxis(axis, value)
    }

    /**
     * Release all active inputs and shutdown the active backend.
     *
     * This MUST be called when a gaming session ends to prevent stuck inputs.
     */
    suspend fun shutdown() {
        val backend = activeBackend ?: return

        // Release all pressed buttons
        pressedButtons.toList().forEach { button ->
            backend.sendButton(button, ButtonState.UP)
        }
        pressedButtons.clear()

        // Reset all active axes to neutral
        axisValues.keys.forEach { axis ->
            val neutral = if (axis == GamepadAxis.LEFT_TRIGGER || axis == GamepadAxis.RIGHT_TRIGGER) 0f else 0f
            backend.sendAxis(axis, neutral)
        }
        axisValues.clear()

        backend.shutdown()
        activeBackend = null
        activeBackendId.value = null
    }
}

/**
 * Supplies and selects the best available input backend.
 */
fun interface BackendProvider {
    fun selectBackend(): InputBackend
}