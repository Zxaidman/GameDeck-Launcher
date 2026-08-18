package com.gamedeck.core.compatibility

/**
 * Compatibility status for a feature or capability.
 */
enum class CompatibilityStatus {
    SUPPORTED,
    SUPPORTED_WITH_SHIZUKU,
    SUPPORTED_WITH_FALLBACK,
    LIMITED,
    EXPERIMENTAL,
    UNTESTED,
    UNKNOWN,
    UNSUPPORTED
}

/**
 * Confidence level for a compatibility result.
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNVERIFIED
}

/**
 * A compatibility record for a device/backend/target combination.
 */
data class CompatibilityRecord(
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val androidApi: Int,
    val gameDeckVersion: String,
    val targetPackage: String,
    val targetName: String,
    val inputBackend: String,
    val shizukuState: ShizukuState,
    val feature: String,
    val status: CompatibilityStatus,
    val confidence: ConfidenceLevel,
    val notes: String? = null,
    val testDate: String? = null
)

/**
 * State of the Shizuku integration.
 */
enum class ShizukuState {
    NOT_INSTALLED,
    STOPPED,
    RUNNING_NO_PERMISSION,
    RUNNING_ADB,
    RUNNING_ROOT,
    UNKNOWN
}

/**
 * Privilege level provided by Shizuku.
 */
enum class PrivilegeLevel {
    NONE,
    ADB_SHELL,
    ROOT,
    UNKNOWN
}