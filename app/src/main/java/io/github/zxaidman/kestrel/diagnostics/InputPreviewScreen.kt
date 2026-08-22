package io.github.zxaidman.kestrel.diagnostics

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zxaidman.kestrel.core.diagnostics.changedEnough
import io.github.zxaidman.kestrel.core.input.AnalogProfile
import io.github.zxaidman.kestrel.core.input.CapabilityState
import io.github.zxaidman.kestrel.core.input.InputCapability
import io.github.zxaidman.kestrel.core.input.applyStick
import io.github.zxaidman.kestrel.core.input.applyTrigger
import io.github.zxaidman.kestrel.core.input.capabilitiesFor
import io.github.zxaidman.kestrel.core.profile.MatchReason
import io.github.zxaidman.kestrel.core.profile.ProfileScope
import io.github.zxaidman.kestrel.core.profile.ProfileSummary
import io.github.zxaidman.kestrel.core.profile.TargetDescriptor
import io.github.zxaidman.kestrel.core.profile.matchProfile
import io.github.zxaidman.kestrel.core.settings.KestrelSettings
import io.github.zxaidman.kestrel.platform.session.ControllerSessionService
import io.github.zxaidman.kestrel.platform.session.SessionState
import io.github.zxaidman.kestrel.platform.settings.AppSettings
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability
import io.github.zxaidman.kestrel.platform.storage.KestrelStorage
import io.github.zxaidman.kestrel.ui.theme.KButton
import io.github.zxaidman.kestrel.ui.theme.KOutlinedButton
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * A diagnostic surface, not a product screen.
 *
 * It exists to let the domain code in `core/` be checked against a real controller on a real phone,
 * which is the one thing unit tests cannot do: the analog transformation is arithmetic and is
 * proven by tests, but whether a curve *feels* right is a question only a thumb can answer.
 *
 * It lives in `app/` under its own package rather than in a `feature/` module because none exists
 * yet, which `CLAUDE.md` §4 allows so long as the package boundary is real. When `feature/` exists
 * this moves there or is deleted.
 *
 * **This screen creates no input.** It reads whatever controller the phone already has — including
 * one created by the Phase 0 harness — and shows what the domain layer makes of it. Kestrel has no
 * input backend yet, and nothing here implies otherwise.
 */

/** What the last save or share did, so neither succeeds or fails silently. */
public object ExportState {
    public val message: androidx.compose.runtime.MutableState<String> =
        androidx.compose.runtime.mutableStateOf("")
}

/** Live values read from whatever controller is connected. */
public class InputPreviewState {

    /**
     * What arrived, in order.
     *
     * The fields below hold the **latest** value of each thing, which is what a screen needs and
     * what an export used to carry. A moment is enough to answer "did anything arrive" and nothing
     * else: it cannot show a press that never got its release, two controls firing when one was
     * touched, or a value climbing while a thumb sat still. Those are the failures that have cost
     * this project time, and each of them is a **sequence**.
     */
    public val trail: io.github.zxaidman.kestrel.core.diagnostics.InputTrail =
        io.github.zxaidman.kestrel.core.diagnostics.InputTrail()

    private var markedX = 0.0
    private var markedY = 0.0
    private var markedRightX = 0.0
    private var markedRightY = 0.0
    private var markedLeftTrigger = 0.0
    private var markedRightTrigger = 0.0

    public var rawX: Double by mutableStateOf(0.0)
    public var rawY: Double by mutableStateOf(0.0)
    public var rawRightX: Double by mutableStateOf(0.0)
    public var rawRightY: Double by mutableStateOf(0.0)
    public var rawLeftTrigger: Double by mutableStateOf(0.0)
    public var rawRightTrigger: Double by mutableStateOf(0.0)
    public var lastButton: String by mutableStateOf("—")
    public var sourceDevice: String by mutableStateOf("—")
    public var eventCount: Int by mutableStateOf(0)

    /** Records a motion event. Axis constants are read here and never leave this layer. */
    public fun record(event: MotionEvent) {
        rawX = event.getAxisValue(MotionEvent.AXIS_X).toDouble()
        rawY = event.getAxisValue(MotionEvent.AXIS_Y).toDouble()
        rawRightX = event.getAxisValue(MotionEvent.AXIS_Z).toDouble()
        rawRightY = event.getAxisValue(MotionEvent.AXIS_RZ).toDouble()
        rawLeftTrigger = event.getAxisValue(MotionEvent.AXIS_BRAKE).toDouble()
        rawRightTrigger = event.getAxisValue(MotionEvent.AXIS_GAS).toDouble()
        sourceDevice = describe(event.deviceId)
        eventCount += 1
        traceAxes(event.deviceId)
    }

