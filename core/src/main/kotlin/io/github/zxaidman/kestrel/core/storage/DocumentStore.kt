package io.github.zxaidman.kestrel.core.storage

import io.github.zxaidman.kestrel.core.common.DomainError
import io.github.zxaidman.kestrel.core.common.Outcome

/**
 * Where a document lives inside Kestrel's folder.
 *
 * A flat set rather than arbitrary paths, and that is a security decision rather than a
 * simplification. Documents can be **imported**, and an imported document that could choose its own
 * path could choose one outside the folder entirely. There is no path to validate here because
 * there is no path: a caller names a folder from this list and a file within it.
 */
public enum class StoreFolder(public val folderName: String) {
    /** The folder itself — settings and anything else that is one-per-installation. */
    ROOT(""),

    LAYOUTS("layouts"),
    SKINS("skins"),
    PROFILES("profiles"),
    REPORTS("reports"),
    ;

    public companion object {
        public fun of(folderName: String): StoreFolder? =
            entries.firstOrNull { it.folderName == folderName }
    }
}

/** What can go wrong reaching a document, described rather than thrown. */
public sealed interface StorageError : DomainError {

    /** No folder has been chosen, or the one that was chosen is no longer reachable. */
    public data class NoLocation(public val detail: String) : StorageError {
        override val message: String = "Kestrel has nowhere to keep its files: $detail"
    }

    public data class NotFound(public val folder: StoreFolder, public val name: String) :
        StorageError {
        override val message: String = "'$name' is not in ${folder.folderName.ifEmpty { "the Kestrel folder" }}."
    }

    /**
     * A name that cannot be a file.
     *
     * Reported with the reason, because the caller may be an import of somebody else's document and
     * the user deserves to know what was wrong with it rather than that "it failed".
     */
    public data class UnsafeName(public val name: String, public val reason: String) : StorageError {
        override val message: String = "'$name' cannot be used as a file name: $reason"
    }

    public data class Unreadable(
        public val folder: StoreFolder,
        public val name: String,
        public val detail: String,
    ) : StorageError {
        override val message: String = "Could not read '$name': $detail"
    }

    public data class Unwritable(
        public val folder: StoreFolder,
        public val name: String,
        public val detail: String,
    ) : StorageError {
        override val message: String = "Could not write '$name': $detail"
    }

    /** A document larger than anything Kestrel writes, which an import is not allowed to be. */
    public data class TooLarge(public val name: String, public val bytes: Long, public val limit: Long) :
        StorageError {
        override val message: String = "'$name' is $bytes bytes; the limit is $limit."
    }
}

/**
 * Somewhere Kestrel keeps text documents.
 *
 * Deliberately small, and deliberately about **documents rather than files**. Nothing above this
 * interface knows whether the storage underneath is a folder the user picked, the application's own
 * private directory, or a map in a test — which is what lets the same code be exercised on a laptop
 * and run against a folder the user can open in a file manager.
 *
 * Text only. Everything Kestrel stores is a configuration document (`ADR-001`), and a store that
 * also handled arbitrary bytes would invite storing things that are not.
 */
public interface DocumentStore {

    /** Where the documents actually are, in words a person can act on. Never a secret. */
    public val description: String

    public fun read(folder: StoreFolder, name: String): Outcome<String>

    public fun write(folder: StoreFolder, name: String, text: String): Outcome<Unit>

    public fun list(folder: StoreFolder): Outcome<List<String>>

    public fun delete(folder: StoreFolder, name: String): Outcome<Unit>

    public fun exists(folder: StoreFolder, name: String): Boolean
}

/**
 * The rules a document name must satisfy before it is allowed near a filesystem.
 *
 * Every one of these exists because a name can arrive from **outside** — an imported layout, a
 * document from the community system planned in `PRD.md` §34, a file a user renamed by hand. A name
 * is not a path and must never be able to become one.
 */
public object DocumentName {

    /** Long enough for a namespaced identifier, short enough for any filesystem to accept. */
    public const val MAX_LENGTH: Int = 96

    /** More than any configuration document Kestrel writes, by a wide margin (`SECURITY.md`). */
    public const val MAX_DOCUMENT_BYTES: Long = 1L * 1024 * 1024

    private val ALLOWED = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    /**
     * Names Windows refuses regardless of extension.
     *
     * Checked because a user copying the Kestrel folder to a computer is a supported thing to do —
     * it is most of the reason the folder is where it is — and a document that cannot be copied is
     * a document that quietly does not get backed up.
     */
    private val RESERVED = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )

    public fun validate(name: String): Outcome<String> {
        fun bad(reason: String) = Outcome.Failure(StorageError.UnsafeName(name, reason))

        if (name.isEmpty()) return bad("it is empty")
        if (name.length > MAX_LENGTH) return bad("it is longer than $MAX_LENGTH characters")
        if (name == "." || name == "..") return bad("it names a directory rather than a document")
        if (name.contains('/') || name.contains('\\')) return bad("it contains a path separator")
        if (!ALLOWED.matches(name)) {
            return bad("only letters, digits, dot, dash and underscore are allowed, and it must start with a letter or digit")
        }
        if (name.substringBefore('.').lowercase() in RESERVED) {
            return bad("'${name.substringBefore('.')}' is a reserved name on some systems")
        }
        return Outcome.Success(name)
    }

    /** The file name a configuration document is stored under. */
    public fun forDocument(id: String): Outcome<String> = validate("$id.json")
}
