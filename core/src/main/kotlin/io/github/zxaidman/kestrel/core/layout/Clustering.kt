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
 * would cover almost nothing and make sliding impossible. **Controls close enough to slide between
 * share a window; controls far enough apart that nobody would try get their own.** The grouping is
 * derived from the layout rather than declared in it, so a user who drags two buttons together in
 * the editor gets sliding between them without knowing the concept exists.
 */
public object Clustering {

    /**
     * How close two controls must be to share a window, as a fraction of the surface's short side.
     *
     * Roughly a finger's width. Below it, a thumb rolling off one control lands on the other and
     * the two clearly belong together; above it, the gap is somewhere a thumb goes deliberately.
     */
    public const val DEFAULT_GAP: Double = 0.03

    /**
     * Groups placed controls, then returns each group with the rectangle enclosing it.
     *
     * Grouping is transitive: if A is near B and B is near C, all three share a window even when A
     * and C are far apart. That is the correct reading — a row of buttons is one row — and it is
     * why this is a connected-components problem rather than a pairwise one.
     *
     * Order is preserved: clusters come out in the order their first element appeared, and elements
     * within a cluster keep their original order, so what is drawn on top stays predictable.
     */
    public fun group(
        placed: List<Pair<String, PixelRect>>,
        gapPixels: Double,
    ): List<Cluster> {
        if (placed.isEmpty()) return emptyList()

        val parent = IntArray(placed.size) { it }

        fun find(a: Int): Int {
            var root = a
            while (parent[root] != root) root = parent[root]
            var walk = a
            while (parent[walk] != root) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            // The lower index wins, which is what keeps the output in declaration order.
            if (rootA < rootB) parent[rootB] = rootA else parent[rootA] = rootB
        }

        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                if (gapBetween(placed[i].second, placed[j].second) <= gapPixels) union(i, j)
            }
        }

        val byRoot = LinkedHashMap<Int, MutableList<Int>>()
        placed.indices.forEach { i -> byRoot.getOrPut(find(i)) { mutableListOf() } += i }

        return byRoot.values.map { members ->
            Cluster(
                elementIds = members.map { placed[it].first },
                bounds = enclosing(members.map { placed[it].second }),
            )
        }
    }

    /**
     * The clear space between two controls, treating each as the circle it is drawn as.
     *
     * Bounding boxes would be wrong here in a way that matters: four round face buttons in a
     * diamond have overlapping boxes and touch nowhere, so every layout would collapse into one
     * window. The inscribed circle is what a thumb actually meets.
     *
     * Negative when they overlap, which callers may treat as "definitely together".
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