    /**
     * Records a key event.
     *
     * **Both directions go into the trail**, though only a press updates the field on screen. A
     * release is the half that matters when a control is stuck, and it was the half being thrown
     * away.
     */
    public fun record(event: KeyEvent) {
        val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                lastButton = name
                sourceDevice = describe(event.deviceId)
                eventCount += 1
                if (event.repeatCount == 0) {
                    mark("key", "$name (${event.keyCode}) down  from ${describe(event.deviceId)}")
                }
            }

            KeyEvent.ACTION_UP ->
                mark("key", "$name (${event.keyCode}) up    from ${describe(event.deviceId)}")
        }
    }

    /** Records a touch-driven stick position, so its source is distinguishable from a device. */
    public fun noteTouchStick(x: Double, y: Double) {
        rawX = x
        rawY = y
        sourceDevice = "touch pad (this screen)"
        eventCount += 1
        traceAxes(null)
    }

    /** Only what moved, and only once it has moved enough to mean something. */
    private fun traceAxes(deviceId: Int?) {
        val from = if (deviceId == null) "" else "  from ${describe(deviceId)}"
        if (changedEnough(markedX, rawX) || changedEnough(markedY, rawY)) {
            markedX = rawX
            markedY = rawY
            mark("leftStick", "%+.3f %+.3f%s".format(rawX, rawY, from))
        }
        if (changedEnough(markedRightX, rawRightX) || changedEnough(markedRightY, rawRightY)) {
            markedRightX = rawRightX
            markedRightY = rawRightY
            mark("rightStick", "%+.3f %+.3f%s".format(rawRightX, rawRightY, from))
        }
        if (changedEnough(markedLeftTrigger, rawLeftTrigger)) {
            markedLeftTrigger = rawLeftTrigger
            mark("L2", "%.3f%s".format(rawLeftTrigger, from))
        }
        if (changedEnough(markedRightTrigger, rawRightTrigger)) {
            markedRightTrigger = rawRightTrigger
            mark("R2", "%.3f%s".format(rawRightTrigger, from))
        }
    }

    private fun mark(kind: String, detail: String) {
        trail.add(System.currentTimeMillis(), kind, detail)
    }

    /** Starts the trail again, so a test can be run without the run before it in the way. */
    public fun clearTrail() {
        trail.clear()
    }

    private fun describe(deviceId: Int): String =
        InputDevice.getDevice(deviceId)?.let { "${it.name} (id ${it.id})" } ?: "id $deviceId"
}

/** Controllers the platform currently reports, by the capabilities they advertise. */
private fun connectedControllers(): List<InputDevice> =
    // getDeviceIds() returns an IntArray, which has map but not mapNotNull.
    InputDevice.getDeviceIds()
        .map { InputDevice.getDevice(it) }
        .filterNotNull()
        .filter { device ->
            val sources = device.sources
            sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        }
        // Phase 0 found a device advertising GAMEPAD with no buttons and no axes at all, so the
        // source flags alone are not evidence of a controller. Capability is read from what it has.
        .filter { it.motionRanges.isNotEmpty() }

