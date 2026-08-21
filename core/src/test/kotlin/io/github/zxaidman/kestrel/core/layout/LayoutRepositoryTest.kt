package io.github.zxaidman.kestrel.core.layout

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.configuration.ConfigurationError
import io.github.zxaidman.kestrel.core.configuration.Json
import io.github.zxaidman.kestrel.core.storage.MemoryDocumentStore
import io.github.zxaidman.kestrel.core.storage.StoreFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayoutRepositoryTest {

    private val store = MemoryDocumentStore()
    private val repository = LayoutRepository(store)

    private fun builtIn(): ControllerLayout =
        (BuiltInLayouts.load(BuiltInLayouts.XBOX_DEFAULT) as Outcome.Success).value

    private fun <T> value(outcome: Outcome<T>): T {
        assertTrue(outcome is Outcome.Success, "expected success, got $outcome")
        return (outcome as Outcome.Success).value
    }

    private fun error(outcome: Outcome<*>): ConfigurationError {
        assertTrue(outcome is Outcome.Failure, "expected a failure, got $outcome")
        return (outcome as Outcome.Failure).error as ConfigurationError
    }

    // --- where a layout comes from ------------------------------------------------------------

    @Test
    fun `a built-in loads from what Kestrel ships, with nothing in the user's folder`() {
        val layout = value(repository.load(BuiltInLayouts.XBOX_DEFAULT))
        assertEquals("Xbox — default", layout.header.name)
    }

    @Test
    fun `a user layout loads from the user's folder`() {
        val copy = value(repository.duplicate(builtIn(), "mine", "Mine"))
        val loaded = value(repository.load(copy.header.id.value))
        assertEquals("Mine", loaded.header.name)
        assertEquals(copy.elements.size, loaded.elements.size)
    }

    @Test
    fun `an id that is not an id fails before anything is read`() {
        assertTrue(error(repository.load("Not An Id")) is ConfigurationError.InvalidId)
    }

    @Test
    fun `a user layout that was never written fails as not found`() {
        assertTrue(repository.load("user.absent") is Outcome.Failure)
    }

    // --- immutability, enforced rather than requested -------------------------------------------

    @Test
    fun `a built-in cannot be saved over`() {
        // The whole of built-in immutability: no code path overwrites a shipped layout, so no
        // interface can accidentally offer one.
        val error = error(repository.save(builtIn()))
        assertTrue(error is ConfigurationError.ImmutableDocument, "got $error")
    }

    @Test
    fun `a built-in cannot be deleted`() {
        assertTrue(error(repository.delete(BuiltInLayouts.XBOX_DEFAULT)) is ConfigurationError.ImmutableDocument)
    }

    @Test
    fun `duplicating is the way to get an editable copy`() {
        val copy = value(repository.duplicate(builtIn(), "mine", "Mine"))
        assertTrue(copy.header.id.value.startsWith("user."))
        assertTrue(repository.save(copy) is Outcome.Success)
    }

    @Test
    fun `a duplicate gets a new identity and keeps everything else`() {
        // An id travelling with the copy would make two documents claim to be the same thing, and
        // every reference between documents would then resolve arbitrarily.
        val source = builtIn()
        val copy = value(repository.duplicate(source, "mine", "Mine"))

        assertTrue(copy.header.id.value != source.header.id.value)
        assertEquals("Mine", copy.header.name)
        assertEquals(source.elements, copy.elements)
        assertEquals(source.orientation, copy.orientation)
    }

    @Test
    fun `a user layout can be deleted, and is gone afterwards`() {
        val copy = value(repository.duplicate(builtIn(), "mine", "Mine"))
        assertTrue(repository.delete(copy.header.id.value) is Outcome.Success)
        assertTrue(repository.load(copy.header.id.value) is Outcome.Failure)
    }

    // --- listing --------------------------------------------------------------------------------

    @Test
    fun `the list is what Kestrel ships, then what the user has made`() {
        repository.duplicate(builtIn(), "beta", "Beta")
        repository.duplicate(builtIn(), "alpha", "Alpha")

        assertEquals(
            listOf(BuiltInLayouts.XBOX_DEFAULT, "user.alpha", "user.beta"),
            repository.list(),
        )
    }

    @Test
    fun `files that are not layouts are ignored rather than offered`() {
        store.write(StoreFolder.LAYOUTS, "notes.txt", "not a layout")
        store.write(StoreFolder.LAYOUTS, "Not An Id.json", "{}")
        assertEquals(emptyList<String>(), repository.userIds())
    }

    // --- falling back ---------------------------------------------------------------------------

    @Test
    fun `a missing layout falls back to the default and says why`() {
        // Leaving the user with no controls at all is a worse answer than the wrong pad.
        val loaded = repository.loadOrDefault("user.deleted")
        assertNotNull(loaded.layout)
        assertEquals(BuiltInLayouts.XBOX_DEFAULT, loaded.layout?.header?.id?.value)
        assertNotNull(loaded.problem)
    }

    @Test
    fun `a layout that loads reports no problem`() {
        val loaded = repository.loadOrDefault(BuiltInLayouts.XBOX_DEFAULT)
        assertNotNull(loaded.layout)
        assertNull(loaded.problem)
    }

    @Test
    fun `a corrupt user layout falls back rather than leaving the pad empty`() {
        store.write(StoreFolder.LAYOUTS, "user.broken.json", "{ this is not json")
        val loaded = repository.loadOrDefault("user.broken")
        assertEquals(BuiltInLayouts.XBOX_DEFAULT, loaded.layout?.header?.id?.value)
        assertNotNull(loaded.problem)
    }

    // --- round trip -----------------------------------------------------------------------------

    @Test
    fun `a layout written and read again is the same layout`() {
        val copy = value(repository.duplicate(builtIn(), "mine", "Mine"))
        assertEquals(copy, value(repository.load("user.mine")))
    }

    @Test
    fun `fields this build has never heard of survive a round trip`() {
        // A document written by a newer build, opened in the editor and saved, must not lose what
        // it added.
        val source = """
            {
              "schemaVersion": 1,
              "type": "controller-layout",
              "id": "user.future",
              "name": "From the future",
              "orientation": "landscape",
              "hapticProfile": "firm",
              "elements": [
                {
                  "id": "face.a", "kind": "button", "binds": "a",
                  "anchor": "bottom-right", "offsetX": 0.2, "offsetY": 0.2, "width": 0.1,
                  "glowColour": "#ff0000"
                }
              ]
            }
        """.trimIndent()
        val layout = value(ControllerLayoutReader.read(value(Json.parse(source))))

        assertTrue(repository.save(layout) is Outcome.Success)
        val again = value(repository.load("user.future"))

        assertEquals("firm", (again.unknownFields["hapticProfile"] as? ConfigNodeText)?.value)
        assertEquals(layout, again)
    }

    @Test
    fun `a copy states every field a person could edit`() {
        // Hand-editing is a thing this project's owner does, and the first version wrote only
        // non-defaults on the grounds that a file should say only what it means. That failed the
        // one job the file has: they copied a layout, looked for `shape`, and found nothing —
        // because it was a circle, so it had not been written. A field that is absent is a field
        // nobody knows exists.
        val copy = value(repository.duplicate(builtIn(), "mine", "Mine"))
        val text = value(store.read(StoreFolder.LAYOUTS, "user.mine.json"))

        listOf("label", "group", "shape", "anchor", "offsetX", "offsetY", "width", "height", "rotation", "binds")
            .forEach { field -> assertTrue(text.contains("\"$field\""), "'$field' was not written") }
        assertTrue(copy.elements.isNotEmpty())
    }
}

/** Local alias so the round-trip assertion reads without importing the whole node hierarchy. */
private typealias ConfigNodeText = io.github.zxaidman.kestrel.core.configuration.ConfigNode.Text
