package com.gamedeck.core.model

/**
 * A gaming application discovered or manually added to GameDeck.
 */
data class GameApplication(
    val packageName: String,
    val displayName: String,
    val iconResource: String? = null,
    val category: ApplicationCategory = ApplicationCategory.UNKNOWN,
    val source: ApplicationSource = ApplicationSource.DISCOVERED,
    val preferredProfile: String? = null,
    val preferredLayout: String? = null,
    val launchBehavior: LaunchBehavior = LaunchBehavior.NORMAL
)

/**
 * Category of a gaming application.
 */
enum class ApplicationCategory {
    EMULATOR,
    STREAMING,
    CLOUD_GAMING,
    ANDROID_GAME,
    UNKNOWN
}

/**
 * How an application was added to GameDeck.
 */
enum class ApplicationSource {
    /** Automatically discovered from installed applications */
    DISCOVERED,

    /** Manually added by the user */
    USER,

    /** From the compatibility registry */
    REGISTRY
}

/**
 * How an application should be launched.
 */
enum class LaunchBehavior {
    NORMAL,
    FORCE_LANDSCAPE,
    FORCE_PORTRAIT,
    PICTURE_IN_PICTURE
}