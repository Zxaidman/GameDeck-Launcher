package io.github.zxaidman.kestrel.platform.input.virtual

import io.github.zxaidman.kestrel.platform.shizuku.IPrivilegedShell

/**
 * The backend `ADR-INPUT-001` selected: a kernel virtual input device, created through the
 * platform's own helper with shell privilege, held for a session by a watchdog that outlives the
 * application.
 *
 * Rebuilt here rather than promoted from `tools/phase0/`, as `PROJECT_STRUCTURE.md` §27 requires.
 * The harness is a measuring instrument and stays one; what carried over is the evidence, not the
 * code.
 *
 * Scope: proven on the Redmi Note 13 5G on HyperOS 3.0.3. Everywhere else this is the project's
 * working assumption and `docs/COMPATIBILITY.md` records those devices as untested.
 */
public object VirtualControllerBackend {

    public const val DEVICE_NAME: String = "Kestrel Virtual Controller"

    private const val DIR = "/data/local/tmp"
    private const val DESCRIPTOR = "$DIR/kestrel-device.json"
    private const val STREAM = "$DIR/kestrel-stream.json"
    private const val GUARD = "$DIR/kestrel-guard.sh"
    private const val LOG = "$DIR/kestrel-device.log"

    /**
     * How the holder identifies itself, read off the reference device:
     * `app_process /system/bin com.android.commands.uinput.Uinput`.
     *
     * Not `uinput`. Matching that name killed nothing, ever, while reporting success from the same
     * broken search — the failure in `docs/phase0/results/tier5-orphan-report.md`. The bracket
     * stops the pattern matching the command carrying it.
     */
    private const val HOLDER = "com.android.commands.uinput[.]Uinput"
    private const val GUARD_PATTERN = "kestrel[-]guard"
    private const val TAIL_PATTERN = "kestrel[-]stream"

    /** Linux input-event codes. Stable kernel ABI, not values invented here. */
    private const val BUTTONS = "304, 305, 307, 308, 310, 311, 312, 313, 314, 315, 317, 318"
    private const val AXES = "0, 1, 2, 5, 9, 10, 16, 17"

    private fun absInfo(code: Int, min: Int, max: Int) =
        """{"code": $code, "info": {"value": 0, "minimum": $min, "maximum": $max, """ +
            """"fuzz": 0, "flat": 0, "resolution": 0}}"""

    private val ABS_INFO = listOf(
        absInfo(0, -32768, 32767), absInfo(1, -32768, 32767),
        absInfo(2, -32768, 32767), absInfo(5, -32768, 32767),
        absInfo(9, 0, 255), absInfo(10, 0, 255),
        absInfo(16, -1, 1), absInfo(17, -1, 1),
    ).joinToString(", ")

    private val DESCRIPTOR_JSON = """
        {"id": 1, "command": "register", "name": "$DEVICE_NAME",
         "vid": 6353, "pid": 20192, "bus": "usb",
         "configuration": [
           {"type": 100, "data": [1, 3]},
           {"type": 101, "data": [$BUTTONS]},
           {"type": 103, "data": [$AXES]}
         ],
         "abs_info": [$ABS_INFO]}
    """.trimIndent().replace("\n", " ")

    /**
     * Opens a controller and arms the watchdog that will close it.
     *
     * @param ownerPackage the application whose life the session is tied to
     */
    public fun open(shell: IPrivilegedShell, ownerPackage: String): String = buildString {
        appendLine(exec(shell, "printf '%s' '$DESCRIPTOR_JSON' > $DESCRIPTOR; : > $STREAM; rm -f $LOG"))
        appendLine(
            exec(shell, "( tail -n +1 -f $STREAM | uinput - ) > $LOG 2>&1 & echo holder started")
        )
        armWatchdog(shell, ownerPackage)
        exec(shell, "printf '%s\\n' '$DESCRIPTOR_JSON' >> $STREAM")
        Thread.sleep(1200)
        appendLine("holding: ${holders(shell).ifBlank { "(nothing — creation failed)" }}")
        appendLine(exec(shell, "head -c 200 $LOG 2>&1").let { if (it.isBlank()) "" else "helper: $it" })
    }.trim()

    /**
     * Arms the process that ends the session when its owner does.
     *
     * **The watchdog watches the owner's process, not a heartbeat from it**, and that is a
     * correction rather than a preference. The first design had the application renew a timestamp
     * every few seconds; on the reference device the platform froze the application in the
     * background, the renewals stopped, and the controller was destroyed mid-session while the
     * notification was still on screen. A frozen application is alive and its session should
     * survive; only a dead or removed one should end it.
     *
     * Two checks, both directly observable from outside the application:
     *
     * - **process alive** — force-stop removes it, so a force-stop ends the session
     * - **package installed** — uninstalling removes it, so an uninstall ends the session
     *
     * Neither needs the application to run any code, which is the whole point: an application being
     * force-stopped or uninstalled never gets the chance.
     */
    private fun armWatchdog(shell: IPrivilegedShell, ownerPackage: String) {
        val script = listOf(
            "while :; do",
            "sleep 3",
            "if ! pgrep -f '$ownerPackage' > /dev/null 2>&1; then break; fi",
            "if ! pm path '$ownerPackage' > /dev/null 2>&1; then break; fi",
            "done",
            "pkill -9 -f '$HOLDER'",
            "pkill -9 -f '$TAIL_PATTERN'",
            "rm -f $STREAM",
        ).joinToString(" ") { "'$it'" }

        exec(shell, "printf '%s\\n' $script > $GUARD")
        exec(shell, "sh $GUARD > /dev/null 2>&1 & echo guard armed")
    }

    /** Closes the session and reports the state afterwards rather than claiming success. */
    public fun close(shell: IPrivilegedShell): String {
        val before = holders(shell)
        exec(shell, "pkill -9 -f '$GUARD_PATTERN'; pkill -9 -f '$HOLDER'; pkill -9 -f '$TAIL_PATTERN'")
        exec(shell, "rm -f $STREAM")
        Thread.sleep(500)
        val after = holders(shell)
        return "before: ${before.ifBlank { "(none)" }}\nafter:  ${after.ifBlank { "(none)" }}"
    }

    /** Which processes hold the device open. The only honest answer to "is one open". */
    public fun holders(shell: IPrivilegedShell): String =
        exec(shell, "pgrep -f '$HOLDER' 2>/dev/null | tr '\\n' ' '")
            .takeUnless { it.startsWith("(no output") } ?: ""

    private fun exec(shell: IPrivilegedShell, command: String): String = try {
        shell.exec(command, 6000).trim()
    } catch (e: Throwable) {
        "(call failed: ${e.javaClass.simpleName})"
    }
}
