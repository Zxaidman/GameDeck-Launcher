package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigNode
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.Json
import io.github.zxaidman.kestrel.core.input.GamepadControl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControllerLayoutTest {

    // --- helpers -----------------------------------------------------------------------------

    private fun obj(vararg fields: Pair<String, ConfigNode>) = ConfigNode.Obj(fields.toMap())
    private fun text(value: String) = ConfigNode.Text(value)
    private fun num(value: Double) = ConfigNode.Num(value)

    private fun element(
        id: String = "face.a",
        kind: String = "button",
        binds: String? = "a",
        extra: Map<String, ConfigNode> = emptyMap(),
    ): ConfigNode.Obj {
        val fields = mutableMapOf<String, ConfigNode>(
            "id" to text(id),
            "kind" to text(kind),
            "anchor" to text("bottom-right"),
            "offsetX" to num(0.2),
            "offsetY" to num(0.2),
            "width" to num(0.12),
        )
        if (binds != null) fields["binds"] = text(binds)
        fields += extra
        return ConfigNode.Obj(fields)
    }

    private fun document(vararg elements: ConfigNode, orientation: String = "landscape") = obj(
        "schemaVersion" to num(1.0),
        "type" to text("controller-layout"),
        "id" to text("builtin.test.layout"),
        "name" to text("Test layout"),
        "orientation" to text(orientation),
        "elements" to ConfigNode.Arr(elements.toList()),
    )

    private fun read(node: ConfigNode) = ControllerLayoutReader.read(node)

    private fun errorOf(node: ConfigNode): ConfigurationError {
        val outcome = read(node)
        assertTrue(outcome is Outcome.Failure, "expected a failure, got $outcome")
        return (outcome as Outcome.Failure).error as ConfigurationError
    }

    private fun success(node: ConfigNode): ControllerLayout {
        val outcome = read(node)
        assertTrue(outcome is Outcome.Success, "expected success, got $outcome")
        return (outcome as Outcome.Success).value
    }

    // --- reading -----------------------------------------------------------------------------

    @Test
    fun `a well-formed layout reads into elements that know what they drive`() {
        val layout = success(
            document(
                element(id = "face.a", kind = "button", binds = "a"),
                element(id = "stick.left", kind = "stick", binds = "left-stick"),
                element(id = "pad", kind = "dpad", binds = "dpad"),
            )
        )

        assertEquals("Test layout", layout.header.name)
        assertEquals(LayoutOrientation.LANDSCAPE, layout.orientation)
        assertEquals(3, layout.elements.size)
        assertEquals(GamepadControl.A, layout.element("face.a")?.binds)
        assertEquals(
            setOf(GamepadControl.A, GamepadControl.LEFT_STICK, GamepadControl.DPAD),
            layout.boundControls,
        )
    }

    @Test
    fun `height defaults to width, so a round control states its size once`() {
        val layout = success(document(element()))
        val placement = layout.elements.single().placement
        assertEquals(placement.width, placement.height)
    }

    @Test
    fun `an explicit height is kept`() {
        val layout = success(document(element(extra = mapOf("height" to num(0.05)))))
        val placement = layout.elements.single().placement
        assertEquals(0.12, placement.width)
        assertEquals(0.05, placement.height)
    }

    @Test
    fun `a label is optional and a control falls back to its own name`() {
        val layout = success(document(element()))
        assertNull(layout.elements.single().label)
        assertEquals("A", layout.elements.single().binds?.defaultLabel)
    }

    // --- what an element is, versus what it drives --------------------------------------------

    @Test
    fun `a stick bound to a button is rejected, naming the field and the allowed values`() {
        // The failure this exists to prevent draws correctly and does nothing, which is the hardest
        // sort to diagnose from the outside.
        val error = errorOf(document(element(id = "stick.left", kind = "stick", binds = "a")))
        assertTrue(error is ConfigurationError.UnknownValue, "got $error")
        error as ConfigurationError.UnknownValue
        assertEquals("elements[0].binds", error.path)
        assertEquals(setOf("left-stick", "right-stick"), error.allowed)
    }

    @Test
    fun `a button bound to a stick is rejected the same way`() {
        val error = errorOf(document(element(kind = "button", binds = "left-stick")))
        assertTrue(error is ConfigurationError.UnknownValue, "got $error")
    }

    @Test
    fun `both trigger kinds accept a trigger, because the difference is presentation`() {
        // ADR-007: a user may choose to present an analog trigger as a button. That is a choice
        // about the layout, not a different control.
        listOf("analog-trigger", "digital-trigger").forEach { kind ->
            val layout = success(document(element(id = "l2", kind = kind, binds = "left-trigger")))
            assertEquals(GamepadControl.LEFT_TRIGGER, layout.elements.single().binds)
        }
    }

    @Test
    fun `a control that is not a decoration must bind to something`() {
        val error = errorOf(document(element(binds = null)))
        assertTrue(error is ConfigurationError.MissingField, "got $error")
        assertEquals("elements[0].binds", error.path)
    }

    @Test
    fun `a decoration must not bind, because artwork that sends input is a mislabelled control`() {
        val ok = success(document(element(id = "art", kind = "decoration", binds = null)))
        assertNull(ok.elements.single().binds)

        val error = errorOf(document(element(id = "art", kind = "decoration", binds = "a")))
        assertTrue(error is ConfigurationError.UnknownValue, "got $error")
    }

    @Test
    fun `an unknown control name is rejected`() {
        val error = errorOf(document(element(binds = "turbo")))
        assertTrue(error is ConfigurationError.UnknownValue, "got $error")
    }

    // --- untrusted input ----------------------------------------------------------------------

    @Test
    fun `two elements sharing an id are refused rather than de-duplicated`() {
        // Picking one silently would make the layout behave differently from the file describing it.
        val error = errorOf(document(element(id = "face.a"), element(id = "face.a", binds = "b")))
        assertTrue(error is ConfigurationError.DuplicateId, "got $error")
        assertEquals("face.a", (error as ConfigurationError.DuplicateId).id)
    }

    @Test
    fun `an element id with unexpected characters is refused`() {
        listOf("Face.A", "face a", "", "-leading", "trailing-", "face/a").forEach { bad ->
            val error = errorOf(document(element(id = bad)))
            assertTrue(
                error is ConfigurationError.InvalidId || error is ConfigurationError.WrongType,
                "'$bad' was accepted, or failed for the wrong reason: $error",
            )
        }
    }

    @Test
    fun `more elements than the limit is refused before anything is built`() {
        val many = Array(ControllerLayoutReader.MAX_ELEMENTS + 1) { element(id = "e$it") }
        val error = errorOf(document(*many))
        assertTrue(error is ConfigurationError.TooManyItems, "got $error")
    }

    @Test
    fun `a control larger than the surface is refused, and the message names the real limit`() {
        val error = errorOf(document(element(extra = mapOf("width" to num(9.0)))))
        assertTrue(error is ConfigurationError.OutOfRange, "got $error")
        error as ConfigurationError.OutOfRange
        assertEquals("elements[0].width", error.path)
        assertEquals(Placement.MAX_SIZE, error.max)
    }

    @Test
    fun `a document of the wrong type is refused`() {
        val wrong = obj(
            "schemaVersion" to num(1.0),
            "type" to text("skin"),
            "id" to text("builtin.test.layout"),
            "name" to text("Not a layout"),
            "orientation" to text("landscape"),
            "elements" to ConfigNode.Arr(emptyList()),
        )
        assertTrue(read(wrong) is Outcome.Failure)
    }

    @Test
    fun `a document from a future schema is refused as unsupported, not as malformed`() {
        val future = obj(
            "schemaVersion" to num(99.0),
            "type" to text("controller-layout"),
            "id" to text("builtin.test.layout"),
            "name" to text("From the future"),
            "orientation" to text("landscape"),
            "elements" to ConfigNode.Arr(emptyList()),
        )
        assertTrue(errorOf(future) is ConfigurationError.UnsupportedSchemaVersion)
    }

    // --- forward compatibility ----------------------------------------------------------------

    @Test
    fun `unknown fields are carried rather than dropped, at both levels`() {
        // Re-exporting a document written by a newer build must not quietly delete what it added.
        val node = obj(
            "schemaVersion" to num(1.0),
            "type" to text("controller-layout"),
            "id" to text("builtin.test.layout"),
            "name" to text("Test layout"),
            "orientation" to text("landscape"),
            "hapticProfile" to text("firm"),
            "elements" to ConfigNode.Arr(
                listOf(element(extra = mapOf("glowColour" to text("#ff0000"))))
            ),
        )
        val layout = success(node)
        assertEquals(text("firm"), layout.unknownFields["hapticProfile"])
        assertEquals(text("#ff0000"), layout.elements.single().unknownFields["glowColour"])
    }
}

