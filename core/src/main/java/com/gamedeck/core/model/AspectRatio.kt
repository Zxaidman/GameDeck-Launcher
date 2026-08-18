package com.gamedeck.core.model

/**
 * An aspect-ratio preset.
 *
 * The preset list is data-driven and must not be hard-coded.
 */
data class AspectRatio(
    val id: String,
    val width: Int,
    val height: Int,
    val builtin: Boolean = false,
    val metadata: AspectRatioMetadata = AspectRatioMetadata()
)

/**
 * Metadata for an aspect-ratio preset.
 */
data class AspectRatioMetadata(
    val schemaVersion: Int = 1,
    val type: String = "aspect-ratio-preset",
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet()
)

/**
 * Built-in aspect-ratio presets.
 */
object AspectRatioPresets {
    val DEFAULT: List<AspectRatio> = listOf(
        AspectRatio("4:3", 4, 3, builtin = true),
        AspectRatio("16:9", 16, 9, builtin = true),
        AspectRatio("18:9", 18, 9, builtin = true),
        AspectRatio("19.5:9", 19, 9, builtin = true),
        AspectRatio("20:9", 20, 9, builtin = true),
        AspectRatio("21:9", 21, 9, builtin = true)
    )
}