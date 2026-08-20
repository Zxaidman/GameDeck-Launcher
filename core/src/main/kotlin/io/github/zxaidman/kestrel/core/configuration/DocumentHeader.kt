package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.Outcome

/** The kinds of configuration document Kestrel reads (`docs/CONFIGURATION_SCHEMA.md`). */
public enum class DocumentType(public val wireName: String) {
    CONTROLLER_DEFINITION("controller-definition"),
    CONTROLLER_LAYOUT("controller-layout"),
    SKIN("skin"),
    GAMING_PROFILE("gaming-profile"),
    MANUAL_APPLICATION("manual-application"),

    /**
     * The one-per-installation document.
     *
     * A document like the rest, rather than a private key-value store, because it lives in the
     * folder the user chose and is meant to be readable, editable and copyable there. Settings that
     * can only be changed through the application are settings that vanish with the application.
     */
    SETTINGS("settings"),
    ;

    public companion object {
        public fun of(wireName: String): DocumentType? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * The four fields every configuration document carries, and the rules about them.
 *
 * Validated before anything type-specific, in a fixed order, because the answers change what the
 * rest of the checks even mean: an unreadable version means the rest of the document cannot be
 * interpreted at all, and the wrong type means the caller is looking at the wrong file.
 *
 * `unknownFields` is the forward-compatibility promise made concrete. A document written by a newer
 * build of Kestrel, at the same schema version, may carry fields this build has never heard of;
 * they are carried through rather than dropped, so re-exporting does not quietly delete them.
 */
public data class DocumentHeader(
    public val schemaVersion: Int,
    public val type: DocumentType,
    public val id: ConfigurationId,
    public val name: String,
    public val unknownFields: Map<String, ConfigNode> = emptyMap(),
) {
    public companion object {

        /** What this build writes and understands. */
        public const val CURRENT_SCHEMA_VERSION: Int = 1

        /** The oldest version still readable. Equal to the current one until a second exists. */
        public const val OLDEST_SUPPORTED_VERSION: Int = 1

        /** Long enough for any reasonable name, short enough to render and to log safely. */
        public const val MAX_NAME_LENGTH: Int = 120

        private val KNOWN_FIELDS = setOf("schemaVersion", "type", "id", "name")

        /**
         * Reads and validates the header of a document the caller expects to be [expected].
         *
         * Version first. A document from a future schema is not malformed — this build is simply
         * older — and reporting it as invalid would tell the user to fix a file that is fine
         * (`docs/CONFIGURATION_SCHEMA.md`, "Schema versioning": unsupported future versions fail
         * safely, and old configurations are never silently reinterpreted).
         */
        public fun read(node: ConfigNode, expected: DocumentType): Outcome<DocumentHeader> {
            val obj = when (val o = ConfigReader.asObject(node)) {
                is Outcome.Failure -> return o
                is Outcome.Success -> o.value
            }

            val version = when (val v = ConfigReader.integer(obj, "schemaVersion")) {
                is Outcome.Failure -> return v
                is Outcome.Success -> v.value
            }
            if (version > CURRENT_SCHEMA_VERSION) {
                return Outcome.Failure(
                    ConfigurationError.UnsupportedSchemaVersion(version, CURRENT_SCHEMA_VERSION)
                )
            }
            if (version < OLDEST_SUPPORTED_VERSION) {
                return Outcome.Failure(
                    ConfigurationError.UnsupportedSchemaVersion(version, OLDEST_SUPPORTED_VERSION)
                )
            }

            val typeName = when (val t = ConfigReader.text(obj, "type")) {
                is Outcome.Failure -> return t
                is Outcome.Success -> t.value
            }
            val type = DocumentType.of(typeName)
                ?: return Outcome.Failure(
                    ConfigurationError.UnknownValue(
                        "type",
                        typeName,
                        DocumentType.entries.map { it.wireName }.toSet(),
                    )
                )
            if (type != expected) {
                return Outcome.Failure(
                    ConfigurationError.UnexpectedDocumentType(expected.wireName, typeName)
                )
            }

            val rawId = when (val i = ConfigReader.text(obj, "id")) {
                is Outcome.Failure -> return i
                is Outcome.Success -> i.value
            }
            val id = when (val parsed = ConfigurationId.parse(rawId)) {
                is Outcome.Failure -> return parsed
                is Outcome.Success -> parsed.value
            }

            val name = when (val n = ConfigReader.text(obj, "name")) {
                is Outcome.Failure -> return n
                is Outcome.Success -> n.value
            }
            if (name.isBlank()) {
                return Outcome.Failure(
                    ConfigurationError.WrongType("name", "a name", "blank")
                )
            }
            if (name.length > MAX_NAME_LENGTH) {
                return Outcome.Failure(
                    ConfigurationError.TooManyItems("name", name.length, MAX_NAME_LENGTH)
                )
            }

            return Outcome.Success(
                DocumentHeader(
                    schemaVersion = version,
                    type = type,
                    id = id,
                    name = name,
                    unknownFields = obj.unknownFields(KNOWN_FIELDS),
                )
            )
        }
    }
}