/** Shapes: what a control is drawn and pressed as, which is not what it does. */
class ControlShapeTest {

    private fun obj(vararg fields: Pair<String, ConfigNode>) = ConfigNode.Obj(fields.toMap())

    private fun documentWith(shape: String?): ConfigNode {
        val element = mutableMapOf<String, ConfigNode>(
            "id" to ConfigNode.Text("l1"),
            "kind" to ConfigNode.Text("button"),
            "binds" to ConfigNode.Text("left-bumper"),
            "anchor" to ConfigNode.Text("top-left"),
            "offsetX" to ConfigNode.Num(0.1),
            "offsetY" to ConfigNode.Num(0.1),
            "width" to ConfigNode.Num(0.2),
            "height" to ConfigNode.Num(0.08),
        )
        if (shape != null) element["shape"] = ConfigNode.Text(shape)
        return obj(
            "schemaVersion" to ConfigNode.Num(1.0),
            "type" to ConfigNode.Text("controller-layout"),
            "id" to ConfigNode.Text("builtin.test.layout"),
            "name" to ConfigNode.Text("Shapes"),
            "orientation" to ConfigNode.Text("landscape"),
            "elements" to ConfigNode.Arr(listOf(ConfigNode.Obj(element))),
        )
    }

    private fun read(shape: String?) =
        (ControllerLayoutReader.read(documentWith(shape)) as Outcome.Success).value.elements.single()

