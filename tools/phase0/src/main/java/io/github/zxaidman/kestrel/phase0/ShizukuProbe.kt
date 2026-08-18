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

    data class Injection(
        val label: String,
        val description: String,
        val command: String,
        /**
         * Sent automatically a moment after the command. An axis set by `motionevent` stays set:
         * the system keeps treating the stick as held and emits repeating directional keys
         * indefinitely. Measured on a real device — the first run produced over 360 repeats and was
         * still going when the report was exported. Nothing releases it implicitly.
         */
        val release: String? = null,
    )

    val INJECTIONS = listOf(
        Injection("A button", "gamepad source, digital", "input gamepad keyevent 96"),
        Injection("D-pad up", "dpad source, digital", "input dpad keyevent 19"),
        Injection(
            "Stick right", "joystick source, analog axis",
            "input joystick motionevent MOVE 0.6 0",
            release = RECENTRE,
        ),
    )

    /** Returns every axis to rest. Also exposed on its own button, as an escape hatch. */
    const val RECENTRE = "input joystick motionevent MOVE 0 0"

    fun clearOutput() { output.value = "" }

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
    fun inject(context: Context, injection: Injection) =
        withService(context, "Injecting: ${injection.command}") { bound ->
            EventLog.note("INJECT ATTEMPT [${injection.label}]: ${injection.command}")
            val result = safeExec(bound, injection.command)
            EventLog.note("INJECT RESULT  [${injection.label}]: ${result.replace("\n", " ").take(120)}")

            val releaseNote = injection.release?.let { release ->
                // Long enough to observe the held state, short enough not to leave it stuck.
                Thread.sleep(1200)
                EventLog.note("AUTO-RELEASE   [${injection.label}]: $release")
                val releaseResult = safeExec(bound, release)
                EventLog.note("RELEASE RESULT [${injection.label}]: ${releaseResult.replace("\n", " ").take(120)}")
                "\nAuto-release sent after 1.2s: $release\n$releaseResult"
            } ?: ""

            buildString {
                appendLine("── injection: ${injection.label}")
                appendLine("\$ ${injection.command}")
                appendLine(result)
                if (releaseNote.isNotEmpty()) appendLine(releaseNote)
            }.trim()
        }


    // ---------------------------------------------------------------------------------------
    // Virtual device creation.
    //
    // The helper reads a device description from standard input and destroys the device when that
    // input closes, so the description is followed by a sleep: the device lives exactly as long as
    // the process holding it, which is also how a production implementation would have to work.
    //
    // Two descriptor formats are attempted because the helper's accepted schema is not documented
    // on-device and `uinput -h` prints nothing on this build. A rejection is as informative as an
    // acceptance: the error text states what the schema actually wants.
    //
    // Button and axis numbers below are Linux input-event constants, which are stable kernel ABI.
    // ---------------------------------------------------------------------------------------

    private const val BUTTONS = "304, 305, 307, 308, 310, 311, 314, 315, 317, 318"
    private const val AXES = "0, 1, 2, 5, 9, 10, 16, 17"

    private fun absInfo(code: Int, min: Int, max: Int) =
        """{"code": $code, "info": {"value": 0, "minimum": $min, "maximum": $max, """ +
            """"fuzz": 0, "flat": 0, "resolution": 0}}"""

    private val ABS_INFO = listOf(
        absInfo(0, -32768, 32767),   // ABS_X      left stick
        absInfo(1, -32768, 32767),   // ABS_Y
        absInfo(2, -32768, 32767),   // ABS_Z      right stick
        absInfo(5, -32768, 32767),   // ABS_RZ
        absInfo(9, 0, 255),          // ABS_GAS    right trigger
        absInfo(10, 0, 255),         // ABS_BRAKE  left trigger
        absInfo(16, -1, 1),          // ABS_HAT0X  d-pad
        absInfo(17, -1, 1),          // ABS_HAT0Y
    ).joinToString(", ")

    private val DESCRIPTOR_NUMERIC = """
        {"id": 1, "command": "register", "name": "Kestrel Virtual Controller",
         "vid": 6353, "pid": 20192, "bus": "usb",
         "configuration": [
           {"type": 100, "data": [1, 3]},
           {"type": 101, "data": [$BUTTONS]},
           {"type": 103, "data": [$AXES]}
         ],
         "abs_info": [$ABS_INFO]}
    """.trimIndent().replace("\n", " ")

    private val DESCRIPTOR_NAMED = """
        {"id": 1, "command": "register", "name": "Kestrel Virtual Controller",
         "vid": 6353, "pid": 20192, "bus": "usb",
         "configuration": [
           {"type": "UI_SET_EVBIT", "data": ["EV_KEY", "EV_ABS"]},
           {"type": "UI_SET_KEYBIT", "data": [$BUTTONS]},
           {"type": "UI_SET_ABSBIT", "data": [$AXES]}
         ],
         "abs_info": [$ABS_INFO]}
    """.trimIndent().replace("\n", " ")

    val CREATIONS = listOf(
        "numeric schema" to DESCRIPTOR_NUMERIC,
        "named schema" to DESCRIPTOR_NAMED,
    )

    /**
     * Holds the device open for five seconds. While it exists the harness's hot-plug listener
     * should log it appearing, and the Devices tab should show it — which is the only proof that
     * matters, since a device that nothing can see has not been created in any useful sense.
     */
    fun createVirtualDevice(context: Context, label: String, descriptor: String) =
        withService(context, "Creating virtual device ($label)…") { bound ->
            val before = android.view.InputDevice.getDeviceIds().size
            EventLog.note("CREATE ATTEMPT [$label] — holding device open for 5s")

            val command = "(echo '" + descriptor + "'; sleep 5) | uinput - 2>&1"
            val result = safeExec(bound, command)

            val after = android.view.InputDevice.getDeviceIds().size
            EventLog.note("CREATE RESULT  [$label]: ${result.replace("\n", " ").take(160)}")

            buildString {
                appendLine("── virtual device attempt: $label")
                appendLine("device count before: $before, after: $after")
                appendLine()
                appendLine("helper output:")
                appendLine(if (result.isBlank()) "(nothing)" else result)
                appendLine()
                appendLine("Check the Events tab for DEVICE ADDED, and the Devices tab during the")
                appendLine("five seconds the device is held open. A rejection message here is a")
                appendLine("result too: it states what the helper's schema actually requires.")
            }.trim()
        }

    /** Manual escape hatch for a stuck axis. */
    fun releaseAll(context: Context) = withService(context, "Releasing…") { bound ->
        EventLog.note("MANUAL RELEASE: $RECENTRE")
        val result = safeExec(bound, RECENTRE)
        "── release all axes\n\$ $RECENTRE\n$result"
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
        if (output.value.isBlank()) output.value = pending

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
            val previous = output.value
            output.value = if (previous.isBlank() || previous.endsWith("…")) {
                result
            } else {
                (previous + "\n\n" + result).takeLast(20000)
            }
            busy.value = false
        }.start()
    }
}
