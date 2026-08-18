package com.gamedeck.platform.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.gamedeck.core.compatibility.PrivilegeLevel
import com.gamedeck.core.compatibility.ShizukuState

/**
 * Detects and reports Shizuku availability and privilege level.
 *
 * Shizuku is an optional capability provider. This service must
 * distinguish between:
 * - Shizuku not installed
 * - Shizuku installed but stopped
 * - Shizuku running but permission not granted
 * - Shizuku running with ADB/shell privileges
 * - Shizuku running with root privileges
 *
 * Note: This implementation uses reflection to avoid a hard dependency
 * on the Shizuku library. When the Shizuku dependency is added, this
 * should be updated to use the official Shizuku API.
 */
class ShizukuCapabilityService(
    private val context: Context
) {

    /**
     * Check whether Shizuku is installed.
     */
    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check whether Shizuku is running.
     */
    fun isShizukuRunning(): Boolean {
        return try {
            // Use reflection to check Shizuku binder status
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val method = shizukuClass.getMethod("pingBinder")
            method.invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check whether Shizuku permission has been granted.
     */
    fun isPermissionGranted(): Boolean {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val method = shizukuClass.getMethod("checkSelfPermission")
            val result = method.invoke(null) as Int
            result == 0 // PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Determine the current Shizuku privilege level.
     */
    fun getPrivilegeLevel(): PrivilegeLevel {
        if (!isShizukuInstalled()) return PrivilegeLevel.NONE
        if (!isShizukuRunning()) return PrivilegeLevel.NONE
        if (!isPermissionGranted()) return PrivilegeLevel.NONE

        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val method = shizukuClass.getMethod("getUid")
            val uid = method.invoke(null) as Int

            when (uid) {
                0 -> PrivilegeLevel.ROOT
                2000 -> PrivilegeLevel.ADB_SHELL
                else -> PrivilegeLevel.UNKNOWN
            }
        } catch (e: Exception) {
            PrivilegeLevel.UNKNOWN
        }
    }

    /**
     * Get the current Shizuku state.
     */
    fun getShizukuState(): ShizukuState {
        if (!isShizukuInstalled()) return ShizukuState.NOT_INSTALLED
        if (!isShizukuRunning()) return ShizukuState.STOPPED
        if (!isPermissionGranted()) return ShizukuState.RUNNING_NO_PERMISSION

        return when (getPrivilegeLevel()) {
            PrivilegeLevel.ROOT -> ShizukuState.RUNNING_ROOT
            PrivilegeLevel.ADB_SHELL -> ShizukuState.RUNNING_ADB
            else -> ShizukuState.UNKNOWN
        }
    }
}