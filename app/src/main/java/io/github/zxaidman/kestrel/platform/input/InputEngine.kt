package io.github.zxaidman.kestrel.platform.input

import io.github.zxaidman.kestrel.core.input.AnalogProfile
import io.github.zxaidman.kestrel.core.input.applyStick
import io.github.zxaidman.kestrel.core.input.applyTrigger
import io.github.zxaidman.kestrel.platform.shizuku.IPrivilegedShell

/**
 * Carries a control the user touched to the controller the platform sees.
 *
 * This is the piece the product had been missing, and its absence was invisible in exactly the way
 * that matters: the on-screen stick moved, the numbers moved, the controller existed and was
 * recognised by five emulators — and nothing connected the two, so an emulator saw a controller
 * that never moved. Everything looked right and nothing arrived.
 *
 * `UI → InputEngine → backend → platform` is the path `CLAUDE.md` §4 requires, and this is the
 * middle of it. The UI hands over controller semantics; the shaping in `core/input/` is applied
 * here, once, for every source; and only below this does anything become a kernel event code.
 */
public class InputEngine(private val shell: IPrivilegedShell) {

    /**
     * The latest position, and a thread that writes it.
     *
     * A thumb produces far more positions than a device needs, and each one written separately
     * would queue behind the last. Only the newest matters — an old stick position is not partial
     * information, it is wrong information — so the writer takes the newest and discards the rest.
     */
    @Volatile private var pendingX: Double = 0.0
    @Volatile private var pendingY: Double = 0.0
    @Volatile private var dirty: Boolean = false
    @Volatile private var running: Boolean = false

    @Volatile public var delivered: Long = 0L
        private set

    @Volatile public var lastError: String = ""
        private set

    public fun start(streamPath: String): Boolean {
        val opened = try {
            shell.openDeviceStream(streamPath)
        } catch (e: Throwable) {
            lastError = "Could not open the stream: ${e.javaClass.simpleName}"
            false
        }
        if (!opened) {
            lastError = "The privileged service refused the stream. Is a session open?"
            return false
        }

        running = true
        Thread {
            while (running) {
                if (dirty) {
                    dirty = false
                    writeStick(pendingX, pendingY)
                }
                // About sixty times a second. Faster gains nothing a screen can show; slower is
                // visible as lag on a fast flick.
                Thread.sleep(16)
            }
        }.also { it.isDaemon = true }.start()
        return true
    }

    public fun stop() {
        running = false
        // Never leave a control held. A stick abandoned at full deflection keeps the platform
        // emitting directional keys indefinitely — measured at over 360 repeats in Phase 0.
        writeStick(0.0, 0.0)
        runCatching { shell.closeDeviceStream() }
    }

    /** Accepts a stick position in controller terms and shapes it before it goes anywhere. */
    public fun stick(rawX: Double, rawY: Double, profile: AnalogProfile) {
        val shaped = applyStick(rawX, rawY, profile)
        pendingX = shaped.x
        pendingY = shaped.y
        dirty = true
    }

    /** A button, written immediately: a press is a moment, not a position to be coalesced. */
    public fun button(code: Int, pressed: Boolean) {
        write(report(listOf(EV_KEY, code, if (pressed) 1 else 0)))
    }

    /** A trigger, shaped like any other analog control. */
    public fun trigger(raw: Double, profile: AnalogProfile, right: Boolean) {
        val value = (applyTrigger(raw, profile) * TRIGGER_MAX).toInt()
        write(report(listOf(EV_ABS, if (right) ABS_GAS else ABS_BRAKE, value)))
    }

    private fun writeStick(x: Double, y: Double) {
        write(
            report(
                listOf(
                    EV_ABS, ABS_X, (x * STICK_MAX).toInt(),
                    EV_ABS, ABS_Y, (y * STICK_MAX).toInt(),
                )
            )
        )
    }

    /**
     * One report, ending in the synchronisation marker.
     *
     * Every report must end with it: without the marker the kernel holds the values and the device
     * appears not to move, which looks identical to nothing being written at all.
     */
    private fun report(events: List<Int>): String {
        val all = events + listOf(SYN, SYN_REPORT, 0)
        return """{"id": 1, "command": "inject", "events": [${all.joinToString(", ")}]}""" + "\n"
    }

    private fun write(json: String) {
        try {
            if (shell.writeDeviceStream(json)) {
                delivered += 1
            } else {
                lastError = "The stream is not open. Start a session first."
            }
        } catch (e: Throwable) {
            lastError = "Write failed: ${e.javaClass.simpleName}"
        }
    }

    private companion object {
        // Linux input-event constants. Stable kernel ABI, not values invented here.
        const val EV_KEY = 1
        const val EV_ABS = 3
        const val SYN = 0
        const val SYN_REPORT = 0
        const val ABS_X = 0
        const val ABS_Y = 1
        const val ABS_BRAKE = 10
        const val ABS_GAS = 9

        // The ranges the descriptor declares. The platform normalises these back to -1…+1 on the
        // way out, which Phase 0 measured rather than assumed.
        const val STICK_MAX = 32767.0
        const val TRIGGER_MAX = 255.0
    }
}
