package io.github.zxaidman.kestrel.phase0

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import rikka.shizuku.Shizuku
import org.json.JSONArray
import org.json.JSONObject

/**
 * EXPERIMENTAL — Phase 0 input feasibility harness.
 *
 * This is a prototype and a measurement instrument. It is not product code, it is not on the
 * product's dependency graph, and nothing in it may be promoted without first moving it behind the
 * abstraction in `platform/input/` (PROJECT_STRUCTURE.md §27).
 *
 * The harness does not synthesise input into its own window. Injection attempts are issued by the
 * platform's own `input` tool in a separate shell-privileged process, and travel the ordinary system
 * input path; this window observes what arrives exactly as any other application would. The command
 * issued is written into the same log as the events that follow, so a result can always be traced to
 * the stimulus that caused it — or shown to have produced nothing. See `docs/phase0/README.md`.
 */
class Phase0Activity : ComponentActivity(), InputManager.InputDeviceListener {

    private lateinit var inputManager: InputManager
    private var devices by mutableStateOf<List<InputDevice>>(emptyList())

    private var pendingExport: String? = null

    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        when {
            uri == null -> ExportState.message.value = "Save cancelled."
            content == null -> ExportState.message.value = "Nothing to save."
            else -> ExportState.message.value = try {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                "Saved. Open your file manager at the folder you chose."
            } catch (e: Exception) {
                "Save failed: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> ShizukuProbe.refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        refreshDevices()

        // Guarded: the harness must still work with Shizuku absent, degrading to observation only.
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Throwable) {
            EventLog.note("Shizuku listener unavailable: ${e.javaClass.simpleName}")
        }
        ShizukuProbe.refreshStatus()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HarnessScreen(
                        devices = devices,
                        onRefresh = ::refreshDevices,
                        onSave = ::saveReport,
                        onShare = ::shareReport,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (e: Throwable) {
            // Nothing to clean up when the library never attached.
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
        // Describe it immediately. A device created by the helper may exist only briefly, and once
        // it is gone the live inventory cannot tell us what it was.
        val device = InputDevice.getDevice(deviceId)
        if (device != null) {
            InputInventory.capture(device, "hot-plug added")
            EventLog.note(
                "DEVICE ADDED   id=$deviceId ${device.name}\n" +
                    "      sources=${InputInventory.describeSources(device.sources)} " +
                    "axes=${device.motionRanges.size} " +
                    "gamepad=${InputInventory.looksLikeGamepad(device)}"
            )
        } else {
            EventLog.note("DEVICE ADDED   id=$deviceId (already gone before it could be read)")
        }
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
        // getDeviceIds() returns an IntArray, which has map but not mapNotNull.
        devices = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull()
    }

    // Every key event routed to this window, including those produced from a shell.
    //
    // Dispatch is the earliest hook, so an event is recorded even when a view would later consume
    // it — which matters here, because focus navigation eats D-pad events before they reach
    // onKeyDown. The event is then passed on untouched: the harness observes, it does not swallow.
    // That also keeps the back gesture working normally rather than trapping the user.
    //
    // The suppression is required because androidx marks its own override as library-group
    // restricted; calling through to it is exactly what an observer must do.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        EventLog.record(event)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        EventLog.record(event)
        return super.dispatchGenericMotionEvent(event)
    }

    private fun buildReport(): String {
        val report = JSONObject()
        report.put("harnessVersion", "phase0-0.0.8")
        report.put("capturedAtMillis", System.currentTimeMillis())
        report.put("device", InputInventory.deviceReport())

        val deviceArray = JSONArray()
        InputInventory.snapshot().forEach { deviceArray.put(it) }
        report.put("inputDevices", deviceArray)

        val capturedArray = JSONArray()
        InputInventory.capturedSnapshot().forEach { capturedArray.put(it) }
        report.put("capturedDevices", capturedArray)
        report.put("eventLog", EventLog.asText())
        report.put("privilegeState", ShizukuProbe.status.value)
        report.put("probeOutput", ShizukuProbe.output.value)
        return report.toString(2)
    }

    /** Lets the user choose the destination, so the file is somewhere they can actually reach. */
    private fun saveReport() {
        pendingExport = buildReport()
        ExportState.message.value = "Choose a folder…"
        try {
            saveLauncher.launch("kestrel-phase0-${System.currentTimeMillis()}.json")
        } catch (e: Exception) {
            pendingExport = null
            ExportState.message.value = "Could not open the file picker: ${e.javaClass.simpleName}"
        }
    }

    private fun shareReport() {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Phase 0 report")
                        putExtra(Intent.EXTRA_TEXT, buildReport())
                    },
                    "Share Phase 0 report",
                )
            )
            ExportState.message.value = ""
        } catch (e: Exception) {
            ExportState.message.value = "Share failed: ${e.javaClass.simpleName}"
        }
    }
}

