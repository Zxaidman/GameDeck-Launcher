package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.Outcome

/**
 * A stable identifier for a configuration document.
 *
 * The rule that matters most is that **an identifier is not a name**. A user renaming their layout
 * from "My Setup" to "Racing" must not change what it *is*, or every profile referring to it breaks
 * (`docs/CONFIGURATION_SCHEMA.md`, "Stable IDs").
 *
 * The namespace also carries meaning rather than being decoration: `builtin.` identifies documents
 * this project ships, which are immutable, and that immutability is enforced here in the domain
 * rather than by a disabled button somewhere in the interface.
 */
@JvmInline
public value class ConfigurationId private constructor(public val value: String) {

    /** Built-ins ship with the product and can never be edited, only duplicated. */
    public val isBuiltIn: Boolean
        get() = value.startsWith("$BUILTIN_NAMESPACE.")

    override fun toString(): String = value

    public companion object {

        public const val BUILTIN_NAMESPACE: String = "builtin"
        public const val USER_NAMESPACE: String = "user"

        /** Long enough for a namespaced UUID, short enough that nothing can hide in one. */
        public const val MAX_LENGTH: Int = 96

        private val SEGMENT = Regex("[a-z0-9][a-z0-9-]*")

        /**
         * Parses an identifier, or explains exactly why it is not one.
         *
         * Deliberately narrow: lowercase, dot-separated segments. Not because anything else would
         * break today, but because identifiers appear in file names, in shared documents, and in
         * references between documents, and every character class allowed here is one that has to
         * keep working in all three places forever. Case-insensitivity in particular would make
         * `User.Layout` and `user.layout` the same identifier on one filesystem and different ones
         * on another.
         */
        public fun parse(raw: String, path: FieldPath = "id"): Outcome<ConfigurationId> {
            fun invalid(reason: String) =
                Outcome.Failure(ConfigurationError.InvalidId(path, raw, reason))

            if (raw.isEmpty()) return invalid("it is empty")
            if (raw.length > MAX_LENGTH) {
                return invalid("it is ${raw.length} characters; the limit is $MAX_LENGTH")
            }
            if (raw != raw.lowercase()) return invalid("it must be lowercase")

            val segments = raw.split('.')
            if (segments.size < 2) {
                return invalid("it must have a namespace, such as '$USER_NAMESPACE.$raw'")
            }
            segments.forEachIndexed { index, segment ->
                if (segment.isEmpty()) return invalid("part ${index + 1} is empty")
                if (!SEGMENT.matches(segment)) {
                    return invalid(
                        "part '$segment' may contain only letters, digits and hyphens, and must " +
                            "start with a letter or digit"
                    )
                }
            }
            return Outcome.Success(ConfigurationId(raw))
        }

        /**
         * Builds a user-owned identifier from a unique string.
         *
         * The caller supplies the unique part, because generating one needs a source of randomness
         * and `core/` stays free of anything it cannot test deterministically.
         */
        public fun user(unique: String): Outcome<ConfigurationId> =
            parse("$USER_NAMESPACE.$unique")
    }
}

/**
 * Refuses a change to a built-in.
 *
 * The whole workflow rests on this one check: Built-in → Duplicate → User copy → Edit. Any code
 * path that writes a document calls it, so there is exactly one place where the rule lives and one
 * place to look when asking whether it is enforced.
 */
public fun requireEditable(id: ConfigurationId): Outcome<ConfigurationId> = if (id.isBuiltIn) {
    Outcome.Failure(ConfigurationError.ImmutableDocument(id.value))
} else {
    Outcome.Success(id)
}
