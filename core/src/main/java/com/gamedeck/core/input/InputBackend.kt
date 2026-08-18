package com.gamedeck.core.input

/**
 * Abstraction for a controller input delivery mechanism.
 *
 * The rest of GameDeck communicates with this interface and never
 * directly with Android input APIs or Shizuku.
 */
interface InputBackend {
    /** Stable identifier for this backend */
    val id: String

    /** Capabilities this backend provides */
    val capabilities: Set<InputCapability>

    /** Initialize the backend. Must be called before sending input. */
    suspend fun initialize(): BackendResult

    /** Send a digital button event */
    suspend fun sendButton(
        button: GamepadButton,
        state: ButtonState
    ): InputResult

    /** Send an analog axis value. Values are normalized. */
    suspend fun sendAxis(
        axis: GamepadAxis,
        value: Float
    ): InputResult

    /** Release all active inputs and clean up resources */
    suspend fun shutdown()
}

/** Result of backend initialization */
sealed interface BackendResult {
    data object Success : BackendResult
    data class Failure(val reason: String) : BackendResult
}

/** Result of an individual input operation */
sealed interface InputResult {
    data object Success : InputResult
    data class Failure(val reason: String) : InputResult
}