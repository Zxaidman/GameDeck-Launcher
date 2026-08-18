package com.gamedeck.core.input

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for InputEngine.
 */
class InputEngineTest {

    @Test
    fun `initialize activates backend`() = runTest {
        val engine = InputEngine { FakeBackend() }
        val result = engine.initialize()

        assertTrue(result is BackendResult.Success)
        assertEquals("fake", engine.backendId.value)
    }

    @Test
    fun `send button forwards to backend`() = runTest {
        val backend = FakeBackend()
        val engine = InputEngine { backend }
        engine.initialize()

        val result = engine.sendButton(GamepadButton.A, ButtonState.DOWN)
        assertTrue(result is InputResult.Success)
        assertEquals(1, backend.buttonEvents.size)
        assertEquals(GamepadButton.A, backend.buttonEvents[0].first)
    }

    @Test
    fun `send axis forwards to backend`() = runTest {
        val backend = FakeBackend()
        val engine = InputEngine { backend }
        engine.initialize()

        val result = engine.sendAxis(GamepadAxis.LEFT_X, 0.5f)
        assertTrue(result is InputResult.Success)
        assertEquals(1, backend.axisEvents.size)
        assertEquals(0.5f, backend.axisEvents[0].second, 0.001f)
    }

    @Test
    fun `shutdown releases all pressed buttons`() = runTest {
        val backend = FakeBackend()
        val engine = InputEngine { backend }
        engine.initialize()

        engine.sendButton(GamepadButton.A, ButtonState.DOWN)
        engine.sendButton(GamepadButton.B, ButtonState.DOWN)
        engine.sendAxis(GamepadAxis.LEFT_X, 0.5f)

        engine.shutdown()

        // Both buttons should have been released
        val releasedButtons = backend.buttonEvents.filter { it.second == ButtonState.UP }
        assertEquals(2, releasedButtons.size)
        // Axis should have been reset to 0
        val resetAxes = backend.axisEvents.filter { it.second == 0f }
        assertTrue(resetAxes.isNotEmpty())
        // Backend should be shut down
        assertTrue(backend.isShutdown)
        // No active backend
        assertEquals(null, engine.backendId.value)
    }

    @Test
    fun `send without backend fails gracefully`() = runTest {
        val engine = InputEngine { FakeBackend() }
        // Don't initialize

        val result = engine.sendButton(GamepadButton.A, ButtonState.DOWN)
        assertTrue(result is InputResult.Failure)
    }
}

/**
 * Fake backend for testing.
 */
class FakeBackend : InputBackend {
    override val id: String = "fake"
    override val capabilities: Set<InputCapability> = setOf(
        InputCapability.DIGITAL_BUTTONS,
        InputCapability.ANALOG_AXES
    )

    val buttonEvents = mutableListOf<Pair<GamepadButton, ButtonState>>()
    val axisEvents = mutableListOf<Pair<GamepadAxis, Float>>()
    var isShutdown = false

    override suspend fun initialize(): BackendResult = BackendResult.Success

    override suspend fun sendButton(button: GamepadButton, state: ButtonState): InputResult {
        buttonEvents.add(button to state)
        return InputResult.Success
    }

    override suspend fun sendAxis(axis: GamepadAxis, value: Float): InputResult {
        axisEvents.add(axis to value)
        return InputResult.Success
    }

    override suspend fun shutdown() {
        isShutdown = true
    }
}