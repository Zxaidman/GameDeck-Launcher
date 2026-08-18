package com.gamedeck.platform.foregroundapp

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Android implementation of foreground application monitoring.
 *
 * Uses UsageStatsManager where available. Requires the
 * PACKAGE_USAGE_STATS permission to be granted by the user.
 *
 * This is a best-effort implementation. Some OEMs restrict
 * usage-stats access even when permission is granted.
 */
class AndroidForegroundAppMonitor(
    private val context: Context
) {
    private val currentPackage = MutableStateFlow<String?>(null)

    /** Currently detected foreground package */
    val foregroundPackage: Flow<String?> = currentPackage.asStateFlow()

    /**
     * Start monitoring the foreground application.
     *
     * Polls UsageStatsManager at a configurable interval.
     * This is a fallback approach; event-driven detection
     * is preferred where available.
     */
    fun startMonitoring(intervalMs: Long = 1000L): Flow<String?> = flow {
        while (true) {
            val pkg = detectForegroundPackage()
            currentPackage.value = pkg
            emit(pkg)
            delay(intervalMs)
        }
    }

    /**
     * Detect the current foreground package using UsageStatsManager.
     */
    private fun detectForegroundPackage(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val endTime = System.currentTimeMillis()
                val beginTime = endTime - 10_000 // Look back 10 seconds

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    beginTime,
                    endTime
                )

                // Find the most recently used package
                stats
                    ?.filter { it.lastTimeUsed > 0 }
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName
            } catch (e: SecurityException) {
                // PACKAGE_USAGE_STATS permission not granted
                null
            } catch (e: Exception) {
                // OEM restrictions or other failures
                null
            }
        }
    }
}