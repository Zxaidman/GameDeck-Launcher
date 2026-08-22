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
    public fun usable(context: Context): LayoutSurface = screen(context).let { screen ->
        LayoutSurface(screen.usableWidth, screen.usableHeight)
    }

    /**
     * The **whole** screen, with the bars and the cutout carried as insets rather than subtracted.
     *
     * This is what the editor draws, and the difference from [usable] is the difference between
     * showing somebody their phone and showing them a rectangle that happens to be where their pad
     * goes. On the reference device those are 2400 × 1080 and 2289 × 927 — 2.22 : 1 against
     * 2.47 : 1, which is visibly not the same shape.
     *
     * Controls resolve against this surface exactly as they do against [usable], because `resolve`
     * places them inside the insets. What is gained is that the bands are drawn, and a control that
     * strays into one is visibly straying into it instead of appearing to hang over an edge.
     */
    public fun screen(context: Context): LayoutSurface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(WindowManager::class.java)
            if (manager != null) {
                val metrics = manager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout()
                )
                val width = metrics.bounds.width().toDouble()
                val height = metrics.bounds.height().toDouble()
                if (width > 0 && height > 0) {
                    return LayoutSurface(
                        widthPx = width,
                        heightPx = height,
                        insetLeft = insets.left.toDouble(),
                        insetTop = insets.top.toDouble(),
                        insetRight = insets.right.toDouble(),
                        insetBottom = insets.bottom.toDouble(),
                    )
                }
            }
        }
        val metrics = context.resources.displayMetrics
        return LayoutSurface(metrics.widthPixels.toDouble(), metrics.heightPixels.toDouble())
    }

    /**
     * The same screen as it would be with the phone turned, which the editor previews.
     *
     * **An estimate, and the only estimate in this file.** The sides swap, and the inset amounts
     * swap with them — what was taken off the top and bottom comes off the left and right. That is
     * what a rotation does to the *amounts*; which physical edge each one lands on depends on which
     * way the phone was turned and on where this particular phone puts its cutout, and neither can
     * be known without actually being in that orientation. The current orientation is measured; the
     * other one is drawn from this, and it is close enough to arrange a pad against and not close
     * enough to make a claim about.
     */
    public fun rotated(surface: LayoutSurface): LayoutSurface = LayoutSurface(
        widthPx = surface.heightPx,
        heightPx = surface.widthPx,
        insetLeft = surface.insetTop,
        insetTop = surface.insetLeft,
        insetRight = surface.insetBottom,
        insetBottom = surface.insetRight,
    )
}
