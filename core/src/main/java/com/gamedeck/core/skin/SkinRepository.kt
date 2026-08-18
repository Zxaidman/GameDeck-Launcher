package com.gamedeck.core.skin

import com.gamedeck.core.configuration.ConfigurationDocument
import com.gamedeck.core.configuration.ExportResult
import com.gamedeck.core.configuration.ImportResult
import com.gamedeck.core.model.SkinDefinition

/**
 * Repository for controller skins.
 */
interface SkinRepository {
    suspend fun get(id: String): SkinDefinition?
    suspend fun list(): List<SkinDefinition>
    suspend fun save(skin: SkinDefinition): SkinSaveResult
    suspend fun delete(id: String): SkinDeleteResult
    suspend fun export(id: String): ExportResult
    suspend fun import(document: ConfigurationDocument): ImportResult
}

/**
 * Result of a skin save operation.
 */
sealed interface SkinSaveResult {
    data object Success : SkinSaveResult
    data class Failure(val reason: String) : SkinSaveResult
}

/**
 * Result of a skin delete operation.
 */
sealed interface SkinDeleteResult {
    data object Success : SkinDeleteResult
    data class Failure(val reason: String) : SkinDeleteResult
}