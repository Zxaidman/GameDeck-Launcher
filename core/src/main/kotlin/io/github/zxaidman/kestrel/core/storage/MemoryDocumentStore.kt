package io.github.zxaidman.kestrel.core.storage

import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.common.flatMap

/**
 * A store that keeps everything in memory.
 *
 * Not a mock. It is the reference implementation of what [DocumentStore] promises, and the tests
 * that describe those promises run against it — so a platform store can be checked against the same
 * expectations rather than against a description of them.
 */
public class MemoryDocumentStore(
    override val description: String = "in memory (nothing is kept)",
) : DocumentStore {

    private val documents = linkedMapOf<Pair<StoreFolder, String>, String>()

    override fun read(folder: StoreFolder, name: String): Outcome<String> =
        DocumentName.validate(name).flatMap {
            documents[folder to name]?.let { text -> Outcome.Success(text) }
                ?: Outcome.Failure(StorageError.NotFound(folder, name))
        }

    override fun write(folder: StoreFolder, name: String, text: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            val size = text.encodeToByteArray().size.toLong()
            if (size > DocumentName.MAX_DOCUMENT_BYTES) {
                Outcome.Failure(
                    StorageError.TooLarge(name, size, DocumentName.MAX_DOCUMENT_BYTES)
                )
            } else {
                documents[folder to name] = text
                Outcome.Success(Unit)
            }
        }

    override fun list(folder: StoreFolder): Outcome<List<String>> =
        Outcome.Success(
            documents.keys.filter { it.first == folder }.map { it.second }.sorted()
        )

    override fun delete(folder: StoreFolder, name: String): Outcome<Unit> =
        DocumentName.validate(name).flatMap {
            if (documents.remove(folder to name) == null) {
                Outcome.Failure(StorageError.NotFound(folder, name))
            } else {
                Outcome.Success(Unit)
            }
        }

    override fun exists(folder: StoreFolder, name: String): Boolean =
        documents.containsKey(folder to name)
}
