package com.gamedeck.core.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A portable GameDeck configuration document.
 *
 * All configuration types share common fields: schemaVersion, type, and id.
 * Unknown future fields are preserved where safe.
 */
@Serializable
data class ConfigurationDocument(
    val schemaVersion: Int,
    val type: String,
    val id: String,
    val name: String,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val data: JsonObject = JsonObject(emptyMap())
)

/**
 * Result of a configuration validation.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult {
        constructor(error: String) : this(listOf(error))
    }
}

/**
 * Result of an import operation.
 */
sealed interface ImportResult {
    data class Success(val id: String) : ImportResult
    data class Failure(val reason: String) : ImportResult
}

/**
 * Result of an export operation.
 */
sealed interface ExportResult {
    data class Success(val document: ConfigurationDocument) : ExportResult
    data class Failure(val reason: String) : ExportResult
}