    @Test
    fun `a control with no shape is a circle, which is what every control was before`() {
        assertEquals(ControlShape.CIRCLE, read(null).shape)
    }

    @Test
    fun `each shape is read by name`() {
        assertEquals(ControlShape.CIRCLE, read("circle").shape)
        assertEquals(ControlShape.SQUARE, read("square").shape)
        assertEquals(ControlShape.RECTANGLE, read("rectangle").shape)
    }

    @Test
    fun `an unknown shape is refused with the alternatives listed`() {
        val outcome = ControllerLayoutReader.read(documentWith("blob"))
        assertTrue(outcome is Outcome.Failure, "'blob' was accepted")
        val error = (outcome as Outcome.Failure).error as ConfigurationError.UnknownValue
        assertEquals(setOf("circle", "square", "rectangle"), error.allowed)
    }

    @Test
    fun `a shape survives being written and read again`() {
        listOf(ControlShape.CIRCLE, ControlShape.SQUARE, ControlShape.RECTANGLE).forEach { shape ->
            val layout = (ControllerLayoutReader.read(documentWith(shape.wireName)) as Outcome.Success).value
            val again = ControllerLayoutReader.read(ControllerLayoutWriter.write(layout))
            assertTrue(again is Outcome.Success, "$shape did not survive: $again")
            assertEquals(shape, (again as Outcome.Success).value.elements.single().shape)
        }
    }

