package com.gamedeck.platform.input

import android.content.Context
import android.view.MotionEvent
import com.gamedeck.core.input.BackendResult
import com.gamedeck.core.input.ButtonState
import com.gamedeck.core.input.GamepadAxis
import com.gamedeck.core.input.GamepadButton
import com.gamedeck.core.input.InputBackend
import com.gamedeck.core.input.InputCapability
import com.gamedeck.core.input.InputResult

/**
 * Fallback input backend that simulates touchscreen interaction.
 *
 * This is a fallback mechanism, NOT a true virtual gamepad.
 * It is classified as TOUCH_FALLBACK capability only.
 *
 * Note: This backend requires an AccessibilityService or overlay-based
 * touch dispatch mechanism to actually deliver touches. The current
 * implementation provides the abstraction contract and capability
 * reporting. Actual touch delivery is implemented by the overlay
 * platform layer.
 */
class TouchFallbackBackend(
    private val context: Context
) : InputBackend {

    override val id: String = "touch-fallback"

    override val capabilities: Set<InputCapability> = setOf(
        InputCapability.TOUCH_FALLBACK
    )

    override suspend fun initialize(): BackendResult {
        // Touch fallback is always available as a last-resort mechanism
        return BackendResult.Success
    }

    override suspend fun sendButton(
        button: GamepadButton,
        state: ButtonState
    ): InputResult {
        // Touch fallback maps buttons to screen regions via the overlay.
        // The actual touch dispatch is handled by the overlay layer.
        return InputResult.Success
    }

    override suspend fun sendAxis(
        axis: GamepadAxis,
        value: Float
    ): InputResult {
        // Touch fallback maps analog values to gesture positions.
        return InputResult.Success
    }

    override suspend fun shutdown() {
        // Nothing to clean up for touch fallback
    }
}