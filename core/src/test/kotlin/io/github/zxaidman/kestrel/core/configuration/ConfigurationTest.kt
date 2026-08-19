package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.input.InputCapability
import io.github.zxaidman.kestrel.core.input.availabilityOf
import io.github.zxaidman.kestrel.core.input.ControlAvailability
import io.github.zxaidman.kestrel.core.layout.ControlKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun obj(vararg pairs: Pair<String, ConfigNode>) = ConfigNode.Obj(mapOf(*pairs))
private fun text(value: String) = ConfigNode.Text(value)
private fun num(value: Number) = ConfigNode.Num(value.toDouble())

private fun validLayout(vararg extra: Pair<String, ConfigNode>) = ConfigNode.Obj(
    mapOf(
        "schemaVersion" to num(1),
        "type" to text("controller-layout"),
        "id" to text("user.racing"),
        "name" to text("Racing"),
    ) + extra,
)

private inline fun <reified T> failureOf(outcome: Outcome<*>): T {
    assertInstanceOf(Outcome.Failure::class.java, outcome)
    val error = (outcome as Outcome.Failure).error
    return assertInstanceOf(T::class.java, error)
}

class ConfigurationIdTest {

    @Test
    fun `a namespaced lowercase identifier is valid`() {
        val parsed = ConfigurationId.parse("builtin.xbox.default")

        assertTrue(parsed is Outcome.Success)
        assertEquals("builtin.xbox.default", (parsed as Outcome.Success).value.value)
    }

    @Test
    fun `an identifier without a namespace is refused with a usable suggestion`() {
        val error = failureOf<ConfigurationError.InvalidId>(ConfigurationId.parse("racing"))

        assertTrue(error.message.contains("user.racing"))
    }

    @Test
    fun `mixed case is refused, because the same id must not depend on the filesystem`() {
        failureOf<ConfigurationError.InvalidId>(ConfigurationId.parse("User.Racing"))
    }

    @Test
    fun `builtin documents are recognised by namespace, not by a flag in the file`() {
        val builtin = (ConfigurationId.parse("builtin.xbox.default") as Outcome.Success).value
        val user = (ConfigurationId.parse("user.racing") as Outcome.Success).value

        assertTrue(builtin.isBuiltIn)
        assertTrue(!user.isBuiltIn)
    }

    @Test
    fun `editing a builtin is refused in the domain, and the message says what to do instead`() {
        val builtin = (ConfigurationId.parse("builtin.xbox.default") as Outcome.Success).value

        val error = failureOf<ConfigurationError.ImmutableDocument>(requireEditable(builtin))

        assertTrue(error.message.contains("Duplicate"))
    }

    @Test
    fun `editing a user document is allowed`() {
        val user = (ConfigurationId.parse("user.racing") as Outcome.Success).value

        assertTrue(requireEditable(user) is Outcome.Success)
    }
}

class DocumentHeaderTest {

    @Test
    fun `a well formed header is read`() {
        val header = DocumentHeader.read(validLayout(), DocumentType.CONTROLLER_LAYOUT)

        assertTrue(header is Outcome.Success)
        val value = (header as Outcome.Success).value
        assertEquals(DocumentType.CONTROLLER_LAYOUT, value.type)
        assertEquals("Racing", value.name)
        assertEquals("user.racing", value.id.value)
    }

    @Test
    fun `a newer schema version fails safely and tells the user to update Kestrel`() {
        val document = validLayout().let {
            ConfigNode.Obj(it.fields + ("schemaVersion" to num(99)))
        }

        val error = failureOf<ConfigurationError.UnsupportedSchemaVersion>(
            DocumentHeader.read(document, DocumentType.CONTROLLER_LAYOUT)
        )

        // Not "invalid file": the file may be perfectly good and this build simply older.
        assertEquals(99, error.found)
        assertTrue(error.message.contains("Update Kestrel"))
    }

    @Test
    fun `the version is checked before anything else in the document`() {
        // Everything but the version is wrong. The version error is still the one reported,
        // because nothing else can be interpreted until the version is understood.
        val document = obj(
            "schemaVersion" to num(99),
            "type" to text("nonsense"),
            "id" to text("NOT AN ID"),
        )

        failureOf<ConfigurationError.UnsupportedSchemaVersion>(
            DocumentHeader.read(document, DocumentType.CONTROLLER_LAYOUT)
        )
    }

    @Test
    fun `reading a skin as a layout is refused by type, not by failing later`() {
        val skin = obj(
            "schemaVersion" to num(1),
            "type" to text("skin"),
            "id" to text("user.dark"),
            "name" to text("Dark"),
        )

        val error = failureOf<ConfigurationError.UnexpectedDocumentType>(
            DocumentHeader.read(skin, DocumentType.CONTROLLER_LAYOUT)
        )

        assertEquals("skin", error.found)
    }

