package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A tall phone in landscape, the ordinary case. */
private val WIDE = LayoutSurface(widthPx = 2400.0, heightPx = 1080.0)

/** A squarer screen, the case that breaks naive normalisation. */
private val SQUARE = LayoutSurface(widthPx = 1600.0, heightPx = 1200.0)

private fun placement(
    anchor: Anchor,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0,
    size: Double = 0.1,
    rotation: Double = 0.0,
) = Placement(anchor, offsetX, offsetY, size, size, rotation)

class PlacementTest {

    @Test
    fun `a control anchored to a corner stays in that corner on any shape of screen`() {
        val button = placement(Anchor.BOTTOM_LEFT, offsetX = 0.2, offsetY = 0.2)

        val onWide = button.resolve(WIDE)
        val onSquare = button.resolve(SQUARE)

        // The thing that must hold: distance from the corner a thumb rests on, in units of the
        // short side, is identical. Normalising against width and height would have moved this
        // control 400px further from the corner on the wider screen.
        assertEquals(0.2, onWide.centerX / WIDE.shortSide, 1e-9)
        assertEquals(0.2, onSquare.centerX / SQUARE.shortSide, 1e-9)
        assertEquals(0.2, (WIDE.heightPx - onWide.centerY) / WIDE.shortSide, 1e-9)
        assertEquals(0.2, (SQUARE.heightPx - onSquare.centerY) / SQUARE.shortSide, 1e-9)
    }

    @Test
    fun `offsets move inwards from whichever corner a control is anchored to`() {
        val left = placement(Anchor.BOTTOM_LEFT, offsetX = 0.2, offsetY = 0.2).resolve(WIDE)
        val right = placement(Anchor.BOTTOM_RIGHT, offsetX = 0.2, offsetY = 0.2).resolve(WIDE)

        // Same positive offsets, mirrored result. An author never writes a negative number to move
        // a right-hand control away from the right edge.
        assertEquals(WIDE.widthPx - right.centerX, left.centerX, 1e-9)
        assertEquals(left.centerY, right.centerY, 1e-9)
    }

    @Test
    fun `a square control stays square when the screen shape changes`() {
        val button = placement(Anchor.CENTER, size = 0.15)

        val onWide = button.resolve(WIDE)
        val onSquare = button.resolve(SQUARE)

        assertEquals(onWide.width, onWide.height, 1e-9)
        assertEquals(onSquare.width, onSquare.height, 1e-9)
        // Sized against the short side, so it is the same fraction of the hand's reach either way.
        assertEquals(0.15 * WIDE.shortSide, onWide.width, 1e-9)
        assertEquals(0.15 * SQUARE.shortSide, onSquare.width, 1e-9)
    }

    @Test
    fun `rotating the phone does not resize controls`() {
        val portrait = LayoutSurface(widthPx = 1080.0, heightPx = 2400.0)
        val button = placement(Anchor.CENTER, size = 0.15)

        assertEquals(button.resolve(WIDE).width, button.resolve(portrait).width, 1e-9)
    }

    @Test
    fun `insets are subtracted, so a cutout does not swallow a control`() {
        val withCutout = WIDE.copy(insetLeft = 100.0, insetBottom = 60.0)
        val button = placement(Anchor.BOTTOM_LEFT, offsetX = 0.0, offsetY = 0.0)

        val rect = button.resolve(withCutout)

        assertEquals(100.0, rect.centerX, 1e-9)
        assertEquals(1020.0, rect.centerY, 1e-9)
    }

    @Test
    fun `an absurd size is refused with the field named`() {
        val outcome = Placement.of(Anchor.CENTER, 0.0, 0.0, width = 9.0, height = 0.1)

        val error = assertInstanceOf(
            ConfigurationError.OutOfRange::class.java,
            (outcome as Outcome.Failure).error,
        )
        assertEquals("width", error.path)
    }

    @Test
    fun `a size of zero is refused, because an invisible control cannot be pressed or fixed`() {
        assertInstanceOf(
            Outcome.Failure::class.java,
            Placement.of(Anchor.CENTER, 0.0, 0.0, width = 0.0, height = 0.1),
        )
    }

    @Test
    fun `a valid placement is accepted`() {
        assertInstanceOf(
            Outcome.Success::class.java,
            Placement.of(Anchor.BOTTOM_RIGHT, 0.2, 0.2, 0.12, 0.12, rotationDegrees = 15.0),
        )
    }
}

class HitTestTest {

    @Test
    fun `a touch inside an upright control hits it`() {
        val rect = PixelRect(centerX = 100.0, centerY = 100.0, width = 40.0, height = 40.0)

        assertTrue(rect.contains(100.0, 100.0))
        assertTrue(rect.contains(119.0, 81.0))
        assertFalse(rect.contains(121.0, 100.0))
    }

