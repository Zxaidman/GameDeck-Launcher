package io.github.zxaidman.kestrel.core.layout

/**
 * What outline a control is drawn and hit-tested as.
 *
 * Separate from [ControlKind], and the difference matters: a kind says what a control *does* — a
 * button, a stick, a trigger — and a shape says what it *looks like*. A shoulder button is a
 * rectangle on most pads and a circle on some; nothing about which one it is changes what it sends.
 *
 * **The shape decides where the control can be pressed, not only how it is drawn.** A rectangle
 * drawn and then hit-tested as a circle would have corners that look pressable and are not, which
 * is the kind of fault a player feels and cannot describe.
 */
public enum class ControlShape(public val wireName: String) {

    /** The default, and what every control was before shapes existed. */
    CIRCLE("circle"),

    /**
     * A rounded square, sized by the shorter of width and height.
     *
     * Deliberately not "a rectangle that happens to be square": stating it means a control stays
     * square when a layout gives it a slightly uneven width and height, which hand-editing a file
     * makes easy to do by accident.
     */
    SQUARE("square"),

    /** A rounded rectangle, using width and height as given. */
    RECTANGLE("rectangle"),
    ;

    public companion object {
        public fun of(wireName: String): ControlShape? = entries.firstOrNull { it.wireName == wireName }
    }
}