@Composable
public fun InputPreviewScreen(
    state: InputPreviewState,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onEditLayout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The shaping is the settings document's, not this screen's. It used to be four `remember`s,
    // which is why every run started by setting it up again.
    val profile = AppSettings.current.value.stickProfile
    val deadzone = profile.deadzone.toFloat()
    val curve = profile.curve.toFloat()
    val sensitivity = profile.sensitivity.toFloat()
    val invertY = profile.invertY

    fun shape(change: (AnalogProfile) -> AnalogProfile) {
        AppSettings.update { it.copy(stickProfile = change(it.stickProfile)) }
    }

    // Everything below reads the platform and Shizuku, neither of which is snapshot state, so
    // nothing recomposed and the screen only updated when it was recreated from scratch — which is
    // why the first device test needed the application clearing from recents to see any change.
    // A ticker is the smallest honest fix: the values are polled, and what is shown is current.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick += 1
        }
    }

    val controllers = remember(tick) { connectedControllers() }
    val shizuku = remember(tick) { ShizukuCapability.state() }
    val sessionOpen = SessionState.open.value
    val sessionDetail = SessionState.detail.value

    val capability = if (controllers.isEmpty()) {
        CapabilityState.CONFIGURE_ONLY
    } else {
        CapabilityState.READY
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Inside the scroll on purpose. It sat above it and held a band of a small screen
        // permanently, which is a title costing more than it says.
        Text(
            text = androidx.compose.ui.res.stringResource(
                id = io.github.zxaidman.kestrel.R.string.app_name
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        val context = LocalContext.current

        Section("Report") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(onClick = onSave) { Text("Save…") }
                KButton(onClick = onShare) { Text("Share") }
                // Start the trail clean, so a test is not read through whatever happened before it.
                KButton(
                    onClick = {
                        state.clearTrail()
                        SessionState.engine?.trail?.clear()
                        ExportState.message.value = "Trail cleared. Do the test, then export."
                    },
                ) { Text("Clear trail") }
            }
            Mono(
                ExportState.message.value.ifBlank {
                    "Exports the device, privilege and session state, plus the last " +
                        "${io.github.zxaidman.kestrel.core.diagnostics.InputTrail.DEFAULT_CAPACITY} " +
                        "things sent and received, in order."
                },
            )
        }

        StorageSection(context)

        Section("Controller session") {
            Mono(
                "Shizuku running:    ${if (shizuku.serviceRunning) "yes" else "no"}\n" +
                    "Permission granted: ${if (shizuku.permissionGranted) "yes" else "no"}\n" +
                    "Privilege:          ${shizuku.privilege}\n" +
                    "Version:            ${shizuku.version ?: "unknown"}\n" +
                    "\n${shizuku.advice}"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(onClick = { ShizukuCapability.bind(context) {} }, enabled = shizuku.serviceRunning) {
                    Text("Connect")
                }
                KButton(onClick = { ShizukuCapability.requestPermission() }, enabled = shizuku.serviceRunning) {
                    Text("Grant")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(onClick = { ControllerSessionService.start(context) }) { Text("Start controller") }
                KButton(onClick = { ControllerSessionService.stop(context) }) { Text("Stop") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Never disabled, and it rebinds before it acts. A controller can outlive the
                // process that created it, so recovery has to work from a cold start with nothing
                // remembered — that is exactly the situation a stuck controller produces.
                KButton(onClick = { ControllerSessionService.stop(context) }) {
                    Text("Force remove any controller")
                }
            }
            Mono(
                "\nSession open: ${if (sessionOpen) "yes" else "no"}\n" +
                    sessionDetail.ifBlank { "(nothing yet)" }
            )
        }

        Section("Touch pad — drives the controller") {
            TouchStick(profile, state)

            val engine = SessionState.engine
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HoldButton("A", 304)
                HoldButton("B", 305)
                HoldButton("X", 307)
                HoldButton("Y", 308)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(
                    onClick = {
                        SessionState.profile = profile
                        if (!io.github.zxaidman.kestrel.platform.overlay.ControllerOverlay
                                .permitted(context)
                        ) {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:" + context.packageName),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            ControllerSessionService.showOverlay(context)
                        }
                    },
                ) {
                    Text(if (SessionState.overlayShown.value) "Controls shown" else "Show controls")
                }
                KButton(onClick = { ControllerSessionService.hideOverlay(context) }) { Text("Hide") }
            }
            // Two rows rather than one. Three buttons side by side fit in landscape and run off
            // the edge in portrait, where nothing scrolls sideways — so the editor could not be
            // opened at all with the phone upright. The one that matters most goes first and alone.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KButton(onClick = onEditLayout) { Text("Edit layout") }
                KButton(onClick = { ExportState.message.value = reloadLayout(context) }) {
                    Text("Reload layout")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Built-in -> duplicate -> user copy -> edit, which is the workflow the schema
                // requires. The built-in cannot be edited because it is inside the application; the
                // copy is a file in the user's own folder, and the pad follows it from then on.
                KButton(onClick = { ExportState.message.value = copyLayoutForEditing(context) }) {
                    Text("Copy layout to my folder")
                }
            }
            Mono("\ncontrol size  %.0f%%".format(SessionState.controlScale.value * 100))
            Slider(
                value = SessionState.controlScale.value,
                onValueChange = { raw ->
                    val it = snap(raw)
                    SessionState.controlScale.value = it
                    // Applied to the windows already on screen rather than by putting them up
                    // again, so a control being held is not dropped mid-press.
                    SessionState.overlay?.resize(it)
                    AppSettings.update { s -> s.copy(controlScale = it.toDouble()) }
                },
                onValueChangeFinished = { AppSettings.persist(context) },
                valueRange = KestrelSettings.MIN_CONTROL_SCALE.toFloat()
                    ..KestrelSettings.MAX_CONTROL_SCALE.toFloat(),
            )
            Mono(
                "\nThe controls on this screen reach the controller only while Kestrel is in " +
                    "front, and that is a limit of where they are rather than of the controller: " +
                    "touching them focuses Kestrel, and the platform sends a controller's events " +
                    "to whichever window has focus. Show controls puts the same stick and buttons " +
                    "in an overlay that never takes focus, which is how they reach a target."
            )
            Mono(
                "\n" + if (engine == null) {
                    "No session, so these controls go nowhere. Start a controller above."
                } else {
                    "reports delivered: ${engine.delivered}" +
                        (if (engine.lastError.isNotBlank()) "\nlast error: ${engine.lastError}" else "")
                } +
                    "\n\nWith a session open, drag the pad or hold a button and the created " +
                    "controller moves. Open an emulator's binding screen and it should bind what " +
                    "you press here."
            )
        }

        Section("Capability") {
            Mono(
                "State:        ${capability.name}\n" +
                    "Can play:     ${if (capability.canStartSession) "yes" else "no"}\n" +
                    "Needs saying: ${if (capability.needsAttention) "yes" else "no"}\n" +
                    "Available:    " + capabilitiesFor(capability, InputCapability.VIRTUAL_CONTROLLER)
                    .joinToString(", ") { it.name }.ifEmpty { "(nothing)" }
            )
            Mono(
                "\nControllers seen: ${controllers.size}\n" +
                    controllers.joinToString("\n") {
                        // A range is reported per source, so a device with three sources lists the
                        // same axis three times. The distinct count is the one that means what a
                        // reader expects; both are shown rather than one being quietly chosen.
                        val distinct = it.motionRanges.map { range -> range.axis }.distinct().size
                        "  ${it.name}  axes=$distinct (ranges=${it.motionRanges.size})"
                    }
                        .ifEmpty { "  (none)" }
            )
        }

        Section("Live input") {
            Mono(
                "From:   ${state.sourceDevice}\n" +
                    "Events: ${state.eventCount}\n" +
                    "Button: ${state.lastButton}\n" +
                    "\nThe stick and trigger readouts below come from a connected controller. The " +
                    "touch pad above has its own, so the two can be compared."
            )
        }

        Section("Left stick — raw against transformed") {
            val transformed = applyStick(state.rawX, state.rawY, profile)
            Mono(
                format("raw   x", state.rawX) + format("  y", state.rawY) + "\n" +
                    format("out   x", transformed.x) + format("  y", transformed.y) + "\n" +
                    format("magnitude", transformed.magnitude)
            )
        }

        Section("Right stick") {
            val transformed = applyStick(state.rawRightX, state.rawRightY, profile)
            Mono(
                format("raw   x", state.rawRightX) + format("  y", state.rawRightY) + "\n" +
                    format("out   x", transformed.x) + format("  y", transformed.y)
            )
        }

        Section("Triggers") {
            Mono(
                format("left  raw", state.rawLeftTrigger) +
                    format("  out", applyTrigger(state.rawLeftTrigger, profile)) + "\n" +
                    format("right raw", state.rawRightTrigger) +
                    format("  out", applyTrigger(state.rawRightTrigger, profile))
            )
        }

        Section("Display") {
            val display = AppSettings.current.value.display

            fun set(update: (io.github.zxaidman.kestrel.core.settings.DisplayPreferences) ->
                io.github.zxaidman.kestrel.core.settings.DisplayPreferences,
            ) {
                AppSettings.update { it.copy(display = update(it.display)) }
                AppSettings.persist(context)
                // Applied at once rather than on the next launch: all three are settings somebody
                // turns on to see what they do.
                (context as? io.github.zxaidman.kestrel.MainActivity)?.applyDisplayPreferences()
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = display.fullScreen,
                    onCheckedChange = { on -> set { it.copy(fullScreen = on) } },
                )
                Text("Full screen", modifier = Modifier.weight(1f))
                Switch(
                    checked = display.drawUnderCutout,
                    onCheckedChange = { on -> set { it.copy(drawUnderCutout = on) } },
                )
                Text("Use the notch area")
            }

            Mono("\norientation")
            io.github.zxaidman.kestrel.core.settings.AppOrientation.entries.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { option ->
                        KButton(
                            onClick = { set { it.copy(orientation = option) } },
                            enabled = display.orientation != option,
                        ) { Text(option.wireName) }
                    }
                }
            }

            Mono("\ntheme")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                io.github.zxaidman.kestrel.core.settings.AppTheme.entries.forEach { option ->
                    KButton(
                        onClick = { set { it.copy(theme = option) } },
                        enabled = display.theme != option,
                    ) { Text(option.wireName) }
                }
            }
            // Two questions, not three answers: light or dark, and then how dark. Offering them as
            // one row of three made them look like one question, which is what they are not.
            val systemIsDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = io.github.zxaidman.kestrel.ui.theme.isDark(display.theme, systemIsDark)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Switch(
                    checked = display.trueBlack,
                    enabled = dark,
                    onCheckedChange = { on -> set { it.copy(trueBlack = on) } },
                )
                Text(
                    text = if (dark) {
                        "True black (AMOLED)"
                    } else {
                        "True black (AMOLED) — applies when dark"
                    },
                )
            }
            Text(
                text = "True black is not a matter of taste on this panel: a black pixel is an " +
                    "unlit one. The pad keeps its own colours whatever is chosen here — it is " +
                    "drawn over other applications and has to be legible on a white page and a " +
                    "black one both.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Shaping") {
            // Written when a drag ends rather than on every frame of it: a slider produces a value
            // per frame, and sixty writes for one decision is sixty writes to the user's storage.
            Labelled("Dead zone", deadzone)
            Slider(
                value = deadzone,
                onValueChange = { v -> shape { it.copy(deadzone = snap(v).toDouble()) } },
                onValueChangeFinished = { AppSettings.persist(context) },
                valueRange = 0f..0.5f,
            )
            Labelled("Curve", curve)
            Slider(
                value = curve,
                onValueChange = { v -> shape { it.copy(curve = snap(v).toDouble()) } },
                onValueChangeFinished = { AppSettings.persist(context) },
                valueRange = 0.4f..3f,
            )
            Labelled("Sensitivity", sensitivity)
            Slider(
                value = sensitivity,
                onValueChange = { v -> shape { it.copy(sensitivity = snap(v).toDouble()) } },
                onValueChangeFinished = { AppSettings.persist(context) },
                valueRange = 0.5f..2.5f,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = invertY,
                    onCheckedChange = { v ->
                        shape { it.copy(invertY = v) }
                        AppSettings.persist(context)
                    },
                )
                Mono("Invert Y")
            }
            Mono(
                "\nPush the stick slowly. Just past the dead zone the output should start from " +
                    "nothing and grow — never jump. That is the property the tests assert and the " +
                    "one you can only judge with a thumb."
            )
        }

        Section("Profile matching") {
            val profiles = listOf(
                ProfileSummary(idOf("user.default"), "Default", ProfileScope.Default),
                ProfileSummary(idOf("user.emulators"), "Emulators", ProfileScope.Family("emulator")),
                ProfileSummary(idOf("user.that-one"), "That one", ProfileScope.Target("org.example.emu")),
            )
            val match = matchProfile(TargetDescriptor("org.example.emu", "emulator"), profiles)
            val familyOnly = matchProfile(TargetDescriptor("org.example.other", "emulator"), profiles)
            val nothing = matchProfile(TargetDescriptor("org.example.unknown"), profiles)

            Mono(
                "Worked example, with three profiles present.\n\n" +
                    line("org.example.emu", match.profile?.name, match.reason) +
                    line("org.example.other", familyOnly.profile?.name, familyOnly.reason) +
                    line("org.example.unknown", nothing.profile?.name, nothing.reason) +
                    "\nEvery answer carries its reason, so the launcher can say why rather than " +
                    "choosing silently."
            )
        }
    }
}