    @Test
    fun `a rotated control does not answer for touches outside itself`() {
        // A tall control turned 45 degrees. Its bounding box covers the top-right corner area, but
        // the control itself does not.
        val rect = PixelRect(
            centerX = 100.0,
            centerY = 100.0,
            width = 20.0,
            height = 100.0,
            rotationDegrees = 45.0,
        )

        // Turned 45 degrees, the long axis runs down-left. The down-right diagonal is therefore
        // inside the bounding box — whose half-extent is about 42px — and outside the control.
        assertFalse(rect.contains(140.0, 140.0))
        assertTrue(rect.boundsOverlap(PixelRect(140.0, 140.0, 4.0, 4.0)))
        // The bounding box says yes and the control says no. That difference is the whole reason
        // hit testing rotates the point instead of testing the box.
    }

    @Test
    fun `a rotated control answers for touches along its own long axis`() {
        val rect = PixelRect(
            centerX = 100.0,
            centerY = 100.0,
            width = 20.0,
            height = 100.0,
            rotationDegrees = 45.0,
        )

        // Screen coordinates grow downwards, so 45 degrees clockwise sends the long axis
        // down-left and up-right. Getting this backwards is exactly the kind of mistake that makes
        // a rotated control respond to the wrong half of the screen.
        assertTrue(rect.contains(70.0, 130.0))
        assertTrue(rect.contains(130.0, 70.0))
    }

    @Test
    fun `a rotated control's bounds account for the rotation`() {
        val upright = PixelRect(100.0, 100.0, 20.0, 100.0)
        val turned = upright.copy(rotationDegrees = 45.0)

        // The first version reported a rotated control's bounds as its unrotated width and height,
        // which is not a conservative approximation — it is wrong in both directions. A turned
        // control would have been reported as clear of a neighbour it visibly overlaps, and as
        // fitting inside a surface it hangs out of.
        assertTrue(turned.right - turned.left > upright.right - upright.left)
        assertEquals(42.43, turned.right - turned.centerX, 0.01)

        val neighbour = PixelRect(135.0, 100.0, 20.0, 20.0)
        assertFalse(upright.boundsOverlap(neighbour))
        assertTrue(turned.boundsOverlap(neighbour))
    }

    @Test
    fun `the control drawn on top is the one touched`() {
        val under = "under" to PixelRect(100.0, 100.0, 60.0, 60.0)
        val over = "over" to PixelRect(110.0, 100.0, 60.0, 60.0)

        assertEquals("over", hitTest(listOf(under, over), 105.0, 100.0))
        assertEquals("under", hitTest(listOf(under, over), 75.0, 100.0))
    }

    @Test
    fun `a touch on nothing hits nothing`() {
        val rect = "a" to PixelRect(100.0, 100.0, 20.0, 20.0)

        assertNull(hitTest(listOf(rect), 500.0, 500.0))
    }
}

class BoundsTest {

    @Test
    fun `a control inside the usable area reports as within it`() {
        val rect = placement(Anchor.BOTTOM_LEFT, offsetX = 0.2, offsetY = 0.2).resolve(WIDE)

        assertTrue(rect.isWithin(WIDE))
    }

    @Test
    fun `a control hanging off the edge is reported, not corrected`() {
        val rect = placement(Anchor.BOTTOM_LEFT, offsetX = 0.0, offsetY = 0.0, size = 0.2)
            .resolve(WIDE)

        // Centred on the corner, so half of it is off-screen. The editor warns; nothing moves it.
        assertFalse(rect.isWithin(WIDE))
    }

    @Test
    fun `a control inside the screen but under an inset is not within the usable area`() {
        val withGestureBar = WIDE.copy(insetBottom = 80.0)
        val rect = PixelRect(centerX = 200.0, centerY = 1050.0, width = 40.0, height = 40.0)

        assertFalse(rect.isWithin(withGestureBar))
    }
}

/**
 * The two rules the editor and the overlay now share, rather than each keeping its own copy.
 *
 * Both of these were faults before they were tests. A square was arranged as a rectangle in the
 * editor and rendered as a square by the pad, and dragging accumulated deltas instead of stating a
 * position, which made snapping impossible to express.
 */
class ShapeAndPlacementTest {

    private fun rect(w: Double, h: Double) = PixelRect(100.0, 200.0, w, h)

    @Test
    fun `a square takes the shorter of its two sides, for both`() {
        val shaped = rect(240.0, 120.0).shapedAs(ControlShape.SQUARE)
        assertEquals(120.0, shaped.width)
        assertEquals(120.0, shaped.height)
    }

    @Test
    fun `a circle is measured by its inscribed radius, so it agrees with what is drawn`() {
        val shaped = rect(240.0, 120.0).shapedAs(ControlShape.CIRCLE)
        assertEquals(120.0, shaped.width)
        assertEquals(120.0, shaped.height)
    }

    @Test
    fun `a rectangle keeps both numbers as written`() {
        val shaped = rect(240.0, 120.0).shapedAs(ControlShape.RECTANGLE)
        assertEquals(240.0, shaped.width)
        assertEquals(120.0, shaped.height)
    }

    @Test
    fun `shaping moves nothing`() {
        val shaped = rect(240.0, 120.0).shapedAs(ControlShape.SQUARE)
        assertEquals(100.0, shaped.centerX)
        assertEquals(200.0, shaped.centerY)
    }

