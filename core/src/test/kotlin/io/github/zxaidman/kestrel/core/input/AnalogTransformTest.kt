package io.github.zxaidman.kestrel.core.input

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalogTransformTest {

    private val standard = AnalogProfile(deadzone = 0.1)

    @Test
    fun `a stick at rest reads as at rest`() {
        val value = applyStick(0.0, 0.0, standard)

        assertEquals(0.0, value.magnitude, 1e-12)
    }

    @Test
    fun `movement inside the dead zone is ignored`() {
        assertEquals(0.0, applyStick(0.05, 0.05, standard).magnitude, 1e-12)
    }

    @Test
    fun `there is no jump at the edge of the dead zone`() {
        // The failure this prevents: ignoring everything below the dead zone but passing what is
        // above it unchanged, so a slow push snaps from nothing to a tenth of full deflection.
        val justInside = applyStick(0.0999, 0.0, standard).magnitude
        val justOutside = applyStick(0.1001, 0.0, standard).magnitude

        assertEquals(0.0, justInside, 1e-12)
        assertTrue(justOutside < 0.01, "expected a tiny value just past the dead zone, got $justOutside")
    }

    @Test
    fun `full deflection still reaches full output`() {
        assertEquals(1.0, applyStick(1.0, 0.0, standard).magnitude, 1e-9)
    }

    @Test
    fun `a worn stick that no longer reaches its corners can still reach full output`() {
        val worn = AnalogProfile(deadzone = 0.1, outerLimit = 0.85)

        assertEquals(1.0, applyStick(0.85, 0.0, worn).magnitude, 1e-9)
    }

    @Test
    fun `a radial dead zone does not produce a cross shaped dead area`() {
        // A diagonal push of 0.08 on each axis is 0.113 from centre — past a 0.1 dead zone. Per-axis
        // filtering would swallow it on both axes and the stick would feel dead on the diagonals.
        val diagonal = applyStick(0.08, 0.08, standard)

        assertTrue(diagonal.magnitude > 0.0, "a diagonal push past the dead zone must register")
    }

    @Test
    fun `direction is preserved exactly, only distance is reshaped`() {
        val value = applyStick(0.6, 0.3, AnalogProfile(deadzone = 0.1, curve = 2.0))

        // Where the player is aiming must not change; only how far. Two to one in, two to one out.
        assertEquals(2.0, value.x / value.y, 1e-9)
    }

    @Test
    fun `output never leaves the unit circle, whatever the sensitivity`() {
        val loud = AnalogProfile(deadzone = 0.0, sensitivity = 4.0)

        val corner = applyStick(1.0, 1.0, loud)

        assertTrue(corner.magnitude <= 1.0 + 1e-9, "magnitude was ${corner.magnitude}")
    }

    @Test
    fun `a curve above one gives finer control near the centre without losing the top`() {
        val fine = AnalogProfile(deadzone = 0.0, curve = 2.0)

        val half = applyStick(0.5, 0.0, fine).magnitude
        val full = applyStick(1.0, 0.0, fine).magnitude

        assertTrue(half < 0.5, "a curve above one should reduce mid travel, got $half")
        assertEquals(1.0, full, 1e-9)
    }

    @Test
    fun `a curve is monotonic, so pushing further never gives less`() {
        val fine = AnalogProfile(deadzone = 0.1, curve = 2.2)

        var previous = -1.0
        var raw = 0.0
        while (raw <= 1.0) {
            val magnitude = applyStick(raw, 0.0, fine).magnitude
            assertTrue(magnitude >= previous - 1e-12, "output fell at raw=$raw")
            previous = magnitude
            raw += 0.01
        }
    }

    @Test
    fun `inversion mirrors the axis and nothing else`() {
        val inverted = AnalogProfile(deadzone = 0.0, invertY = true)

        val plain = applyStick(0.4, 0.7, AnalogProfile.NONE)
        val flipped = applyStick(0.4, 0.7, inverted)

        assertEquals(plain.x, flipped.x, 1e-9)
        assertEquals(plain.y, -flipped.y, 1e-9)
    }

    @Test
    fun `values beyond the declared range are clamped rather than trusted`() {
        val value = applyStick(4.0, 0.0, AnalogProfile.NONE)

        assertTrue(value.magnitude <= 1.0 + 1e-9)
    }

    @Test
    fun `a value that is not a number reads as at rest`() {
        assertEquals(0.0, applyStick(Double.NaN, Double.NaN, standard).magnitude, 1e-12)
    }

    @Test
    fun `a trigger rests at zero and reaches one`() {
        assertEquals(0.0, applyTrigger(0.0, AnalogProfile.DEFAULT_TRIGGER), 1e-12)
        assertEquals(1.0, applyTrigger(1.0, AnalogProfile.DEFAULT_TRIGGER), 1e-9)
    }

    @Test
    fun `a trigger ignores inversion rather than resting fully pressed`() {
        val silly = AnalogProfile(deadzone = 0.0, invertX = true, invertY = true)

        assertEquals(0.0, applyTrigger(0.0, silly), 1e-12)
    }

    @Test
    fun `half a trigger's travel is half its output on a linear profile`() {
        assertEquals(0.5, applyTrigger(0.5, AnalogProfile(deadzone = 0.0)), 1e-9)
    }

    @Test
    fun `the value measured on the reference device passes through unchanged`() {
        // Phase 0 measured a half-pressed trigger arriving as 0.502 and a half-deflected stick as
        // -0.500. With no shaping, the transformation must not alter what the platform reported.
        assertEquals(0.502, applyTrigger(0.502, AnalogProfile.NONE), 1e-9)
        assertEquals(0.5, abs(applyStick(-0.5, 0.0, AnalogProfile.NONE).x), 1e-9)
    }

    @Test
    fun `the unit circle bound holds across the whole square of inputs`() {
        val profile = AnalogProfile(deadzone = 0.08, curve = 1.7, sensitivity = 1.5)

        var x = -1.0
        while (x <= 1.0) {
            var y = -1.0
            while (y <= 1.0) {
                val value = applyStick(x, y, profile)
                assertTrue(
                    hypot(value.x, value.y) <= 1.0 + 1e-9,
                    "escaped the unit circle at ($x, $y)",
                )
                y += 0.05
            }
            x += 0.05
        }
    }
}
