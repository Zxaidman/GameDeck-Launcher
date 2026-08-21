package io.github.zxaidman.kestrel.platform.display

import android.content.Context
import android.os.Build
import android.view.WindowManager
import io.github.zxaidman.kestrel.core.layout.LayoutSurface

/**
 * The part of this phone's screen a pad can actually be put on.
 *
 * There is one answer to this question and two places that need it: the overlay, which is placed
 * into that area by the window manager, and the editor, which has to *draw* it. They must agree —
 * an editor whose canvas is the shape of its own window rather than the shape of the phone shows
 * controls overlapping that do not overlap on the device, and clear ones that do. That is worse
 * than editing numbers in a file, because it invites trust it has not earned.
 *
 * Insets are subtracted with `getInsetsIgnoringVisibility`, not the visible ones: a status bar that
 * is hidden right now can come back while a pad is on screen, and a layout arranged against the
 * whole display then shifts and overlaps itself the moment it does. That failure was reported and
 * this is the fix that holds it.
 */
public object DeviceSurface {

    /**
     * The usable area, in pixels, in the orientation the phone is in now.
     *
     * Falls back to the display metrics on anything older than API 30, which reports the whole
     * display rather than the usable part — less accurate, and the only thing available there.
     */
    public fun usable(context: Context): LayoutSurface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(WindowManager::class.java)
            if (manager != null) {
                val metrics = manager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout()
                )
                val width = metrics.bounds.width() - insets.left - insets.right
                val height = metrics.bounds.height() - insets.top - insets.bottom
                if (width > 0 && height > 0) {
                    return LayoutSurface(width.toDouble(), height.toDouble())
                }
            }
        }
        val metrics = context.resources.displayMetrics
        return LayoutSurface(metrics.widthPixels.toDouble(), metrics.heightPixels.toDouble())
    }

    /** The same area as it would be with the phone turned, which the editor previews. */
    public fun rotated(surface: LayoutSurface): LayoutSurface =
        LayoutSurface(surface.heightPx, surface.widthPx)
}
