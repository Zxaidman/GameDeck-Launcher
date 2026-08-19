package io.github.zxaidman.kestrel.core.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * These tests encode decisions rather than mechanics.
 *
 * Each one corresponds to a rule someone chose — in `ADR-007` or `docs/DEGRADED_STATE.md` — so that
 * changing the behaviour means confronting the decision rather than adjusting an expectation.
 */
class CapabilityStateTest {

    @Test
    fun `a session can only start on the preferred backend`() {
        assertTrue(CapabilityState.FULL.canStartSession)
        assertTrue(CapabilityState.READY.canStartSession)
        assertFalse(CapabilityState.REDUCED.canStartSession)
        assertFalse(CapabilityState.CONFIGURE_ONLY.canStartSession)
    }

    @Test
    fun `reduced states are the ones the user is told about`() {
        assertFalse(CapabilityState.FULL.needsAttention)
        assertFalse(CapabilityState.READY.needsAttention)
        assertTrue(CapabilityState.REDUCED.needsAttention)
        assertTrue(CapabilityState.CONFIGURE_ONLY.needsAttention)
    }

    @Test
    fun `the preferred backend provides everything Phase 0 measured`() {
        val full = capabilitiesFor(CapabilityState.FULL)
        assertTrue(InputCapability.ANALOG_STICK in full)
        assertTrue(InputCapability.ANALOG_TRIGGER in full)
        assertTrue(InputCapability.SIMULTANEOUS in full)
        assertTrue(InputCapability.DEVICE_IDENTITY in full)
    }

    @Test
    fun `the touch fallback has no analog controls and no device identity`() {
        val reduced = capabilitiesFor(CapabilityState.REDUCED)
        assertFalse(InputCapability.ANALOG_STICK in reduced)
        assertFalse(InputCapability.ANALOG_TRIGGER in reduced)
        assertFalse(InputCapability.DEVICE_IDENTITY in reduced)
        assertTrue(InputCapability.BUTTONS in reduced)
    }

    @Test
    fun `configure only provides no input at all`() {
        assertEquals(emptySet<InputCapability>(), capabilitiesFor(CapabilityState.CONFIGURE_ONLY))
    }
}

class ControlAvailabilityTest {

    private val stick = ControlRequirement("left-stick", setOf(InputCapability.ANALOG_STICK))
    private val buttonA = ControlRequirement("button-a", setOf(InputCapability.BUTTONS))
    private val trigger = ControlRequirement("l2", setOf(InputCapability.ANALOG_TRIGGER))
    private val decoration = ControlRequirement("label", emptySet())

    @Test
    fun `a control needing nothing is always available`() {
        assertEquals(
            ControlAvailability.AVAILABLE,
            availabilityOf(decoration, emptySet()),
        )
    }

    @Test
    fun `a stick is disabled rather than removed when the backend cannot do analog`() {
        val availability = availabilityOf(stick, InputCapability.TOUCH_FALLBACK_EXPECTED)

        // ADR-007: the only two outcomes are available and disabled. Substitution and removal are
        // not representable here on purpose — a stick never quietly becomes a d-pad.
        assertEquals(ControlAvailability.DISABLED_BY_CAPABILITY, availability)
        assertFalse(availability.isUsable)
    }

    @Test
    fun `the same layout keeps every control in both tiers`() {
        val layout = listOf(stick, buttonA, trigger, decoration)

        val onPreferred = layout.map { availabilityOf(it, InputCapability.VIRTUAL_CONTROLLER) }
        val onFallback = layout.map { availabilityOf(it, InputCapability.TOUCH_FALLBACK_EXPECTED) }

        // The point of ADR-007: one layout, same controls, different availability.
        assertEquals(layout.size, onPreferred.size)
        assertEquals(layout.size, onFallback.size)
        assertTrue(onPreferred.all { it.isUsable })
        assertEquals(2, onFallback.count { it.isUsable })
    }

    @Test
    fun `disabled controls are known before a session starts`() {
        val layout = listOf(stick, buttonA, trigger)

        val disabled = disabledControls(layout, InputCapability.TOUCH_FALLBACK_EXPECTED)

        assertEquals(listOf("left-stick", "l2"), disabled.map { it.controlId })
    }

    @Test
    fun `nothing needs saying when the whole layout works`() {
        val layout = listOf(stick, buttonA, trigger)

        assertTrue(disabledControls(layout, InputCapability.VIRTUAL_CONTROLLER).isEmpty())
    }

    @Test
    fun `the reason is reported as missing capabilities, not just control names`() {
        val layout = listOf(stick, buttonA, trigger)

        val missing = missingCapabilities(layout, InputCapability.TOUCH_FALLBACK_EXPECTED)

        assertEquals(
            setOf(InputCapability.ANALOG_STICK, InputCapability.ANALOG_TRIGGER),
            missing,
        )
    }

    @Test
    fun `with no backend every control that needs anything is disabled`() {
        val layout = listOf(stick, buttonA, trigger, decoration)

        val disabled = disabledControls(layout, capabilitiesFor(CapabilityState.CONFIGURE_ONLY))

        assertEquals(listOf("left-stick", "button-a", "l2"), disabled.map { it.controlId })
    }
}
