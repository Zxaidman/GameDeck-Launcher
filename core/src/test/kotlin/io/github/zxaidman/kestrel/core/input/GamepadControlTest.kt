package io.github.zxaidman.kestrel.core.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GamepadControlTest {

    @Test
    fun `every wire name is unique, because they are schema and not labels`() {
        val names = GamepadControl.entries.map { it.wireName }
        assertEquals(names.size, names.toSet().size, "two controls share a wire name")
    }

    @Test
    fun `wire names are lower-case and hyphenated, so a document never depends on casing`() {
        GamepadControl.entries.forEach {
            assertTrue(
                it.wireName.matches(Regex("[a-z]+(-[a-z]+)*")),
                "'${it.wireName}' is not a lower-case hyphenated name",
            )
        }
    }

    @Test
    fun `a stick press is a button, not a stick`() {
        // It is a separate control a layout can place anywhere, which is why the reference layout
        // can put L3 beside the stick rather than on it.
        assertEquals(ControlForm.BUTTON, GamepadControl.LEFT_STICK_PRESS.form)
        assertEquals(ControlForm.STICK, GamepadControl.LEFT_STICK.form)
    }

    @Test
    fun `lookup is by wire name and rejects anything else`() {
        assertEquals(GamepadControl.LEFT_TRIGGER, GamepadControl.of("left-trigger"))
        assertNull(GamepadControl.of("L2"))
        assertNull(GamepadControl.of("left_trigger"))
        assertNull(GamepadControl.of(""))
    }

    @Test
    fun `a standard pad's controls are all present`() {
        listOf(
            "a", "b", "x", "y",
            "left-bumper", "right-bumper",
            "left-trigger", "right-trigger",
            "left-stick", "right-stick",
            "left-stick-press", "right-stick-press",
            "start", "select", "dpad",
        ).forEach { assertNotNull(GamepadControl.of(it), "missing '$it'") }
    }

    @Test
    fun `controls can be listed by form, which is what an error message needs`() {
        assertEquals(
            listOf(GamepadControl.LEFT_STICK, GamepadControl.RIGHT_STICK),
            GamepadControl.withForm(ControlForm.STICK),
        )
        assertEquals(listOf(GamepadControl.DPAD), GamepadControl.withForm(ControlForm.DPAD))
    }
}
