package io.github.zxaidman.kestrel.phase0

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * EXPERIMENTAL — Phase 0 measurement code.
 *
 * Records every key and motion event the harness window receives, with the identity of the device
 * that produced it. This is the instrument used by Tests 2 through 8 in `docs/PHASE-0.md`: press a
 * control, then read what Android actually delivered.
 *
 * The device id on each line matters more than the key code. An event that arrives from the
 * touchscreen or from a virtual keyboard is not controller input, even when the key code says
 * BUTTON_A.
 */
object EventLog {

    private const val MAX_ENTRIES = 600

    val entries = mutableStateListOf<String>()

    // Snapshot-backed, not a plain Int. A plain var is invisible to composition, so the on-screen
    // count silently stopped matching the log — it only appeared to update when something else
    // happened to recompose.
    private val counterState = mutableIntStateOf(0)

    val counter: Int
        get() = counterState.intValue

    fun clear() {
        entries.clear()
        counterState.intValue = 0
    }

    private fun add(line: String) {
        counterState.intValue += 1
        while (entries.size >= MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }
        // Newest first, so the most recent event is visible without scrolling.
        entries.add(0, "%04d  %s".format(counter, line))
    }

    fun record(event: KeyEvent) {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "ACTION_${event.action}"
        }
        val repeat = if (event.repeatCount > 0) " repeat=${event.repeatCount}" else ""
        add(
            "KEY   ${KeyEvent.keyCodeToString(event.keyCode)} $action$repeat\n" +
                "      dev=${event.deviceId} src=${InputInventory.describeSources(event.source)} " +
                "scan=${event.scanCode}"
        )
    }

    fun record(event: MotionEvent) {
        val axes = listOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        )
        val moved = axes.mapNotNull { axis ->
            val value = event.getAxisValue(axis)
            // Only report axes that are actually deflected, so a resting stick does not flood the log.
            if (kotlin.math.abs(value) > 0.02f) {
                "${MotionEvent.axisToString(axis).removePrefix("AXIS_")}=%.3f".format(value)
            } else {
                null
            }
        }
        val body = if (moved.isEmpty()) "(all axes at rest)" else moved.joinToString("  ")
        add(
            "MOTION $body\n" +
                "      dev=${event.deviceId} src=${InputInventory.describeSources(event.source)}"
        )
    }

    fun note(text: String) = add("NOTE  $text")

    /**
     * True when the event came from a device advertising controller semantics. Used to make the
     * distinction visible in the log rather than leaving it to interpretation.
     */
    fun isFromGamepad(deviceId: Int): Boolean {
        val device = InputDevice.getDevice(deviceId) ?: return false
        return InputInventory.looksLikeGamepad(device)
    }

    fun asText(): String = entries.reversed().joinToString("\n")
}
