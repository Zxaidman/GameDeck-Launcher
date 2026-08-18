package com.gamedeck.platform.profile

import android.content.Context
import android.content.pm.PackageManager
import com.gamedeck.core.compatibility.BuiltInCompatibilityRegistry
import com.gamedeck.core.compatibility.CompatibilityRegistry
import com.gamedeck.core.model.ApplicationCategory
import com.gamedeck.core.model.ApplicationSource
import com.gamedeck.core.model.GameApplication
import com.gamedeck.core.profile.GameApplicationRepository

/**
 * Android implementation of the game application repository.
 *
 * Discovers installed gaming applications using the compatibility
 * registry and package metadata.
 */
class AndroidGameApplicationRepository(
    private val context: Context,
    private val compatibilityRegistry: CompatibilityRegistry = BuiltInCompatibilityRegistry
) : GameApplicationRepository {

    private val manuallyAdded = mutableSetOf<String>()

    override suspend fun discover(): List<GameApplication> {
        val packageManager = context.packageManager
        val installedPackages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        val discovered = installedPackages.mapNotNull { appInfo ->
            val entry = compatibilityRegistry.lookup(appInfo.packageName) ?: return@mapNotNull null

            GameApplication(
                packageName = appInfo.packageName,
                displayName = packageManager.getApplicationLabel(appInfo).toString(),
                iconResource = null,
                category = categoryFromString(entry.category),
                source = ApplicationSource.DISCOVERED,
                preferredProfile = entry.recommendedProfile,
                preferredLayout = entry.recommendedLayout
            )
        }

        return discovered + manuallyAdded.mapNotNull { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                GameApplication(
                    packageName = pkg,
                    displayName = packageManager.getApplicationLabel(appInfo).toString(),
                    iconResource = null,
                    category = ApplicationCategory.UNKNOWN,
                    source = ApplicationSource.USER
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    override suspend fun addManual(packageName: String): Result<GameApplication> {
        val packageManager = context.packageManager
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val app = GameApplication(
                packageName = packageName,
                displayName = packageManager.getApplicationLabel(appInfo).toString(),
                iconResource = null,
                category = ApplicationCategory.UNKNOWN,
                source = ApplicationSource.USER
            )
            manuallyAdded.add(packageName)
            Result.success(app)
        } catch (e: PackageManager.NameNotFoundException) {
            Result.failure(e)
        }
    }

    override suspend fun removeManual(packageName: String) {
        manuallyAdded.remove(packageName)
    }

    override suspend fun list(): List<GameApplication> = discover()

    private fun categoryFromString(category: String): ApplicationCategory {
        return when (category.lowercase()) {
            "emulator" -> ApplicationCategory.EMULATOR
            "streaming" -> ApplicationCategory.STREAMING
            "cloud_gaming" -> ApplicationCategory.CLOUD_GAMING
            "android_game" -> ApplicationCategory.ANDROID_GAME
            else -> ApplicationCategory.UNKNOWN
        }
    }
}