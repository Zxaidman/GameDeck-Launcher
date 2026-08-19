package io.github.zxaidman.kestrel.core.input

/**
 * Something an input backend can or cannot do.
 *
 * These are **controller semantics**, not platform facts: nothing here names a key code, an axis
 * constant, or a privilege level. A backend reports which of these it provides, and the rest of the
 * product asks the question in these terms (`CLAUDE.md` §5).
 *
 * The set is deliberately about what separates one backend from another. Phase 0 measured the
 * difference between a virtual controller and shell injection precisely along these lines: the
 * privileged path delivered every one of them, and the platform's `input` command could not reach a
 * right stick or a trigger at all (`docs/phase0/results/tier3-injection-report.md`).
 */
public enum class InputCapability {

    /** Digital face and shoulder buttons. Every backend worth having provides this. */
    BUTTONS,

    /** A directional pad, as discrete directions. */
    DPAD,

    /** A stick reporting continuous values, not just eight directions. */
    ANALOG_STICK,

    /** A trigger reporting how far it is pressed, rather than only that it is. */
    ANALOG_TRIGGER,

    /**
     * More than one control held at once, arriving as simultaneous state.
     *
     * A backend that queues input instead of holding it cannot support anything that needs two
     * controls together, which is most things.
     */
    SIMULTANEOUS,

    /**
     * The target sees a controller device rather than touches or key presses.
     *
     * This is the capability that decides whether a binding screen can bind anything, and whether a
     * streaming client will forward input as a controller. A backend without it can still drive
     * some targets, but never as a controller.
     */
    DEVICE_IDENTITY,

    /** The controller reports rumble the target can drive. */
    VIBRATION,
    ;

    public companion object {

        /**
         * What the preferred backend provided on the reference device, measured rather than assumed
         * (`docs/phase0/results/tier5-exercise-report.md`, `docs/phase0/results/tier6-report.md`).
         *
         * Vibration is absent because the created device declared no vibrator and nothing tested
         * one.
         */
        public val VIRTUAL_CONTROLLER: Set<InputCapability> = setOf(
            BUTTONS,
            DPAD,
            ANALOG_STICK,
            ANALOG_TRIGGER,
            SIMULTANEOUS,
            DEVICE_IDENTITY,
        )

        /**
         * What a touch fallback is *expected* to provide (`ADR-006`).
         *
         * **Expected, not measured.** Nothing about the fallback has been tested. A gesture has a
         * position but no magnitude, so analog controls are absent; the target sees touches, so
         * there is no device identity. This constant will be corrected by evidence, and it is
         * written here rather than assumed at a call site so that there is one place to correct.
         */
        public val TOUCH_FALLBACK_EXPECTED: Set<InputCapability> = setOf(
            BUTTONS,
            DPAD,
        )
    }
}
