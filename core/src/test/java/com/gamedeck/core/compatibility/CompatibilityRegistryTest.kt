package com.gamedeck.core.compatibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the built-in compatibility registry.
 */
class CompatibilityRegistryTest {

    private val registry = BuiltInCompatibilityRegistry

    @Test
    fun `known package is found`() {
        val entry = registry.lookup("org.ppsspp.ppsspp")
        assertNotNull(entry)
        assertEquals("PPSSPP", entry?.name)
    }

    @Test
    fun `unknown package returns null`() {
        val entry = registry.lookup("com.unknown.app")
        assertNull(entry)
    }

    @Test
    fun `registry contains all target applications`() {
        val packages = registry.list().map { it.packageName }
        assertTrue("org.ppsspp.ppsspp" in packages)
        assertTrue("org.dolphinemu.dolphinemu" in packages)
        assertTrue("com.retroarch" in packages)
        assertTrue("com.limelight" in packages)
        assertTrue("com.valvesoftware.steamlink" in packages)
    }

    @Test
    fun `all entries have valid status`() {
        registry.list().forEach { entry ->
            assertTrue(
                "Entry ${entry.packageName} has invalid status",
                entry.status in CompatibilityStatus.entries
            )
        }
    }
}