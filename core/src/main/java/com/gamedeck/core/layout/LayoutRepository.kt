package com.gamedeck.core.layout

import com.gamedeck.core.configuration.ConfigurationDocument
import com.gamedeck.core.configuration.ExportResult
import com.gamedeck.core.configuration.ImportResult
import com.gamedeck.core.model.LayoutDefinition

/**
 * Repository for controller layouts.
 *
 * Built-in layouts are immutable. save() must refuse modification
 * of built-in layouts at the repository/domain layer.
 */
interface LayoutRepository {
    suspend fun get(id: String): LayoutDefinition?
    suspend fun list(): List<LayoutDefinition>
    suspend fun save(layout: LayoutDefinition): SaveResult
    suspend fun duplicate(sourceId: String, destinationId: String): SaveResult
    suspend fun delete(id: String): DeleteResult
    suspend fun export(id: String): ExportResult
    suspend fun import(document: ConfigurationDocument): ImportResult
}

/**
 * Result of a save operation.
 */
sealed interface SaveResult {
    data object Success : SaveResult
    data class Failure(val reason: String) : SaveResult
}

/**
 * Result of a delete operation.
 */
sealed interface DeleteResult {
    data object Success : DeleteResult
    data class Failure(val reason: String) : DeleteResult
}