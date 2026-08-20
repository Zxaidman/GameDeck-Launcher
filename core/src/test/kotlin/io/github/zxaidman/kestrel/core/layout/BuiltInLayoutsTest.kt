package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationId
import io.github.zxaidman.kestrel.core.input.GamepadControl
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The built-in is a shipped file, so these are not tests of a fixture — they are the check that
 * what Kestrel ships parses, validates, and describes a pad someone can actually play with.
 */
class BuiltInLayoutsTest {

    private fun xbox(): ControllerLayout {
        val outcome = BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT)
        assertTrue(outcome is Outcome.Success, "the built-in layout did not load: $outcome")
        return (outcome as Outcome.Success).value
    }

    @Test
    fun `the shipped layout parses and validates through the ordinary reader`() {
        val layout = xbox()
        assertEquals("Xbox — default", layout.header.name)
        assertEquals(LayoutOrientation.ANY, layout.orientation)
    }

    @Test
    fun `it is a built-in, which is what makes it immutable`() {
        val id = xbox().header.id
        assertEquals(BuiltInLayouts.XBOX_DEFAULT, id.value)
        assertTrue(id.isBuiltIn, "a shipped layout that is not a built-in could be edited in place")
    }

    @Test
    fun `every control a standard pad has is present exactly once`() {
        val layout = xbox()
        assertEquals(
            GamepadControl.entries.toSet(),
            layout.boundControls,
            "the built-in does not offer every control the pad declares",
        )

        val bindings = layout.elements.mapNotNull { it.binds }
        assertEquals(
            bindings.size,
            bindings.toSet().size,
            "a control is bound by two elements, so pressing one would be ambiguous",
        )
    }

    @Test
    fun `the triggers are analog, because the backend sends an axis`() {
        val layout = xbox()
        listOf(GamepadControl.LEFT_TRIGGER, GamepadControl.RIGHT_TRIGGER).forEach { trigger ->
            val element = layout.elements.single { it.binds == trigger }
            assertEquals(ControlKind.ANALOG_TRIGGER, element.kind)
        }
    }

    @Test
    fun `nothing is anchored where a thumb cannot reach it`() {
        // Sticks, pad and face buttons belong to the bottom corners; shoulders to the top ones.
        // A control that drifts to the middle of the screen is unreachable while holding a phone.
        val layout = xbox()
        layout.elements.forEach { element ->
            assertTrue(
                element.placement.anchor != Anchor.CENTER,
                "'${element.id}' is anchored to the centre of the screen",
            )
        }
    }

    @Test
    fun `every control fits on a landscape phone without leaving the screen`() {
        // 2400x1080 is the reference device in landscape. A layout whose controls resolve outside
        // the surface is one whose controls cannot be pressed.
        val surface = LayoutSurface(2400.0, 1080.0)
        xbox().elements.forEach { element ->
            val rect = element.placement.resolve(surface)
            assertTrue(
                rect.isWithin(surface),
                "'${element.id}' resolves to $rect, which is outside the screen",
            )
        }
    }

    @Test
    fun `every control fits on a portrait phone too, since the layout claims either orientation`() {
        val surface = LayoutSurface(1080.0, 2400.0)
        xbox().elements.forEach { element ->
            val rect = element.placement.resolve(surface)
            assertTrue(
                rect.isWithin(surface),
                "'${element.id}' resolves to $rect, which is outside the screen",
            )
        }
    }

    @Test
    fun `no two controls overlap, so a thumb cannot press two at once by accident`() {
        // Compared as circles rather than as bounding boxes, because these controls are round and
        // two squares touching at a corner is not two controls touching. A diamond of four face
        // buttons has overlapping boxes by construction and no overlapping buttons at all.
        listOf(LayoutSurface(2400.0, 1080.0), LayoutSurface(1080.0, 2400.0)).forEach { surface ->
            val placed = xbox().elements.map { it.id to it.placement.resolve(surface) }
            for (i in placed.indices) {
                for (j in i + 1 until placed.size) {
                    val (leftId, left) = placed[i]
                    val (rightId, right) = placed[j]
                    val apart = hypot(left.centerX - right.centerX, left.centerY - right.centerY)
                    val touching = minOf(left.width, left.height) / 2 +
                        minOf(right.width, right.height) / 2
                    assertTrue(
                        apart >= touching,
                        "'$leftId' and '$rightId' overlap on " +
                            "${surface.widthPx.toInt()}x${surface.heightPx.toInt()}: " +
                            "centres ${apart.toInt()}px apart, touching at ${touching.toInt()}px",
                    )
                }
            }
        }
    }

    @Test
    fun `an id that was never shipped fails as an unresolved reference, not as a crash`() {
        val outcome = BuiltInLayouts.load("builtin.nothing.here")
        assertTrue(outcome is Outcome.Failure, "a missing built-in was reported as success")
    }

    @Test
    fun `every advertised id loads`() {
        BuiltInLayouts.ids().forEach { id ->
            assertTrue(
                BuiltInLayouts.load(id) is Outcome.Success,
                "'$id' is advertised but does not load",
            )
            assertTrue(ConfigurationId.parse(id) is Outcome.Success, "'$id' is not a valid id")
        }
    }
}
