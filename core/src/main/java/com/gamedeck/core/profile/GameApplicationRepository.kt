package com.gamedeck.core.profile

import com.gamedeck.core.model.GameApplication

/**
 * Repository for gaming applications.
 */
interface GameApplicationRepository {
    /** Discover installed gaming applications */
    suspend fun discover(): List<GameApplication>

    /** Add an application manually */
    suspend fun addManual(packageName: String): Result<GameApplication>

    /** Remove a manually added application */
    suspend fun removeManual(packageName: String)

    /** List all known gaming applications */
    suspend fun list(): List<GameApplication>
}