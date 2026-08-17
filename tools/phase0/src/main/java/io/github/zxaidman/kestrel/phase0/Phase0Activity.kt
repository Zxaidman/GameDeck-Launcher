package io.github.zxaidman.kestrel.phase0

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * EXPERIMENTAL — Phase 0 input feasibility harness.
 *
 * This is a prototype and a measurement instrument. It is not product code, it is not on the
 * product's dependency graph, and nothing in it may be promoted without first moving it behind the
 * abstraction in `platform/input/` (PROJECT_STRUCTURE.md §27).
 *
 * The harness deliberately injects nothing. It only observes. Injection candidates are driven from
 * a shell, and this screen shows what — if anything — actually arrived. See
 * `docs/phase0/README.md` for the procedure.
 */
class Phase0Activity : ComponentActivity(), InputManager.InputDeviceListener {

    private lateinit var inputManager: InputManager
    private var devices by mutableStateOf<List<InputDevice>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        refreshDevices()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HarnessScreen(
                        devices = devices,
                        onRefresh = ::refreshDevices,
                        onExport = ::exportReport,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refreshDevices()
        EventLog.note("harness resumed")
    }

    override fun onPause() {
        super.onPause()
        inputManager.unregisterInputDeviceListener(this)
        EventLog.note("harness paused — Test 13 lifecycle checkpoint")
    }

    // Device hot-plug. A virtual device created from a shell should appear here without a refresh.
    override fun onInputDeviceAdded(deviceId: Int) {
        val name = InputDevice.getDevice(deviceId)?.name ?: "unknown"
        EventLog.note("DEVICE ADDED   id=$deviceId $name")
        refreshDevices()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        EventLog.note("DEVICE REMOVED id=$deviceId")
        refreshDevices()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        EventLog.note("DEVICE CHANGED id=$deviceId")
        refreshDevices()
    }

    private fun refreshDevices() {
        devices = InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it) }
    }

    // Every key event routed to this window, including those produced from a shell.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        EventLog.record(event)
        // BACK is left to the system so the harness stays escapable.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        EventLog.record(event)
        return true
    }

    private fun exportReport(): String {
        val report = JSONObject()
        report.put("harnessVersion", "phase0-0.0.1")
        report.put("capturedAtMillis", System.currentTimeMillis())
        report.put("device", InputInventory.deviceReport())

        val deviceArray = JSONArray()
        InputInventory.snapshot().forEach { deviceArray.put(it) }
        report.put("inputDevices", deviceArray)
        report.put("eventLog", EventLog.asText())

        return try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val file = File(dir, "phase0-report-${System.currentTimeMillis()}.json")
            file.writeText(report.toString(2))

            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Phase 0 report")
                        putExtra(Intent.EXTRA_TEXT, report.toString(2))
                    },
                    "Share Phase 0 report",
                )
            )
            "Saved: ${file.absolutePath}"
        } catch (e: Exception) {
            "Export failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

private const val TAB_DEVICES = 0
private const val TAB_EVENTS = 1

@Composable
private fun HarnessScreen(
    devices: List<InputDevice>,
    onRefresh: () -> Unit,
    onExport: () -> String,
) {
    var tab by remember { mutableStateOf(TAB_DEVICES) }
    var status by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "Kestrel Phase 0 — observation only",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${Build_MODEL()}  ·  Android ${android.os.Build.VERSION.RELEASE} " +
                "(API ${android.os.Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { tab = TAB_DEVICES }) { Text("Devices (${devices.size})") }
            Button(onClick = { tab = TAB_EVENTS }) { Text("Events (${EventLog.counter})") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRefresh) { Text("Refresh") }
            Button(onClick = { EventLog.clear() }) { Text("Clear log") }
            Button(onClick = { status = onExport() }) { Text("Export") }
        }

        if (status.isNotEmpty()) {
            Text(text = status, style = MaterialTheme.typography.bodySmall)
        }

        when (tab) {
            TAB_DEVICES -> DeviceList(devices)
            else -> EventList()
        }
    }
}

@Composable
private fun Build_MODEL(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

@Composable
private fun DeviceList(devices: List<InputDevice>) {
    if (devices.isEmpty()) {
        Text("No input devices reported.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(devices) { device ->
            Text(
                text = InputInventory.summarise(device),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun EventList() {
    if (EventLog.entries.isEmpty()) {
        Text(
            "No events captured yet.\n\n" +
                "Press a control, or drive an injection candidate from a shell, then read what " +
                "arrived here. An empty log after an injection attempt is itself a result — " +
                "record it."
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(EventLog.entries) { line ->
            Text(text = line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}
