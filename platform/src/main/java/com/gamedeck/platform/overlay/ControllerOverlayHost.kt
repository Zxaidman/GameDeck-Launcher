package com.gamedeck.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Android overlay window host for the controller layer.
 *
 * Creates and manages a system overlay window that displays the
 * virtual controller above the target gaming application.
 *
 * Requires the SYSTEM_ALERT_WINDOW permission.
 */
class ControllerOverlayHost(
    private val context: Context
) {
    private var overlayView: FrameLayout? = null
    private var windowManager: WindowManager? = null

    /**
     * Whether the overlay is currently shown.
     */
    var isShowing: Boolean = false
        private set

    /**
     * Show the controller overlay.
     *
     * @param contentView the controller content to display
     */
    fun show(contentView: FrameLayout): Boolean {
        if (isShowing) return true

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutParams = createLayoutParams()

            wm.addView(contentView, layoutParams)
            overlayView = contentView
            windowManager = wm
            isShowing = true
            true
        } catch (e: SecurityException) {
            // SYSTEM_ALERT_WINDOW permission not granted
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Hide and remove the controller overlay.
     */
    fun hide() {
        if (!isShowing) return

        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            // View may already be removed
        }

        overlayView = null
        windowManager = null
        isShowing = false
    }

    /**
     * Create window layout parameters for the overlay.
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}