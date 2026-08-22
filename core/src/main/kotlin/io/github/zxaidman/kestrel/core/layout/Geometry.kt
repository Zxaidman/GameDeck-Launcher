package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.FieldPath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a control sits, in terms that survive being moved to another phone.
 *
 * `docs/CONFIGURATION_SCHEMA.md` requires coordinates that are device-independent rather than one
 * phone's pixels. That is the easy half. The hard half is that "normalised" alone is not enough:
 * a layout built on a 20:9 phone and opened on a 4:3 tablet has to remain *playable*, and the two
 * ways of naively normalising both fail.
 *
 * - Normalising position against the full width and height moves a thumb-reachable control into the
 *   middle of a wider screen.
 * - Normalising *size* against width and height independently turns a round button into an ellipse.
 *
 * So position and size are normalised differently, and deliberately:
 *
 * - **Position** is an offset from an [Anchor], so a control pinned to the bottom-left corner stays
 *   in the corner where a thumb is, whatever the screen becomes.
 * - **Size** is measured against the surface's **shorter side only**, so a control keeps its shape
 *   and its physical size relative to the hand holding the phone.
 */

/** A fraction of a surface, from 0 to 1. */
public typealias Fraction = Double

/**
 * The point a control's position is measured from.
 *
 * A control belongs to a corner or an edge, not to an abstract coordinate. Controls a thumb reaches
 * are anchored to the bottom corners; a pause control belongs to the top edge. The anchor is what
 * makes a layout survive a change of aspect ratio, and it is why offsets are small numbers.
 */
