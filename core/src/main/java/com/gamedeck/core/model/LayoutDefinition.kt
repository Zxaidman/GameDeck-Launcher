package com.gamedeck.core.model

import com.gamedeck.core.input.GamepadAxis
import com.gamedeck.core.input.GamepadButton

/**
 * A controller layout definition.
 *
 * Defines which controls appear, where they are positioned, and how
 * they behave. Does NOT define visual appearance (see SkinDefinition).
 */
data class LayoutDefinition(
    val id: String,
    val name: String,
    val controllerDefinition: String,
    val elements: List<LayoutElement>,
    val metadata: LayoutMetadata = LayoutMetadata()
)

/**
 * Metadata for a layout definition.
 */
data class LayoutMetadata(
    val schemaVersion: Int = 1,
    val type: String = "controller-layout",
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet(),
    val builtin: Boolean = false,
    val editable: Boolean = false,
    val sourceTemplate: String? = null
)

/**
 * A single control element within a layout.
 *
 * Positions use normalized coordinates (0.0 ... 1.0) relative to the
 * controller area so layouts are device-independent.
 */
data class LayoutElement(
    val id: String,
    val control: ControlType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val mapping: ControlMapping? = null,
    val behavior: ControlBehavior = ControlBehavior(),
    val anchors: Set<Anchor> = emptySet()
)

/**
 * Type of a control element.
 */
enum class ControlType {
    BUTTON,
    DPAD,
    ANALOG_STICK,
    TRIGGER,
    SHOULDER_BUTTON
}

/**
 * Where a control is anchored within the controller area.
 */
enum class Anchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    CENTER,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}

/**
 * Maps a control element to a semantic input.
 */
sealed interface ControlMapping {
    /** Maps to a digital button */
    data class Button(val button: GamepadButton) : ControlMapping

    /** Maps to an analog axis pair (sticks) */
    data class Analog(val xAxis: GamepadAxis, val yAxis: GamepadAxis) : ControlMapping
}

/**
 * Behavioral parameters for a control.
 */
data class ControlBehavior(
    val deadZone: Float = 0.1f,
    val sensitivity: Float = 1.0f,
    val hapticEnabled: Boolean = true,
    val invertY: Boolean = false
)