/**
 * A stick driven by a finger, so the shaping can be judged rather than only computed.
 *
 * This exists because of a real ambiguity: a created controller cycles fixed values — full
 * deflection, then rest — so watching it can never show whether the transition past the dead zone
 * is smooth. Only a continuous input can, and until now there was none to hand.
 */
@Composable
private fun TouchStick(profile: AnalogProfile, state: InputPreviewState) {
    var raw by remember { mutableStateOf(Offset.Zero) }

    // The pad kept its position to itself in the first version, so the readouts below stayed at
    // zero while the dot moved — the pad worked and appeared not to. Its values now go to the same
    // place a controller's do, and the source says which is which.
    val out = applyStick(raw.x.toDouble(), raw.y.toDouble(), profile)
    Mono(
        "raw   x %+.3f  y %+.3f\n".format(raw.x, raw.y) +
            "out   x %+.3f  y %+.3f\n".format(out.x, out.y) +
            "magnitude %.3f".format(out.magnitude)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    // Releasing must centre the stick on the device too, not only on screen. A
                    // control left deflected keeps the platform emitting directional keys.
                    onDragEnd = {
                        raw = Offset.Zero
                        SessionState.engine?.stick(0.0, 0.0, profile)
                    },
                    onDragCancel = {
                        raw = Offset.Zero
                        SessionState.engine?.stick(0.0, 0.0, profile)
                    },
                ) { change, _ ->
                    val radius = min(size.width, size.height) / 2f
                    val dx = (change.position.x - size.width / 2f) / radius
                    val dy = (change.position.y - size.height / 2f) / radius
                    raw = Offset(dx.coerceIn(-1f, 1f), dy.coerceIn(-1f, 1f))
                    state.noteTouchStick(raw.x.toDouble(), raw.y.toDouble())
                    // The step that was missing: what the thumb does reaches the controller.
                    SessionState.engine?.stick(raw.x.toDouble(), raw.y.toDouble(), profile)
                }
            }
    ) {
        val radius = min(size.width, size.height) / 2f * 0.9f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val drawn = applyStick(raw.x.toDouble(), raw.y.toDouble(), profile)

        drawCircle(Color.Gray.copy(alpha = 0.25f), radius = radius, center = centre)
        // The dead zone drawn where it actually is, so the number on the slider has a picture.
        drawCircle(
            Color.Red.copy(alpha = 0.30f),
            radius = (radius * profile.deadzone).toFloat(),
            center = centre,
        )
        drawCircle(
            Color.Gray,
            radius = 14f,
            center = centre + Offset(raw.x * radius, raw.y * radius),
        )
        drawCircle(
            Color.Green,
            radius = 20f,
            center = centre + Offset((drawn.x * radius).toFloat(), (drawn.y * radius).toFloat()),
        )
    }
}

