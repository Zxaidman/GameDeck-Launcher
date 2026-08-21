package io.github.zxaidman.kestrel.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.zxaidman.kestrel.core.common.Outcome
import io.github.zxaidman.kestrel.core.layout.Anchor
import io.github.zxaidman.kestrel.core.layout.ControlKind
import io.github.zxaidman.kestrel.core.layout.ControlShape
import io.github.zxaidman.kestrel.core.layout.ControllerLayout
import io.github.zxaidman.kestrel.core.layout.LayoutElement
import io.github.zxaidman.kestrel.core.layout.LayoutSurface
import io.github.zxaidman.kestrel.core.layout.Placement
import io.github.zxaidman.kestrel.core.layout.PixelRect
import io.github.zxaidman.kestrel.core.layout.resolve
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Editing a layout by moving it, rather than by typing numbers into a file.
 *
 * The file stays the truth — this writes the same document a text editor would, and everything it
 * can do can still be done by hand. What it adds is the one thing a text editor cannot: **seeing
 * where a control is while deciding where it should be.** Nobody can picture `offsetX: 0.22` on a
 * phone they are holding, which is exactly the confusion the project owner reported.
 *
 * Two rules it keeps, because they are the schema's rules rather than this screen's:
 *
 * **A built-in is never edited.** Opening the editor on one duplicates it first, and the copy is
 * what is edited from then on. That is `docs/CONFIGURATION_SCHEMA.md`'s built-in → duplicate → user
 * copy, and it is enforced here by doing it rather than by refusing.
 *
 * **Nothing is saved until it is saved.** Dragging changes what is on screen; the file changes when
 * the button is pressed. An editor that wrote every frame of a drag would be an editor with no way
 * to change your mind.
 */
@Composable
public fun LayoutEditorScreen(
    layout: ControllerLayout,
    onSave: (ControllerLayout) -> String,
    onClose: () -> Unit,
) {
    var working by remember(layout.header.id) { mutableStateOf(layout) }
    var selectedId by remember(layout.header.id) { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var dirty by remember(layout.header.id) { mutableStateOf(false) }

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

        // The preview takes everything left, and is drawn at the shape of this screen — so what is
        // arranged here is what the overlay will put on the same screen.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LayoutCanvas(
                layout = working,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                onMove = { id, dx, dy ->
                    working = working.moving(id, dx, dy)
                    dirty = true
                },
            )
        }

        if (selected == null) {
            Text(
                text = "Touch a control to select it, then drag it. Nothing is written until Save.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            SelectedControlBar(
                element = selected,
                onChange = { updated ->
                    working = working.replacing(updated)
                    dirty = true
                },
            )
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** What the selected control is, and the four things that can be changed about it. */
@Composable
private fun SelectedControlBar(element: LayoutElement, onChange: (LayoutElement) -> Unit) {
    val placement = element.placement
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = "${element.id}   ${element.kind.wireName}   " +
                "x %.2f  y %.2f  w %.2f  h %.2f".format(
                    placement.offsetX, placement.offsetY, placement.width, placement.height,
                ),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onChange(element.resizedBy(-STEP)) }) { Text("−") }
            Button(onClick = { onChange(element.resizedBy(STEP)) }) { Text("+") }
            Button(onClick = { onChange(element.withNextShape()) }) { Text(element.shape.wireName) }
            Button(onClick = { onChange(element.withNextAnchor()) }) {
                Text(placement.anchor.wireName)
            }
            Button(onClick = { onChange(element.taller(STEP)) }) { Text("taller") }
            Button(onClick = { onChange(element.taller(-STEP)) }) { Text("shorter") }
        }
    }
}

/** A step small enough to place a control with and large enough to feel like a press. */
private const val STEP = 0.02

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

private fun ControllerLayout.replacing(element: LayoutElement): ControllerLayout =
    copy(elements = elements.map { if (it.id == element.id) element else it })

/**
 * Moves a control by a distance on screen, in the direction the drag went.
 *
 * An offset is measured **inwards from its anchor**, so a control pinned bottom-right moves left
 * and up as its offsets grow. Dragging must not make the author think about that: the sign is
 * flipped here so a control follows the finger.
 */
