package io.github.zxaidman.kestrel.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.layout.Anchor
import io.github.zxaidman.kestrel.core.layout.Cluster
import io.github.zxaidman.kestrel.core.layout.Clustering
import io.github.zxaidman.kestrel.core.layout.ControlKind
import io.github.zxaidman.kestrel.core.layout.ControlShape
import io.github.zxaidman.kestrel.core.layout.ControllerLayout
import io.github.zxaidman.kestrel.core.layout.LayoutElement
import io.github.zxaidman.kestrel.core.layout.LayoutSurface
import io.github.zxaidman.kestrel.core.layout.PixelRect
import io.github.zxaidman.kestrel.core.layout.Placement
import io.github.zxaidman.kestrel.core.layout.centeredAt
import io.github.zxaidman.kestrel.core.layout.effectiveShape
import io.github.zxaidman.kestrel.core.layout.resolve
import io.github.zxaidman.kestrel.core.layout.shapedAs
import io.github.zxaidman.kestrel.platform.display.DeviceSurface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Editing a layout by moving it, rather than by typing numbers into a file.
 *
 * The file stays the truth — this writes the same document a text editor would, and everything it
 * can do can still be done by hand. What it adds is the one thing a text editor cannot: **seeing
 * where a control is while deciding where it should be.**
 *
 * Three rules it keeps, because they are the schema's rules rather than this screen's:
 *
 * **A built-in is never edited.** Opening the editor on one duplicates it first, and the copy is
 * what is edited from then on.
 *
 * **Nothing is saved until it is saved.** Dragging changes what is on screen; the file changes when
 * the button is pressed.
 *
 * **The canvas is the phone, not the page.** The arrangement is drawn inside a rectangle with the
 * device's own aspect ratio, scaled to fit whole and never scrolled. The previous version drew into
 * whatever space the screen gave it, which on this device is close to ultrawide — so controls
 * appeared to overlap that did not, and, worse, some that did overlap looked clear. An editor that
 * lies about overlap is worse than a text file, because it invites trust it has not earned.
 */
