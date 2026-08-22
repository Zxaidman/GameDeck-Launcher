package io.github.zxaidman.kestrel.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputTrailTest {

    @Test
    fun `keeps marks in the order they happened`() {
        val trail = InputTrail(capacity = 8)
        trail.add(1L, "key", "A down")
        trail.add(2L, "key", "A up")

        assertEquals(listOf("A down", "A up"), trail.snapshot().map { it.detail })
        assertEquals(0L, trail.dropped)
    }

    @Test
    fun `keeps the newest when full, and says how many it dropped`() {
        val trail = InputTrail(capacity = 3)
        repeat(5) { trail.add(it.toLong(), "key", "press $it") }

        // The oldest two are gone; the newest three remain. A trail that kept the oldest would fill
        // with the moments before the interesting one and never reach it.
        assertEquals(listOf("press 2", "press 3", "press 4"), trail.snapshot().map { it.detail })
        assertEquals(2L, trail.dropped)
    }

    @Test
    fun `a cleared trail reports nothing dropped`() {
        val trail = InputTrail(capacity = 2)
        repeat(4) { trail.add(it.toLong(), "axis", "x") }
        trail.clear()

        assertTrue(trail.snapshot().isEmpty())
        assertEquals(0L, trail.dropped)
    }

    @Test
    fun `a resting value records nothing and a moving one records`() {
        assertFalse(changedEnough(0.500, 0.505))
        assertFalse(changedEnough(0.500, 0.500))
        assertTrue(changedEnough(0.500, 0.530))
        // Direction does not matter; distance does.
        assertTrue(changedEnough(0.500, 0.470))
    }

    @Test
    fun `the threshold is inclusive, so a value exactly at it is recorded`() {
        assertTrue(changedEnough(0.0, 0.02))
    }
}