/**
 * A button that presses on touch down and releases on touch up, like a real one.
 *
 * Deliberately not an `onClick`: a click is a completed gesture, reported after the finger lifts,
 * which would send a press and a release together and make holding a control impossible. A
 * controller button is a state with a duration, so the press and the release are separate events.
 */
@Composable
private fun HoldButton(label: String, keyCode: Int) {
    Text(
        text = " $label ",
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .padding(4.dp)
            .pointerInput(keyCode) {
                detectTapGestures(
                    onPress = {
                        SessionState.engine?.button(keyCode, true)
                        // Waits for the finger to lift or the gesture to be cancelled; either way
                        // the button must be released, or it stays held on the device.
                        tryAwaitRelease()
                        SessionState.engine?.button(keyCode, false)
                    },
                )
            },
        style = MaterialTheme.typography.titleLarge,
    )
}

private fun idOf(raw: String) =
    (io.github.zxaidman.kestrel.core.configuration.ConfigurationId.parse(raw)
        as io.github.zxaidman.kestrel.core.common.Outcome.Success).value

private fun line(target: String, profile: String?, reason: MatchReason): String =
    "  %-22s -> %-12s %s\n".format(target, profile ?: "(none)", reason.name)

private fun format(label: String, value: Double): String = "%s %+.3f".format(label, value)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun Labelled(label: String, value: Float) {
    Mono("$label  %.2f".format(value))
}