    @Test
    fun `a missing field names the field`() {
        val document = obj(
            "schemaVersion" to num(1),
            "type" to text("controller-layout"),
            "id" to text("user.racing"),
        )

        val error = failureOf<ConfigurationError.MissingField>(
            DocumentHeader.read(document, DocumentType.CONTROLLER_LAYOUT)
        )

        assertEquals("name", error.path)
    }

    @Test
    fun `a document that is not an object is refused before any field is read`() {
        failureOf<ConfigurationError.NotADocument>(
            DocumentHeader.read(ConfigNode.Arr(emptyList()), DocumentType.CONTROLLER_LAYOUT)
        )
    }

    @Test
    fun `unknown fields survive validation instead of being dropped`() {
        val document = validLayout("hapticProfile" to text("strong"))

        val header = DocumentHeader.read(document, DocumentType.CONTROLLER_LAYOUT)

        val unknown = (header as Outcome.Success).value.unknownFields
        assertEquals(setOf("hapticProfile"), unknown.keys)
        assertNotNull(unknown["hapticProfile"])
    }
}

class ConfigReaderTest {

    @Test
    fun `a whole number written as a decimal is accepted`() {
        val result = ConfigReader.integer(obj("schemaVersion" to num(1.0)), "schemaVersion")

        assertEquals(1, (result as Outcome.Success).value)
    }

    @Test
    fun `a fractional value where a whole number belongs is a type error`() {
        failureOf<ConfigurationError.WrongType>(
            ConfigReader.integer(obj("schemaVersion" to num(1.5)), "schemaVersion")
        )
    }

    @Test
    fun `a number outside its range reports the range it should have been in`() {
        val error = failureOf<ConfigurationError.OutOfRange>(
            ConfigReader.number(obj("opacity" to num(1.4)), "opacity", min = 0.0, max = 1.0)
        )

        assertEquals(1.4, error.found)
        assertTrue(error.message.contains("between 0.0 and 1.0"))
    }

    @Test
    fun `an oversized list is refused rather than accepted and rendered`() {
        val many = ConfigNode.Arr(List(50) { text("x") })

        val error = failureOf<ConfigurationError.TooManyItems>(
            ConfigReader.list(obj("elements" to many), "elements", limit = 32)
        )

        assertEquals(50, error.found)
        assertEquals(32, error.limit)
    }

    @Test
    fun `an absent list reads as empty rather than missing`() {
        val result = ConfigReader.list(obj(), "elements", limit = 32)

        assertTrue((result as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `an unknown enum value lists what was allowed`() {
        val error = failureOf<ConfigurationError.UnknownValue>(
            ConfigReader.enum(
                obj("control" to text("paddle")),
                "control",
                ControlKind.entries.toTypedArray(),
                ControlKind::wireName,
            )
        )

        assertEquals("paddle", error.found)
        assertTrue(error.allowed.contains("stick"))
    }
}

class ControlKindTest {

    @Test
    fun `a control's requirement is derived from its kind, not stored in the document`() {
        assertEquals(
            setOf(InputCapability.ANALOG_STICK, InputCapability.SIMULTANEOUS),
            ControlKind.STICK.requires,
        )
        assertEquals(emptySet<InputCapability>(), ControlKind.DECORATION.requires)
    }

    @Test
    fun `a stick is disabled on the fallback and a button is not`() {
        val fallback = InputCapability.TOUCH_FALLBACK_EXPECTED

        assertEquals(
            ControlAvailability.DISABLED_BY_CAPABILITY,
            availabilityOf(ControlKind.STICK.requirementFor("left-stick"), fallback),
        )
        assertEquals(
            ControlAvailability.AVAILABLE,
            availabilityOf(ControlKind.BUTTON.requirementFor("a"), fallback),
        )
    }

    @Test
    fun `a digital trigger is a choice the user makes, not a downgrade applied to them`() {
        val fallback = InputCapability.TOUCH_FALLBACK_EXPECTED

        // ADR-007 forbids the product turning an analog trigger into a digital one. A user may
        // still choose a digital trigger, and then it works where the analog one cannot.
        assertEquals(
            ControlAvailability.DISABLED_BY_CAPABILITY,
            availabilityOf(ControlKind.ANALOG_TRIGGER.requirementFor("l2"), fallback),
        )
        assertEquals(
            ControlAvailability.AVAILABLE,
            availabilityOf(ControlKind.DIGITAL_TRIGGER.requirementFor("l2"), fallback),
        )
    }

    @Test
    fun `decoration is never disabled, even with no backend at all`() {
        assertEquals(
            ControlAvailability.AVAILABLE,
            availabilityOf(ControlKind.DECORATION.requirementFor("label"), emptySet()),
        )
    }
}