object ExportState {
    val message = androidx.compose.runtime.mutableStateOf("")
}

private fun deviceHeadline(): String =
    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}  ·  " +
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

private const val TAB_DEVICES = 0
private const val TAB_EVENTS = 1
private const val TAB_PROBE = 2

@Composable
private fun HarnessScreen(
    devices: List<InputDevice>,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    var tab by remember { mutableStateOf(TAB_DEVICES) }

    // Android 15 draws edge to edge by default at this target level, so content sits under the
    // status and navigation bars unless it is inset explicitly.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
    ) {
        Text(
            text = "Kestrel Phase 0 — observation only",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = deviceHeadline(),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { tab = TAB_DEVICES }) { Text("Devices (${devices.size})") }
            Button(onClick = { tab = TAB_EVENTS }) { Text("Events (${EventLog.counter})") }
            Button(onClick = { tab = TAB_PROBE }) { Text("Probe") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRefresh) { Text("Refresh") }
            Button(onClick = { EventLog.clear() }) { Text("Clear log") }
            Button(onClick = onSave) { Text("Save…") }
            Button(onClick = onShare) { Text("Share") }
        }

        if (ExportState.message.value.isNotEmpty()) {
            Text(text = ExportState.message.value, style = MaterialTheme.typography.bodySmall)
        }

        when (tab) {
            TAB_DEVICES -> DeviceList(devices)
            TAB_EVENTS -> EventList()
            else -> ProbePanel()
        }
    }
}

/**
 * Tier 5, made runnable without a computer.
 *
 * Everything here reads state. Nothing creates a device or produces an event, so a result shown on
 * this screen cannot have been manufactured by the harness itself.
 */
@Composable
private fun ProbePanel() {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = "Privilege state",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = ShizukuProbe.status.value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ShizukuProbe.refreshStatus() }) { Text("Refresh status") }
            Button(onClick = { ShizukuProbe.requestPermission() }) { Text("Grant permission") }
        }

        Button(
            onClick = { ShizukuProbe.runProbes(context) },
            enabled = !ShizukuProbe.busy.value,
        ) {
            Text(if (ShizukuProbe.busy.value) "Running…" else "Run probe")
        }

        Text(
            text = "Injection attempts",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Each runs the system's own input tool with shell privilege. Press one, then " +
                "read the Events tab: the log shows the command, then whatever actually arrived. " +
                "Nothing arriving is also a result worth recording.",
            fontSize = 12.sp,
        )
        for (injection in ShizukuProbe.INJECTIONS) {
            Button(
                onClick = { ShizukuProbe.inject(context, injection) },
                enabled = !ShizukuProbe.busy.value,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("${injection.label} — ${injection.description}")
            }
        }

        Text(
            text = "Virtual device",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Attempts to create a controller the system sees as its own device — the only " +
                "route to a real device identity. Each holds the device open for five seconds; " +
                "watch the Devices tab during that window, and the Events tab for DEVICE ADDED.",
            fontSize = 12.sp,
        )
        for ((label, descriptor) in ShizukuProbe.CREATIONS) {
            Button(
                onClick = { ShizukuProbe.createVirtualDevice(context, label, descriptor) },
                enabled = !ShizukuProbe.busy.value,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text("Create virtual controller — $label")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { ShizukuProbe.destroyVirtualDevice(context) },
                enabled = !ShizukuProbe.busy.value,
            ) {
                Text("Destroy device")
            }
            Button(
                onClick = { ShizukuProbe.releaseAll(context) },
                enabled = !ShizukuProbe.busy.value,
            ) {
                Text("RELEASE ALL")
            }
            Button(
                onClick = { ShizukuProbe.clearOutput() },
                enabled = !ShizukuProbe.busy.value,
            ) {
                Text("Clear output")
            }
        }
        Text(
            text = "An injected axis stays where it is put. Nothing returns it to centre on its " +
                "own, and while it is held the system emits directional keys without stopping. " +
                "The stick test now auto-releases after 1.2 seconds; RELEASE ALL is the manual " +
                "escape hatch if anything is ever left held.",
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (ShizukuProbe.output.value.isNotEmpty()) {
            Text(
                text = ShizukuProbe.output.value,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Text(
                text = "\nThe probe asks whether a shell-privileged process can reach the kernel " +
                    "virtual-input facility — the one path that could give Kestrel a real " +
                    "controller identity. It only reads; it creates nothing.\n\n" +
                    "Requires Shizuku installed, running, and permission granted.",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

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
