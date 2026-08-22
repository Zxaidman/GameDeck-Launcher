package io.github.zxaidman.kestrel.core.input

/**
 * What Kestrel can currently do about input, as one value the whole product agrees on.
 *
 * The states and their meanings are decided in `docs/DEGRADED_STATE.md`. The reason this exists as
 * a domain type rather than a screen's local state is that the answer has to be identical on the
 * home screen, in the editor, at session start, and in the session itself — and the reduced states
 * are not rare. The preferred backend needs Shizuku, and Shizuku stops on every reboot, so
 * **every user's phone is in a reduced state after every restart**.
 */
public enum class CapabilityState {

    /** A session is open on the preferred backend. */
    FULL,

    /** The preferred backend is available; no session is open yet. */
    READY,

    /** The preferred backend is unavailable and a fallback can be used instead. */
    REDUCED,

    /**
     * No input backend is available.
     *
     * Everything except playing still works: browsing and adding targets, layouts, skins, settings.
     * The application never refuses to start because input is unavailable
     * (`docs/DEGRADED_STATE.md` §2).
     */
    CONFIGURE_ONLY,
    ;

    /** Whether a session can be started at all in this state. */
    public val canStartSession: Boolean
        get() = this == FULL || this == READY

    /** Whether the user should be told something is missing, prominently. */
    public val needsAttention: Boolean
        get() = this == REDUCED || this == CONFIGURE_ONLY
}

/**
 * The capabilities available in a given state, given what each backend provides.
 *
 * Kept as a function of the state rather than a property of it: the fallback's capabilities are an
 * expectation today (`ADR-006`) and will be replaced by measurements, and the preferred backend's
 * set could differ on hardware nobody has tested yet. The state says which backend is in play; the
 * backend says what it can do.
 */
public fun capabilitiesFor(
    state: CapabilityState,
    preferred: Set<InputCapability> = InputCapability.VIRTUAL_CONTROLLER,
    fallback: Set<InputCapability> = InputCapability.TOUCH_FALLBACK_EXPECTED,
): Set<InputCapability> = when (state) {
    CapabilityState.FULL, CapabilityState.READY -> preferred
    CapabilityState.REDUCED -> fallback
    CapabilityState.CONFIGURE_ONLY -> emptySet()
}
