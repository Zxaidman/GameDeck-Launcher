package io.github.zxaidman.kestrel.core.storage

import io.github.zxaidman.kestrel.core.common.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a [DocumentStore] promises, described once.
 *
 * Run here against [MemoryDocumentStore]. A platform store is expected to satisfy the same list,
 * which is why the expectations live in tests rather than in a comment.
 */
class DocumentStoreTest {

    private fun store() = MemoryDocumentStore()

    private fun <T> value(outcome: Outcome<T>): T {
        assertTrue(outcome is Outcome.Success, "expected success, got $outcome")
        return (outcome as Outcome.Success).value
    }

    private fun error(outcome: Outcome<*>): StorageError {
        assertTrue(outcome is Outcome.Failure, "expected a failure, got $outcome")
        return (outcome as Outcome.Failure).error as StorageError
    }

    @Test
    fun `what was written is what comes back`() {
        val store = store()
        value(store.write(StoreFolder.LAYOUTS, "user.mine.json", """{"a":1}"""))
        assertEquals("""{"a":1}""", value(store.read(StoreFolder.LAYOUTS, "user.mine.json")))
    }

    @Test
    fun `folders are separate, so the same name in two of them is two documents`() {
        val store = store()
        value(store.write(StoreFolder.LAYOUTS, "thing.json", "layout"))
        value(store.write(StoreFolder.SKINS, "thing.json", "skin"))

        assertEquals("layout", value(store.read(StoreFolder.LAYOUTS, "thing.json")))
        assertEquals("skin", value(store.read(StoreFolder.SKINS, "thing.json")))
    }

    @Test
    fun `a missing document is a typed absence, not an empty string`() {
        val outcome = store().read(StoreFolder.LAYOUTS, "absent.json")
        assertTrue(error(outcome) is StorageError.NotFound)
    }

    @Test
    fun `writing again replaces, because a document has one current version`() {
        val store = store()
        value(store.write(StoreFolder.ROOT, "settings.json", "first"))
        value(store.write(StoreFolder.ROOT, "settings.json", "second"))
        assertEquals("second", value(store.read(StoreFolder.ROOT, "settings.json")))
        assertEquals(listOf("settings.json"), value(store.list(StoreFolder.ROOT)))
    }

    @Test
    fun `listing is sorted and covers only the folder asked for`() {
        val store = store()
        value(store.write(StoreFolder.LAYOUTS, "b.json", "-"))
        value(store.write(StoreFolder.LAYOUTS, "a.json", "-"))
        value(store.write(StoreFolder.SKINS, "z.json", "-"))

        assertEquals(listOf("a.json", "b.json"), value(store.list(StoreFolder.LAYOUTS)))
        assertEquals(listOf("z.json"), value(store.list(StoreFolder.SKINS)))
        assertEquals(emptyList<String>(), value(store.list(StoreFolder.PROFILES)))
    }

    @Test
    fun `deleting something that is not there is a failure rather than a shrug`() {
        val store = store()
        assertTrue(error(store.delete(StoreFolder.LAYOUTS, "absent.json")) is StorageError.NotFound)

        value(store.write(StoreFolder.LAYOUTS, "here.json", "-"))
        value(store.delete(StoreFolder.LAYOUTS, "here.json"))
        assertFalse(store.exists(StoreFolder.LAYOUTS, "here.json"))
    }

    // --- names, which arrive from outside ---------------------------------------------------

    @Test
    fun `a name that could escape the folder is refused`() {
        // The reason this matters: an imported document may bring its own name, and a name that can
        // become a path can name somewhere Kestrel was never given.
        listOf(
            "../escape.json",
            "..\\escape.json",
            "sub/dir.json",
            "sub\\dir.json",
            "..",
            ".",
            "",
        ).forEach { name ->
            assertTrue(
                error(store().write(StoreFolder.LAYOUTS, name, "-")) is StorageError.UnsafeName,
                "'$name' was accepted",
            )
        }
    }

    @Test
    fun `a name reserved by another operating system is refused`() {
        // Copying the Kestrel folder to a computer is a supported thing to do — it is most of the
        // reason the folder is where it is — and a document that cannot be copied is one that
        // quietly does not get backed up.
        listOf("con.json", "PRN.json", "aux.json", "com1.json", "LPT9.json").forEach { name ->
            assertTrue(
                error(store().write(StoreFolder.LAYOUTS, name, "-")) is StorageError.UnsafeName,
                "'$name' was accepted",
            )
        }
    }

    @Test
    fun `ordinary document names are accepted`() {
        listOf(
            "settings.json",
            "builtin.xbox.default.json",
            "user.0f8e-4c21.json",
            "My_Layout-2.json",
        ).forEach { name ->
            assertTrue(
                store().write(StoreFolder.LAYOUTS, name, "-") is Outcome.Success,
                "'$name' was refused",
            )
        }
    }

    @Test
    fun `a name longer than any filesystem wants is refused`() {
        val long = "a".repeat(DocumentName.MAX_LENGTH + 1)
        assertTrue(error(store().write(StoreFolder.LAYOUTS, long, "-")) is StorageError.UnsafeName)
    }

    @Test
    fun `reading also validates the name, so a bad one cannot be probed with`() {
        assertTrue(
            error(store().read(StoreFolder.LAYOUTS, "../../secret")) is StorageError.UnsafeName
        )
    }

    // --- size, because imports are untrusted ---------------------------------------------------

    @Test
    fun `a document larger than the limit is refused`() {
        val huge = "x".repeat((DocumentName.MAX_DOCUMENT_BYTES + 1).toInt())
        val outcome = store().write(StoreFolder.LAYOUTS, "big.json", huge)
        assertTrue(error(outcome) is StorageError.TooLarge)
    }

    @Test
    fun `a document at the limit is accepted, so the limit is a limit and not a margin`() {
        val atLimit = "x".repeat(DocumentName.MAX_DOCUMENT_BYTES.toInt())
        assertTrue(store().write(StoreFolder.LAYOUTS, "big.json", atLimit) is Outcome.Success)
    }

    @Test
    fun `the size limit counts bytes rather than characters`() {
        // A character is not a byte once anyone names a layout in their own language.
        val store = store()
        val multiByte = "é".repeat(DocumentName.MAX_DOCUMENT_BYTES.toInt() / 2 + 1)
        assertTrue(error(store.write(StoreFolder.LAYOUTS, "big.json", multiByte)) is StorageError.TooLarge)
    }

    // --- the name a document is stored under ----------------------------------------------------

    @Test
    fun `a document is stored under its identifier with a json extension`() {
        assertEquals(
            "builtin.xbox.default.json",
            value(DocumentName.forDocument("builtin.xbox.default")),
        )
    }

    @Test
    fun `an identifier that cannot be a file name is caught before anything is written`() {
        assertTrue(error(DocumentName.forDocument("../escape")) is StorageError.UnsafeName)
    }
}