public enum class Anchor(
    public val wireName: String,
    /** Where this anchor sits within the surface, as fractions of width and height. */
    public val originX: Fraction,
    public val originY: Fraction,
) {
    TOP_LEFT("top-left", 0.0, 0.0),
    TOP_CENTER("top-center", 0.5, 0.0),
    TOP_RIGHT("top-right", 1.0, 0.0),
    CENTER_LEFT("center-left", 0.0, 0.5),
    CENTER("center", 0.5, 0.5),
    CENTER_RIGHT("center-right", 1.0, 0.5),
    BOTTOM_LEFT("bottom-left", 0.0, 1.0),
    BOTTOM_CENTER("bottom-center", 0.5, 1.0),
    BOTTOM_RIGHT("bottom-right", 1.0, 1.0),
    ;

    public companion object {
        public fun of(wireName: String): Anchor? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * A control's placement, independent of any screen.
 *
 * [offsetX] and [offsetY] move the control's **centre** away from its anchor, as a fraction of the
 * surface's shorter side — the same unit as [width] and [height], so a control and its offsets
 * scale together and the arrangement holds its proportions.
 *
 * Offsets are signed and point inwards from the anchor by convention: a control anchored
 * bottom-right with positive offsets moves left and up, towards the middle of the screen. That is
 * handled in [resolve] rather than being the author's problem.
 */
public data class Placement(
    public val anchor: Anchor,
    public val offsetX: Double,
    public val offsetY: Double,
    public val width: Double,
    public val height: Double,
    /** Clockwise, in degrees. Rotation affects hit testing, not just how the control is drawn. */
    public val rotationDegrees: Double = 0.0,
) {
    public companion object {

        /**
         * Bounds that exist to catch nonsense rather than to constrain design.
         *
         * A control wider than the surface's short side is almost certainly a mistake or a hostile
         * import, and either way it would cover the screen.
         */
        public const val MIN_SIZE: Double = 0.01
        public const val MAX_SIZE: Double = 2.0
        public const val MAX_OFFSET: Double = 4.0

        /** Validates a placement, naming the field at fault (`docs/CONFIGURATION_SCHEMA.md`). */
        public fun of(
            anchor: Anchor,
            offsetX: Double,
            offsetY: Double,
            width: Double,
            height: Double,
            rotationDegrees: Double = 0.0,
            path: FieldPath = "",
        ): Outcome<Placement> {
            fun range(field: String, value: Double, min: Double, max: Double): ConfigurationError? =
                if (value.isNaN() || value !in min..max) {
                    ConfigurationError.OutOfRange(path.let { if (it.isEmpty()) field else "$it.$field" }, value, min, max)
                } else {
                    null
                }

            val problem = range("width", width, MIN_SIZE, MAX_SIZE)
                ?: range("height", height, MIN_SIZE, MAX_SIZE)
                ?: range("offsetX", offsetX, -MAX_OFFSET, MAX_OFFSET)
                ?: range("offsetY", offsetY, -MAX_OFFSET, MAX_OFFSET)
                ?: range("rotation", rotationDegrees, -360.0, 360.0)

            return problem?.let { Outcome.Failure(it) }
                ?: Outcome.Success(Placement(anchor, offsetX, offsetY, width, height, rotationDegrees))
        }
    }
}

/** A rectangle in surface pixels, with the rotation still attached because hit testing needs it. */
public data class PixelRect(
    public val centerX: Double,
    public val centerY: Double,
    public val width: Double,
    public val height: Double,
    public val rotationDegrees: Double = 0.0,
) {
    /**
     * Half the width of the upright box that encloses this control, rotation included.
     *
     * A rotated control occupies more room than its own width and height. Reporting the unrotated
     * extents as bounds was wrong in both directions: a rotated control could be reported as clear
     * of a neighbour it visibly overlaps, and as fitting inside a surface it hangs out of.
     */
    private val halfBoundsWidth: Double
        get() = if (rotationDegrees == 0.0) {
            width / 2
        } else {
            val radians = rotationDegrees * PI_OVER_180
            abs(width / 2 * cos(radians)) + abs(height / 2 * sin(radians))
        }

    private val halfBoundsHeight: Double
        get() = if (rotationDegrees == 0.0) {
            height / 2
        } else {
            val radians = rotationDegrees * PI_OVER_180
            abs(width / 2 * sin(radians)) + abs(height / 2 * cos(radians))
        }

    public val left: Double get() = centerX - halfBoundsWidth
    public val top: Double get() = centerY - halfBoundsHeight
    public val right: Double get() = centerX + halfBoundsWidth
    public val bottom: Double get() = centerY + halfBoundsHeight

    /**
     * Whether a touch at this point is on this control.
     *
     * Rotation is applied to the *point*, not the rectangle: the point is rotated back around the
     * centre and then tested against an upright rectangle. Testing the bounding box instead would
     * make a rotated control respond to touches outside itself and, where two rotated controls sit
     * close together, respond to touches meant for its neighbour.
     */
    public fun contains(x: Double, y: Double): Boolean {
        if (rotationDegrees == 0.0) {
            return x in left..right && y in top..bottom
        }
        val radians = -rotationDegrees * PI_OVER_180
        val dx = x - centerX
        val dy = y - centerY
        val localX = dx * cos(radians) - dy * sin(radians)
        val localY = dx * sin(radians) + dy * cos(radians)
        return abs(localX) <= width / 2 && abs(localY) <= height / 2
    }

    /**
     * Whether two controls' bounding boxes intersect.
     *
     * **Approximate for rotated controls**, and named as a hint rather than a fact for that reason.
     * It is an editor aid — "these two probably overlap" — and must never be used to decide which
     * control receives a touch. That question is [contains], which is exact.
     */
    public fun boundsOverlap(other: PixelRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    internal companion object {
        const val PI_OVER_180: Double = 0.017453292519943295
    }
}

/**
 * The area a layout is drawn into, in pixels, after the parts of the screen that cannot be used.
 *
 * Insets are not decoration: a control under a display cutout is invisible, and one under a
 * gesture bar competes with the system for the same touch. Both are device-specific, which is
 * exactly why the layout itself must not encode them — the surface subtracts them, and the same
 * layout lands correctly on a phone with a cutout and one without.
 */
public data class LayoutSurface(
    public val widthPx: Double,
    public val heightPx: Double,
    public val insetLeft: Double = 0.0,
    public val insetTop: Double = 0.0,
    public val insetRight: Double = 0.0,
    public val insetBottom: Double = 0.0,
) {
    public val usableWidth: Double get() = max(0.0, widthPx - insetLeft - insetRight)
    public val usableHeight: Double get() = max(0.0, heightPx - insetTop - insetBottom)

    /**
     * The unit sizes are measured in.
     *
     * The shorter side, so that a control is the same size relative to the hand in landscape and
     * portrait, and so that rotating the phone does not resize every control.
     */
    public val shortSide: Double get() = min(usableWidth, usableHeight)
}

/**
 * Places a control on a real surface.
 *
 * The offset is applied *inwards* from the anchor, so an author never has to think about signs:
 * moving a bottom-right control "in by 0.2" is 0.2 in both directions regardless of which corner it
 * is pinned to. A centre anchor has no inward direction, so its offsets are used as written.
 */
public fun Placement.resolve(surface: LayoutSurface): PixelRect {
    val unit = surface.shortSide
    val originX = surface.insetLeft + anchor.originX * surface.usableWidth
    val originY = surface.insetTop + anchor.originY * surface.usableHeight

    val inwardX = when (anchor.originX) {
        0.0 -> 1.0
        1.0 -> -1.0
        else -> 1.0
    }
    val inwardY = when (anchor.originY) {
        0.0 -> 1.0
        1.0 -> -1.0
        else -> 1.0
    }

    return PixelRect(
        centerX = originX + offsetX * unit * inwardX,
        centerY = originY + offsetY * unit * inwardY,
        width = width * unit,
        height = height * unit,
        rotationDegrees = rotationDegrees,
    )
}

/**
 * Whether a resolved control is entirely inside the usable area.
 *
 * Used by the editor to warn, not to refuse. An author may deliberately run a control off the edge —
 * a shoulder button half off the top is a real design — and `ADR-007`'s spirit applies here too:
 * the product says what it sees rather than overruling the person.
 */
public fun PixelRect.isWithin(surface: LayoutSurface): Boolean =
    left >= surface.insetLeft &&
        top >= surface.insetTop &&
        right <= surface.insetLeft + surface.usableWidth &&
        bottom <= surface.insetTop + surface.usableHeight

/**
 * The topmost control at a point.
 *
 * Later elements win, matching draw order: the control drawn on top is the one touched. Anything
 * else would make overlapping controls behave differently from how they look.
 */
public fun hitTest(elements: List<Pair<String, PixelRect>>, x: Double, y: Double): String? =
    elements.lastOrNull { (_, rect) -> rect.contains(x, y) }?.first

/**
 * The rectangle a control of this shape actually occupies.
 *
 * A [ControlShape.SQUARE] is *sized by the shorter of width and height* — it is a square, not "a
 * rectangle that happens to be square" — and a [ControlShape.CIRCLE] is drawn and pressed at the
 * inscribed radius. Only a [ControlShape.RECTANGLE] uses both numbers as written.
 *
 * This exists because the rule was written twice and the two copies disagreed. The overlay applied
 * it and the editor's preview did not, so a square with `width 0.24` and `height 0.12` was arranged
 * as a rectangle and then rendered as a square: the editor was lying about the thing it exists to
 * show. Both now ask here.
 */
public fun PixelRect.shapedAs(shape: ControlShape): PixelRect =
    if (shape == ControlShape.RECTANGLE) {
        this
    } else {
        val side = min(width, height)
        copy(width = side, height = side)
    }

/**
 * The placement that puts this control's centre at a point on a surface.
 *
 * The inverse of [resolve], and it exists so that dragging a control can be expressed as *where the
 * finger is* rather than as an accumulation of small deltas. Accumulating deltas drifts, and it
 * makes snapping impossible to write: a snap is a statement about an absolute position.
 *
 * Size, shape and rotation are untouched — this moves a control and nothing else.
 */
public fun Placement.centeredAt(surface: LayoutSurface, centerX: Double, centerY: Double): Placement {
    val unit = surface.shortSide
    if (unit <= 0.0) return this

    val originX = surface.insetLeft + anchor.originX * surface.usableWidth
    val originY = surface.insetTop + anchor.originY * surface.usableHeight
    val inwardX = if (anchor.originX == 1.0) -1.0 else 1.0
    val inwardY = if (anchor.originY == 1.0) -1.0 else 1.0

    return copy(
        offsetX = ((centerX - originX) / (unit * inwardX))
            .coerceIn(-Placement.MAX_OFFSET, Placement.MAX_OFFSET),
        offsetY = ((centerY - originY) / (unit * inwardY))
            .coerceIn(-Placement.MAX_OFFSET, Placement.MAX_OFFSET),
    )
}
