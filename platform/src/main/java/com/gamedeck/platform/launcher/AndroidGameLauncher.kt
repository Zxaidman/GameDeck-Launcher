package com.gamedeck.platform.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Launches gaming applications on Android.
 */
class AndroidGameLauncher(
    private val context: Context
) {

    /**
     * Launch an application by package name.
     *
     * @return true if the launch intent was resolved and started
     */
    fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Open the system settings for overlay permission.
     */
    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Open the system settings for usage access permission.
     */
    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Check whether overlay permission is granted.
     */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}