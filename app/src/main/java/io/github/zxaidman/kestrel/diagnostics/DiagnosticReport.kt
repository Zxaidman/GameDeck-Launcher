package io.github.zxaidman.kestrel.diagnostics

import android.content.Context
import android.os.Build
import android.view.InputDevice
import io.github.zxaidman.kestrel.platform.input.virtual.VirtualControllerBackend
import io.github.zxaidman.kestrel.platform.session.SessionState
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability
import org.json.JSONArray
import org.json.JSONObject

/**
 * A machine-readable record of what the phone reports.
 *
 * Every conclusion this project has reached came from one of these rather than from a description
 * of what someone saw, and the difference has mattered repeatedly: a screenshot shows a number,
 * an export shows the device that produced it, its descriptor, and what else was present at the
 * time. It carries the build fingerprint and no personal data (`SECURITY.md`).
 */
public object DiagnosticReport {

    public fun build(context: Context, state: InputPreviewState): String {
        val report = JSONObject()
        report.put("kestrelVersion", versionOf(context))
        report.put("capturedAtMillis", System.currentTimeMillis())

        report.put(
            "device",
            JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("androidRelease", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("securityPatch", Build.VERSION.SECURITY_PATCH)
                put("fingerprint", Build.FINGERPRINT)
            },
        )

        val shizuku = ShizukuCapability.state()
        report.put(
            "privilege",
            JSONObject().apply {
                put("serviceRunning", shizuku.serviceRunning)
                put("permissionGranted", shizuku.permissionGranted)
                put("level", shizuku.privilege.name)
                put("version", shizuku.version ?: JSONObject.NULL)
                put("usable", shizuku.usable)
            },
        )

        report.put(
            "session",
            JSONObject().apply {
                put("open", SessionState.open.value)
                put("detail", SessionState.detail.value)
                put(
                    "holders",
                    ShizukuCapability.shell()?.let { VirtualControllerBackend.holders(it) } ?: "(no shell)",
                )
            },
        )

        val devices = JSONArray()
        InputDevice.getDeviceIds().forEach { id ->
            val device = InputDevice.getDevice(id) ?: return@forEach
            devices.put(
                JSONObject().apply {
                    put("id", device.id)
                    put("name", device.name)
                    put("descriptor", device.descriptor)
                    put("sourcesRaw", device.sources)
                    put("controllerNumber", device.controllerNumber)
                    put("isExternal", device.isExternal)
                    put("rangeCount", device.motionRanges.size)
                    put("distinctAxes", device.motionRanges.map { it.axis }.distinct().size)
                    put(
                        "axes",
                        JSONArray().apply {
                            device.motionRanges.map { it.axis }.distinct().forEach { axis ->
                                put(android.view.MotionEvent.axisToString(axis))
                            }
                        },
                    )
                },
            )
        }
        report.put("inputDevices", devices)

        report.put(
            "lastInput",
            JSONObject().apply {
                put("source", state.sourceDevice)
                put("events", state.eventCount)
                put("lastButton", state.lastButton)
                put("rawX", state.rawX)
                put("rawY", state.rawY)
            },
        )

        return report.toString(2)
    }

    private fun versionOf(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
