package io.github.zxaidman.kestrel.phase0

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * EXPERIMENTAL — Phase 0 measurement code.
 *
 * Reports what Android says about the input devices currently attached. This answers Test 1
 * (Device Discovery) and supplies the evidence for Test 10 (Gamepad Device Identity) in
 * `docs/PHASE-0.md`.
 *
 * Everything here is observation. Nothing is inferred: if Android does not report a property, it
 * is recorded as absent rather than guessed.
 */
object InputInventory {

    /** Buttons a controller-style device would be expected to advertise. */
    private val GAMEPAD_KEYS = intArrayOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
    )

    private val SOURCE_NAMES = listOf(
        InputDevice.SOURCE_KEYBOARD to "KEYBOARD",
        InputDevice.SOURCE_DPAD to "DPAD",
        InputDevice.SOURCE_GAMEPAD to "GAMEPAD",
        InputDevice.SOURCE_TOUCHSCREEN to "TOUCHSCREEN",
        InputDevice.SOURCE_MOUSE to "MOUSE",
        InputDevice.SOURCE_STYLUS to "STYLUS",
        InputDevice.SOURCE_TRACKBALL to "TRACKBALL",
        InputDevice.SOURCE_TOUCHPAD to "TOUCHPAD",
        InputDevice.SOURCE_JOYSTICK to "JOYSTICK",
        InputDevice.SOURCE_ROTARY_ENCODER to "ROTARY_ENCODER",
    )

    fun describeSources(sources: Int): String {
        val present = SOURCE_NAMES.filter { (mask, _) -> sources and mask == mask }.map { it.second }
        return if (present.isEmpty()) "0x${Integer.toHexString(sources)}" else present.joinToString("|")
    }

    /**
     * True when the device advertises controller semantics. This is the distinction Phase 0 exists
     * to measure — a device that only reports KEYBOARD is not a controller, however it was created.
     */
    fun looksLikeGamepad(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    /**
     * Rumble support, read through the API appropriate to the running version.
     * `InputDevice.getVibrator()` is deprecated from API 31 in favour of the manager.
     */
    private fun hasVibrator(device: InputDevice): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.vibratorManager.vibratorIds.isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            device.vibrator?.hasVibrator() ?: false
        }
    }.getOrDefault(false)

    /**
     * Devices captured the moment they appeared, by the hot-plug callback.
     *
     * A device created by the `uinput` helper can exist for a fraction of a second. Reading the
     * live inventory afterwards finds nothing, which is exactly what made the first creation run
     * inconclusive: the device was observed to exist but never described. These entries survive it.
     */
    val captured = mutableListOf<JSONObject>()

    fun capture(device: InputDevice, note: String) {
        val json = describe(device)
        json.put("capturedBecause", note)
        json.put("capturedAtMillis", System.currentTimeMillis())
        synchronized(captured) {
            captured.add(json)
            while (captured.size > 40) captured.removeAt(0)
        }
    }

    fun capturedSnapshot(): List<JSONObject> = synchronized(captured) { captured.toList() }

    fun snapshot(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            out.add(describe(device))
        }
        return out
    }

    fun describe(device: InputDevice): JSONObject {
        val json = JSONObject()
        json.put("id", device.id)
        json.put("name", device.name)
        json.put("descriptor", device.descriptor)
        json.put("isVirtual", device.isVirtual)
        json.put("isExternal", runCatching { device.isExternal }.getOrDefault(false))
        json.put("sources", describeSources(device.sources))
        json.put("sourcesRaw", device.sources)
        json.put("vendorId", device.vendorId)
        json.put("productId", device.productId)
        json.put("controllerNumber", device.controllerNumber)
        json.put("keyboardType", device.keyboardType)
        json.put("looksLikeGamepad", looksLikeGamepad(device))
        json.put("hasVibrator", hasVibrator(device))

        val axes = JSONArray()
        for (range in device.motionRanges) {
            val axis = JSONObject()
            axis.put("axis", MotionEvent.axisToString(range.axis))
            axis.put("source", describeSources(range.source))
            axis.put("min", range.min.toDouble())
            axis.put("max", range.max.toDouble())
            axis.put("flat", range.flat.toDouble())
            axis.put("fuzz", range.fuzz.toDouble())
            axis.put("resolution", range.resolution.toDouble())
            axes.put(axis)
        }
        json.put("motionRanges", axes)

        val supported = JSONArray()
        val missing = JSONArray()
        val hasKeys = device.hasKeys(*GAMEPAD_KEYS)
        for (i in GAMEPAD_KEYS.indices) {
            val name = KeyEvent.keyCodeToString(GAMEPAD_KEYS[i])
            if (hasKeys[i]) supported.put(name) else missing.put(name)
        }
        json.put("gamepadKeysPresent", supported)
        json.put("gamepadKeysAbsent", missing)

        return json
    }

    /** Short human-readable form for the on-screen list. */
    fun summarise(device: InputDevice): String {
        val marker = if (looksLikeGamepad(device)) "[GAMEPAD]" else "[ other ]"
        val virtual = if (device.isVirtual) " virtual" else ""
        val axisCount = device.motionRanges.size
        return "$marker id=${device.id}$virtual  ${device.name}\n" +
            "         sources=${describeSources(device.sources)}\n" +
            "         vendor=0x${Integer.toHexString(device.vendorId)} " +
            "product=0x${Integer.toHexString(device.productId)} axes=$axisCount"
    }

    fun deviceReport(): JSONObject {
        val json = JSONObject()
        json.put("manufacturer", Build.MANUFACTURER)
        json.put("brand", Build.BRAND)
        json.put("model", Build.MODEL)
        json.put("device", Build.DEVICE)
        json.put("product", Build.PRODUCT)
        json.put("hardware", Build.HARDWARE)
        json.put("androidRelease", Build.VERSION.RELEASE)
        json.put("sdkInt", Build.VERSION.SDK_INT)
        json.put("securityPatch", Build.VERSION.SECURITY_PATCH)
        json.put("buildId", Build.ID)
        json.put("buildDisplay", Build.DISPLAY)
        json.put("fingerprint", Build.FINGERPRINT)
        return json
    }
}