    @Test
    fun `a stick is round however the document describes it`() {
        val stick = LayoutElement(
            id = "stick.left",
            kind = ControlKind.STICK,
            binds = null,
            label = null,
            shape = ControlShape.RECTANGLE,
            group = null,
            placement = Placement(Anchor.BOTTOM_LEFT, 0.2, 0.2, 0.2, 0.1),
        )
        assertEquals(ControlShape.CIRCLE, stick.effectiveShape())
        assertEquals(ControlShape.RECTANGLE, stick.copy(kind = ControlKind.BUTTON).effectiveShape())
    }

    @Test
    fun `placing a control at a point puts its centre there, from every anchor`() {
        val surface = LayoutSurface(2400.0, 1080.0)
        Anchor.entries.forEach { anchor ->
            val placement = Placement(anchor, 0.2, 0.2, 0.1, 0.1)
                .centeredAt(surface, 1234.0, 567.0)
            val rect = placement.resolve(surface)
            assertEquals(1234.0, rect.centerX, 1e-9, "x is wrong from $anchor")
            assertEquals(567.0, rect.centerY, 1e-9, "y is wrong from $anchor")
        }
    }

    @Test
    fun `placing a control changes where it is and nothing else`() {
        val surface = LayoutSurface(2400.0, 1080.0)
        val before = Placement(Anchor.BOTTOM_RIGHT, 0.2, 0.2, 0.15, 0.08, rotationDegrees = 20.0)
        val after = before.centeredAt(surface, 100.0, 100.0)

        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        assertEquals(before.anchor, after.anchor)
        assertEquals(before.rotationDegrees, after.rotationDegrees)
    }

    @Test
    fun `resolving and then placing at the same point is a round trip`() {
        val surface = LayoutSurface(1080.0, 2400.0)
        val original = Placement(Anchor.BOTTOM_LEFT, 0.31, 0.27, 0.19, 0.19)
        val rect = original.resolve(surface)
        val again = original.centeredAt(surface, rect.centerX, rect.centerY)

        assertEquals(original.offsetX, again.offsetX, 1e-9)
        assertEquals(original.offsetY, again.offsetY, 1e-9)
    }

    @Test
    fun `a point beyond what a layout may say is clamped rather than written`() {
        val surface = LayoutSurface(2400.0, 1080.0)
        val placed = Placement(Anchor.BOTTOM_LEFT, 0.0, 0.0, 0.1, 0.1)
            .centeredAt(surface, 9_000_000.0, 9_000_000.0)
        assertTrue(placed.offsetX <= Placement.MAX_OFFSET)
        assertTrue(placed.offsetY <= Placement.MAX_OFFSET)
    }

    @Test
    fun `a surface with no area is left alone rather than dividing by zero`() {
        val original = Placement(Anchor.BOTTOM_LEFT, 0.2, 0.2, 0.1, 0.1)
        assertEquals(original, original.centeredAt(LayoutSurface(0.0, 0.0), 10.0, 10.0))
    }
}

/**
 * One document, two arrangements.
 *
 * The rule these protect is that a layout written before the portrait arrangement existed still
 * means exactly what it meant — and that a build which does not understand the field does not
 * destroy it.
 */
class PerOrientationPlacementTest {

    private fun element(portraitPlacement: Placement? = null) = LayoutElement(
        id = "face.a",
        kind = ControlKind.BUTTON,
        binds = null,
        label = null,
        shape = ControlShape.CIRCLE,
        group = null,
        placement = Placement(Anchor.BOTTOM_RIGHT, 0.30, 0.20, 0.12, 0.12),
        portraitPlacement = portraitPlacement,
    )

    @Test
    fun `no portrait arrangement means the landscape one, in both`() {
        val only = element()
        assertEquals(only.placement, only.placementFor(portrait = false))
        assertEquals(only.placement, only.placementFor(portrait = true))
    }

    @Test
    fun `a portrait arrangement is used upright and nowhere else`() {
        val upright = Placement(Anchor.BOTTOM_RIGHT, 0.22, 0.44, 0.16, 0.16)
        val both = element(upright)
        assertEquals(both.placement, both.placementFor(portrait = false))
        assertEquals(upright, both.placementFor(portrait = true))
    }

    @Test
    fun `editing one orientation cannot touch the other`() {
        // The fault this prevents is the one that makes the feature pointless: arranging portrait
        // and finding landscape has moved.
        val start = element(Placement(Anchor.BOTTOM_RIGHT, 0.22, 0.44, 0.16, 0.16))
        val moved = start.withPlacementFor(
            portrait = true,
            placement = Placement(Anchor.BOTTOM_RIGHT, 0.10, 0.10, 0.16, 0.16),
        )
        assertEquals(start.placement, moved.placement)
        assertEquals(0.10, moved.placementFor(portrait = true).offsetX, 1e-9)
    }

    @Test
    fun `giving portrait its own arrangement starts it as a copy, not as nothing`() {
        val copied = element().let { it.copy(portraitPlacement = it.placement) }
        assertEquals(copied.placement, copied.placementFor(portrait = true))
    }
}
