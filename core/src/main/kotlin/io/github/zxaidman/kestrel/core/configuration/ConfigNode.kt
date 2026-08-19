package io.github.zxaidman.kestrel.core.configuration

/**
 * A parsed configuration document, before it means anything.
 *
 * `core/` is plain Kotlin and holds no parser, on purpose. Reading bytes is I/O and belongs to
 * `data/`; deciding whether what was read is *valid* is domain logic and belongs here. This type is
 * the seam between the two: a parser produces it, and every validation rule in
 * `docs/CONFIGURATION_SCHEMA.md` is expressed against it without the domain ever depending on a
 * particular JSON library.
 *
 * It also keeps a promise the schema document makes. Unknown non-executable fields are preserved
 * where safe, and they can only be preserved if the original document survives validation intact —
 * so validation reads from this tree rather than consuming it.
 */
public sealed interface ConfigNode {

    public data class Text(public val value: String) : ConfigNode

    /**
     * Any JSON number. Kept as a `Double` because that is what JSON guarantees; callers that need a
     * whole number ask for one and get a typed error if it is not.
     */
    public data class Num(public val value: Double) : ConfigNode

    public data class Bool(public val value: Boolean) : ConfigNode

    public data object Null : ConfigNode

    public data class Arr(public val items: List<ConfigNode>) : ConfigNode

    public data class Obj(public val fields: Map<String, ConfigNode>) : ConfigNode {

        public operator fun get(key: String): ConfigNode? = fields[key]

        public fun has(key: String): Boolean = key in fields

        /**
         * Field names this document carries that the caller did not ask about.
         *
         * Used to preserve forward-compatible additions rather than silently dropping them on the
         * next export (`docs/CONFIGURATION_SCHEMA.md`, "Unknown fields").
         */
        public fun unknownFields(known: Set<String>): Map<String, ConfigNode> =
            fields.filterKeys { it !in known }
    }
}

/** Describes where in a document a problem was found, for an error a person can act on. */
public typealias FieldPath = String

/** Appends a field to a path, so nested errors say `elements[2].control` rather than `control`. */
public fun FieldPath.child(field: String): FieldPath = if (isEmpty()) field else "$this.$field"

/** Appends an index to a path. */
public fun FieldPath.index(at: Int): FieldPath = "$this[$at]"