    @Test
    fun `every editable field is written, including the ones at their default`() {
        // The opposite was tried first — omit anything default, so the file says only what it
        // means — and it failed the one job this file has. A layout is written so a person can open
        // it and change it, and a field that is absent is a field they do not know exists: the
        // project owner copied a layout, looked for `shape`, and found nothing to edit.
        val layout = (ControllerLayoutReader.read(documentWith(null)) as Outcome.Success).value
        val written = ControllerLayoutWriter.write(layout) as ConfigNode.Obj
        val element = (written.fields["elements"] as ConfigNode.Arr).items.single() as ConfigNode.Obj

        listOf("id", "kind", "binds", "label", "group", "shape", "anchor", "offsetX", "offsetY", "width", "height", "rotation")
            .forEach { field -> assertTrue(field in element.fields, "'$field' was not written") }
        assertEquals(ConfigNode.Text("circle"), element.fields["shape"])
    }

    @Test
    fun `an optional field with nothing in it is written as null rather than left out`() {
        // So the shape of every element is identical and what is missing is visible as missing.
        val layout = (ControllerLayoutReader.read(documentWith(null)) as Outcome.Success).value
        val written = ControllerLayoutWriter.write(layout) as ConfigNode.Obj
        val element = (written.fields["elements"] as ConfigNode.Arr).items.single() as ConfigNode.Obj
        assertEquals(ConfigNode.Null, element.fields["label"])
        assertEquals(ConfigNode.Null, element.fields["group"])
    }
}

/** The half of `FEAT-15` that lives in the file rather than in memory. */
class PortraitPlacementDocumentTest {

    private fun read(text: String) = ControllerLayoutReader.read(
        when (val parsed = Json.parse(text)) {
            is Outcome.Failure -> error("the test's own JSON did not parse: ${parsed.error.message}")
            is Outcome.Success -> parsed.value
        }
    )

    private fun ok(text: String): ControllerLayout = when (val o = read(text)) {
        is Outcome.Failure -> error("the layout was refused: ${o.error.message}")
        is Outcome.Success -> o.value
    }

    private val header = """
        "schemaVersion":1,"type":"controller-layout","id":"user.x","name":"X","orientation":"any"
    """.trimIndent()

    private fun oneElement(extra: String) = """
        {$header,"elements":[{"id":"face.a","kind":"button","binds":"a",
        "anchor":"bottom-right","offsetX":0.3,"offsetY":0.2,"width":0.12$extra}]}
    """.trimIndent()

    @Test
    fun `a document written before the portrait field means the same as it did`() {
        val layout = ok(oneElement(""))
        val element = layout.elements.single()
        assertNull(element.portraitPlacement)
        assertEquals(element.placement, element.placementFor(portrait = true))
    }

    @Test
    fun `a portrait arrangement survives being written and read back`() {
        val original = ok(oneElement(""))
        val upright = Placement(Anchor.BOTTOM_LEFT, 0.11, 0.42, 0.18, 0.18)
        val edited = original.copy(
            elements = original.elements.map { it.copy(portraitPlacement = upright) }
        )

        val text = Json.write(ControllerLayoutWriter.write(edited))
        val again = ok(text).elements.single()

        assertEquals(upright, again.portraitPlacement)
        assertEquals(original.elements.single().placement, again.placement)
    }

    @Test
    fun `an explicit null portrait is the same as no portrait at all`() {
        val layout = ok(oneElement(""","portrait":null"""))
        assertNull(layout.elements.single().portraitPlacement)
    }

    @Test
    fun `a portrait arrangement is validated like any other, not waved through`() {
        val outcome = read(
            oneElement(""","portrait":{"anchor":"bottom-left","offsetX":0.1,"offsetY":0.1,"width":99}""")
        )
        assertTrue(outcome is Outcome.Failure, "a width of 99 was accepted upright: $outcome")
    }
}
