package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigNode
import io.github.zxaidman.kestrel.core.configuration.ConfigReader
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.DocumentHeader
import io.github.zxaidman.kestrel.core.configuration.DocumentType
import io.github.zxaidman.kestrel.core.configuration.FieldPath
import io.github.zxaidman.kestrel.core.configuration.child
import io.github.zxaidman.kestrel.core.configuration.index
import io.github.zxaidman.kestrel.core.input.ControlForm
import io.github.zxaidman.kestrel.core.input.GamepadControl

/** Which way round a layout is meant to be held. */
public enum class LayoutOrientation(public val wireName: String) {
    LANDSCAPE("landscape"),
    PORTRAIT("portrait"),

    /** Usable either way. The layout's anchors carry it; nothing is rearranged. */
    ANY("any"),
    ;

    public companion object {
        public fun of(wireName: String): LayoutOrientation? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * One control in a layout: what it is, what it drives, and where it sits.
 *
 * [kind] and [binds] answer two different questions and are both stored. The kind is how the control
 * is *presented* — a trigger drawn as a button is a real choice a user may make (`ControlKind`) —
 * and the binding is which control on the pad it drives. Deriving either from the other would take
 * that choice away.
 */
public data class LayoutElement(
    public val id: String,
    public val kind: ControlKind,

    /**
     * The pad control this drives, or `null` for a decoration.
     *
     * Nullable rather than a sentinel, because "this element sends nothing" is a fact worth being
     * unable to ignore at the call site.
     */
    public val binds: GamepadControl?,

    /** What to draw on it, or `null` to use the control's own default. */
    public val label: String?,
    public val placement: Placement,
    public val unknownFields: Map<String, ConfigNode> = emptyMap(),
)

/**
 * A controller layout, as a document rather than as code.
 *
 * This is the change that makes the rest of the product possible. While the arrangement of controls
 * lived in a Kotlin file, there was nothing for a layout editor to edit, nothing for a skin to
 * dress, and nothing for a profile to select — three phases of `PRD.md` all waiting on the same
 * missing noun. A layout that is data is also a layout that can be exported, imported, validated,
 * versioned and shared, which is what `ADR-001` chose JSON for in the first place.
 *
 * Nothing here draws anything or knows a pixel. [Placement] turns an element into a rectangle when
 * a surface is known, and that is the only place a screen enters the picture.
 */
public data class ControllerLayout(
    public val header: DocumentHeader,
    public val orientation: LayoutOrientation,
    public val elements: List<LayoutElement>,
    public val unknownFields: Map<String, ConfigNode> = emptyMap(),
) {
    /** Every pad control this layout can actually drive. Decorations contribute nothing. */
    public val boundControls: Set<GamepadControl>
        get() = elements.mapNotNull { it.binds }.toSet()

    public fun element(id: String): LayoutElement? = elements.firstOrNull { it.id == id }
}

/**
 * Reads a layout document, and refuses anything it cannot vouch for.
 *
 * Every rule here exists because an imported document is **untrusted input** — it may have been
 * written by a different build, edited by hand, or downloaded from a stranger — and
 * `docs/CONFIGURATION_SCHEMA.md` requires that invalid data produce a typed error rather than a
 * crash or a half-built object. A layout that parsed into something unusable would surface as a
 * broken control mid-session, which is the worst possible moment to discover it.
 */
public object ControllerLayoutReader {

    /**
     * More controls than any pad has, by a margin, and small enough that a hostile document cannot
     * make the application unusable by arithmetic alone (`SECURITY.md`).
     */
    public const val MAX_ELEMENTS: Int = 64

    public const val MAX_ELEMENT_ID_LENGTH: Int = 64
    public const val MAX_LABEL_LENGTH: Int = 24

    /** Lower case, digits, and the three separators a hand-written id tends to use. */
    private val ELEMENT_ID = Regex("[a-z0-9]([a-z0-9._-]*[a-z0-9])?")

    private val KNOWN_DOCUMENT_FIELDS =
        setOf("schemaVersion", "type", "id", "name", "orientation", "elements")
    private val KNOWN_ELEMENT_FIELDS =
        setOf("id", "kind", "binds", "label", "anchor", "offsetX", "offsetY", "width", "height", "rotation")

    public fun read(node: ConfigNode): Outcome<ControllerLayout> {
        val header = when (val h = DocumentHeader.read(node, DocumentType.CONTROLLER_LAYOUT)) {
            is Outcome.Failure -> return h
            is Outcome.Success -> h.value
        }
        val obj = when (val o = ConfigReader.asObject(node)) {
            is Outcome.Failure -> return o
            is Outcome.Success -> o.value
        }

        val orientation = when (
            val o = ConfigReader.enum(
                obj, "orientation", LayoutOrientation.entries.toTypedArray(), { it.wireName },
            )
        ) {
            is Outcome.Failure -> return o
            is Outcome.Success -> o.value
        }

        val raw = when (val l = ConfigReader.list(obj, "elements", MAX_ELEMENTS)) {
            is Outcome.Failure -> return l
            is Outcome.Success -> l.value
        }

        val elements = mutableListOf<LayoutElement>()
        val seen = mutableSetOf<String>()
        raw.forEachIndexed { at, item ->
            val path = "elements".index(at)
            val element = when (val e = readElement(item, path)) {
                is Outcome.Failure -> return e
                is Outcome.Success -> e.value
            }
            // Duplicates are rejected rather than de-duplicated. Two elements with one id means the
            // author meant something this reader cannot know, and picking one silently would make a
            // layout behave differently from the file that describes it.
            if (!seen.add(element.id)) {
                return Outcome.Failure(ConfigurationError.DuplicateId(path.child("id"), element.id))
            }
            elements += element
        }

        return Outcome.Success(
            ControllerLayout(
                header = header,
                orientation = orientation,
                elements = elements,
                unknownFields = obj.unknownFields(KNOWN_DOCUMENT_FIELDS),
            )
        )
    }

    private fun readElement(node: ConfigNode, path: FieldPath): Outcome<LayoutElement> {
        val obj = when (val o = ConfigReader.asObject(node, path)) {
            is Outcome.Failure -> return o
            is Outcome.Success -> o.value
        }

        val id = when (val i = ConfigReader.text(obj, "id", path)) {
            is Outcome.Failure -> return i
            is Outcome.Success -> i.value
        }
        if (id.length > MAX_ELEMENT_ID_LENGTH || !ELEMENT_ID.matches(id)) {
            return Outcome.Failure(
                ConfigurationError.InvalidId(
                    path.child("id"),
                    id,
                    "expected lower-case letters, digits, dot, dash or underscore, " +
                        "1 to $MAX_ELEMENT_ID_LENGTH characters, starting and ending alphanumeric",
                )
            )
        }

        val kind = when (
            val k = ConfigReader.enum(
                obj, "kind", ControlKind.entries.toTypedArray(), { it.wireName }, path,
            )
        ) {
            is Outcome.Failure -> return k
            is Outcome.Success -> k.value
        }

        val bindsName = when (val b = ConfigReader.optionalText(obj, "binds", path)) {
            is Outcome.Failure -> return b
            is Outcome.Success -> b.value
        }
        val binds = when (val b = resolveBinding(kind, bindsName, path)) {
            is Outcome.Failure -> return b
            is Outcome.Success -> b.value
        }

        val label = when (val l = ConfigReader.optionalText(obj, "label", path)) {
            is Outcome.Failure -> return l
            is Outcome.Success -> l.value
        }
        if (label != null && label.length > MAX_LABEL_LENGTH) {
            return Outcome.Failure(
                ConfigurationError.OutOfRange(
                    path.child("label"), label.length.toDouble(), 0.0, MAX_LABEL_LENGTH.toDouble(),
                )
            )
        }

        val placement = when (val p = readPlacement(obj, path)) {
            is Outcome.Failure -> return p
            is Outcome.Success -> p.value
        }

        return Outcome.Success(
            LayoutElement(
                id = id,
                kind = kind,
                binds = binds,
                label = label,
                placement = placement,
                unknownFields = obj.unknownFields(KNOWN_ELEMENT_FIELDS),
            )
        )
    }

    /**
     * Checks that what an element *is* and what it *drives* agree.
     *
     * A stick bound to `A`, or a button bound to a stick, is the kind of mistake that produces a
     * control which draws correctly and does nothing — the hardest sort to diagnose from the
     * outside, because everything looks right. Catching it at read time turns it into a message
     * naming the field.
     *
     * A decoration is the one element that must **not** bind: it is artwork, and artwork that sends
     * input is a control that was mislabelled.
     */
    private fun resolveBinding(
        kind: ControlKind,
        bindsName: String?,
        path: FieldPath,
    ): Outcome<GamepadControl?> {
        val at = path.child("binds")

        if (kind == ControlKind.DECORATION) {
            return if (bindsName == null) {
                Outcome.Success(null)
            } else {
                Outcome.Failure(ConfigurationError.UnknownValue(at, bindsName, emptySet()))
            }
        }

        if (bindsName == null) return Outcome.Failure(ConfigurationError.MissingField(at))

        val expected = expectedForm(kind)
        val allowed = GamepadControl.withForm(expected).map { it.wireName }.toSet()
        val control = GamepadControl.of(bindsName)
            ?: return Outcome.Failure(ConfigurationError.UnknownValue(at, bindsName, allowed))

        return if (control.form == expected) {
            Outcome.Success(control)
        } else {
            Outcome.Failure(ConfigurationError.UnknownValue(at, bindsName, allowed))
        }
    }

    private fun expectedForm(kind: ControlKind): ControlForm = when (kind) {
        ControlKind.BUTTON -> ControlForm.BUTTON
        ControlKind.DPAD -> ControlForm.DPAD
        ControlKind.STICK -> ControlForm.STICK
        // Both trigger kinds bind to a trigger. The difference between them is how the layout
        // presents it, which is the user's choice and not a different control (`ADR-007`).
        ControlKind.ANALOG_TRIGGER, ControlKind.DIGITAL_TRIGGER -> ControlForm.TRIGGER
        ControlKind.DECORATION -> ControlForm.BUTTON
    }

    private fun readPlacement(obj: ConfigNode.Obj, path: FieldPath): Outcome<Placement> {
        val anchor = when (
            val a = ConfigReader.enum(obj, "anchor", Anchor.entries.toTypedArray(), { it.wireName }, path)
        ) {
            is Outcome.Failure -> return a
            is Outcome.Success -> a.value
        }

        // Bounds come from Placement rather than from numbers chosen here, so the message a user
        // sees names the real limit and there is one place that decides what it is.
        fun number(field: String, min: Double, max: Double, default: Double?): Outcome<Double> =
            if (default != null && !obj.has(field)) {
                Outcome.Success(default)
            } else {
                ConfigReader.number(obj, field, min, max, path)
            }

        val offsetX = when (
            val v = number("offsetX", -Placement.MAX_OFFSET, Placement.MAX_OFFSET, null)
        ) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val offsetY = when (
            val v = number("offsetY", -Placement.MAX_OFFSET, Placement.MAX_OFFSET, null)
        ) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val width = when (val v = number("width", Placement.MIN_SIZE, Placement.MAX_SIZE, null)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        // A square control is the common case, so height defaults to width rather than making every
        // round button state the same number twice.
        val height = when (
            val v = number("height", Placement.MIN_SIZE, Placement.MAX_SIZE, width)
        ) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }
        val rotation = when (val v = number("rotation", -360.0, 360.0, 0.0)) {
            is Outcome.Failure -> return v
            is Outcome.Success -> v.value
        }

        return Placement.of(anchor, offsetX, offsetY, width, height, rotation, path)
    }
}

/**
 * Turns a layout back into a document.
 *
 * The property that matters is that **what was read comes back out**: a layout imported, opened in
 * the editor and exported again must still be the same layout, including the fields this build has
 * never heard of. Unknown fields are written back exactly where they were found, which is the
 * forward-compatibility promise `docs/CONFIGURATION_SCHEMA.md` makes being kept rather than
 * described.
 *
 * Optional fields are written only when they carry something. A file full of `"label": null` and
 * `"rotation": 0` is harder to read and harder to hand-edit, and hand-editing is a thing this
 * project's own owner does.
 */
public object ControllerLayoutWriter {

    public fun write(layout: ControllerLayout): ConfigNode {
        val fields = LinkedHashMap<String, ConfigNode>()
        fields["schemaVersion"] = ConfigNode.Num(layout.header.schemaVersion.toDouble())
        fields["type"] = ConfigNode.Text(DocumentType.CONTROLLER_LAYOUT.wireName)
        fields["id"] = ConfigNode.Text(layout.header.id.value)
        fields["name"] = ConfigNode.Text(layout.header.name)
        fields["orientation"] = ConfigNode.Text(layout.orientation.wireName)
        fields["elements"] = ConfigNode.Arr(layout.elements.map { element(it) })
        // Carried, not dropped: a document written by a newer build must survive a round trip
        // through this one.
        fields += layout.unknownFields
        return ConfigNode.Obj(fields)
    }

    private fun element(element: LayoutElement): ConfigNode {
        val fields = LinkedHashMap<String, ConfigNode>()
        fields["id"] = ConfigNode.Text(element.id)
        fields["kind"] = ConfigNode.Text(element.kind.wireName)
        element.binds?.let { fields["binds"] = ConfigNode.Text(it.wireName) }
        element.label?.let { fields["label"] = ConfigNode.Text(it) }

        val placement = element.placement
        fields["anchor"] = ConfigNode.Text(placement.anchor.wireName)
        fields["offsetX"] = ConfigNode.Num(placement.offsetX)
        fields["offsetY"] = ConfigNode.Num(placement.offsetY)
        fields["width"] = ConfigNode.Num(placement.width)
        // Written only when it differs, because height defaults to width and a round control
        // stating its size twice is noise in a file people edit by hand.
        if (placement.height != placement.width) {
            fields["height"] = ConfigNode.Num(placement.height)
        }
        if (placement.rotationDegrees != 0.0) {
            fields["rotation"] = ConfigNode.Num(placement.rotationDegrees)
        }
        fields += element.unknownFields
        return ConfigNode.Obj(fields)
    }
}
