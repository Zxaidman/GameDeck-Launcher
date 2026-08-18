package com.gamedeck.core.model

/**
 * A gaming profile connects a target application to GameDeck behavior.
 */
data class GamingProfile(
    val id: String,
    val name: String,
    val application: ApplicationReference,
    val layout: String,
    val skin: String,
    val display: DisplayConfiguration,
    val input: InputPreference = InputPreference(),
    val metadata: ProfileMetadata = ProfileMetadata()
)

/**
 * Reference to a target application.
 */
data class ApplicationReference(
    val packageName: String
)

/**
 * Display configuration for a gaming session.
 */
data class DisplayConfiguration(
    val orientation: Orientation = Orientation.LANDSCAPE,
    val scalingMode: ScalingMode = ScalingMode.FIT,
    val aspectRatio: String = "16:9",
    val gameAreaWidth: Float? = null,
    val gameAreaHeight: Float? = null,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val alignment: Alignment = Alignment.CENTER,
    val margins: Margins = Margins(),
    val controllerRegionHeight: Float = 0.35f
)

/**
 * Screen orientation preference.
 */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE,
    SENSOR
}

/**
 * Game display scaling mode.
 */
enum class ScalingMode {
    /** Preserve aspect ratio and fit within available area */
    FIT,

    /** Preserve aspect ratio and fill available area (may crop) */
    FILL,

    /** Scale independently in horizontal and vertical dimensions */
    STRETCH,

    /** Integer scaling (reserved for future implementation) */
    INTEGER_SCALE
}

/**
 * Alignment of the game area within the display region.
 */
enum class Alignment {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}

/**
 * Margins around the game area.
 */
data class Margins(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)

/**
 * Input preference for a gaming session.
 */
data class InputPreference(
    val preferredBackend: String? = null,
    val allowFallback: Boolean = true,
    val hapticsEnabled: Boolean = true
)

/**
 * Metadata for a gaming profile.
 */
data class ProfileMetadata(
    val schemaVersion: Int = 1,
    val type: String = "gaming-profile",
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet(),
    val builtin: Boolean = false,
    val editable: Boolean = false,
    val sourceTemplate: String? = null
)