@Composable
public fun LayoutEditorScreen(
    layout: ControllerLayout,
    onSave: (ControllerLayout) -> String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Re-measured when the phone turns, because the usable area is not the same on both sides of a
    // rotation — the cutout and the bars move with it.
    val measured = remember(configuration) { DeviceSurface.usable(context) }

    var working by remember(layout.header.id) { mutableStateOf(layout) }
    var selectedId by remember(layout.header.id) { mutableStateOf<String?>(null) }
    var dirty by remember(layout.header.id) { mutableStateOf(false) }
    var mode by remember { mutableStateOf(EditorMode.CONTROLS) }
    var message by remember { mutableStateOf("") }
    var gridPx by remember { mutableStateOf(DEFAULT_GRID) }
    var snapToGrid by remember { mutableStateOf(false) }
    var snapToEdges by remember { mutableStateOf(false) }
    var typingNumbers by remember { mutableStateOf(false) }
    var previewLandscape by remember(measured) {
        mutableStateOf(measured.widthPx >= measured.heightPx)
    }

    // One layout, two shapes of phone. A pad that fits in landscape can overlap itself in portrait,
    // which is exactly what was shipped once, so both are previewable without turning the phone.
    val device = remember(measured, previewLandscape) {
        if ((measured.widthPx >= measured.heightPx) == previewLandscape) {
            measured
        } else {
            DeviceSurface.rotated(measured)
        }
    }

    val selected = working.element(selectedId ?: "")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = working.header.name + if (dirty) " •" else "",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = dirty,
                onClick = {
                    message = onSave(working)
                    dirty = false
                },
            ) { Text("Save") }
            TextButton(onClick = onClose) { Text("Close") }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // The canvas is docked and fixed; the tools scroll beside it. Which side depends on the
            // shape of the screen this editor is on, not on the shape of the phone being drawn.
            val besideEachOther = maxWidth > maxHeight
            val dockWidth = maxWidth * DOCK_SHARE_WIDE
            val dockHeight = maxHeight * DOCK_SHARE_TALL

            val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                EditorCanvas(
                    modifier = canvasModifier,
                    device = device,
                    layout = working,
                    mode = mode,
                    selectedId = selectedId,
                    gridPx = gridPx,
                    snapToGrid = snapToGrid,
                    snapToEdges = snapToEdges,
                    onSelect = { selectedId = it },
                    onPlace = { updated ->
                        working = working.replacing(updated)
                        dirty = true
                    },
                )
            }

            val tools: @Composable (Modifier) -> Unit = { toolsModifier ->
                Column(
                    modifier = toolsModifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModeSwitch(mode = mode, onMode = { mode = it })

                    SelectionHeader(
                        element = selected,
                        canType = mode == EditorMode.CONTROLS,
                        onType = { typingNumbers = true },
                    )

                    ControlTools(
                        element = selected,
                        enabled = mode == EditorMode.CONTROLS,
                        onChange = { updated ->
                            working = working.replacing(updated)
                            dirty = true
                        },
                    )

                    WindowTools(
                        layout = working,
                        device = device,
                        element = selected,
                        enabled = mode == EditorMode.WINDOWS,
                        onChange = { updated ->
                            working = working.replacing(updated)
                            dirty = true
                        },
                    )

                    GridTools(
                        gridPx = gridPx,
                        onGrid = { gridPx = it },
                        snapToGrid = snapToGrid,
                        onSnapToGrid = { snapToGrid = it },
                        snapToEdges = snapToEdges,
                        onSnapToEdges = { snapToEdges = it },
                        previewLandscape = previewLandscape,
                        onPreviewLandscape = { previewLandscape = it },
                        device = device,
                    )

                    if (message.isNotBlank()) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            if (besideEachOther) {
                Row(modifier = Modifier.fillMaxSize()) {
                    canvas(Modifier.width(dockWidth).fillMaxHeight())
                    tools(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    canvas(Modifier.height(dockHeight).fillMaxWidth())
                    tools(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    if (typingNumbers && selected != null) {
        NumbersDialog(
            element = selected,
            onDismiss = { typingNumbers = false },
            onApply = { updated ->
                working = working.replacing(updated)
                dirty = true
                typingNumbers = false
            },
        )
    }
}

/** Which of the two things on this screen is being edited. */
public enum class EditorMode(public val label: String) {
    /** Where a control sits, how big it is, what shape it is. */
    CONTROLS("Controls"),

    /** Which controls share a window — and therefore how much of the screen the pad takes away. */
    WINDOWS("Windows"),
}

private val GRID_SIZES = listOf(32, 64, 128, 256)
private const val DEFAULT_GRID = 64
private const val DOCK_SHARE_WIDE = 0.58f
private const val DOCK_SHARE_TALL = 0.52f

/** A step small enough to place a control with and large enough to feel like a press. */
private const val STEP = 0.02

// --- the tool panel ------------------------------------------------------------------------------

@Composable
private fun ModeSwitch(mode: EditorMode, onMode: (EditorMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorMode.entries.forEach { candidate ->
            if (candidate == mode) {
                Button(onClick = { onMode(candidate) }) { Text(candidate.label) }
            } else {
                OutlinedButton(onClick = { onMode(candidate) }) { Text(candidate.label) }
            }
        }
    }
    Text(
        text = when (mode) {
            EditorMode.CONTROLS -> "Drag a control to move it. Nothing is written until Save."
            EditorMode.WINDOWS ->
                "A window is the box around everything in one group. A finger can slide between " +
                    "controls that share one — and everything else the box covers stops reaching " +
                    "the game underneath."
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SelectionHeader(element: LayoutElement?, canType: Boolean, onType: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = element?.let {
                "${it.id}   ${it.kind.wireName}   " +
                    "x %.2f  y %.2f  w %.2f  h %.2f".format(
                        it.placement.offsetX, it.placement.offsetY,
                        it.placement.width, it.placement.height,
                    )
            } ?: "Nothing selected — touch a control.",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = element != null && canType, onClick = onType) { Text("⋮") }
    }
}

@Composable
private fun ControlTools(
    element: LayoutElement?,
    enabled: Boolean,
    onChange: (LayoutElement) -> Unit,
) {
    val live = enabled && element != null
    Text("Control", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = live, onClick = { element?.let { onChange(it.resizedBy(-STEP)) } }) {
            Text("−")
        }
        Button(enabled = live, onClick = { element?.let { onChange(it.resizedBy(STEP)) } }) {
            Text("+")
        }
        Button(enabled = live, onClick = { element?.let { onChange(it.taller(STEP)) } }) {
            Text("taller")
        }
        Button(enabled = live, onClick = { element?.let { onChange(it.taller(-STEP)) } }) {
            Text("shorter")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = live, onClick = { element?.let { onChange(it.withNextShape()) } }) {
            Text(element?.shape?.wireName ?: "shape")
        }
        Button(enabled = live, onClick = { element?.let { onChange(it.withNextAnchor()) } }) {
            Text(element?.placement?.anchor?.wireName ?: "anchor")
        }
    }
}

@Composable
private fun WindowTools(
    layout: ControllerLayout,
    device: LayoutSurface,
    element: LayoutElement?,
    enabled: Boolean,
    onChange: (LayoutElement) -> Unit,
) {
    val live = enabled && element != null
    Spacer(modifier = Modifier.height(2.dp))
    Text("Window", style = MaterialTheme.typography.labelLarge)

    val options = layout.windowOptions()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            enabled = live,
            onClick = { element?.let { onChange(it.withGroupStep(options, -1)) } },
        ) { Text("◀") }
        Text(
            text = element?.group ?: "own window",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = live,
            onClick = { element?.let { onChange(it.withGroupStep(options, 1)) } },
        ) { Text("▶") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = live, onClick = { element?.let { onChange(it.copy(group = null)) } }) {
            Text("own window")
        }
    }

    val clusters = layout.clustersOn(device)
    val screen = device.widthPx * device.heightPx
    clusters.forEach { cluster ->
        val share = if (screen <= 0) 0.0 else cluster.bounds.width * cluster.bounds.height / screen
        val mine = element != null && element.id in cluster.elementIds
        Text(
            text = (if (mine) "▸ " else "  ") +
                "%3d%%  %s".format((share * 100).roundToInt(), cluster.elementIds.joinToString(" ")),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (share > CROWDED) Color(0xFFE0603A) else MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        text = "Percentages are of the whole screen. Past a quarter, a window is taking away more " +
            "than a pad should.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Where a window stops being a pad and starts being a lid. Matches the shipped layout's tests. */
private const val CROWDED = 0.25

@Composable
private fun GridTools(
    gridPx: Int,
    onGrid: (Int) -> Unit,
    snapToGrid: Boolean,
    onSnapToGrid: (Boolean) -> Unit,
    snapToEdges: Boolean,
    onSnapToEdges: (Boolean) -> Unit,
    previewLandscape: Boolean,
    onPreviewLandscape: (Boolean) -> Unit,
    device: LayoutSurface,
) {
    var open by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(2.dp))
    Text("Grid and snapping", style = MaterialTheme.typography.labelLarge)

    Box {
        OutlinedButton(onClick = { open = true }) { Text("Grid  ${gridPx} × ${gridPx} px") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GRID_SIZES.forEach { size ->
                DropdownMenuItem(
                    text = { Text("$size × $size px") },
                    onClick = {
                        onGrid(size)
                        open = false
                    },
                )
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = snapToGrid, onCheckedChange = onSnapToGrid)
        Text("Snap to the grid", style = MaterialTheme.typography.bodyMedium)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = snapToEdges, onCheckedChange = onSnapToEdges)
        Text("Snap to gamepad edges", style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        text = "Edge snapping lines a control up with the other controls and with the edges of the " +
            "screen, and wins over the grid when both could apply. Centres are what snap.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "The grid is measured in this phone's pixels. The file stores fractions rounded to " +
            "two decimals, so a snapped control can land a pixel off the line on another screen.",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(2.dp))
    Text("Preview", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to "landscape", false to "portrait").forEach { (wide, name) ->
            if (wide == previewLandscape) {
                Button(onClick = { onPreviewLandscape(wide) }) { Text(name) }
            } else {
                OutlinedButton(onClick = { onPreviewLandscape(wide) }) { Text(name) }
            }
        }
    }
    Text(
        text = "Canvas is %d × %d px — this phone's usable screen, bars and cutout already taken off."
            .format(device.widthPx.roundToInt(), device.heightPx.roundToInt()),
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * The four numbers, typed rather than dragged.
 *
 * Dragging is for arranging; typing is for the moment somebody already knows the number they want.
 * The units are stated here because they were reported as confusing, and they are not obvious:
 * an offset runs from the anchor to the control's **centre**, and every one of the four is a
 * fraction of the screen's **shorter side**.
 */
@Composable
private fun NumbersDialog(
    element: LayoutElement,
    onDismiss: () -> Unit,
    onApply: (LayoutElement) -> Unit,
) {
    var offsetX by remember { mutableStateOf("%.2f".format(element.placement.offsetX)) }
    var offsetY by remember { mutableStateOf("%.2f".format(element.placement.offsetY)) }
    var width by remember { mutableStateOf("%.2f".format(element.placement.width)) }
    var height by remember { mutableStateOf("%.2f".format(element.placement.height)) }
    var problem by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(element.id) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Offsets run from the ${element.placement.anchor.wireName} anchor to " +
                        "the control's centre, inwards. All four are fractions of the screen's " +
                        "shorter side.",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField("offsetX", offsetX) { offsetX = it }
                NumberField("offsetY", offsetY) { offsetY = it }
                NumberField("width", width) { width = it }
                NumberField("height", height) { height = it }
                if (problem.isNotBlank()) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE0603A),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val numbers = listOf(offsetX, offsetY, width, height).map { it.trim().toDoubleOrNull() }
                if (numbers.any { it == null }) {
                    problem = "Every field has to be a number."
                    return@Button
                }
                val candidate = Placement.of(
                    anchor = element.placement.anchor,
                    offsetX = round(numbers[0]!!),
                    offsetY = round(numbers[1]!!),
                    width = round(numbers[2]!!),
                    height = round(numbers[3]!!),
                    rotationDegrees = element.placement.rotationDegrees,
                )
                when (candidate) {
                    is Outcome.Failure -> problem = candidate.error.message
                    is Outcome.Success -> onApply(element.copy(placement = candidate.value))
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- editing a control ---------------------------------------------------------------------------

private fun LayoutElement.resizedBy(delta: Double): LayoutElement {
    val next = (placement.width + delta).coerceIn(Placement.MIN_SIZE, Placement.MAX_SIZE)
    val ratio = if (placement.width == 0.0) 1.0 else next / placement.width
    return copy(
        placement = placement.copy(
            width = round(next),
            height = round((placement.height * ratio).coerceIn(Placement.MIN_SIZE, Placement.MAX_SIZE)),
        )
    )
}

private fun LayoutElement.taller(delta: Double): LayoutElement = copy(
    placement = placement.copy(
        height = round((placement.height + delta).coerceIn(Placement.MIN_SIZE, Placement.MAX_SIZE)),
    )
)

private fun LayoutElement.withNextShape(): LayoutElement {
    val order = ControlShape.entries
    return copy(shape = order[(order.indexOf(shape) + 1) % order.size])
}

private fun LayoutElement.withNextAnchor(): LayoutElement {
    // Only the corners and edges a control is ever pinned to. The centre is excluded on purpose:
    // a control anchored to the middle of the screen is one no thumb can reach while holding a
    // phone, and offering it here would be offering a mistake.
    val order = listOf(
        Anchor.BOTTOM_LEFT, Anchor.BOTTOM_RIGHT, Anchor.TOP_LEFT, Anchor.TOP_RIGHT,
        Anchor.BOTTOM_CENTER, Anchor.TOP_CENTER, Anchor.CENTER_LEFT, Anchor.CENTER_RIGHT,
    )
    val next = order[(order.indexOf(placement.anchor).coerceAtLeast(0) + 1) % order.size]
    return copy(placement = placement.copy(anchor = next))
}

/**
 * Every window a control could be moved into: its own, one that already exists, or a new one.
 *
 * A new name is offered rather than typed. Group names follow the same rules as element ids, and a
 * keyboard is a way to break that rule on a screen where the only thing the name has to do is be
 * different from the others.
 */
private fun ControllerLayout.windowOptions(): List<String?> {
    val existing = elements.mapNotNull { it.group }.distinct().sorted()
    var n = 1
    while ("group-$n" in existing) n += 1
    return listOf(null) + existing + listOf("group-$n")
}

private fun LayoutElement.withGroupStep(options: List<String?>, step: Int): LayoutElement {
    if (options.isEmpty()) return this
    val at = options.indexOf(group).let { if (it < 0) 0 else it }
    val next = ((at + step) % options.size + options.size) % options.size
    return copy(group = options[next])
}

private fun ControllerLayout.replacing(element: LayoutElement): ControllerLayout =
    copy(elements = elements.map { if (it.id == element.id) element else it })

private fun ControllerLayout.clustersOn(surface: LayoutSurface): List<Cluster> =
    Clustering.group(
        this,
        elements.map { it.id to it.placement.resolve(surface).shapedAs(it.effectiveShape()) },
    )

/** Two decimals, the same as the file gets, so what is on screen is what will be written. */
private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

// --- the canvas ----------------------------------------------------------------------------------

/** Where the device rectangle sits inside the dock, and how far it is scaled down to get there. */
private data class Fit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
) {
    val surface: LayoutSurface get() = LayoutSurface(width.toDouble(), height.toDouble())
}

/** A line a control snapped to, drawn only while it is holding. */
private data class Guide(val vertical: Boolean, val at: Float)

/**
 * The layout, drawn inside a rectangle shaped like the phone.
 *
 * Deliberately not the overlay's own renderer. The overlay draws into windows it owns, positioned
 * by the window manager; this draws into a rectangle inside an ordinary screen. They agree on the
 * part that has to match — the same `Placement`, the same `resolve`, the same shape rules — and
 * differ on everything a preview does not need.
 */
@Composable
private fun EditorCanvas(
    modifier: Modifier,
    device: LayoutSurface,
    layout: ControllerLayout,
    mode: EditorMode,
    selectedId: String?,
    gridPx: Int,
    snapToGrid: Boolean,
    snapToEdges: Boolean,
    onSelect: (String?) -> Unit,
    onPlace: (LayoutElement) -> Unit,
) {
    // Read through these inside the gesture handlers rather than capturing them. A pointerInput
    // keyed on anything that changes during a drag restarts mid-gesture, which cancels the drag —
    // so the keys stay still and the values are looked up fresh.
    val liveLayout by rememberUpdatedState(layout)
    val liveMode by rememberUpdatedState(mode)
    val liveGrid by rememberUpdatedState(gridPx)
    val liveSnapGrid by rememberUpdatedState(snapToGrid)
    val liveSnapEdges by rememberUpdatedState(snapToEdges)
    val liveSelect by rememberUpdatedState(onSelect)
    val livePlace by rememberUpdatedState(onPlace)

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var guides by remember { mutableStateOf<List<Guide>>(emptyList()) }

    fun fit(): Fit {
        val bounds = canvasSize
        if (bounds.width <= 0f || bounds.height <= 0f || device.widthPx <= 0 || device.heightPx <= 0) {
            return Fit(0f, 0f, 0f, 0f, 1f)
        }
        val room = 0.94f
        val scale = min(
            bounds.width * room / device.widthPx.toFloat(),
            bounds.height * room / device.heightPx.toFloat(),
        )
        val width = device.widthPx.toFloat() * scale
        val height = device.heightPx.toFloat() * scale
        return Fit((bounds.width - width) / 2, (bounds.height - height) / 2, width, height, scale)
    }

    fun rectOf(fit: Fit, element: LayoutElement): PixelRect =
        element.placement.resolve(fit.surface).shapedAs(element.effectiveShape())

    fun hit(fit: Fit, at: Offset): String? {
        val x = (at.x - fit.left).toDouble()
        val y = (at.y - fit.top).toDouble()
        // Last first, so the control drawn on top is the one selected.
        return liveLayout.elements.reversed().firstOrNull { element ->
            val rect = rectOf(fit, element)
            when (element.effectiveShape()) {
                ControlShape.CIRCLE ->
                    hypot(x - rect.centerX, y - rect.centerY) <= min(rect.width, rect.height) / 2
                else -> abs(x - rect.centerX) <= rect.width / 2 &&
                    abs(y - rect.centerY) <= rect.height / 2
            }
        }?.id
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(layout.header.id, device) {
                detectTapGestures { at -> liveSelect(hit(fit(), at)) }
            }
            .pointerInput(layout.header.id, device) {
                detectDragGestures(
                    onDragStart = { at ->
                        val fitted = fit()
                        val id = hit(fitted, at)
                        dragging = id
                        liveSelect(id)
                        val element = id?.let { liveLayout.element(it) }
                        grab = if (element == null) {
                            Offset.Zero
                        } else {
                            val rect = rectOf(fitted, element)
                            Offset(
                                at.x - fitted.left - rect.centerX.toFloat(),
                                at.y - fitted.top - rect.centerY.toFloat(),
                            )
                        }
                    },
                    onDragEnd = {
                        dragging = null
                        guides = emptyList()
                    },
                    onDragCancel = {
                        dragging = null
                        guides = emptyList()
                    },
                ) { change, _ ->
                    change.consume()
                    if (liveMode != EditorMode.CONTROLS) return@detectDragGestures
                    val id = dragging ?: return@detectDragGestures
                    val element = liveLayout.element(id) ?: return@detectDragGestures
                    val fitted = fit()
                    if (fitted.width <= 0f) return@detectDragGestures

                    val wanted = Offset(
                        change.position.x - fitted.left - grab.x,
                        change.position.y - fitted.top - grab.y,
                    )
                    val snapped = snap(
                        layout = liveLayout,
                        fit = fitted,
                        element = element,
                        wanted = wanted,
                        gridPx = liveGrid,
                        toGrid = liveSnapGrid,
                        toEdges = liveSnapEdges,
                    )
                    guides = snapped.guides
                    livePlace(
                        element.copy(
                            placement = element.placement
                                .centeredAt(fitted.surface, snapped.x, snapped.y)
                                .rounded()
                        )
                    )
                }
            },
    ) {
        val fitted = fit()
        if (fitted.width <= 0f) return@Canvas
        drawScreen(fitted)
        drawGrid(fitted, gridPx)

        val placed = layout.elements.map { it.id to rectOf(fitted, it) }
        if (mode == EditorMode.WINDOWS) {
            drawWindows(fitted, Clustering.group(layout, placed), selectedId)
        }
        layout.elements.forEachIndexed { index, element ->
            drawControl(fitted, element, placed[index].second, element.id == selectedId)
        }
        guides.forEach { guide -> drawGuide(fitted, guide) }
    }
}

/** The rounding the file gets, applied to a placement rather than to one number at a time. */
private fun Placement.rounded(): Placement = copy(
    offsetX = round(offsetX),
    offsetY = round(offsetY),
    width = round(width),
    height = round(height),
)

// --- snapping ------------------------------------------------------------------------------------

private data class Snapped(val x: Double, val y: Double, val guides: List<Guide>)

/**
 * Where a dragged control actually lands.
 *
 * Two aids, and a rule for when they disagree: **edge snapping wins over the grid**. Lining a
 * control up with the one next to it is a statement about this layout; landing on a grid line is a
 * statement about the screen, and the first is what somebody dragging a control is usually after.
 * Applied per axis, so a control can line up with a neighbour horizontally and sit on the grid
 * vertically.
 *
 * Snapping is done in the canvas's own pixels, which are the phone's pixels scaled by the same
 * factor the whole drawing is — so a 64px grid is 64 phone pixels, not 64 of the editor's.
 */
private fun snap(
    layout: ControllerLayout,
    fit: Fit,
    element: LayoutElement,
    wanted: Offset,
    gridPx: Int,
    toGrid: Boolean,
    toEdges: Boolean,
): Snapped {
    val rect = element.placement.resolve(fit.surface).shapedAs(element.effectiveShape())
    val threshold = max(6.0, min(fit.width, fit.height) * 0.02)
    val step = gridPx * fit.scale.toDouble()

    val others = layout.elements
        .filter { it.id != element.id }
        .map { it.placement.resolve(fit.surface).shapedAs(it.effectiveShape()) }

    val verticalLines = buildList {
        add(0.0)
        add(fit.width.toDouble() / 2)
        add(fit.width.toDouble())
        others.forEach {
            add(it.left)
            add(it.centerX)
            add(it.right)
        }
    }
    val horizontalLines = buildList {
        add(0.0)
        add(fit.height.toDouble() / 2)
        add(fit.height.toDouble())
        others.forEach {
            add(it.top)
            add(it.centerY)
            add(it.bottom)
        }
    }

    val guides = mutableListOf<Guide>()
    val x = snapAxis(
        wanted.x.toDouble(), rect.width / 2, verticalLines, threshold, toEdges,
        if (toGrid) step else 0.0,
    ) { at -> guides += Guide(vertical = true, at = at.toFloat()) }
    val y = snapAxis(
        wanted.y.toDouble(), rect.height / 2, horizontalLines, threshold, toEdges,
        if (toGrid) step else 0.0,
    ) { at -> guides += Guide(vertical = false, at = at.toFloat()) }

    return Snapped(x, y, guides)
}

private inline fun snapAxis(
    wanted: Double,
    half: Double,
    lines: List<Double>,
    threshold: Double,
    toEdges: Boolean,
    gridStep: Double,
    onGuide: (Double) -> Unit,
): Double {
    if (toEdges) {
        // Any of the control's own three lines may be what lines up: its two edges or its middle.
        var bestShift = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestLine = 0.0
        listOf(-half, 0.0, half).forEach { own ->
            lines.forEach { line ->
                val shift = line - (wanted + own)
                val distance = abs(shift)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestShift = shift
                    bestLine = line
                }
            }
        }
        if (bestDistance <= threshold) {
            onGuide(bestLine)
            return wanted + bestShift
        }
    }
    if (gridStep > 0.0) {
        return Math.round(wanted / gridStep) * gridStep
    }
    return wanted
}

// --- drawing -------------------------------------------------------------------------------------

private fun DrawScope.drawScreen(fit: Fit) {
    drawRect(
        color = Color(0xFF0B0D11),
        topLeft = Offset(fit.left, fit.top),
        size = Size(fit.width, fit.height),
    )
    drawRect(
        color = Color(0xFF7C8798),
        topLeft = Offset(fit.left, fit.top),
        size = Size(fit.width, fit.height),
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawGrid(fit: Fit, gridPx: Int) {
    val step = gridPx * fit.scale
    if (step < 6f) return
    val colour = Color(0xFF2A3038)
    var x = step
    while (x < fit.width) {
        drawLine(colour, Offset(fit.left + x, fit.top), Offset(fit.left + x, fit.top + fit.height), 1f)
        x += step
    }
    var y = step
    while (y < fit.height) {
        drawLine(colour, Offset(fit.left, fit.top + y), Offset(fit.left + fit.width, fit.top + y), 1f)
        y += step
    }
}

private fun DrawScope.drawGuide(fit: Fit, guide: Guide) {
    val colour = Color(0xFFF2B441)
    if (guide.vertical) {
        val x = fit.left + guide.at
        drawLine(colour, Offset(x, fit.top), Offset(x, fit.top + fit.height), 2f)
    } else {
        val y = fit.top + guide.at
        drawLine(colour, Offset(fit.left, y), Offset(fit.left + fit.width, y), 2f)
    }
}

private fun DrawScope.drawWindows(fit: Fit, clusters: List<Cluster>, selectedId: String?) {
    clusters.forEach { cluster ->
        val mine = selectedId != null && selectedId in cluster.elementIds
        val bounds = cluster.bounds
        val crowded = bounds.width * bounds.height >
            fit.width.toDouble() * fit.height.toDouble() * CROWDED
        val colour = when {
            mine -> Color(0xFF60BAFF)
            crowded -> Color(0xFFE0603A)
            else -> Color(0xFF8A93A0)
        }
        drawRect(
            color = colour.copy(alpha = if (mine) 0.16f else 0.08f),
            topLeft = Offset(fit.left + bounds.left.toFloat(), fit.top + bounds.top.toFloat()),
            size = Size(bounds.width.toFloat(), bounds.height.toFloat()),
        )
        drawRect(
            color = colour,
            topLeft = Offset(fit.left + bounds.left.toFloat(), fit.top + bounds.top.toFloat()),
            size = Size(bounds.width.toFloat(), bounds.height.toFloat()),
            style = Stroke(width = if (mine) 4f else 2f),
        )
    }
}

private fun DrawScope.drawControl(
    fit: Fit,
    element: LayoutElement,
    rect: PixelRect,
    selected: Boolean,
) {
    val fill = Color(0xFF5C626C).copy(alpha = 0.80f)
    val edge = if (selected) Color(0xFF60BAFF) else Color(0xFF0C0E12).copy(alpha = 0.60f)
    val stroke = if (selected) 6f else 3f
    val centre = Offset(fit.left + rect.centerX.toFloat(), fit.top + rect.centerY.toFloat())

    when (element.effectiveShape()) {
        ControlShape.CIRCLE -> {
            val radius = (min(rect.width, rect.height) / 2).toFloat()
            drawCircle(fill, radius, centre)
            drawCircle(edge, radius, centre, style = Stroke(width = stroke))
        }

        else -> {
            val size = Size(rect.width.toFloat(), rect.height.toFloat())
            val corner = CornerRadius(min(size.width, size.height) * 0.18f)
            val topLeft = Offset(centre.x - size.width / 2, centre.y - size.height / 2)
            drawRoundRect(fill, topLeft, size, corner)
            drawRoundRect(edge, topLeft, size, corner, style = Stroke(width = stroke))
        }
    }

    // A cross for the pad, so its shape is recognisable at a glance rather than another circle.
    if (element.kind == ControlKind.DPAD) {
        val arm = (min(rect.width, rect.height) / 2 * 0.9).toFloat()
        val half = arm * 0.33f
        drawRoundRect(
            color = Color(0xFF9AA1AC),
            topLeft = Offset(centre.x - half, centre.y - arm),
            size = Size(half * 2, arm * 2),
            cornerRadius = CornerRadius(half * 0.4f),
        )
        drawRoundRect(
            color = Color(0xFF9AA1AC),
            topLeft = Offset(centre.x - arm, centre.y - half),
            size = Size(arm * 2, half * 2),
            cornerRadius = CornerRadius(half * 0.4f),
        )
    }
}

/**
 * Opens a layout for editing, duplicating it first when it is one Kestrel ships.
 *
 * The duplication is done rather than demanded. A user who presses Edit on the built-in wants to
 * change how their pad looks, not to learn why they cannot — and the rule that a built-in is
 * immutable is kept exactly as strictly either way.
 */
public fun openForEditing(
    layout: ControllerLayout,
    duplicate: (ControllerLayout) -> Outcome<ControllerLayout>,
): Outcome<ControllerLayout> =
    if (layout.header.id.isBuiltIn) duplicate(layout) else Outcome.Success(layout)