private fun ControllerLayout.moving(id: String, dx: Double, dy: Double): ControllerLayout {
    val element = element(id) ?: return this
    val anchor = element.placement.anchor
    val inwardX = if (anchor.originX == 1.0) -1.0 else 1.0
    val inwardY = if (anchor.originY == 1.0) -1.0 else 1.0
    return replacing(
        element.copy(
            placement = element.placement.copy(
                offsetX = round(
                    (element.placement.offsetX + dx * inwardX)
                        .coerceIn(-Placement.MAX_OFFSET, Placement.MAX_OFFSET)
                ),
                offsetY = round(
                    (element.placement.offsetY + dy * inwardY)
                        .coerceIn(-Placement.MAX_OFFSET, Placement.MAX_OFFSET)
                ),
            )
        )
    )
}

/** Two decimals, the same as the file gets, so what is on screen is what will be written. */
private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

/**
 * The layout, drawn at the size of this screen, with one control selected.
 *
 * Deliberately not the overlay's own renderer. The overlay draws into windows it owns, positioned
 * by the window manager; this draws into a rectangle inside an ordinary screen. They agree on
 * geometry — the same `Placement`, the same `resolve` — which is the part that has to match, and
 * they differ on everything a preview does not need.
 */
@Composable
private fun LayoutCanvas(
    layout: ControllerLayout,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Double, Double) -> Unit,
) {
    var size by remember { mutableStateOf(Size.Zero) }

    fun surfaceOf(bounds: Size) = LayoutSurface(bounds.width.toDouble(), bounds.height.toDouble())

    fun hit(bounds: Size, at: Offset): String? {
        val surface = surfaceOf(bounds)
        // Last first, so the control drawn on top is the one selected.
        return layout.elements.reversed().firstOrNull { element ->
            val rect = element.placement.resolve(surface)
            when (element.shape) {
                ControlShape.CIRCLE ->
                    hypot(at.x - rect.centerX, at.y - rect.centerY) <=
                        min(rect.width, rect.height) / 2
                else -> abs(at.x - rect.centerX) <= rect.width / 2 &&
                    abs(at.y - rect.centerY) <= rect.height / 2
            }
        }?.id
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(layout.header.id) {
                detectTapGestures { at -> onSelect(hit(size, at)) }
            }
            .pointerInput(layout.header.id, selectedId) {
                detectDragGestures(
                    onDragStart = { at -> hit(size, at)?.let(onSelect) },
                ) { change, dragged ->
                    change.consume()
                    val id = selectedId ?: return@detectDragGestures
                    val unit = min(size.width, size.height).toDouble()
                    if (unit > 0) onMove(id, dragged.x / unit, dragged.y / unit)
                }
            },
    ) {
        size = this.size
        val surface = surfaceOf(this.size)
        layout.elements.forEach { element ->
            drawControl(element, element.placement.resolve(surface), element.id == selectedId)
        }
    }
}

private fun DrawScope.drawControl(element: LayoutElement, rect: PixelRect, selected: Boolean) {
    val fill = Color(0xFF5C626C).copy(alpha = 0.80f)
    val edge = if (selected) Color(0xFF60BAFF) else Color(0xFF0C0E12).copy(alpha = 0.60f)
    val stroke = if (selected) 6f else 3f
    val centre = Offset(rect.centerX.toFloat(), rect.centerY.toFloat())

    when (element.shape) {
        ControlShape.CIRCLE -> {
            val radius = (min(rect.width, rect.height) / 2).toFloat()
            drawCircle(fill, radius, centre)
            drawCircle(edge, radius, centre, style = Stroke(width = stroke))
        }

        else -> {
            val size = Size(rect.width.toFloat(), rect.height.toFloat())
            val corner = androidx.compose.ui.geometry.CornerRadius(min(size.width, size.height) * 0.18f)
            val topLeft = Offset(rect.left.toFloat(), rect.top.toFloat())
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
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.4f),
        )
        drawRoundRect(
            color = Color(0xFF9AA1AC),
            topLeft = Offset(centre.x - arm, centre.y - half),
            size = Size(arm * 2, half * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.4f),
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
