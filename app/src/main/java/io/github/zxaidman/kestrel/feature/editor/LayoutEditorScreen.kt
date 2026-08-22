package io.github.zxaidman.kestrel.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.text.style.TextAlign
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
import io.github.zxaidman.kestrel.core.layout.isWithin
import io.github.zxaidman.kestrel.core.layout.resolve
import io.github.zxaidman.kestrel.core.layout.shapedAs
import io.github.zxaidman.kestrel.platform.display.DeviceSurface
import io.github.zxaidman.kestrel.platform.settings.AppSettings
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
    onPreviewOrientation: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // The whole display, or what is left after the system's share, depending on the setting — and
    // whichever it is, it is the same answer the pad itself asks for. The bars are still measured
    // so the band can be drawn: a control under the status bar is allowed and worth seeing.
    val wholeScreen = AppSettings.current.value.display.drawUnderCutout
    val screen = remember(configuration) { DeviceSurface.screen(context) }
    val device = remember(configuration, wholeScreen) {
        DeviceSurface.forPad(context, wholeScreen)
    }
    val bars = remember(screen, device, wholeScreen) {
        if (!wholeScreen) {
            null
        } else {
            Rect(
                left = screen.insetLeft.toFloat(),
                top = screen.insetTop.toFloat(),
                right = (screen.widthPx - screen.insetRight).toFloat(),
                bottom = (screen.heightPx - screen.insetBottom).toFloat(),
            )
        }
    }

    var working by remember(layout.header.id) { mutableStateOf(layout) }
    var selectedId by remember(layout.header.id) { mutableStateOf<String?>(null) }
    var dirty by remember(layout.header.id) { mutableStateOf(false) }
    var mode by remember { mutableStateOf(EditorMode.CONTROLS) }
    var message by remember { mutableStateOf("") }
    var gridUnit by remember { mutableStateOf(DEFAULT_GRID) }
    var snapToGrid by remember { mutableStateOf(false) }
    var snapToEdges by remember { mutableStateOf(false) }
    var typingNumbers by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val selected = working.element(selectedId ?: "")
    val landscape = device.widthPx >= device.heightPx

    Box(modifier = Modifier.fillMaxSize()) {
        EditorCanvas(
            modifier = Modifier.fillMaxSize(),
            device = device,
            bars = bars,
            layout = working,
            mode = mode,
            selectedId = selectedId,
            gridUnit = gridUnit,
            snapToGrid = snapToGrid,
            snapToEdges = snapToEdges,
            onSelect = { selectedId = it },
            onPlace = { updated ->
                working = working.replacing(updated)
                dirty = true
            },
        )

        // The middle of the screen, which is the one place a pad never is: controls belong to the
        // corners and edges a thumb reaches, and the centre is what a game is played through.
        // Anywhere else and these would sit on top of the thing being arranged.
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { toolsOpen = true }) { Text("Tools") }
                FloatingActionButton(
                    containerColor = if (dirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    onClick = {
                        message = if (dirty) {
                            dirty = false
                            onSave(working)
                        } else {
                            "Nothing has changed."
                        }
                    },
                ) { Text("Save") }
                FloatingActionButton(
                    onClick = { if (dirty) leaving = true else onClose() },
                ) { Text("Exit") }
            }

            Caption(
                text = working.header.name + if (dirty) "  •  unsaved" else "",
            )
            selected?.let { Caption(text = it.summary(device)) }

            val strays = working.elements.count { element ->
                !element.placement.resolve(device)
                    .shapedAs(element.effectiveShape())
                    .isWithin(device)
            }
            if (strays > 0) {
                Caption(
                    text = "$strays outside the screen entirely — the pad will not match this.",
                    colour = Color(0xFFE0603A),
                )
            }
            if (message.isNotBlank()) Caption(text = message)
        }

        if (toolsOpen) {
            ToolsSheet(
                landscape = landscape,
                onDismiss = { toolsOpen = false },
            ) {
                ModeSwitch(mode = mode, onMode = { mode = it })

                SelectionHeader(element = selected, device = device)

                ControlTools(
                    element = selected,
                    enabled = mode == EditorMode.CONTROLS,
                    onChange = { updated ->
                        working = working.replacing(updated)
                        dirty = true
                    },
                    onType = { typingNumbers = true },
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
                    gridUnit = gridUnit,
                    onGrid = { gridUnit = it },
                    snapToGrid = snapToGrid,
                    onSnapToGrid = { snapToGrid = it },
                    snapToEdges = snapToEdges,
                    onSnapToEdges = { snapToEdges = it },
                    landscape = landscape,
                    onPreviewOrientation = onPreviewOrientation,
                    device = device,
                    wholeScreen = wholeScreen,
                )
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

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave without saving?") },
            text = { Text("The arrangement on screen has not been written to the file.") },
            confirmButton = {
                Button(onClick = {
                    leaving = false
                    onClose()
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { leaving = false }) { Text("Stay") }
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

/**
 * Grid steps, in the unit the document itself uses: a fraction of the screen's shorter side.
 *
 * It was pixels, and the project owner named the fault: a control is `0.12` and a grid line was
 * `32px`, so comparing them meant doing arithmetic while arranging a pad. Moving the grid rather
 * than the control also buys something the pixel grid could not promise — `0.01` is exactly the
 * precision the file is rounded to, so a snapped control lands on a number the file can hold.
 *
 * The extremes are gone after a round of use: `0.01` was too fine to see and `0.25` too coarse to
 * place anything with. What is left spans a fifth of a button to most of one.
 */
private val GRID_SIZES = listOf(0.02, 0.04, 0.06, 0.10)
private const val DEFAULT_GRID = 0.04

/** A step small enough to place a control with and large enough to feel like a press. */
private const val STEP = 0.02

// --- the furniture that floats on the canvas -----------------------------------------------------

/** A line of text that has to stay readable over whatever the canvas is drawing behind it. */
@Composable
private fun Caption(text: String, colour: Color = Color(0xFFE8EBEF)) {
    Surface(
        color = Color(0xFF0B0D11).copy(alpha = 0.82f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colour,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Everything that used to be a side panel, opened over the canvas and closed again.
 *
 * The canvas wants the whole screen because it is a picture of the whole screen; a panel that
 * permanently takes a quarter of it is a quarter of the picture missing. So the tools come when
 * they are asked for. Down one edge in landscape and up from the bottom in portrait, which is
 * where a hand already is in each.
 */
@Composable
private fun ToolsSheet(
    landscape: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (landscape) {
                        Modifier.fillMaxHeight().fillMaxWidth(0.62f)
                    } else {
                        Modifier.fillMaxWidth().fillMaxHeight(0.72f)
                    }
                )
                // The sheet swallows its own touches; only the dimmed area outside it dismisses.
                .clickable(enabled = false, onClick = {}),
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("Done") }
                }
                content()
            }
        }
    }
}

// --- the tools -----------------------------------------------------------------------------------

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

/** The selected control in both units at once, which is the only way the two read on one scale. */
private fun LayoutElement.summary(device: LayoutSurface): String {
    val unit = device.shortSide
    return "$id  x %.2f  y %.2f  w %.2f  h %.2f   (%d × %d px)".format(
        placement.offsetX, placement.offsetY, placement.width, placement.height,
        (placement.width * unit).roundToInt(), (placement.height * unit).roundToInt(),
    )
}

@Composable
private fun SelectionHeader(element: LayoutElement?, device: LayoutSurface) {
    Text(
        text = element?.let { "${it.kind.wireName}\n${it.summary(device)}" }
            ?: "Nothing selected — touch a control.",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlTools(
    element: LayoutElement?,
    enabled: Boolean,
    onChange: (LayoutElement) -> Unit,
    onType: () -> Unit,
) {
    val live = enabled && element != null
    Text("Control", style = MaterialTheme.typography.labelLarge)
    // Wrapping, not a row. A row that does not wrap loses its last button off the edge, which is
    // how `⋮ values` came to exist in portrait and not in landscape.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
        Button(enabled = live, onClick = { element?.let { onChange(it.withNextShape()) } }) {
            Text(element?.shape?.wireName ?: "shape")
        }
        Button(enabled = live, onClick = { element?.let { onChange(it.withNextAnchor()) } }) {
            Text(element?.placement?.anchor?.wireName ?: "anchor")
        }
        Button(enabled = live, onClick = onType) { Text("⋮ values") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            enabled = live,
            onClick = { element?.let { onChange(it.withGroupStep(options, -1)) } },
        ) { Text("◀") }
        Button(
            enabled = live,
            onClick = { element?.let { onChange(it.withGroupStep(options, 1)) } },
        ) { Text("▶") }
        Button(enabled = live, onClick = { element?.let { onChange(it.copy(group = null)) } }) {
            Text("own window")
        }
    }
    Text(
        text = "in: " + (element?.group ?: "own window"),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )

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

/**
 * A grid step said in both units at once, which is the point of the change.
 *
 * `0.04 · 37 px` — the number the file holds, and what it is on this phone. Neither alone was
 * enough: the fraction is what gets written and the pixels are what the eye is measuring.
 */
private fun gridLabel(unit: Double, device: LayoutSurface): String =
    "%.2f · %d px".format(unit, (unit * device.shortSide).roundToInt())

/** Where a window stops being a pad and starts being a lid. Matches the shipped layout's tests. */
private const val CROWDED = 0.25

@Composable
private fun GridTools(
    gridUnit: Double,
    onGrid: (Double) -> Unit,
    snapToGrid: Boolean,
    onSnapToGrid: (Boolean) -> Unit,
    snapToEdges: Boolean,
    onSnapToEdges: (Boolean) -> Unit,
    landscape: Boolean,
    onPreviewOrientation: (Boolean) -> Unit,
    device: LayoutSurface,
    wholeScreen: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Spacer(modifier = Modifier.height(2.dp))
    Text("Grid and snapping", style = MaterialTheme.typography.labelLarge)

    Box {
        OutlinedButton(onClick = { open = true }) { Text("Grid  " + gridLabel(gridUnit, device)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GRID_SIZES.forEach { size ->
                DropdownMenuItem(
                    text = { Text(gridLabel(size, device)) },
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
            "screen, and wins over the grid when both could apply. While a control is being " +
            "dragged, a yellow line shows what it has caught — that is the guide.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = "The grid is measured in the same unit as the controls — a fraction of the screen's " +
            "shorter side — so a 0.12 button against a 0.04 grid means what it looks like. A step " +
            "of 0.02 is exactly what the file stores.",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(2.dp))
    Text("Orientation", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to "landscape", false to "portrait").forEach { (wide, name) ->
            if (wide == landscape) {
                Button(onClick = { onPreviewOrientation(wide) }) { Text(name) }
            } else {
                OutlinedButton(onClick = { onPreviewOrientation(wide) }) { Text(name) }
            }
        }
    }
    Text(
        text = "This turns the phone rather than drawing a small picture of it turned. A portrait " +
            "pad drawn inside a landscape editor is a strip too narrow to work in, and its bars " +
            "were guessed at — only the orientation the phone is in can be measured. Leaving the " +
            "editor puts the orientation back to whatever the display settings say.",
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = if (wholeScreen) {
            ("Canvas is %d × %d px — the whole screen, which is what the pad uses. The shaded band " +
                "is where the system bars and the cutout are: a control there is allowed and will " +
                "share that space with the system.")
                .format(device.widthPx.roundToInt(), device.heightPx.roundToInt())
        } else {
            ("Canvas is %d × %d px — the screen less the system bars and the cutout, because " +
                "\"use the notch area\" is off in settings.")
                .format(device.widthPx.roundToInt(), device.heightPx.roundToInt())
        },
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
            // Two to a row and the body scrolls. Four fields stacked in a dialog on a landscape
            // phone put width and height below the fold with no way to reach them, which is a
            // feature that works and cannot be used.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Offsets run from the ${element.placement.anchor.wireName} anchor to " +
                        "the control's centre, inwards. All four are fractions of the screen's " +
                        "shorter side.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("offsetX", offsetX, Modifier.weight(1f)) { offsetX = it }
                    NumberField("offsetY", offsetY, Modifier.weight(1f)) { offsetY = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("width", width, Modifier.weight(1f)) { width = it }
                    NumberField("height", height, Modifier.weight(1f)) { height = it }
                }
                // A numeric keyboard does not always offer a minus sign, and an offset is allowed
                // to be negative. Reported on the reference device as "the keyboard only shows
                // numbers", with pasting as the only way round it.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { offsetX = negated(offsetX) }) { Text("± offsetX") }
                    OutlinedButton(onClick = { offsetY = negated(offsetY) }) { Text("± offsetY") }
                }
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
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Flips the sign of what is typed, including the half-typed and the nonsense. */
private fun negated(text: String): String {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("-") -> trimmed.removePrefix("-")
        trimmed.isEmpty() -> "-"
        else -> "-$trimmed"
    }
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

/**
 * Where the device rectangle sits inside the dock, and how far it is scaled down to get there.
 *
 * [surface] is the **whole screen** at canvas scale, insets included, so a control resolves into the
 * usable part of it exactly as it does on the phone. [usable] is that inner rectangle, in canvas
 * coordinates, which is what the grid is drawn over and what a control is checked against.
 */
private data class Fit(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
    val surface: LayoutSurface,
    /** Where the system bars and the cutout are, in canvas coordinates, or null when there is no
     *  band to draw. Drawn and never subtracted: a control there is allowed, and shares that space
     *  with the system rather than being pushed out of it. */
    val bars: Rect?,
)

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
    bars: Rect?,
    layout: ControllerLayout,
    mode: EditorMode,
    selectedId: String?,
    gridUnit: Double,
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
    val liveGrid by rememberUpdatedState(gridUnit)
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
        val empty = LayoutSurface(0.0, 0.0)
        if (bounds.width <= 0f || bounds.height <= 0f || device.widthPx <= 0 || device.heightPx <= 0) {
            return Fit(0f, 0f, 0f, 0f, 1f, empty, null)
        }
        val room = 0.96f
        val scale = min(
            bounds.width * room / device.widthPx.toFloat(),
            bounds.height * room / device.heightPx.toFloat(),
        )
        val width = device.widthPx.toFloat() * scale
        val height = device.heightPx.toFloat() * scale
        return Fit(
            left = (bounds.width - width) / 2,
            top = (bounds.height - height) / 2,
            width = width,
            height = height,
            scale = scale,
            surface = LayoutSurface(width.toDouble(), height.toDouble()),
            bars = bars?.let {
                Rect(it.left * scale, it.top * scale, it.right * scale, it.bottom * scale)
            },
        )
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
            .pointerInput(layout.header.id, device, bars) {
                detectTapGestures { at -> liveSelect(hit(fit(), at)) }
            }
            .pointerInput(layout.header.id, device, bars) {
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
                        gridUnit = liveGrid,
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
        drawGrid(fitted, gridUnit)

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
    gridUnit: Double,
    toGrid: Boolean,
    toEdges: Boolean,
): Snapped {
    val rect = element.placement.resolve(fit.surface).shapedAs(element.effectiveShape())
    val threshold = max(6.0, min(fit.width, fit.height) * 0.02)
    val step = gridUnit * fit.surface.shortSide

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

/**
 * The phone: the whole screen, with what the system takes drawn as a band rather than cut off.
 *
 * Subtracting the bands instead was the previous version's fault. It gave a rectangle 2289 × 927 on
 * a 2400 × 1080 phone — a different shape, presented as the phone — and a control placed against the
 * top of the usable area appeared to hang over the edge of the world when in fact it was sitting
 * against the status bar.
 */
private fun DrawScope.drawScreen(fit: Fit) {
    drawRect(
        color = Color(0xFF0B0D11),
        topLeft = Offset(fit.left, fit.top),
        size = Size(fit.width, fit.height),
    )
    // The band is drawn and not subtracted. The pad uses the whole display, so a control here is
    // where the layout says it is — it simply shares that strip with the status bar or the gesture
    // bar, and somebody arranging a pad should be able to see that while deciding.
    fit.bars?.let { inner ->
        val band = Color(0xFF1B2028)
        drawRect(band, Offset(fit.left, fit.top), Size(fit.width, inner.top))
        drawRect(
            band,
            Offset(fit.left, fit.top + inner.bottom),
            Size(fit.width, fit.height - inner.bottom),
        )
        drawRect(band, Offset(fit.left, fit.top + inner.top), Size(inner.left, inner.height))
        drawRect(
            band,
            Offset(fit.left + inner.right, fit.top + inner.top),
            Size(fit.width - inner.right, inner.height),
        )
        drawRect(
            color = Color(0xFF3C444F),
            topLeft = Offset(fit.left + inner.left, fit.top + inner.top),
            size = Size(inner.width, inner.height),
            style = Stroke(width = 1.5f),
        )
    }
    drawRect(
        color = Color(0xFF7C8798),
        topLeft = Offset(fit.left, fit.top),
        size = Size(fit.width, fit.height),
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawGrid(fit: Fit, gridUnit: Double) {
    val step = (gridUnit * fit.surface.shortSide).toFloat()
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
    // A control that has left the screen is marked rather than moved. It is a real design to
    // run a shoulder button off an edge, and `ADR-007`'s spirit applies: say what is true, do not
    // overrule the person. What is *not* acceptable is letting it look fine here and then arrive
    // somewhere else on the phone, which is what the window manager will do with it.
    // Measured against the surface the pad itself uses, so what is flagged is genuinely off the
    // display rather than merely under a status bar.
    val outside = !rect.isWithin(fit.surface)
    val fill = Color(0xFF5C626C).copy(alpha = 0.80f)
    val edge = when {
        selected -> Color(0xFF60BAFF)
        outside -> Color(0xFFE0603A)
        else -> Color(0xFF0C0E12).copy(alpha = 0.60f)
    }
    val stroke = if (selected || outside) 6f else 3f
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
