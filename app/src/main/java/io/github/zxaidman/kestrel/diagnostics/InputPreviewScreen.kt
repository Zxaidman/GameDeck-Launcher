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
import androidx.compose.material3.Button
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
import io.github.zxaidman.kestrel.platform.session.ControllerSessionService
import io.github.zxaidman.kestrel.platform.session.SessionState
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability
import kotlinx.coroutines.delay
import kotlin.math.min

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
    }

    public fun record(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastButton = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
            sourceDevice = describe(event.deviceId)
            eventCount += 1
        }
    }

    /** Records a touch-driven stick position, so its source is distinguishable from a device. */
    public fun noteTouchStick(x: Double, y: Double) {
        rawX = x
        rawY = y
        sourceDevice = "touch pad (this screen)"
        eventCount += 1
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
    modifier: Modifier = Modifier,
) {
    var deadzone by remember { mutableStateOf(0.10f) }
    var curve by remember { mutableStateOf(1.0f) }
    var sensitivity by remember { mutableStateOf(1.0f) }
    var invertY by remember { mutableStateOf(false) }

    val profile = AnalogProfile(
        deadzone = deadzone.toDouble(),
        curve = curve.toDouble(),
        sensitivity = sensitivity.toDouble(),
        invertY = invertY,
    )

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
        val context = LocalContext.current

        Section("Report") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onSave) { Text("Save…") }
                Button(onClick = onShare) { Text("Share") }
            }
            Mono(ExportState.message.value.ifBlank { "Exports the device, privilege and session state." })
        }

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
                Button(onClick = { ShizukuCapability.bind(context) {} }, enabled = shizuku.serviceRunning) {
                    Text("Connect")
                }
                Button(onClick = { ShizukuCapability.requestPermission() }, enabled = shizuku.serviceRunning) {
                    Text("Grant")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { ControllerSessionService.start(context) }) { Text("Start controller") }
                Button(onClick = { ControllerSessionService.stop(context) }) { Text("Stop") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Never disabled, and it rebinds before it acts. A controller can outlive the
                // process that created it, so recovery has to work from a cold start with nothing
                // remembered — that is exactly the situation a stuck controller produces.
                Button(onClick = { ControllerSessionService.stop(context) }) {
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
                Button(
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
                Button(onClick = { ControllerSessionService.hideOverlay(context) }) { Text("Hide") }
            }
            Mono("\ncontrol size  %.0f%%".format(SessionState.controlScale.value * 100))
            Slider(
                value = SessionState.controlScale.value,
                onValueChange = {
                    SessionState.controlScale.value = it
                    // Applied to the windows already on screen rather than by putting them up
                    // again, so a control being held is not dropped mid-press.
                    SessionState.overlay?.resize(it)
                },
                valueRange = io.github.zxaidman.kestrel.platform.overlay.ControllerOverlay.MIN_SCALE
                    ..io.github.zxaidman.kestrel.platform.overlay.ControllerOverlay.MAX_SCALE,
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

        Section("Shaping") {
            Labelled("Dead zone", deadzone) { deadzone = it }
            Slider(value = deadzone, onValueChange = { deadzone = it }, valueRange = 0f..0.5f)
            Labelled("Curve", curve) { curve = it }
            Slider(value = curve, onValueChange = { curve = it }, valueRange = 0.4f..3f)
            Labelled("Sensitivity", sensitivity) { sensitivity = it }
            Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0.5f..2.5f)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(checked = invertY, onCheckedChange = { invertY = it })
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
private fun Labelled(label: String, value: Float, onChange: (Float) -> Unit) {
    Mono("$label  %.2f".format(value))
}
