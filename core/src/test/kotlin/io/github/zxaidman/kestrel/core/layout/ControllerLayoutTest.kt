package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigNode
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
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
