package com.gamedeck.core.compatibility

/**
 * Data-driven compatibility registry.
 *
 * Maps known gaming application packages to GameDeck behavior.
 */
data class CompatibilityEntry(
    val packageName: String,
    val name: String,
    val category: String,
    val recommendedLayout: String? = null,
    val recommendedSkin: String? = null,
    val recommendedProfile: String? = null,
    val status: CompatibilityStatus = CompatibilityStatus.UNKNOWN,
    val notes: String? = null
)

/**
 * Registry of known gaming applications.
 */
interface CompatibilityRegistry {
    fun lookup(packageName: String): CompatibilityEntry?
    fun list(): List<CompatibilityEntry>
}

/**
 * Built-in compatibility registry with known gaming applications.
 */
object BuiltInCompatibilityRegistry : CompatibilityRegistry {

    private val entries: List<CompatibilityEntry> = listOf(
        CompatibilityEntry(
            packageName = "org.ppsspp.ppsspp",
            name = "PPSSPP",
            category = "emulator",
            recommendedLayout = "builtin.psp.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "org.dolphinemu.dolphinemu",
            name = "Dolphin",
            category = "emulator",
            recommendedLayout = "builtin.gamecube.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "com.retroarch",
            name = "RetroArch",
            category = "emulator",
            recommendedLayout = "builtin.generic.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "com.limelight",
            name = "Moonlight",
            category = "streaming",
            recommendedLayout = "builtin.xbox.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "com.valvesoftware.steamlink",
            name = "Steam Link",
            category = "streaming",
            recommendedLayout = "builtin.xbox.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "com.microsoft.xboxone.smartglass",
            name = "Xbox",
            category = "cloud_gaming",
            recommendedLayout = "builtin.xbox.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "com.nvidia.geforcenow",
            name = "GeForce NOW",
            category = "cloud_gaming",
            recommendedLayout = "builtin.xbox.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        ),
        CompatibilityEntry(
            packageName = "xyz.aethersx2.android",
            name = "NetherSX2",
            category = "emulator",
            recommendedLayout = "builtin.ps2.default",
            recommendedSkin = "builtin.minimal.dark",
            status = CompatibilityStatus.EXPERIMENTAL
        )
    )

    override fun lookup(packageName: String): CompatibilityEntry? {
        return entries.firstOrNull { it.packageName == packageName }
    }

    override fun list(): List<CompatibilityEntry> = entries
}