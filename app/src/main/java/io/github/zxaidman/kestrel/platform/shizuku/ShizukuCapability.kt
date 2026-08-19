package io.github.zxaidman.kestrel.platform.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

/**
 * The privilege level actually obtained, which is not the same as what is installed.
 *
 * `ARCHITECTURE.md` §14 requires these to be reported as separate facts, and the reason is that
 * collapsing them into one boolean produces exactly the wrong message: a user with Shizuku
 * installed but not running is told something different from one who never installed it, and both
 * are told something different from one who has it running but has not granted permission.
 */
public enum class PrivilegeLevel { NONE, ADB_SHELL, ROOT, UNKNOWN }

/** What is true right now about privilege. Four facts, never merged. */
public data class ShizukuState(
    public val serviceRunning: Boolean,
    public val permissionGranted: Boolean,
    public val privilege: PrivilegeLevel,
    public val version: Int?,
) {
    public val usable: Boolean
        get() = serviceRunning && permissionGranted &&
            (privilege == PrivilegeLevel.ADB_SHELL || privilege == PrivilegeLevel.ROOT)

    /** What to tell the user, in the order they can act on it (`docs/DEGRADED_STATE.md` §3). */
    public val advice: String
        get() = when {
            !serviceRunning -> "Shizuku is not running. Start it, then come back."
            !permissionGranted -> "Shizuku is running. Grant Kestrel permission to use it."
            privilege == PrivilegeLevel.NONE -> "Shizuku granted no usable privilege."
            else -> "Ready."
        }
}

/**
 * The one place Shizuku is spoken to.
 *
 * Every call is guarded, because the whole point of `ADR-003` is that Shizuku is optional: with it
 * absent the application must still run, and the failure mode must be a reported state rather than
 * a crash on a missing class.
 */
public object ShizukuCapability {

    public const val PERMISSION_REQUEST_CODE: Int = 7301

    private var shell: IPrivilegedShell? = null

    /** Reads the current state. Cheap enough to call whenever a screen appears. */
    public fun state(): ShizukuState {
        val running = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            return ShizukuState(false, false, PrivilegeLevel.UNKNOWN, null)
        }
        if (!running) return ShizukuState(false, false, PrivilegeLevel.NONE, null)

        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
        val privilege = try {
            when (Shizuku.getUid()) {
                0 -> PrivilegeLevel.ROOT
                2000 -> PrivilegeLevel.ADB_SHELL
                else -> PrivilegeLevel.NONE
            }
        } catch (e: Throwable) {
            PrivilegeLevel.UNKNOWN
        }
        val version = try { Shizuku.getVersion() } catch (e: Throwable) { null }

        return ShizukuState(true, granted, privilege, version)
    }

    public fun requestPermission() {
        runCatching {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }
    }

    /** The bound shell, if one is available. Null is an answer, not an error. */
    public fun shell(): IPrivilegedShell? = shell

    /**
     * Binds the privileged service, calling back on whatever thread Shizuku uses.
     *
     * `daemon(false)` matters: it ties this service to the application's own process, so it goes
     * away when the application does.
     */
    public fun bind(context: Context, onReady: (IPrivilegedShell?) -> Unit) {
        shell?.let { onReady(it); return }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, PrivilegedShellService::class.java.name)
            ).daemon(false).processNameSuffix("privileged").debuggable(false).version(2)

            Shizuku.bindUserService(args, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    shell = IPrivilegedShell.Stub.asInterface(binder)
                    onReady(shell)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shell = null
                }
            })
        } catch (e: Throwable) {
            onReady(null)
        }
    }
}
