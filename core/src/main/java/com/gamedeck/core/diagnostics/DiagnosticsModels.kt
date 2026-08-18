package com.gamedeck.core.diagnostics

import com.gamedeck.core.compatibility.ShizukuState
import com.gamedeck.core.input.InputCapability

/**
 * Diagnostics for an input backend.
 */
data class BackendDiagnostics(
    val backendId: String,
    val available: Boolean,
    val capabilities: Set<InputCapability>,
    val reasonUnavailable: String? = null,
    val lastError: String? = null
)

/**
 * Device-level diagnostics.
 */
data class DeviceDiagnostics(
    val androidVersion: String,
    val androidApi: Int,
    val deviceModel: String,
    val manufacturer: String,
    val gameDeckVersion: String
)

/**
 * Shizuku status diagnostics.
 */
data class ShizukuDiagnostics(
    val state: ShizukuState,
    val privilegeLevel: String,
    val permissionGranted: Boolean,
    val userServiceStarted: Boolean
)

/**
 * Session-level diagnostics for a gaming session.
 */
data class SessionDiagnostics(
    val sessionId: String? = null,
    val currentForegroundPackage: String? = null,
    val selectedProfile: String? = null,
    val currentLayout: String? = null,
    val currentScalingMode: String? = null,
    val activeBackend: String? = null,
    val overlayActive: Boolean = false,
    val inputTestResult: String? = null
)

/**
 * Complete diagnostics export.
 */
data class CompleteDiagnostics(
    val device: DeviceDiagnostics,
    val shizuku: ShizukuDiagnostics,
    val session: SessionDiagnostics,
    val backends: List<BackendDiagnostics>
)