package com.gamedeck.core.input

import com.gamedeck.core.model.ControlBehavior
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure Kotlin analog stick processing pipeline.
 *
 * Touch coordinate -> stick geometry -> dead zone -> normalization
 * -> sensitivity -> curve -> output [-1, +1]
 *
 * Fully unit-testable without an Android device.
 */
class AnalogProcessor {

    /**
     * Process a raw stick input vector into a normalized output.
     *
     * @param rawX raw X input in the range [-1, +1]
     * @param rawY raw Y input in the range [-1, +1]
     * @param behavior control behavior (dead zone, sensitivity, invertY)
     * @return normalized output vector in the range [-1, +1]
     */
    fun processStick(
        rawX: Float,
        rawY: Float,
        behavior: ControlBehavior = ControlBehavior()
    ): StickOutput {
        var x = rawX
        var y = rawY

        // Invert Y if configured
        if (behavior.invertY) {
            y = -y
        }

        // Compute magnitude and direction
        val magnitude = sqrt(x * x + y * y)
        val angle = if (magnitude > 0f) {
            // Angle of the vector (atan2 style)
            kotlin.math.atan2(y, x)
        } else {
            0f
        }

        // Apply dead zone
        val adjustedMagnitude = if (magnitude < behavior.deadZone) {
            0f
        } else {
            // Rescale magnitude from dead zone range to [0, 1]
            (magnitude - behavior.deadZone) / (1f - behavior.deadZone)
        }

        // Apply sensitivity
        val sensitiveMagnitude = applySensitivity(adjustedMagnitude, behavior.sensitivity)

        // Compute output components
        val outX = kotlin.math.cos(angle) * sensitiveMagnitude
        val outY = kotlin.math.sin(angle) * sensitiveMagnitude

        return StickOutput(
            x = outX.coerceIn(-1f, 1f),
            y = outY.coerceIn(-1f, 1f),
            magnitude = sensitiveMagnitude.coerceIn(0f, 1f),
            angle = angle,
            inDeadZone = magnitude < behavior.deadZone
        )
    }

    /**
     * Process a trigger value in the range [0, +1].
     */
    fun processTrigger(
        rawValue: Float,
        behavior: ControlBehavior = ControlBehavior()
    ): Float {
        if (rawValue < behavior.deadZone) return 0f

        val adjusted = (rawValue - behavior.deadZone) / (1f - behavior.deadZone)
        return applySensitivity(adjusted, behavior.sensitivity).coerceIn(0f, 1f)
    }

    /**
     * Apply a sensitivity curve to a normalized magnitude.
     * Values > 1.0 increase sensitivity; values < 1.0 decrease it.
     */
    private fun applySensitivity(value: Float, sensitivity: Float): Float {
        if (sensitivity <= 0f) return 0f
        if (sensitivity == 1f) return value

        // Simple power curve: x^(1/sensitivity)
        return kotlin.math.pow(value, 1f / sensitivity)
    }
}

/**
 * Output of analog stick processing.
 */
data class StickOutput(
    val x: Float,
    val y: Float,
    val magnitude: Float,
    val angle: Float,
    val inDeadZone: Boolean
)