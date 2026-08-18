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

    /**
     * Appends to the transcript immediately, from whichever thread is running the work.
     *
     * Results used to be assembled into one string and shown only when the whole action finished,
     * so a test that holds a device for several seconds looked frozen: on device, nothing appeared
     * until the device had already been created, pressed and removed — by which time the operator
     * had no idea which part of it had worked. Each step now reports as it happens.
     *
     * Compose snapshot state is safe to write from a background thread, and the transcript is
     * bounded so a long session cannot grow without limit.
     */
    private fun emit(text: String) {
        val previous = output.value
        output.value = (if (previous.isBlank()) text else "$previous\n$text").takeLast(20000)
    }

    fun runProbes(context: Context) = withService(context, "Binding shell-privileged service…") { bound ->
        emit("Probe run — read-only. Nothing was injected or created.")
        for ((label, command) in PROBES) {
            // Each result appears as it returns; some of these commands take a moment.
            emit("\n── $label\n\$ $command\n${safeExec(bound, command).trim()}")
        }
        ""
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

    private const val BUTTONS =
        "304, 305, 307, 308, 310, 311, 312, 313, 314, 315, 317, 318"
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

    private const val DESCRIPTOR_PATH = "/data/local/tmp/kestrel-uinput.json"
    private const val HELPER_LOG = "/data/local/tmp/kestrel-uinput.log"
    private const val INJECT_PATH = "/data/local/tmp/kestrel-inject.json"

    /**
     * Starts the helper as a background process holding the device for 30 seconds.
     *
     * Quoting discipline matters here more than it looks. An earlier version wrapped the command in
     * a second `sh -c "..."` layer; the descriptor contains double quotes, so the shell broke apart
     * inside the device name and the helper never ran — reported on device as
     * `Virtual: no closing quote`. The descriptor is therefore written to a file first, using only
     * single quotes, which the JSON never contains. Nothing here is nested.
     *
     * The device lives exactly as long as the process holding its file descriptor, which is why the
     * pipeline holds it open rather than letting the helper reach end of input and exit. That is
     * also the shape a production backend must take: a long-lived process per session.
     */
    fun createVirtualDevice(context: Context, label: String, descriptor: String) =
        withService(context, "Creating virtual device ($label)…") { bound ->
            EventLog.note("CREATE ATTEMPT [$label] — background helper, 30s hold")

            // Single-quoted only. The descriptor contains double quotes but never single quotes.
            val written = safeExec(bound, "printf '%s' '$descriptor' > $DESCRIPTOR_PATH; echo wrote=$?")
            val size = safeExec(bound, "wc -c < $DESCRIPTOR_PATH")
            val valid = safeExec(bound, "head -c 60 $DESCRIPTOR_PATH")

            emit("── virtual device attempt: $label")
            emit("descriptor written: ${written.trim()}, bytes: ${size.trim()}")
            emit("starts with: ${valid.trim()}")

            safeExec(bound, "rm -f $HELPER_LOG")
            val started = safeExec(
                bound,
                "( (cat $DESCRIPTOR_PATH; sleep 30) | uinput - ) > $HELPER_LOG 2>&1 & echo launched"
            )
            emit("${started.trim()} — holding the device for 30 seconds, checking in 1.5s…")

            Thread.sleep(1500)

            // Raw output, no derived claim. This check reported a false positive in one version
            // (matching the shell that was failing to run the helper) and a false negative in the
            // next (reporting NOT RUNNING while the device demonstrably lived its full 30 seconds).
            // An instrument that asserts a conclusion its evidence does not support is worse than
            // one that simply shows what it saw.
            val alive = safeExec(bound, "ps -A 2>/dev/null | grep -i uinput | grep -v grep")
            val log = safeExec(bound, "cat $HELPER_LOG 2>&1 | head -20")
            val count = android.view.InputDevice.getDeviceIds().size

            EventLog.note("CREATE RESULT  [$label]: devices=$count, process lines=${alive.lines().size}")

            buildString {
                appendLine("processes matching uinput (raw):")
                appendLine(if (alive.isBlank()) "  (none listed — the device may still exist; trust the Devices tab)" else alive)
                appendLine("helper output: ${if (log.isBlank()) "(none — good, no error)" else log}")
                appendLine("device count now: $count")
                appendLine()
                appendLine("Open the Devices tab within 30 seconds and look for Kestrel Virtual")
                appendLine("Controller. Its full description is captured in the Events tab the")
                appendLine("moment it appears, and kept in the export whether or not it persists.")
            }.trim()
        }

    /**
     * Registers the device, then writes button presses to it.
     *
     * This is the last open question. The device exists and is classified as a controller; whether
     * events written to it arrive attributed to *it*, rather than to the system virtual device, is
     * the difference between a device that looks right and a controller that works.
     *
     * Event triples are (type, code, value) in Linux input terms: type 1 is a key, type 0 with
     * code 0 is the synchronisation marker every report must end with. Stable kernel ABI, not
     * values invented here.
     */
    fun createAndPress(context: Context, label: String, descriptor: String) =
        withService(context, "Creating device and pressing A…") { bound ->
            EventLog.note("CREATE+PRESS [$label] — register, then press BUTTON_A three times")
            emit("── create and press: $label")

            val wrote = safeExec(bound, "printf '%s' '$descriptor' > $DESCRIPTOR_PATH; echo wrote=$?")
            emit("descriptor: ${wrote.trim()}")

            val press = """{"id": 1, "command": "inject", "events": [1, 304, 1, 0, 0, 0]}"""
            val release = """{"id": 1, "command": "inject", "events": [1, 304, 0, 0, 0, 0]}"""
            val script = (1..3).joinToString("\n") { "$press\n$release" }
            val wroteScript = safeExec(bound, "printf '%s' '$script' > $INJECT_PATH; echo wrote=$?")
            emit("press script: ${wroteScript.trim()} (three press/release pairs)")

            safeExec(bound, "rm -f $HELPER_LOG")
            val launched = safeExec(
                bound,
                "( (cat $DESCRIPTOR_PATH; sleep 2; cat $INJECT_PATH; sleep 20) | uinput - ) " +
                    "> $HELPER_LOG 2>&1 & echo launched"
            )
            emit("${launched.trim()} — registering the device now")

            // The helper waits two seconds after registration before writing the presses, so the
            // device is enumerated and classified before anything is sent to it. Report each stage
            // as it passes rather than going silent for the whole four seconds.
            Thread.sleep(2000)
            emit("registered — writing BUTTON_A presses now; watch the Events tab")

            Thread.sleep(2000)
            emit("presses sent — the device stays alive for about 20 more seconds")

            val log = safeExec(bound, "cat $HELPER_LOG 2>&1 | head -20")
            val procs = safeExec(bound, "ps -A 2>/dev/null | grep -i uinput | grep -v grep")

            buildString {
                appendLine("helper output: ${if (log.isBlank()) "(none — no error)" else log}")
                appendLine("processes matching uinput (raw):")
                appendLine(if (procs.isBlank()) "  (none listed)" else procs)
                appendLine()
                appendLine("Now read the Events tab. What matters is the dev= on any BUTTON_A that")
                appendLine("arrived. If it names the created device's id, the device is delivering")
                appendLine("its own input and this is a complete result. If it says dev=-1, the")
                appendLine("events came from the system device instead. If nothing arrived, the")
                appendLine("inject format is wrong and the helper output above will say so.")
            }.trim()
        }

    // ---------------------------------------------------------------------------------------
    // The full exercise.
    //
    // One button proved the device can deliver its own input. It did not prove the device can
    // deliver a *controller's* input: analog sticks, analog triggers, a hat, and several buttons
    // held at once are what separate a controller from a key emitter, and the acceptance criteria
    // in docs/PHASE-0.md §29 name all of them.
    //
    // Every stage sets a value, holds it, then returns it to rest. Nothing here may end with an
    // axis left deflected — a stuck axis makes the system emit directional keys without stopping,
    // which was measured earlier at over 360 repeats from a process that had already exited.
    //
    // Event triples are (type, code, value): type 3 is EV_ABS, type 1 is EV_KEY, and type 0 with
    // code 0 is the SYN_REPORT every report must end with. Stable kernel ABI.
    // ---------------------------------------------------------------------------------------

    private fun inject(vararg events: Int) =
        """{"id": 1, "command": "inject", "events": [${events.joinToString(", ")}]}"""

    private const val SYN = "0, 0, 0"

    /**
     * Each stage is a label, what it should produce, and the events to write.
     *
     * The half-deflection stages matter as much as the full ones. The descriptor declares raw
     * kernel ranges (±32768, 0–255) but the platform reports the axis normalised, so a half value
     * is the only way to tell a real conversion from a value that happens to saturate at 1.0.
     */
    private val EXERCISE = listOf(
        Triple(
            "left stick — full right, then centre",
            "AXIS_X near +1.0, then at rest",
            listOf(inject(3, 0, 32767, 0, 0, 0), inject(3, 0, 0, 0, 0, 0)),
        ),
        Triple(
            "left stick — half left, then centre",
            "AXIS_X near -0.5 — proves the value is scaled, not saturated",
            listOf(inject(3, 0, -16384, 0, 0, 0), inject(3, 0, 0, 0, 0, 0)),
        ),
        Triple(
            "left stick — full up, then centre",
            "AXIS_Y near -1.0 (up is negative), then at rest",
            listOf(inject(3, 1, -32768, 0, 0, 0), inject(3, 1, 0, 0, 0, 0)),
        ),
        Triple(
            "right stick — diagonal, then centre",
            "AXIS_Z and AXIS_RZ together — the axes shell injection could never reach",
            listOf(
                inject(3, 2, 32767, 3, 5, -32768, 0, 0, 0),
                inject(3, 2, 0, 3, 5, 0, 0, 0, 0),
            ),
        ),
        Triple(
            "triggers — both fully pressed, then released",
            "AXIS_GAS and AXIS_BRAKE near +1.0 — analog, not a button",
            listOf(
                inject(3, 9, 255, 3, 10, 255, 0, 0, 0),
                inject(3, 9, 0, 3, 10, 0, 0, 0, 0),
            ),
        ),
        Triple(
            "triggers — half pressed, then released",
            "AXIS_GAS near +0.5",
            listOf(inject(3, 9, 128, 0, 0, 0), inject(3, 9, 0, 0, 0, 0)),
        ),
        Triple(
            "d-pad — right and down, then centre",
            "AXIS_HAT_X +1 and AXIS_HAT_Y +1, or DPAD key events",
            listOf(
                inject(3, 16, 1, 3, 17, 1, 0, 0, 0),
                inject(3, 16, 0, 3, 17, 0, 0, 0, 0),
            ),
        ),
        Triple(
            "three buttons at once — A, B, Y",
            "three DOWN events before any UP — simultaneous state, not a queue",
            listOf(
                inject(1, 304, 1, 1, 305, 1, 1, 308, 1, 0, 0, 0),
                inject(1, 304, 0, 1, 305, 0, 1, 308, 0, 0, 0, 0),
            ),
        ),
    )

    private const val STREAM_PATH = "/data/local/tmp/kestrel-stream.json"

    /** Seconds each stage holds its value before returning to rest. */
    private const val HOLD_SECONDS = 1


    /**
     * Opens a device that stays alive, and can be written to on demand.
     *
     * Two earlier designs failed here, and both failures are the reason this one is shaped as it
     * is.
     *
     * The first scheduled the whole run inside one shell command — descriptor, sleeps, stages —
     * while the harness ran a matching schedule of its own to label the log. Two clocks with
     * nothing tying them together: on the reference device they drifted by roughly twenty seconds
     * and every stage marker landed after the events it was meant to introduce.
     *
     * The second used a named pipe with a sleeping process holding the write end open. It froze the
     * harness on the first real run. Opening a pipe blocks until the other end is opened, so any
     * step in that handshake that does not complete — for any reason, and there are several — stops
     * the thread forever, and the whole session was lost.
     *
     * This one cannot block. The stream is an ordinary file, appended to, and `tail -f` feeds it to
     * the helper. Appending to a file never waits for a reader. There is still one clock: each
     * stage is written by the same thread that writes its marker, immediately after it, so a marker
     * cannot drift from the events it names.
     *
     * This is also the shape a production backend needs: a device that outlives any single command,
     * with input pushed to it as it happens rather than scheduled in advance.
     */
    private fun openSession(bound: IProbeService, descriptor: String): String = buildString {
        appendLine(safeExec(bound, "printf '%s' '$descriptor' > $DESCRIPTOR_PATH; echo descriptor=$?").trim())

        // Truncate rather than delete: the reader below follows this path, and replacing the file
        // under it would leave it following one nothing writes to.
        appendLine(safeExec(bound, ": > $STREAM_PATH; rm -f $HELPER_LOG; echo stream=$?").trim())

        // tail -f never reaches end of input, so the helper keeps the device open and waits for
        // whatever is appended next.
        appendLine(
            safeExec(
                bound,
                "( tail -n +1 -f $STREAM_PATH | uinput - ) > $HELPER_LOG 2>&1 & echo reader=started"
            ).trim()
        )

        appendLine(safeExec(bound, "printf '%s\\n' '$descriptor' >> $STREAM_PATH; echo register=$?").trim())
        Thread.sleep(1500)

        val helper = safeExec(bound, "head -c 400 $HELPER_LOG 2>&1")
        appendLine("helper: ${if (helper.isBlank()) "(silent — no error)" else helper.trim()}")
        val alive = safeExec(bound, "ps -A 2>/dev/null | grep -i uinput | grep -v grep")
        appendLine("uinput process: ${if (alive.isBlank()) "(none listed)" else alive.trim()}")
    }

    /** Appends one report to the open device's stream. Single quotes only; the JSON contains none. */
    private fun send(bound: IProbeService, json: String) =
        safeExec(bound, "printf '%s\\n' '$json' >> $STREAM_PATH")

    /**
     * Creates the device, then drives every control on it in turn.
     *
     * Each stage writes its marker to the event log and then writes the events, so the log reads as
     * stimulus followed by whatever arrived — including nothing, which for a given control is a
     * result and must be recorded as one.
     */
    fun createAndExercise(context: Context, label: String, descriptor: String) =
        withService(context, "Creating device and exercising every control…") { bound ->
            EventLog.note("CREATE+EXERCISE [$label] — sticks, triggers, d-pad, simultaneous buttons")
            emit("── create and exercise: $label")

            val opened = openSession(bound, descriptor)
            emit("device opened${if (opened.isBlank()) "" else " — helper says: ${opened.trim()}"}")

            EXERCISE.forEachIndexed { index, (name, expected, events) ->
                EventLog.note("EXERCISE ${index + 1}/${EXERCISE.size}: $name — expect $expected")
                emit("  ${index + 1}. $name")

                send(bound, events[0])
                Thread.sleep(HOLD_SECONDS * 1000L)
                send(bound, events[1])
                Thread.sleep(700)
            }

            EventLog.note("EXERCISE complete — every control returned to rest")
            val log = safeExec(bound, "cat $HELPER_LOG 2>&1 | head -30")
            safeExec(bound, "pkill -x uinput")

            buildString {
                appendLine("helper output: ${if (log.isBlank()) "(none — no error)" else log}")
                appendLine("device closed.")
                appendLine()
                appendLine("Read the Events tab. Each EXERCISE note is followed by whatever that")
                appendLine("control produced. What matters for each: did anything arrive, what")
                appendLine("dev= was on it, and for the axes, what value — a half-deflection")
                appendLine("stage reporting 1.000 means the value saturated rather than scaled.")
                appendLine("A stage with nothing after it is a control that did not come through,")
                appendLine("and that is a result worth exporting too.")
            }.trim()
        }

    /**
     * Tier 6 support: opens the device and leaves it there, cycling one control at a time.
     *
     * A target application's own binding screen is better evidence than gameplay, because it states
     * what it thinks it received. Reaching that screen means leaving the harness, so nothing here
     * may depend on the harness staying in the foreground: the whole schedule is handed to the
     * shell-privileged process, which is not subject to the battery restrictions that would
     * otherwise stop a backgrounded measurement mid-run.
     *
     * The cycle is deliberately slow. A binding screen takes whatever arrives first, so a control
     * every few seconds is bindable one at a time; a fast loop would bind everything to whatever
     * was pressed last.
     */
    private const val STAGE_PATH = "/data/local/tmp/kestrel-stage"

    /**
     * Tier 6 support: opens the device and leaves it there, cycling one control at a time.
     *
     * A target application's own binding screen is better evidence than gameplay, because it states
     * what it thinks it received. Reaching that screen means leaving the harness, so nothing here
     * may depend on the harness staying in the foreground: the whole schedule is handed to the
     * shell-privileged process in one command, which is not subject to the battery restrictions
     * that would otherwise stop a backgrounded measurement mid-run.
     *
     * This deliberately uses the plain pipeline rather than the appended stream the exercise test
     * uses. The pipeline is the mechanism that has already delivered every control on this hardware;
     * the stream exists to keep log markers aligned with events, and when the operator is in another
     * application there are no markers to align. Proven mechanism where it is proven.
     *
     * The cycle is slow on purpose. A binding screen takes whatever arrives first, so a control
     * every few seconds is bindable one at a time; a fast loop would bind everything to whatever
     * was pressed last.
     */
    fun holdForTarget(context: Context, label: String, descriptor: String) =
        withService(context, "Opening a device and holding it for target testing…") { bound ->
            EventLog.note("HOLD [$label] — device open, controls cycling for about two minutes")
            emit("── hold for target testing: $label")

            emit(safeExec(bound, "printf '%s' '$descriptor' > $DESCRIPTOR_PATH; echo descriptor=$?").trim())

            EXERCISE.forEachIndexed { index, (_, _, events) ->
                events.forEachIndexed { half, json ->
                    safeExec(bound, "printf '%s' '$json' > $STAGE_PATH-$index-$half.json")
                }
            }
            emit("${EXERCISE.size} controls prepared, three rounds each")

            val schedule = buildString {
                append("cat $DESCRIPTOR_PATH; sleep 3")
                repeat(3) {
                    EXERCISE.indices.forEach { index ->
                        append("; cat $STAGE_PATH-$index-0.json; sleep 1")
                        append("; cat $STAGE_PATH-$index-1.json; sleep 3")
                    }
                }
                // The device must outlive its last release, or the release is never delivered.
                append("; sleep 5")
            }

            safeExec(bound, "rm -f $HELPER_LOG")
            emit(safeExec(bound, "( ($schedule) | uinput - ) > $HELPER_LOG 2>&1 & echo cycling").trim())

            Thread.sleep(3500)
            val count = android.view.InputDevice.getDeviceIds().size
            val helper = safeExec(bound, "head -c 300 $HELPER_LOG 2>&1")
            emit("device count now: $count")
            emit("helper: ${if (helper.isBlank()) "(silent — no error)" else helper.trim()}")

            buildString {
                appendLine("The device is open and cycles every control three times, about four")
                appendLine("seconds apart, for roughly two minutes.")
                appendLine()
                appendLine("Check the Devices tab first — if Kestrel Virtual Controller is not")
                appendLine("listed there, nothing was created and the rest of this will show")
                appendLine("nothing either.")
                appendLine()
                appendLine("Then leave this screen. Open the target application, find its")
                appendLine("controller or input settings, and look for two things:")
                appendLine("  1. does it list Kestrel Virtual Controller as connected?")
                appendLine("  2. on its binding screen, does a control bind itself as the cycle runs?")
                appendLine()
                appendLine("Come back and press Destroy device when finished.")
            }.trim()
        }

    /** Stops any helper, so a device is never left behind. */
    fun destroyVirtualDevice(context: Context) = withService(context, "Stopping helper…") { bound ->
        EventLog.note("DESTROY: stopping uinput helper")
        safeExec(bound, "pkill -x uinput; pkill -x tail")
        Thread.sleep(400)
        val remaining = safeExec(bound, "pgrep -x uinput || echo NONE")
        safeExec(bound, "rm -f $STREAM_PATH")
        "── destroy virtual device\nstill running: ${remaining.trim()}"
    }

    /**
     * Escape hatch for the harness itself.
     *
     * Never disabled, because the state it exists to recover from is the one where everything else
     * is. A run that wedged left every control locked and the session unrecoverable; this unlocks
     * them, stops any helper, and says what it found afterwards.
     */
    fun forceReset(context: Context) {
        busy.value = false
        emit("\n── RESET — controls unlocked")
        EventLog.note("RESET pressed")

        val bound = service
        if (bound == null) {
            emit("No privileged service is bound, so nothing was running to stop.")
            return
        }
        Thread {
            val result = safeExec(
                bound,
                "pkill -x uinput; pkill -x tail; rm -f $STREAM_PATH; pgrep -x uinput || echo NONE",
                4000,
            )
            emit("after reset — uinput processes: ${result.trim()}")
        }.start()
    }

    /** Manual escape hatch for a stuck axis. */
    fun releaseAll(context: Context) = withService(context, "Releasing…") { bound ->
        EventLog.note("MANUAL RELEASE: $RECENTRE")
        val result = safeExec(bound, RECENTRE)
        "── release all axes\n\$ $RECENTRE\n$result"
    }

    /**
     * Every call is bounded. The service kills anything that overruns and says so, so a command
     * that never returns costs one line in the transcript instead of the whole session.
     */
    private fun safeExec(bound: IProbeService, command: String, timeoutMs: Int = 6000): String = try {
        bound.exec(command, timeoutMs)
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
        emit("\n$pending")

        val existing = service
        if (existing != null) {
            runOffThread(existing, work)
            return
        }

        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ProbeService::class.java.name)
            ).daemon(false).processNameSuffix("probe").debuggable(false).version(5)

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
            // Work that reports progressively returns nothing more to add.
            if (result.isNotBlank()) emit(result)
            busy.value = false
        }.start()
    }
}
