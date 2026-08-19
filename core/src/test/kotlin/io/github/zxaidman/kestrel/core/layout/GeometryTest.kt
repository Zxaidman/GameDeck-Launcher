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