/**
 * Where Kestrel keeps what the user made, and the one action that moves it somewhere lasting.
 *
 * Placed near the top of the screen rather than buried in a settings page, because until it is
 * answered every layout, profile and setting is one uninstall away from being gone — and the whole
 * point of asking is that the user should not discover that afterwards.
 */
@Composable
private fun StorageSection(context: android.content.Context) {
    var refresh by remember { mutableStateOf(0) }
    val chosen = remember(refresh) { KestrelStorage.usingChosenFolder(context) }
    val store = remember(refresh) { KestrelStorage.current(context) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val tree = result.data?.data
        if (tree == null) {
            AppSettings.message.value = "No folder was chosen, so nothing changed."
        } else {
            AppSettings.message.value = when (val outcome = KestrelStorage.useFolder(context, tree)) {
                is io.github.zxaidman.kestrel.core.common.Outcome.Success -> outcome.value
                is io.github.zxaidman.kestrel.core.common.Outcome.Failure -> outcome.error.message
            }
            AppSettings.reload(context)
            refresh += 1
        }
    }

    Section("Files") {
        Mono(
            "Kept in: ${store.description}\n" +
                if (chosen) {
                    "Everything here survives uninstalling Kestrel, and can be copied in a file " +
                        "manager or on a computer."
                } else {
                    "This is inside Android/data, which a file manager cannot open and which is " +
                        "deleted when Kestrel is uninstalled. Choose a folder to keep your work."
                }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KButton(onClick = { runCatching { picker.launch(KestrelStorage.folderPicker()) } }) {
                Text(if (chosen) "Change folder" else "Choose folder")
            }
            if (chosen) {
                KButton(
                    onClick = {
                        AppSettings.message.value = KestrelStorage.forgetFolder(context)
                        AppSettings.reload(context)
                        refresh += 1
                    },
                ) { Text("Stop using it") }
            }
            KButton(
                onClick = {
                    AppSettings.reload(context)
                    refresh += 1
                },
            ) { Text("Reload") }
        }
        if (!chosen) {
            Mono(
                "\nMake a folder called ${KestrelStorage.SUGGESTED_FOLDER_NAME} at the top level " +
                    "of your storage — beside Android, not inside it — and pick that. Anything " +
                    "already saved is copied into it."
            )
        }
        if (AppSettings.message.value.isNotBlank()) Mono("\n" + AppSettings.message.value)
    }
}

