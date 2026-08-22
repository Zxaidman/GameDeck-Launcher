package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.DomainError

/**
 * Everything that can be wrong with a configuration document.
 *
 * `docs/CONFIGURATION_SCHEMA.md` requires that invalid data produce **a typed error and never a
 * crash**. Exhaustive and sealed so a caller can react to a specific failure — an unsupported
 * future version deserves "this file is newer than this version of Kestrel", not "invalid file" —
 * rather than matching on message text.
 *
 * Every message names the field it concerns. A user who is handed someone else's layout and told
 * only that it is invalid has no way forward; told that `elements[3].opacity` is 1.4 and must be
 * between 0 and 1, they can fix it or report it usefully.
 *
 * Messages must never carry file contents beyond the offending value, and never anything personal
 * (`SECURITY.md`).
 */
public sealed interface ConfigurationError : DomainError {

    /** Where the problem is, in document terms. Empty for whole-document problems. */
    public val path: FieldPath

    /** The document is not an object at all — an array or a bare value was supplied. */
    public data class NotADocument(override val path: FieldPath = "") : ConfigurationError {
        override val message: String = "A configuration document must be a JSON object."
    }

    /** A field the schema requires is absent. */
    public data class MissingField(override val path: FieldPath) : ConfigurationError {
        override val message: String = "Required field '$path' is missing."
    }

    /** A field is present but of the wrong kind. */
    public data class WrongType(
        override val path: FieldPath,
        public val expected: String,
        public val found: String,
    ) : ConfigurationError {
        override val message: String = "Field '$path' should be $expected but is $found."
    }

    /**
     * The document declares a schema version this build does not understand.
     *
     * Kept apart from every other error because it is the one case where the document may be
     * perfectly valid and this build is simply older. It must fail safely rather than being
     * reinterpreted (`docs/CONFIGURATION_SCHEMA.md`, "Schema versioning").
     */
    public data class UnsupportedSchemaVersion(
        public val found: Int,
        public val supported: Int,
        override val path: FieldPath = "schemaVersion",
    ) : ConfigurationError {
        override val message: String = if (found > supported) {
            "This file uses schema version $found, and this version of Kestrel understands up to " +
                "$supported. Update Kestrel to open it."
        } else {
            "Schema version $found is no longer supported; the oldest readable version is $supported."
        }
    }

    /** The document is a valid configuration of a different kind than the caller asked for. */
    public data class UnexpectedDocumentType(
        public val expected: String,
        public val found: String,
        override val path: FieldPath = "type",
    ) : ConfigurationError {
        override val message: String = "Expected a '$expected' document but this is a '$found'."
    }

    /** The value is not one of the values the schema allows. */
    public data class UnknownValue(
        override val path: FieldPath,
        public val found: String,
        public val allowed: Set<String>,
    ) : ConfigurationError {
        override val message: String =
            "Field '$path' has value '$found'; allowed values are ${allowed.sorted().joinToString(", ")}."
    }

    /** A number outside the range the schema permits. */
    public data class OutOfRange(
        override val path: FieldPath,
        public val found: Double,
        public val min: Double,
        public val max: Double,
    ) : ConfigurationError {
        override val message: String = "Field '$path' is $found but must be between $min and $max."
    }

    /**
     * Text that is not a document at all.
     *
     * Separate from every error beside it, and deliberately so: those describe a document that
     * parsed and then failed a rule, which a user can act on by editing a named field. This one
     * says the file never became a document, and the only useful thing to report is where reading
     * stopped.
     */
    public data class MalformedDocument(
        public val offset: Int,
        public val reason: String,
        override val path: FieldPath = "",
    ) : ConfigurationError {
        override val message: String = "Not readable as a document: $reason (at character $offset)."
    }

    /** An identifier that does not meet the rules in `docs/CONFIGURATION_SCHEMA.md`. */
    public data class InvalidId(
        override val path: FieldPath,
        public val found: String,
        public val reason: String,
    ) : ConfigurationError {
        override val message: String = "Identifier '$found' in '$path' is not valid: $reason"
    }

    /** A collection larger than the schema permits, which is a denial-of-service guard. */
    public data class TooManyItems(
        override val path: FieldPath,
        public val found: Int,
        public val limit: Int,
    ) : ConfigurationError {
        override val message: String = "Field '$path' has $found items; the limit is $limit."
    }

    /** Two items share an identifier that must be unique within the document. */
    public data class DuplicateId(
        override val path: FieldPath,
        public val id: String,
    ) : ConfigurationError {
        override val message: String = "Identifier '$id' appears more than once in '$path'."
    }

    /** A reference to something the document, or the library, does not contain. */
    public data class UnresolvedReference(
        override val path: FieldPath,
        public val target: String,
    ) : ConfigurationError {
        override val message: String = "Field '$path' refers to '$target', which does not exist."
    }

    /**
     * An attempt to modify a built-in.
     *
     * Enforced in the domain, not by hiding an edit button (`CLAUDE.md` §5). The workflow is
     * Built-in → Duplicate → User copy → Edit, and this error is what makes the first step
     * unavoidable.
     */
    public data class ImmutableDocument(
        public val id: String,
        override val path: FieldPath = "id",
    ) : ConfigurationError {
        override val message: String =
            "'$id' is built in and cannot be changed. Duplicate it first, then edit the copy."
    }
}
