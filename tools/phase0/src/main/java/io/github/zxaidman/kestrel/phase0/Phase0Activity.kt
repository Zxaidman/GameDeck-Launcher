package io.github.zxaidman.kestrel.phase0

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
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

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                EventLog.note(
                    "Notification permission refused — a session will still run and still stop, " +
                        "but without the notification there is no visible handle on it"
                )
            }
        }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> ShizukuProbe.refreshStatus() }

    /**
     * Holds Back while a test is running, and only then.
     *
     * The created controller delivers `KEYCODE_BACK` alongside `BUTTON_B` — the platform's own
     * fallback mapping, seen on a physical controller in Tier 1 and on the created one since. An
     * unguarded Back finishes the activity, so a test that presses B would end the measurement it
     * is halfway through and take the evidence with it.
     *
     * The event is still recorded before this runs: dispatch sees everything, and this only stops
     * the activity acting on it. Enabled for the seconds a test lasts, so nothing traps the user.
     */
    private val runGuard = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            EventLog.note("BACK held — a test is running, the activity was not finished")
        }
    }

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

        onBackPressedDispatcher.addCallback(this, runGuard)

        // The notification is the only always-available way to end a session, so asking for it is
        // asking for the stop button rather than for the ability to interrupt anyone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                LaunchedEffect(ShizukuProbe.busy.value) {
                    runGuard.isEnabled = ShizukuProbe.busy.value
                }
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
        report.put("harnessVersion", "phase0-0.0.17")
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

    /**
     * Shares the report as an actual `.json` file.
     *
     * It used to share the report as text in the message body, which arrives as a wall of pasted
     * characters that has to be copied back out into a file before anything can read it — and which
     * some applications silently truncate. A file arrives as a file: it can be dropped straight into
     * `docs/phase0/results/inbox/`.
     *
     * The file is written into a cache subdirectory that the provider in the manifest exposes, and
     * the receiving application is granted read access to that one URI only.
     */
    private fun shareReport() {
        try {
            val directory = File(cacheDir, "reports").apply { mkdirs() }
            // Old exports would otherwise accumulate in the cache unnoticed.
            directory.listFiles()?.forEach { it.delete() }

            val file = File(directory, "kestrel-phase0-${System.currentTimeMillis()}.json")
            file.writeText(buildReport())

            val uri = FileProvider.getUriForFile(this, "$packageName.reports", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share Phase 0 report",
                )
            )
            ExportState.message.value = "Sharing ${file.name}"
        } catch (e: Exception) {
            ExportState.message.value = "Share failed: ${e.javaClass.simpleName}: ${e.message}"
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
    val screenContext = LocalContext.current

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

        // Every control is locked while a test runs, and this is not a cosmetic guard.
        //
        // Measured on the reference device: the created controller's own input operated the
        // harness. The stick's synthesised d-pad keys walked focus onto a button and the fallback
        // key for BUTTON_A activated it, which opened the file picker in the middle of a run and
        // paused the activity being measured. An instrument that its own stimulus can drive is
        // measuring itself. See `docs/phase0/results/tier5-exercise-report.md` §4.
        val locked = ShizukuProbe.busy.value

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Tabs stay live. Switching one cannot damage a measurement, and locking them left the
            // operator unable to watch the log being written during the run they were watching.
            Button(onClick = { tab = TAB_DEVICES }) { Text("Devices (${devices.size})") }
            Button(onClick = { tab = TAB_EVENTS }) { Text("Events (${EventLog.counter})") }
            Button(onClick = { tab = TAB_PROBE }) { Text("Probe") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRefresh, enabled = !locked) { Text("Refresh") }
            Button(onClick = { EventLog.clear() }, enabled = !locked) { Text("Clear log") }
            Button(onClick = onSave, enabled = !locked) { Text("Save…") }
            Button(onClick = onShare, enabled = !locked) { Text("Share") }
        }

        // Neither of these is ever disabled. A created device can outlive the harness process, so
        // it can also outlive anything the harness remembers about it — on the reference device one
        // survived Destroy, force-stop, clearing data and uninstalling the application, and kept
        // delivering input with nothing installed. Recovery must work from a cold start, on a
        // device this install never created.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ShizukuProbe.forceReset(screenContext) }) { Text("RESET") }
            Button(onClick = { ShizukuProbe.stopOrphans(screenContext) }) {
                Text("STOP ANY DEVICE")
            }
            Button(onClick = { ShizukuProbe.listHolders(screenContext) }) { Text("What is open?") }
        }

        if (locked) {
            Text(
                text = "Test running — Save, Share and Clear are locked so the input under test " +
                    "cannot operate them, and Back is held. Tabs still work. If nothing changes " +
                    "for a long time, press RESET.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Visible without Shizuku, without the Probe tab, and on a fresh install: a device left
        // behind by an earlier run is still delivering input to whatever is on screen, and the
        // operator needs to know that before anything else on this screen matters.
        val orphan = devices.any { it.name.contains("Kestrel", ignoreCase = true) }
        if (orphan) {
            Text(
                text = "A Kestrel virtual controller is currently open on this device. If you did " +
                    "not just start a test, it is left over from an earlier run and is still " +
                    "sending input. Open the Probe tab and press STOP ANY DEVICE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
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

        Button(
            onClick = {
                val (label, descriptor) = ShizukuProbe.CREATIONS.first()
                ShizukuProbe.createAndPress(context, label, descriptor)
            },
            enabled = !ShizukuProbe.busy.value,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text("Create AND press A — the decisive test")
        }

        Button(
            onClick = {
                val (label, descriptor) = ShizukuProbe.CREATIONS.first()
                ShizukuProbe.createAndExercise(context, label, descriptor)
            },
            enabled = !ShizukuProbe.busy.value,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text("Create AND exercise everything — 20s")
        }
        // A session, rather than a fixed-length hold. There is no timer to outlast and no timer to
        // wait out: the device exists while the notification does, and ends when the operator says
        // so or when the application stops renewing its lease.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val (label, descriptor) = ShizukuProbe.CREATIONS.first()
                    SessionService.start(context)
                    ShizukuProbe.startSession(context, label, descriptor, cycling = true)
                },
                enabled = !ShizukuProbe.busy.value,
            ) {
                Text("START session (cycling)")
            }
            Button(
                onClick = {
                    val (label, descriptor) = ShizukuProbe.CREATIONS.first()
                    SessionService.start(context)
                    ShizukuProbe.startSession(context, label, descriptor, cycling = false)
                },
                enabled = !ShizukuProbe.busy.value,
            ) {
                Text("START quiet")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ShizukuProbe.pauseCycle(context) }) { Text("Pause input") }
            Button(onClick = { ShizukuProbe.resumeCycle(context) }) { Text("Resume") }
            Button(onClick = { SessionService.stop(context) }) { Text("STOP session") }
        }

        Text(
            text = "A session keeps the controller open while you are somewhere else, which is " +
                "what makes testing another application possible at all. Cycling drives one " +
                "control every few seconds so a binding screen has something to bind; quiet opens " +
                "the device and sends nothing. Pause stops the input and returns every control to " +
                "rest without closing the device.\n\n" +
                "It is not permanent. The notification is the handle: Stop there ends it from " +
                "anywhere, and the device also closes by itself within about 15 seconds if this " +
                "application is force-stopped, has its data cleared, or is uninstalled — nothing " +
                "in the application has to run for that to happen.",
            fontSize = 12.sp,
        )

        Text(
            text = "Drives both sticks, both triggers, the d-pad and three buttons at once " +
                "through the created device, each held for a second and then returned to rest. " +
                "Leave the screen on and do not touch anything while it runs; read the Events " +
                "tab afterwards. Half-deflection stages are the ones that prove an axis is " +
                "analog rather than a switch.",
            fontSize = 12.sp,
        )

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
