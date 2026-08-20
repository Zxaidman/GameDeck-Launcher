package io.github.zxaidman.kestrel.diagnostics

import android.content.Context
import android.os.Build
import android.view.InputDevice
import io.github.zxaidman.kestrel.platform.input.fallback.ProbeState
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

        // The fallback under measurement. Reported whether or not it has been used, because "the
        // service was not even enabled" is an answer a reader needs to be able to distinguish from
        // "it was enabled and produced nothing".
        report.put(
            "fallbackProbe",
            JSONObject().apply {
                put("enabledInSettings", FallbackProbe.enabledInSettings(context))
                put("serviceConnected", ProbeState.connected)
                put("holdsWriteSecureSettings", FallbackProbe.holdsWriteSecureSettings(context))
                put("serviceNote", ProbeState.note)
                val measured = FallbackProbe.last
                if (measured == null) {
                    put("measured", false)
                } else {
                    put("measured", measured.samples.isNotEmpty())
                    put("note", measured.note)
                    put("landed", measured.samples.size)
                    put("missed", measured.missed)
                    put("gesturesCompleted", measured.accepted)
                    put("gesturesCancelled", measured.cancelled)
                    put("latencyBestMs", measured.best)
                    put("latencyMedianMs", measured.median)
                    put("latencyWorstMs", measured.worst)
                    put("latencyAllMs", JSONArray().apply { measured.samples.forEach { put(it) } })
                    put("dragAccepted", measured.dragAccepted)
                    put("dragMovements", measured.dragMoves)
                    put("dragSpanMs", measured.dragSpanMillis)
                }
            },
        )

        // The two halves of the same story: what Kestrel sent, and what the platform delivered
        // back. Either one alone can only say whether something happened at that instant; together
        // and in order they say where a fault is — above the virtual device or below it.
        report.put("sent", trail(SessionState.engine?.trail, null))
        report.put(
            "received",
            trail(
                state.trail,
                "Only fills while Kestrel's own screen has focus. The platform delivers a " +
                    "controller's events to the focused window, so an empty trail during play in " +
                    "another application is expected and is not evidence that nothing arrived.",
            ),
        )

        return report.toString(2)
    }

    /**
     * A trail, oldest first, with times relative to its own first mark.
     *
     * Relative because the question a reader has is "how long after the press did the release
     * arrive", not "what time was it". `dropped` is reported even when it is zero: a reader has to
     * be able to tell a quiet trail from a truncated one, and silence about it would leave that
     * ambiguous.
     */
    private fun trail(
        source: io.github.zxaidman.kestrel.core.diagnostics.InputTrail?,
        note: String?,
    ): JSONObject {
        val marks = source?.snapshot().orEmpty()
        val first = marks.firstOrNull()?.atMillis ?: 0L
        return JSONObject().apply {
            put("count", marks.size)
            put("dropped", source?.dropped ?: 0L)
            if (note != null) put("note", note)
            put(
                "marks",
                JSONArray().apply {
                    marks.forEach { mark ->
                        put(
                            JSONObject().apply {
                                put("tMs", mark.atMillis - first)
                                put("kind", mark.kind)
                                put("detail", mark.detail)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun versionOf(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
