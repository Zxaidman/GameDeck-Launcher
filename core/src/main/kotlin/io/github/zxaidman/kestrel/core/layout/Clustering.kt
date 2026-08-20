package io.github.zxaidman.kestrel.core.layout

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A set of controls that share one window on screen, and the rectangle that window occupies.
 */
public data class Cluster(
    public val elementIds: List<String>,
    public val bounds: PixelRect,
)

/**
 * Decides which controls share a window, from where they sit rather than from how they were
 * declared.
 *
 * This exists because two measured facts pull in opposite directions.
 *
 * **A finger cannot move between windows.** A pointer belongs to the window that received its
 * touch-down for the life of the gesture, so sliding a thumb from one control to the next only
 * works if both are in the same window. That is what makes rolling across two face buttons press
 * both, and what lets a thumb hold `L3` and then move the stick.
 *
 * **A window is dead everywhere its controls are not.** Measured on the reference device: a view
 * refusing a touch does not pass it to the application underneath, and the platform's own remedy —
 * an irregular touchable region — is not public API. So every pixel of window that is not a control
 * is a pixel the user cannot touch anything through.
 *
 * One window for everything would give perfect sliding and cover the screen. One window per control
 * would cover almost nothing and make sliding impossible. **The layout says which controls share
 * one**, through each element's `group`.
 *
 * It was briefly inferred from how close controls were drawn, and that failed on the very layout it
 * was written for: the gap that had to mean "together" and the gap that had to mean "apart" were
 * fifteen pixels apart, so the answer flipped with rounding and with the size setting. A gesture
 * that works at one size and not another is worse than one that never worked.
 */
public object Clustering {

    /**
     * Puts each control in the window its layout says it belongs to.
     *
     * A control with no group gets a window of its own. That is the safe default rather than a
     * limitation: a window holds nothing but the controls in it, and everything else it covers is
     * dead to whatever is underneath, so the smallest window that works is the right one.
     *
     * Clusters come out in the order their first control was declared, and controls keep their
     * order inside one, so what is drawn on top stays predictable.
     */
    public fun group(placed: List<Pair<String, PixelRect>>, groups: Map<String, String?>): List<Cluster> {
        if (placed.isEmpty()) return emptyList()

        val byKey = LinkedHashMap<String, MutableList<Pair<String, PixelRect>>>()
        placed.forEach { entry ->
            // An ungrouped control is keyed by its own id, which cannot collide with a group name
            // it does not have — and if it did, the two would belong together anyway.
            val key = groups[entry.first] ?: "\u0000${entry.first}"
            byKey.getOrPut(key) { mutableListOf() } += entry
        }

        return byKey.values.map { members ->
            Cluster(
                elementIds = members.map { it.first },
                bounds = enclosing(members.map { it.second }),
            )
        }
    }

    /** The same, taken straight from a layout, which is how the overlay calls it. */
    public fun group(layout: ControllerLayout, placed: List<Pair<String, PixelRect>>): List<Cluster> =
        group(placed, layout.elements.associate { it.id to it.group })

    /**
     * The clear space between two controls, treating each as the circle it is drawn as.
     *
     * Bounding boxes would be wrong here in a way that matters: four round face buttons in a
     * diamond have overlapping boxes and touch nowhere. The inscribed circle is what a thumb meets.
     *
     * Negative when they overlap. Not used for grouping — that is declared — but it is how a layout
     * is checked for controls sitting on top of each other.
     */
    public fun gapBetween(a: PixelRect, b: PixelRect): Double {
        val apart = hypot(a.centerX - b.centerX, a.centerY - b.centerY)
        val touching = min(a.width, a.height) / 2 + min(b.width, b.height) / 2
        return apart - touching
    }

    /** The smallest rectangle containing all of them, with no rotation of its own. */
    public fun enclosing(rects: List<PixelRect>): PixelRect {
        val left = rects.minOf { it.left }
        val top = rects.minOf { it.top }
        val right = rects.maxOf { it.right }
        val bottom = rects.maxOf { it.bottom }
        return PixelRect(
            centerX = (left + right) / 2,
            centerY = (top + bottom) / 2,
            width = max(0.0, right - left),
            height = max(0.0, bottom - top),
        )
    }
}

/**
 * Applies the user's size setting to a placement.
 *
 * **Offsets scale with sizes, deliberately.** Scaling only the controls would leave them pinned
 * where a full-size arrangement put them, so a smaller pad would sit further from the corner rather
 * than nearer it — the opposite of what someone reaching for a smaller control wants. Scaling both
 * shrinks the whole arrangement towards its anchors, which keeps every control the same distance
 * from the edge in proportion to its own size.
 */
public fun Placement.scaledBy(factor: Double): Placement = copy(
    offsetX = offsetX * factor,
    offsetY = offsetY * factor,
    width = width * factor,
    height = height * factor,
)
