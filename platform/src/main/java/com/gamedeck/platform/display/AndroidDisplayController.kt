package com.gamedeck.platform.display

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import com.gamedeck.core.model.DisplayConfiguration
import com.gamedeck.core.model.Orientation
import com.gamedeck.core.model.ScalingMode

/**
 * Android implementation of the game display controller.
 *
 * Distinguishes between:
 * - UI-level scaling (what GameDeck controls in its own surface)
 * - External activity/window manipulation (limited by Android)
 * - System-level display manipulation (may require elevated privileges)
 */
class AndroidDisplayController {

    /**
     * Apply orientation to an activity.
     */
    fun setOrientation(activity: Activity, orientation: Orientation) {
        val requestedOrientation = when (orientation) {
            Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Orientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        activity.requestedOrientation = requestedOrientation
    }

    /**
     * Keep the screen on during a gaming session.
     */
    fun keepScreenOn(activity: Activity, enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Calculate the game area dimensions for a given display configuration.
     *
     * This is UI-level scaling calculation. It does not claim to resize
     * an external application's window.
     *
     * @param availableWidth available width in pixels
     * @param availableHeight available height in pixels
     * @param config display configuration
     * @return calculated game area dimensions
     */
    fun calculateGameArea(
        availableWidth: Int,
        availableHeight: Int,
        config: DisplayConfiguration
    ): GameArea {
        val aspectParts = config.aspectRatio.split(":")
        val aspectWidth = aspectParts.getOrNull(0)?.toFloatOrNull() ?: 16f
        val aspectHeight = aspectParts.getOrNull(1)?.toFloatOrNull() ?: 9f
        val aspect = aspectWidth / aspectHeight

        // Reserve controller region height
        val gameHeight = availableHeight * (1f - config.controllerRegionHeight)
        val gameWidth = availableWidth.toFloat()

        return when (config.scalingMode) {
            ScalingMode.FIT -> {
                // Fit within available area preserving aspect ratio
                val fitHeight = gameWidth / aspect
                if (fitHeight <= gameHeight) {
                    GameArea(
                        width = gameWidth.toInt(),
                        height = fitHeight.toInt(),
                        offsetX = 0,
                        offsetY = ((gameHeight - fitHeight) / 2f).toInt()
                    )
                } else {
                    val fitWidth = gameHeight * aspect
                    GameArea(
                        width = fitWidth.toInt(),
                        height = gameHeight.toInt(),
                        offsetX = ((gameWidth - fitWidth) / 2f).toInt(),
                        offsetY = 0
                    )
                }
            }

            ScalingMode.FILL -> {
                // Fill available area preserving aspect ratio (may crop)
                val fillHeight = gameWidth / aspect
                if (fillHeight >= gameHeight) {
                    GameArea(
                        width = gameWidth.toInt(),
                        height = fillHeight.toInt(),
                        offsetX = 0,
                        offsetY = ((gameHeight - fillHeight) / 2f).toInt()
                    )
                } else {
                    val fillWidth = gameHeight * aspect
                    GameArea(
                        width = fillWidth.toInt(),
                        height = gameHeight.toInt(),
                        offsetX = ((gameWidth - fillWidth) / 2f).toInt(),
                        offsetY = 0
                    )
                }
            }

            ScalingMode.STRETCH -> {
                // Scale independently in both dimensions
                GameArea(
                    width = gameWidth.toInt(),
                    height = gameHeight.toInt(),
                    offsetX = 0,
                    offsetY = 0
                )
            }

            ScalingMode.INTEGER_SCALE -> {
                // Integer scaling (reserved for future implementation)
                // Fall back to FIT for now
                val fitHeight = gameWidth / aspect
                if (fitHeight <= gameHeight) {
                    GameArea(
                        width = gameWidth.toInt(),
                        height = fitHeight.toInt(),
                        offsetX = 0,
                        offsetY = ((gameHeight - fitHeight) / 2f).toInt()
                    )
                } else {
                    val fitWidth = gameHeight * aspect
                    GameArea(
                        width = fitWidth.toInt(),
                        height = gameHeight.toInt(),
                        offsetX = ((gameWidth - fitWidth) / 2f).toInt(),
                        offsetY = 0
                    )
                }
            }
        }
    }
}

/**
 * Calculated game area dimensions.
 */
data class GameArea(
    val width: Int,
    val height: Int,
    val offsetX: Int,
    val offsetY: Int
)