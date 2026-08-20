package io.github.zxaidman.kestrel.core.settings

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigNode
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.Json
import io.github.zxaidman.kestrel.core.input.AnalogProfile
import io.github.zxaidman.kestrel.core.input.DeadzoneShape
import io.github.zxaidman.kestrel.core.storage.MemoryDocumentStore
import io.github.zxaidman.kestrel.core.storage.StoreFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KestrelSettingsTest {

    private fun <T> value(outcome: Outcome<T>): T {
        assertTrue(outcome is Outcome.Success, "expected success, got $outcome")
        return (outcome as Outcome.Success).value
    }

    private fun error(outcome: Outcome<*>) = (outcome as Outcome.Failure).error

    private fun parse(text: String) = SettingsDocument.read(value(Json.parse(text)))

    // --- the round trip that makes settings survive an uninstall --------------------------------

    @Test
    fun `settings written to a store come back as what they were`() {
        val store = MemoryDocumentStore()
        val settings = KestrelSettings(
            controlScale = 0.8,
            stickProfile = AnalogProfile(
                deadzone = 0.15,
                outerLimit = 0.95,
                curve = 1.4,
                sensitivity = 1.2,
                invertX = true,
                invertY = false,
                deadzoneShape = DeadzoneShape.AXIAL,
            ),
            layoutId = "user.my-layout",
        )

        value(SettingsDocument.save(store, settings))
        assertEquals(settings, value(SettingsDocument.load(store)))
    }

    @Test
    fun `a first run has no settings file, which is not an error`() {
        assertEquals(KestrelSettings(), value(SettingsDocument.load(MemoryDocumentStore())))
    }

    @Test
    fun `the settings file lands in the folder root, where a person can find it`() {
        val store = MemoryDocumentStore()
        value(SettingsDocument.save(store, KestrelSettings()))
        assertEquals(listOf("settings.json"), value(store.list(StoreFolder.ROOT)))
    }

    @Test
    fun `what is written is readable by a person, not a blob`() {
        val text = Json.write(SettingsDocument.write(KestrelSettings(controlScale = 0.8)))
        assertTrue(text.contains("\"controlScale\": 0.8"), text)
        assertTrue(text.contains("\"type\": \"settings\""), text)
        assertTrue(text.contains("\n"), "settings were written as one line")
    }

    // --- reading somebody else's file, or an older one ------------------------------------------

    @Test
    fun `a missing field is an older build's file, not a broken one`() {
        // Refusing to start because a field was added would make an upgrade a data loss.
        val settings = value(
            parse("""{"schemaVersion":1,"type":"settings","id":"user.settings","name":"S"}""")
        )
        assertEquals(KestrelSettings(), settings)
    }

    @Test
    fun `a field that is present and wrong is reported rather than ignored`() {
        val outcome = parse(
            """{"schemaVersion":1,"type":"settings","id":"user.settings","name":"S",
               "controlScale": 40}"""
        )
        val error = error(outcome)
        assertTrue(error is ConfigurationError.OutOfRange, "got $error")
        assertEquals(KestrelSettings.MAX_CONTROL_SCALE, (error as ConfigurationError.OutOfRange).max)
    }

    @Test
    fun `a stick field that is present and wrong is reported with its path`() {
        val outcome = parse(
            """{"schemaVersion":1,"type":"settings","id":"user.settings","name":"S",
               "stick": {"deadzone": 5}}"""
        )
        val error = error(outcome)
        assertTrue(error is ConfigurationError.OutOfRange, "got $error")
        assertEquals("stick.deadzone", (error as ConfigurationError.OutOfRange).path)
    }

    @Test
    fun `a partial stick keeps the defaults for everything it does not mention`() {
        val settings = value(
            parse("""{"schemaVersion":1,"type":"settings","id":"user.settings","name":"S",
                     "stick": {"deadzone": 0.2}}""")
        )
        val defaults = AnalogProfile.DEFAULT_STICK
        assertEquals(0.2, settings.stickProfile.deadzone)
        assertEquals(defaults.curve, settings.stickProfile.curve)
        assertEquals(defaults.deadzoneShape, settings.stickProfile.deadzoneShape)
    }

    @Test
    fun `a document of the wrong type is refused`() {
        val outcome = parse(
            """{"schemaVersion":1,"type":"controller-layout","id":"user.settings","name":"S"}"""
        )
        assertTrue(outcome is Outcome.Failure)
    }

    @Test
    fun `a settings file from a future schema is refused as unsupported`() {
        val outcome = parse(
            """{"schemaVersion":99,"type":"settings","id":"user.settings","name":"S"}"""
        )
        assertTrue(error(outcome) is ConfigurationError.UnsupportedSchemaVersion)
    }

    @Test
    fun `a file that is not JSON at all is reported as such`() {
        val store = MemoryDocumentStore()
        value(store.write(StoreFolder.ROOT, KestrelSettings.DOCUMENT_NAME, "not json"))
        assertTrue(error(SettingsDocument.load(store)) is ConfigurationError.MalformedDocument)
    }

    // --- forward compatibility -------------------------------------------------------------------

    @Test
    fun `a field a newer build wrote survives being read and written by this one`() {
        val store = MemoryDocumentStore()
        value(
            store.write(
                StoreFolder.ROOT,
                KestrelSettings.DOCUMENT_NAME,
                """{"schemaVersion":1,"type":"settings","id":"user.settings","name":"S",
                   "controlScale":0.7,"hapticStrength":"firm"}""",
            )
        )

        val settings = value(SettingsDocument.load(store))
        assertEquals(ConfigNode.Text("firm"), settings.unknownFields["hapticStrength"])

        // The half that matters: writing it back must not delete it.
        value(SettingsDocument.save(store, settings))
        val text = value(store.read(StoreFolder.ROOT, KestrelSettings.DOCUMENT_NAME))
        assertTrue(text.contains("hapticStrength"), text)
    }

    @Test
    fun `an unknown field never overwrites a field this build owns`() {
        val settings = KestrelSettings(
            controlScale = 0.5,
            unknownFields = mapOf("controlScale" to ConfigNode.Num(9.0)),
        )
        val written = SettingsDocument.write(settings) as ConfigNode.Obj
        assertEquals(ConfigNode.Num(0.5), written["controlScale"])
    }
}