/**
 * Two decimals, so a slider writes a number a person can read in the file it lands in.
 *
 * A drag produces values like `0.34827995`, and `settings.json` is a document the user is invited
 * to open and edit. The precision being discarded is far below what a thumb can set or an eye can
 * see, and what is gained is a file that reads like something a person wrote.
 */
private fun snap(value: Float): Float = Math.round(value * 100f) / 100f

/**
 * Copies the layout Kestrel is using into the user's folder, and starts using the copy.
 *
 * This is the moment the pad stops being something inside the application. From here the file in
 * `Kestrel/layouts/` is what the overlay draws — edit it in any text editor, press **Reload
 * layout**, and the controls move. It is also the built-in -> duplicate -> user copy step that
 * `docs/CONFIGURATION_SCHEMA.md` requires, made visible: the shipped layout is never edited,
 * because it cannot be.
 */
private fun copyLayoutForEditing(context: android.content.Context): String {
    val store = io.github.zxaidman.kestrel.platform.storage.KestrelStorage.current(context)
    val repository = io.github.zxaidman.kestrel.core.layout.LayoutRepository(store)
    val current = AppSettings.current.value.layoutId

    val source = when (val loaded = repository.load(current)) {
        is io.github.zxaidman.kestrel.core.common.Outcome.Failure ->
            return "Could not read '$current': ${loaded.error.message}"
        is io.github.zxaidman.kestrel.core.common.Outcome.Success -> loaded.value
    }
    if (!source.header.id.isBuiltIn) {
        return "'${source.header.name}' is already your own copy, in ${store.description}."
    }

    return when (val copy = repository.duplicate(source, "xbox", source.header.name + " (my copy)")) {
        is io.github.zxaidman.kestrel.core.common.Outcome.Failure ->
            "Could not write the copy: ${copy.error.message}"
        is io.github.zxaidman.kestrel.core.common.Outcome.Success -> {
            AppSettings.update { it.copy(layoutId = copy.value.header.id.value) }
            AppSettings.persist(context)
            writeFieldGuide(store)
            reloadLayout(context)
            "Copied to layouts/${copy.value.header.id.value}.json in ${store.description}, " +
                "with HOW-TO-EDIT.md beside it. Edit it, then press Reload layout."
        }
    }
}

