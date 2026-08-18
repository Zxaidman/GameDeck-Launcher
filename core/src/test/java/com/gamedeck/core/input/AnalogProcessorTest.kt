package com.gamedeck.core.input

import com.gamedeck.core.model.ControlBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AnalogProcessor.
 */
class AnalogProcessorTest {

    private val processor = AnalogProcessor()

    @Test
    fun `center input produces zero output`() {
        val result = processor.processStick(0f, 0f)
        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
        assertTrue(result.inDeadZone)
    }

    @Test
    fun `full right input produces full right output`() {
        val result = processor.processStick(1f, 0f)
        assertEquals(1f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun `full up input produces full up output`() {
        val result = processor.processStick(0f, 1f)
        assertEquals(0f, result.x, 0.001f)
        assertEquals(1f, result.y, 0.001f)
    }

    @Test
    fun `dead zone suppresses small inputs`() {
        val behavior = ControlBehavior(deadZone = 0.2f)
        val result = processor.processStick(0.1f, 0f, behavior)
        assertEquals(0f, result.x, 0.001f)
        assertTrue(result.inDeadZone)
    }

    @Test
    fun `input above dead zone is rescaled`() {
        val behavior = ControlBehavior(deadZone = 0.2f)
        val result = processor.processStick(0.6f, 0f, behavior)
        // (0.6 - 0.2) / (1 - 0.2) = 0.5
        assertEquals(0.5f, result.x, 0.001f)
    }

    @Test
    fun `invert Y flips vertical axis`() {
        val behavior = ControlBehavior(invertY = true)
        val result = processor.processStick(0f, 1f, behavior)
        assertEquals(-1f, result.y, 0.001f)
    }

    @Test
    fun `trigger below dead zone is zero`() {
        val behavior = ControlBehavior(deadZone = 0.1f)
        val result = processor.processTrigger(0.05f, behavior)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `trigger full value is one`() {
        val result = processor.processTrigger(1f)
        assertEquals(1f, result, 0.001f)
    }

    @Test
    fun `trigger value is rescaled above dead zone`() {
        val behavior = ControlBehavior(deadZone = 0.2f)
        val result = processor.processTrigger(0.6f, behavior)
        // (0.6 - 0.2) / (1 - 0.2) = 0.5
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `diagonal input preserves direction`() {
        val result = processor.processStick(0.7071f, 0.7071f)
        assertEquals(1f, result.magnitude, 0.01f)
        assertEquals(0.7071f, result.x, 0.01f)
        assertEquals(0.7071f, result.y, 0.01f)
    }
}