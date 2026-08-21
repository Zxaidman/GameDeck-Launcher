package io.github.zxaidman.kestrel.core.settings

/**
 * Which way up Kestrel should be.
 *
 * A handheld is held one way, so the default is landscape — but a phone is not a handheld, and a
 * user who wants to arrange a layout with one hand on the sofa should not have to turn the room.
 * Every option here is a real answer for somebody, which is why this is a setting rather than a
 * decision.
 */
public enum class AppOrientation(public val wireName: String) {

    /** Whatever the phone's own rotation setting says, including its rotation lock. */
    AUTO("auto"),

    /** Landscape, and stay there whichever way the phone is turned. */
    LANDSCAPE("landscape"),

    /** Landscape the other way up, for a phone whose camera or cable is on the wrong side. */
    REVERSE_LANDSCAPE("reverse-landscape"),

    /** Landscape, but flip when the phone is turned over. */
    SENSOR_LANDSCAPE("sensor-landscape"),

    /** Portrait, and stay there. */
    PORTRAIT("portrait"),

    /**
     * Portrait, flipping when the phone is turned over — where the phone allows it.
     *
     * Most do not. Reverse portrait is unsupported on a great many devices, so this often behaves
     * exactly like [PORTRAIT]; that is the platform's answer rather than Kestrel's, and there is no
     * separate reverse-portrait option because it would be an option that does nothing.
     */
    SENSOR_PORTRAIT("sensor-portrait"),
    ;

    public companion object {
        public fun of(wireName: String): AppOrientation? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * How much of the screen Kestrel takes, and what it is allowed to draw under.
 *
 * Both default to on, and both are settings rather than decisions.
 *
 * **Full screen** hides the system bars. A pad drawn under a status bar loses the space to it, and
 * a notification sliding in over a control mid-play is worse than not seeing the time.
 *
 * **The cutout** is the notch or hole. Drawing under it is what makes a phone with one the same
 * shape as a phone without: refuse, and the platform letterboxes the whole application to below the
 * notch, which on a wide screen is a visible black band and less room for controls. Some people
 * would rather have the band than have a control near the camera, so it can be turned off.
 */
public data class DisplayPreferences(
    public val fullScreen: Boolean = true,
    public val drawUnderCutout: Boolean = true,
    public val orientation: AppOrientation = AppOrientation.LANDSCAPE,
)
