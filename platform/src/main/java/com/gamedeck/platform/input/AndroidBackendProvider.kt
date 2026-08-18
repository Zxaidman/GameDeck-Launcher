package com.gamedeck.platform.input

import android.content.Context
import com.gamedeck.core.input.BackendProvider
import com.gamedeck.core.input.InputBackend
import com.gamedeck.platform.shizuku.ShizukuCapabilityService

/**
 * Selects the best available input backend based on device capabilities.
 *
 * Selection order (subject to Phase 0 results):
 * 1. Shizuku-based backend (if available and authorized)
 * 2. Touch fallback backend (always available)
 *
 * The final ordering depends on Phase 0 feasibility results.
 */
class AndroidBackendProvider(
    private val context: Context
) : BackendProvider {

    private val shizukuService = ShizukuCapabilityService(context)

    override fun selectBackend(): InputBackend {
        // Phase 0 will determine the actual backend priority.
        // For now, use touch fallback as the baseline.
        return TouchFallbackBackend(context)
    }
}