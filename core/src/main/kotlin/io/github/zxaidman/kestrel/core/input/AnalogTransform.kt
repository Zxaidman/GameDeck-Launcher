package io.github.zxaidman.kestrel.core.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign

/**
 * Turning what a stick reports into what the player meant.
 *
 * `CLAUDE.md` §5 puts dead zone, sensitivity, inversion and curves in the transformation layer
 * rather than in any backend, and requires them to be pure and unit-testable. This is that layer.
 * Nothing here knows what produced the values.
 *
 * Phase 0 established the units this works in: the platform hands over sticks already normalised to
 * `-1…+1` and triggers to `0…+1` (`docs/phase0/results/tier5-exercise-report.md`), so no conversion
 * happens here — only the shaping the player asked for.
 */

/** A stick position after transformation. Both components are within the unit circle. */
public data class StickValue(public val x: Double, public val y: Double) {
    public val magnitude: Double get() = hypot(x, y)

    public companion object {
        public val CENTER: StickValue = StickValue(0.0, 0.0)
    }
}

/**
 * How a dead zone is measured.
 *
 * The difference is not cosmetic. A per-axis dead zone on a stick produces a cross-shaped dead
 * area: a small diagonal push is ignored on both axes even though the stick has clearly moved, and
 * pushing along one axis lets the other pass unfiltered, so the aim drifts. Radial measures the
 * distance from centre, which is what the player is doing with their thumb.
 */
public enum class DeadzoneShape {
    /** Distance from centre. Correct for a stick. */
    RADIAL,

    /** Each axis independently. Correct for a single axis, such as a trigger. */
    AXIAL,
}

/**
 * The shaping applied to one analog control.
 *
 * @param deadzone deflection below which the control reads as at rest, as a fraction of full travel
 * @param outerLimit deflection at or above which the control reads as fully pressed, so a stick
 *   that no longer quite reaches its corners still reports 1.0
 * @param curve exponent applied to the magnitude: 1.0 is linear, above 1.0 gives finer control near
 *   centre, below 1.0 makes the control more immediate
 * @param sensitivity multiplier applied after the curve, clamped afterwards
 * @param invertX mirrors the horizontal axis
 * @param invertY mirrors the vertical axis, which is the common preference for aiming
 */
public data class AnalogProfile(
    public val deadzone: Double = 0.10,
    public val outerLimit: Double = 1.0,
    public val curve: Double = 1.0,
    public val sensitivity: Double = 1.0,
    public val invertX: Boolean = false,
    public val invertY: Boolean = false,
    public val deadzoneShape: DeadzoneShape = DeadzoneShape.RADIAL,
) {
    public companion object {
        /** No shaping at all — what a control reports is what the player gets. */
        public val NONE: AnalogProfile = AnalogProfile(deadzone = 0.0, curve = 1.0)

        /**
         * A starting point, not a claim.
         *
         * A tenth of travel is a common default for a stick that has not been worn in. The right
         * value is a property of the hardware in the player's hands, which is why it is a setting.
         */
        public val DEFAULT_STICK: AnalogProfile = AnalogProfile()

        /** Triggers rest at zero and only travel one way, so a dead zone there is per-axis. */
        public val DEFAULT_TRIGGER: AnalogProfile =
            AnalogProfile(deadzone = 0.05, deadzoneShape = DeadzoneShape.AXIAL)
    }
}

/**
 * Rescales a deflection so that it starts from zero at the edge of the dead zone.
 *
 * This is the part that is easy to get wrong and unpleasant to use when it is wrong. Simply
 * *ignoring* everything below the dead zone leaves a jump: at 0.099 the control is at rest and at
 * 0.101 it is already a tenth of the way over, so a slow push snaps into motion. Rescaling means
 * the first movement past the dead zone is the smallest possible movement.
 */
private fun rescale(magnitude: Double, deadzone: Double, outerLimit: Double): Double {
    if (magnitude <= deadzone) return 0.0
    val span = outerLimit - deadzone
    if (span <= 0.0) return 1.0
    return ((magnitude - deadzone) / span).coerceAtMost(1.0)
}

/**
 * Applies a profile to a stick.
 *
 * Order matters and is fixed here so no caller can vary it: dead zone, then curve, then sensitivity,
 * then clamping, then inversion. Curving before the dead zone would shape a range the player cannot
 * reach; clamping before sensitivity would make sensitivity above 1.0 do nothing at the edges.
 */
public fun applyStick(rawX: Double, rawY: Double, profile: AnalogProfile): StickValue {
    val x = if (rawX.isNaN()) 0.0 else rawX.coerceIn(-1.0, 1.0)
    val y = if (rawY.isNaN()) 0.0 else rawY.coerceIn(-1.0, 1.0)

    if (profile.deadzoneShape == DeadzoneShape.AXIAL) {
        return StickValue(
            applyAxis(x, profile),
            applyAxis(y, profile),
        ).let { if (profile.invertX || profile.invertY) it.invert(profile) else it }
    }

    val magnitude = hypot(x, y)
    if (magnitude == 0.0) return StickValue.CENTER

    val shaped = rescale(magnitude, profile.deadzone, profile.outerLimit)
    if (shaped == 0.0) return StickValue.CENTER

    val curved = shaped.pow(profile.curve)
    val scaled = (curved * profile.sensitivity).coerceIn(0.0, 1.0)

    // Direction is preserved exactly; only the distance from centre is reshaped. Anything else
    // would change where the player is aiming, not how fast they get there.
    val unitX = x / magnitude
    val unitY = y / magnitude
    return StickValue(unitX * scaled, unitY * scaled).invert(profile)
}

private fun StickValue.invert(profile: AnalogProfile): StickValue = StickValue(
    if (profile.invertX) -x else x,
    if (profile.invertY) -y else y,
)

private fun applyAxis(raw: Double, profile: AnalogProfile): Double {
    val magnitude = abs(raw)
    val shaped = rescale(magnitude, profile.deadzone, profile.outerLimit)
    if (shaped == 0.0) return 0.0
    val curved = shaped.pow(profile.curve)
    return (curved * profile.sensitivity).coerceIn(0.0, 1.0) * sign(raw)
}

/**
 * Applies a profile to a trigger.
 *
 * A trigger rests at zero and travels one way, so there is no direction to preserve and no circle
 * to stay inside. Inversion is meaningless here and is ignored rather than silently producing a
 * trigger that is fully pressed at rest.
 */
public fun applyTrigger(raw: Double, profile: AnalogProfile): Double {
    val value = if (raw.isNaN()) 0.0 else raw.coerceIn(0.0, 1.0)
    val shaped = rescale(value, profile.deadzone, profile.outerLimit)
    if (shaped == 0.0) return 0.0
    return (shaped.pow(profile.curve) * profile.sensitivity).coerceIn(0.0, 1.0)
}
