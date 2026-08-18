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
 * Reports the privilege state, runs read-only probes, and issues injection attempts through the
 * platform's own `input` command in a shell-privileged process.
 *
 * On the harness staying honest: the injection is performed by the operating system's own tool in a
 * separate process, and travels the ordinary system input path. The harness does not synthesise
 * events into its own window — it watches them arrive exactly as any other application would. The
 * command issued is written into the same log as the events that follow, so stimulus and response
 * are interleaved in one record and cannot be mistaken for each other.
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
            appendLine(
                "Identity:          " + when (uid) {
                    0 -> "root (uid 0)"
                    2000 -> "shell (uid 2000)"
                    -1 -> "unknown"
                    else -> "uid $uid"
                }
            )
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
     * Read-only inspection.
     *
     * The open test matters most. `test -w` calls access(2), which consults only the classic
     * permission bits and is blind to SELinux. Actually opening the node is the only way to learn
     * whether policy permits it, and policy is where this most often fails.
     */
    private val PROBES = listOf(
        "identity" to "id",
        "device node" to "ls -lZ /dev/uinput 2>&1 || ls -l /dev/uinput 2>&1",
        "permission bits say" to
            "test -w /dev/uinput && echo 'writable (DAC only)' || echo 'not writable'",
        "ACTUAL OPEN for write" to
            "(exec 9>/dev/uinput) 2>&1 && echo 'OPEN SUCCEEDED' || echo 'OPEN DENIED'",
        "uinput helper" to "command -v uinput || echo 'not on PATH'",
        "uinput invoked bare" to "uinput 2>&1 | head -15",
        "input full usage" to "input 2>&1 | head -60",
        "selinux mode" to "getenforce 2>&1",
        "recent denials" to
            "(dmesg 2>/dev/null | grep -i 'avc.*denied' | tail -5) || echo '(kernel log not readable)'",
    )

    /** Injection attempts. Each is the platform's own tool, run in a shell-privileged process. */
    val INJECTIONS = listOf(
        Triple("A button", "gamepad source, digital", "input gamepad keyevent 96"),
        Triple("D-pad up", "dpad source, digital", "input dpad keyevent 19"),
        Triple("Stick X", "joystick source, analog axis", "input joystick motionevent MOVE --axis X,0.6"),
        Triple("Stick X alt", "alternative axis syntax", "input joystick motionevent MOVE 0.6 0"),
    )

    fun runProbes(context: Context) = withService(context, "Binding shell-privileged service…") { bound ->
        buildString {
            appendLine("Probe run — read-only. Nothing was injected or created.")
            appendLine()
            for ((label, command) in PROBES) {
                appendLine("── $label")
                appendLine("\$ $command")
                appendLine(safeExec(bound, command))
                appendLine()
            }
        }.trim()
    }

    /**
     * Issues one injection and records the command in the event log first, so the log reads as
     * stimulus followed by whatever the system actually delivered — or by nothing, which is equally
     * a result.
     */
    fun inject(context: Context, label: String, command: String) =
        withService(context, "Injecting: $command") { bound ->
            EventLog.note("INJECT ATTEMPT [$label]: $command")
            val result = safeExec(bound, command)
            EventLog.note("INJECT RESULT  [$label]: ${result.replace("\n", " ").take(120)}")
            buildString {
                appendLine("Injection attempt: $label")
                appendLine("\$ $command")
                appendLine(result)
                appendLine()
                appendLine("Now open the Events tab. Anything the system delivered appears there,")
                appendLine("between the INJECT ATTEMPT and INJECT RESULT lines.")
                appendLine("Check the src= on each event: KEYBOARD is key emulation, GAMEPAD or")
                appendLine("JOYSTICK is controller semantics. An empty result is also a result.")
            }.trim()
        }

    private fun safeExec(bound: IProbeService, command: String): String = try {
        bound.exec(command)
    } catch (e: Throwable) {
        "(call failed: ${e.javaClass.simpleName}: ${e.message})"
    }

    /**
     * Binds the privileged service if needed, then runs the work off the main thread — binder calls
     * block, and blocking the main thread would freeze the very UI that is meant to be reporting.
     */
    private fun withService(context: Context, pending: String, work: (IProbeService) -> String) {
        if (busy.value) return
        busy.value = true
        output.value = pending

        val existing = service
        if (existing != null) {
            runOffThread(existing, work)
            return
        }

        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ProbeService::class.java.name)
            ).daemon(false).processNameSuffix("probe").debuggable(false).version(4)

            Shizuku.bindUserService(args, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val bound = IProbeService.Stub.asInterface(binder)
                    service = bound
                    if (bound == null) {
                        output.value = "Service bound but returned no interface."
                        busy.value = false
                    } else {
                        runOffThread(bound, work)
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

    private fun runOffThread(bound: IProbeService, work: (IProbeService) -> String) {
        Thread {
            val result = try {
                work(bound)
            } catch (e: Throwable) {
                "Failed: ${e.javaClass.simpleName}: ${e.message}"
            }
            output.value = result
            busy.value = false
        }.start()
    }
}
