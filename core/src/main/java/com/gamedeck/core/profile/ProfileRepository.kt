package com.gamedeck.core.profile

import com.gamedeck.core.configuration.ConfigurationDocument
import com.gamedeck.core.configuration.ExportResult
import com.gamedeck.core.configuration.ImportResult
import com.gamedeck.core.model.GamingProfile

/**
 * Repository for gaming profiles.
 */
interface ProfileRepository {
    suspend fun get(id: String): GamingProfile?
    suspend fun list(): List<GamingProfile>
    suspend fun save(profile: GamingProfile): ProfileSaveResult
    suspend fun delete(id: String): ProfileDeleteResult
    suspend fun export(id: String): ExportResult
    suspend fun import(document: ConfigurationDocument): ImportResult

    /** Find the best profile for a package name */
    suspend fun findForPackage(packageName: String): GamingProfile?
}

/**
 * Result of a profile save operation.
 */
sealed interface ProfileSaveResult {
    data object Success : ProfileSaveResult
    data class Failure(val reason: String) : ProfileSaveResult
}

/**
 * Result of a profile delete operation.
 */
sealed interface ProfileDeleteResult {
    data object Success : ProfileDeleteResult
    data class Failure(val reason: String) : ProfileDeleteResult
}