/** Reads the layout again and hands it to the controls, without restarting the session. */
private fun reloadLayout(context: android.content.Context): String {
    val store = io.github.zxaidman.kestrel.platform.storage.KestrelStorage.current(context)
    val repository = io.github.zxaidman.kestrel.core.layout.LayoutRepository(store)
    val id = AppSettings.current.value.layoutId
    return when (val loaded = repository.load(id)) {
        is io.github.zxaidman.kestrel.core.common.Outcome.Failure ->
            "'$id' could not be read, so the controls were left as they are: ${loaded.error.message}"
        is io.github.zxaidman.kestrel.core.common.Outcome.Success -> {
            SessionState.overlay?.apply(loaded.value)
            "Controls redrawn from '${loaded.value.header.name}'."
        }
    }
}

private val LINE_END: String = System.lineSeparator()

/**
 * Writes a guide beside the layouts, because JSON cannot carry a comment.
 *
 * The project owner copied a layout and reported the offsets as "a little confusing", which they
 * are: `offsetX: 0.24` says nothing about where a control is unless you already know it is measured
 * inwards from an anchor as a fraction of the screen's shorter side. A schema document in the
 * repository does not help somebody holding a phone with a file manager open.
 *
 * Overwritten each time, so it describes the build that wrote it rather than an older one.
 */
private fun writeFieldGuide(
    store: io.github.zxaidman.kestrel.core.storage.DocumentStore,
): Unit {
    val text = """
        # Editing a Kestrel layout

        Each file in this folder is one controller layout. Edit it in any text editor, then press
        **Reload layout** in Kestrel — or use **Edit layout**, which does the same thing by dragging.

        ## Where a control is

        Position is **not** measured from the corner of the screen in pixels. It is measured from an
        **anchor**, so a layout made on one phone still fits a hand on a different one.

        - `anchor` — the edge or corner the control is pinned to: `bottom-left`, `bottom-right`,
          `top-left`, `top-right`, `bottom-center`, `top-center`, `center-left`, `center-right`.
        - `offsetX`, `offsetY` — how far the control's **centre** sits **inwards from that anchor**.

        Inwards is the part that surprises people. A control anchored `bottom-right` with larger
        offsets moves **left and up**, towards the middle. That is what makes the same numbers work
        in portrait and landscape: they always mean "this far in from my own corner".

        ## What the numbers are measured in

        Fractions of the screen's **shorter side** — never pixels, and never the longer side. So
        `width: 0.13` is thirteen hundredths of the short edge, which is the same physical size
        whichever way the phone is held. Two decimals is as fine as anything needs to be; it is
        about one pixel on a 1080-wide screen.

        ## Every field on a control

        | Field | What it does |
        | --- | --- |
        | `id` | Its name in this file. Must be unique. |
        | `kind` | What it **is**: `button`, `dpad`, `stick`, `analog-trigger`, `digital-trigger`, `decoration`. |
        | `binds` | Which control on the pad it drives, such as `a`, `left-stick`, `left-trigger`. |
        | `label` | What is drawn on it. `null` uses the control's own name. |
        | `group` | Controls sharing a group share one window — **and only controls in one window can be slid between**. That is what lets a thumb roll across the face buttons, or hold `L3` and then move the stick. `null` means a window of its own. |
        | `shape` | `circle`, `square` or `rectangle`. It decides where the control can be **pressed**, not only how it is drawn. |
        | `anchor`, `offsetX`, `offsetY` | Where it is, as above. |
        | `width`, `height` | How big, as fractions of the short side. |
        | `rotation` | Degrees clockwise. Affects pressing as well as drawing. |

        ## Rules Kestrel will not let you break

        - A `stick` must bind to a stick and a `dpad` to the pad. A stick bound to `a` draws
          correctly and does nothing, so it is refused instead.
        - A `decoration` must not bind to anything. Artwork that sends input is a mislabelled
          control.
        - Two controls cannot share an `id`.
        - Sticks and pads are always drawn round, whatever `shape` says.

        If a file cannot be read, **Reload layout** says why and names the field. Nothing is changed
        until it can be read, so a mistake here costs a reload rather than a layout.
    """.trimIndent() + LINE_END

    store.write(
        io.github.zxaidman.kestrel.core.storage.StoreFolder.LAYOUTS,
        "HOW-TO-EDIT.md",
        text,
    )
}
