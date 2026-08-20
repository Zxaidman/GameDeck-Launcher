package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationId
import io.github.zxaidman.kestrel.core.input.GamepadControl
import io.github.zxaidman.kestrel.core.settings.KestrelSettings
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

    /**
     * Every combination a user can actually produce.
     *
     * The two failures this catches are the two that were shipped: a layout authored at one size
     * and then scaled again, and a maximum setting that puts controls on top of each other. Both
     * looked correct in the one orientation and at the one size they were checked at.
     */
    private fun everyScreen(): List<Pair<LayoutSurface, Double>> {
        val surfaces = listOf(LayoutSurface(2400.0, 1080.0), LayoutSurface(1080.0, 2400.0))
        val scales = listOf(
            KestrelSettings.MIN_CONTROL_SCALE,
            0.60,
            KestrelSettings.DEFAULT_CONTROL_SCALE,
            KestrelSettings.MAX_CONTROL_SCALE,
        )
        return surfaces.flatMap { surface -> scales.map { surface to it } }
    }

    @Test
    fun `every control stays on the screen at every size, in both orientations`() {
        everyScreen().forEach { (surface, scale) ->
            xbox().elements.forEach { element ->
                val rect = element.placement.scaledBy(scale).resolve(surface)
                assertTrue(
                    rect.isWithin(surface),
                    "'${element.id}' leaves the screen at ${scale.times(100).toInt()}% on " +
                        "${surface.widthPx.toInt()}x${surface.heightPx.toInt()}: $rect",
                )
            }
        }
    }

    @Test
    fun `no two controls overlap at any size, in either orientation`() {
        // Compared as circles rather than as bounding boxes, because these controls are round and
        // two squares touching at a corner is not two controls touching. A diamond of four face
        // buttons has overlapping boxes by construction and no overlapping buttons at all.
        everyScreen().forEach { (surface, scale) ->
            val placed = xbox().elements.map { it.id to it.placement.scaledBy(scale).resolve(surface) }
            for (i in placed.indices) {
                for (j in i + 1 until placed.size) {
                    val (leftId, left) = placed[i]
                    val (rightId, right) = placed[j]
                    val apart = hypot(left.centerX - right.centerX, left.centerY - right.centerY)
                    val touching = minOf(left.width, left.height) / 2 +
                        minOf(right.width, right.height) / 2
                    assertTrue(
                        apart >= touching,
                        "'$leftId' and '$rightId' overlap at ${scale.times(100).toInt()}% on " +
                            "${surface.widthPx.toInt()}x${surface.heightPx.toInt()}: " +
                            "centres ${apart.toInt()}px apart, touching at ${touching.toInt()}px",
                    )
                }
            }
        }
    }

    @Test
    fun `the default size reproduces the arrangement measured on the reference device`() {
        // The pad the project owner tested and approved had face buttons 112px across on a 1080px
        // short side. The document is authored larger so the setting has somewhere to grow, and the
        // default brings it back to what a hand settled on.
        val surface = LayoutSurface(2400.0, 1080.0)
        val faceA = xbox().element("face.a")!!
        val rect = faceA.placement
            .scaledBy(KestrelSettings.DEFAULT_CONTROL_SCALE)
            .resolve(surface)
        assertEquals(112.0, rect.width, 4.0, "the default size no longer matches the tested pad")
    }

    @Test
    fun `numbers in the shipped layout are readable, because people edit this file`() {
        // Two decimals. A layout a user is invited to open in a text editor should not be full of
        // 0.22437499999999998.
        xbox().elements.forEach { element ->
            listOf(
                "offsetX" to element.placement.offsetX,
                "offsetY" to element.placement.offsetY,
                "width" to element.placement.width,
                "height" to element.placement.height,
            ).forEach { (field, value) ->
                val rounded = Math.round(value * 100.0) / 100.0
                assertEquals(
                    rounded,
                    value,
                    1e-9,
                    "'${element.id}' has $field = $value, which is not two decimals",
                )
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
