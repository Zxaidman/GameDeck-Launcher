package com.gamedeck.core.configuration

/**
 * Validates configuration documents against schema rules.
 *
 * Validation must be strict enough to reject malformed data but
 * permissive enough to preserve unknown future fields.
 */
class ConfigurationValidator {

    private val supportedTypes = setOf(
        "controller-definition",
        "controller-layout",
        "controller-skin",
        "gaming-profile",
        "application-record",
        "aspect-ratio-preset",
        "community-manifest",
        "compatibility-record"
    )

    /**
     * Validate a configuration document.
     */
    fun validate(document: ConfigurationDocument): ValidationResult {
        val errors = mutableListOf<String>()

        // Schema version must be positive
        if (document.schemaVersion < 1) {
            errors.add("schemaVersion must be >= 1")
        }

        // Type must be supported
        if (document.type !in supportedTypes) {
            errors.add("Unsupported configuration type: ${document.type}")
        }

        // ID must be non-empty
        if (document.id.isBlank()) {
            errors.add("id must not be blank")
        }

        // ID format: builtin.*, user.*, profile.*, skin.*, controller.*
        val validIdPrefixes = listOf("builtin.", "user.", "profile.", "skin.", "controller.", "aspect.")
        if (validIdPrefixes.none { document.id.startsWith(it) }) {
            errors.add("id must start with one of: ${validIdPrefixes.joinToString(", ")}")
        }

        // Name must be non-empty
        if (document.name.isBlank()) {
            errors.add("name must not be blank")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}