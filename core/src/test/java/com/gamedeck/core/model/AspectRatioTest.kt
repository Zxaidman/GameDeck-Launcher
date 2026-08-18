package com.gamedeck.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for aspect ratio presets.
 */
class AspectRatioTest {

    @Test
    fun `default presets include all required ratios`() {
        val ids = AspectRatioPresets.DEFAULT.map { it.id }
        assertTrue("4:3" in ids)
        assertTrue("16:9" in ids)
        assertTrue("18:9" in ids)
        assertTrue("19.5:9" in ids)
        assertTrue("20:9" in ids)
        assertTrue("21:9" in ids)
    }

    @Test
    fun `all default presets are builtin`() {
        assertTrue(AspectRatioPresets.DEFAULT.all { it.builtin })
    }

    @Test
    fun `preset dimensions are correct`() {
        val ratio = AspectRatioPresets.DEFAULT.first { it.id == "16:9" }
        assertEquals(16, ratio.width)
        assertEquals(9, ratio.height)
    }

    @Test
    fun `preset ids are unique`() {
        val ids = AspectRatioPresets.DEFAULT.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}