package com.gamedeck.core.model

/**
 * A skin defines the visual appearance of a controller.
 *
 * Skins are separated from layouts: a layout defines what controls exist
 * and where they are; a skin defines how they look.
 */
data class SkinDefinition(
    val id: String,
    val name: String,
    val styles: Map<String, ControlStyle> = emptyMap(),
    val assets: Map<String, String> = emptyMap(),
    val metadata: SkinMetadata = SkinMetadata()
)

/**
 * Metadata for a skin definition.
 */
data class SkinMetadata(
    val schemaVersion: Int = 1,
    val type: String = "controller-skin",
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet(),
    val builtin: Boolean = false,
    val editable: Boolean = false
)

/**
 * Visual style for a control type or specific control.
 */
data class ControlStyle(
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val borderWidth: Float = 0f,
    val cornerRadius: Float = 0f,
    val opacity: Float = 1f,
    val labelColor: String? = null,
    val labelSize: Float = 14f,
    val pressedColor: String? = null,
    val highlightColor: String? = null,
    val shadowColor: String? = null,
    val shadowRadius: Float = 0f,
    val shape: ControlShape = ControlShape.CIRCLE
)

/**
 * Shape of a control.
 */
enum class ControlShape {
    CIRCLE,
    ROUNDED_RECTANGLE,
    RECTANGLE,
    OVAL,
    DIAMOND
}