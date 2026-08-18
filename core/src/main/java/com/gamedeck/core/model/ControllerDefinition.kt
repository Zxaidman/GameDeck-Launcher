package com.gamedeck.core.model

import com.gamedeck.core.input.GamepadAxis
import com.gamedeck.core.input.GamepadButton

/**
 * A logical controller definition.
 *
 * Defines which buttons and axes a controller has, but NOT screen positions.
 * Screen arrangement lives in LayoutDefinition.
 */
data class ControllerDefinition(
    val id: String,
    val name: String,
    val buttons: Set<GamepadButton>,
    val axes: Set<GamepadAxis>,
    val triggers: Set<GamepadButton>,
    val metadata: ControllerMetadata = ControllerMetadata()
)

/**
 * Metadata for a controller definition.
 */
data class ControllerMetadata(
    val schemaVersion: Int = 1,
    val type: String = "controller-definition",
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet(),
    val builtin: Boolean = false,
    val editable: Boolean = false
)