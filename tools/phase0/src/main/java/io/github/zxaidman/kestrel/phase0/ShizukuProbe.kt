package io.github.zxaidman.kestrel.phase0

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

/**
 * EXPERIMENTAL — Phase 0 harness only.
 *
 * Reports the privilege state, and runs read-only probes through a shell-privileged service.
 *
 * The state model mirrors `ARCHITECTURE.md` §14 deliberately: installed, running, permission
 * granted, and the actual identity obtained are four separate facts, and none of them implies
 * another. This class is where that distinction is first tested against a real device rather than
 * assumed.
 */
object ShizukuProbe {

    const val PERMISSION_REQUEST_CODE = 4001

    val status = mutableStateOf("Not checked yet.")
    val output = mutableStateOf("")
    val busy = mutableStateOf(false)

    private var service: IProbeService? = null

    /** The four facts, kept apart on purpose. Shizuku installed never means capability available. */
    fun refreshStatus() {
        status.value = buildString {
            val running = try {
                Shizuku.pingBinder()
            } catch (e: Throwable) {
                appendLine("Shizuku library error: ${e.javaClass.simpleName}")
                false
            }
            appendLine("Service running:   ${if (running) "yes" else "no"}")

            if (!running) {
                appendLine("Permission:        n/a — service is not running")
                appendLine("Identity:          unknown")
                appendLine()
                appendLine("Start Shizuku, then press Refresh status.")
                return@buildString
            }

            val granted = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
            appendLine("Permission:        ${if (granted) "granted" else "NOT granted"}")

            val uid = try { Shizuku.getUid() } catch (e: Throwable) { -1 }
            val identity = when (uid) {
                0 -> "root (uid 0)"
                2000 -> "shell (uid 2000)"
                -1 -> "unknown"
                else -> "uid $uid"
            }
            appendLine("Identity:          $identity")
            appendLine("Shizuku version:   ${try { Shizuku.getVersion() } catch (e: Throwable) { "unknown" }}")

            if (!granted) {
                appendLine()
                appendLine("Press Grant permission below.")
            }
        }.trim()
    }

    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Throwable) {
            status.value = "Permission request failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * The Tier 5 question, asked as plainly as possible: does the virtual-input device node exist,
     * what are its permissions and owning group, and is the helper command present.
     *
     * Every one of these is a read. None of them creates a device or produces an event.
     */
    private val PROBES = listOf(
        "identity" to "id",
        "device node" to "ls -lZ /dev/uinput 2>&1 || ls -l /dev/uinput 2>&1",
        "node readable" to "test -r /dev/uinput && echo READABLE || echo 'not readable'",
        "node writable" to "test -w /dev/uinput && echo WRITABLE || echo 'not writable'",
        "uinput command" to "command -v uinput || echo 'not on PATH'",
        "uinput usage" to "uinput -h 2>&1 | head -20",
        "input command" to "input 2>&1 | head -25",
        "kernel module" to "grep -c uinput /proc/devices 2>/dev/null || echo 'unreadable'",
        "selinux mode" to "getenforce 2>&1",
    )

    fun runProbes(context: Context) {
        if (busy.value) return
        busy.value = true
        output.value = "Binding shell-privileged service…"

        val existing = service
        if (existing != null) {
            execAll(existing)
            return
        }

        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ProbeService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("probe")
                .debuggable(false)
                .version(2)

            Shizuku.bindUserService(args, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val bound = IProbeService.Stub.asInterface(binder)
                    service = bound
                    if (bound == null) {
                        output.value = "Service bound but returned no interface."
                        busy.value = false
                    } else {
                        execAll(bound)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            })
        } catch (e: Throwable) {
            output.value = "Could not bind the service.\n\n" +
                "${e.javaClass.simpleName}: ${e.message}\n\n" +
                "This usually means Shizuku is not running, or permission was not granted."
            busy.value = false
        }
    }

    private fun execAll(bound: IProbeService) {
        val text = buildString {
            appendLine("Probe run — read-only. Nothing was injected or created.")
            appendLine()
            for ((label, command) in PROBES) {
                appendLine("── $label")
                appendLine("\$ $command")
                appendLine(
                    try {
                        bound.exec(command)
                    } catch (e: Throwable) {
                        "(call failed: ${e.javaClass.simpleName}: ${e.message})"
                    }
                )
                appendLine()
            }
        }
        output.value = text.trim()
        busy.value = false
        EventLog.note("probe run completed — see Probe tab")
    }
}
