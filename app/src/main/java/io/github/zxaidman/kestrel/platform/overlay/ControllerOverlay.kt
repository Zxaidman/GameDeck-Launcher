package io.github.zxaidman.kestrel.platform.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.zxaidman.kestrel.core.input.AnalogProfile
import io.github.zxaidman.kestrel.core.input.GamepadControl
import io.github.zxaidman.kestrel.core.layout.Anchor
import io.github.zxaidman.kestrel.core.layout.Cluster
import io.github.zxaidman.kestrel.core.layout.Clustering
import io.github.zxaidman.kestrel.core.layout.ControlKind
import io.github.zxaidman.kestrel.core.layout.ControlShape
import io.github.zxaidman.kestrel.core.layout.ControllerLayout
import io.github.zxaidman.kestrel.core.layout.LayoutElement
import io.github.zxaidman.kestrel.core.layout.LayoutSurface
import io.github.zxaidman.kestrel.core.layout.PixelRect
import io.github.zxaidman.kestrel.core.layout.resolve
import io.github.zxaidman.kestrel.core.layout.scaledBy
import io.github.zxaidman.kestrel.platform.input.GamepadCodes
import io.github.zxaidman.kestrel.platform.input.InputEngine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The controls, drawn from a layout document over whatever the user is playing.
 *
 * **Nothing here decides what the pad looks like any more.** The arrangement is a
 * [ControllerLayout] — a file — and this class turns it into windows. What that buys is not
 * tidiness: it is that the layout editor, the skin system and per-target profiles all become
 * possible, because there is finally something for them to edit, dress and select. While the pad
 * was a Kotlin file, none of them had a subject.
 *
 * Four rules shape the rest, and every one was learned from a device.
 *
 * **A control must not take focus.** The platform delivers a controller's events to the focused
 * window, so controls inside an ordinary activity send their input back to Kestrel — measured in
 * `docs/phase0/results/app-stick-focus-20260819-redmi-note-13-5g.json`.
 *
 * **A control must not cover anything it does not need.** The first version was one window the size
 * of the screen that consumed every touch on the phone; only a reboot recovered it. Each cluster of
 * controls gets a window sized to itself, and `Clustering` decides what a cluster is.
 *
 * **Touches must split across those windows.** `FLAG_SPLIT_TOUCH` is what lets a second finger reach
 * a second window. Without it, holding the stick froze every other control and the phone underneath.
 *
 * **A window is dead everywhere its controls are not**, and that cannot be fixed from here —
 * measured: a view refusing a touch does not hand it to the application below, and the platform's
 * own remedy is not public API. It is why clusters are kept as small as the layout allows.
 */
public class ControllerOverlay(
    private val context: Context,
    private val engine: InputEngine,
    private var profile: AnalogProfile,
    private var scale: Float,
    private var layout: ControllerLayout,
) {

    private val windows = context.getSystemService(WindowManager::class.java)
    private var clusters: List<ClusterView> = emptyList()
    private var toggle: ToggleView? = null
    private var controlsVisible = false

    /** Roughly a thumb's reach, in pixels, from the shorter side of the screen. */
    private val unit: Int
        get() {
            val metrics = context.resources.displayMetrics
            return min(metrics.widthPixels, metrics.heightPixels)
        }

    /**
     * The area the controls actually have, which is not the size of the display.
     *
     * The window manager places an overlay inside what is left after the system bars, so a layout
     * resolved against the whole display lands somewhere else — every control pushed down by the
     * height of the status bar, the bottom row running off the screen, and the pad overlapping
     * itself. Measured on the reference device: it happened whenever the status bar was showing.
     *
     * **The bars are subtracted whether or not they are showing**, via
     * `getInsetsIgnoringVisibility`. A status bar can appear at any moment — a notification, a swipe
     * — and controls that move when it does are controls a thumb has to find again mid-play.
     */
    private fun surface(): LayoutSurface {
        val manager = windows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && manager != null) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout()
            )
            val width = metrics.bounds.width() - insets.left - insets.right
            val height = metrics.bounds.height() - insets.top - insets.bottom
            if (width > 0 && height > 0) {
                return LayoutSurface(width.toDouble(), height.toDouble())
            }
        }
        val metrics = context.resources.displayMetrics
        return LayoutSurface(metrics.widthPixels.toDouble(), metrics.heightPixels.toDouble())
    }

    /**
     * Shows the toggle, and nothing else.
     *
     * The toggle comes up first and alone on purpose: it is small, it is always reachable, and it
     * is the way out. A user who cannot make the controls go away has lost their phone until they
     * reboot it, which happened once and must not happen again.
     */
    public fun show(): Boolean {
        if (toggle != null) return true
        val view = ToggleView(context) { toggleControls() }
        // Deliberately not scaled with the controls. It is the way out, and a way out that shrinks
        // with a setting is a way out someone can make too small to use.
        val size = (unit * 0.10f).toInt()
        return runCatching {
            windows?.addView(
                view,
                params(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, (unit * 0.02f).toInt()),
            )
            toggle = view
            true
        }.getOrElse { false }
    }

    public fun hide() {
        hideControls()
        toggle?.let { runCatching { windows?.removeView(it) } }
        toggle = null
    }

    public val visible: Boolean
        get() = toggle != null

    public val controlsOn: Boolean
        get() = controlsVisible

    /** How many windows the current layout needed, for a screen that wants to say so. */
    public val clusterCount: Int
        get() = clusters.size

    public fun update(profile: AnalogProfile) {
        this.profile = profile
        clusters.forEach { it.profile = profile }
    }

    /**
     * Changes the size.
     *
     * **Moves the windows rather than replacing them, whenever it can.** Dragging a slider produces
     * a change every frame, and removing and re-adding eight windows that often left visible trails
     * and made the drag lag behind the thumb. Since the layout says which controls share a window,
     * a size change cannot alter the grouping — so the same windows are simply re-measured, and the
     * controls inside them told where they now are.
     *
     * A full rebuild is kept for the case where the grouping genuinely differs, which a layout
     * change can cause. It releases everything first: a control that disappears mid-press leaves
     * nothing behind able to release it.
     */
    public fun resize(scale: Float) {
        this.scale = scale
        if (!controlsVisible) return
        if (!reposition()) rebuild()
    }

    public fun apply(layout: ControllerLayout) {
        this.layout = layout
        rebuild()
    }

    /**
     * Recomputes for the screen as it is now.
     *
     * Called when the phone is turned. Every position is a fraction of a surface, and the surface
     * changed — so without this the controls stay where the old screen put them, which is what
     * showing and hiding them was working around.
     */
    public fun refresh() {
        if (!controlsVisible) return
        if (!reposition()) rebuild()
    }

    private fun rebuild() {
        if (!controlsVisible) return
        hideControls()
        showControls()
    }

    /** Re-measures the existing windows, or reports that the grouping no longer matches them. */
    private fun reposition(): Boolean {
        val plan = plan() ?: return false
        if (plan.size != clusters.size) return false
        plan.forEachIndexed { index, piece ->
            val view = clusters[index]
            if (view.ids != piece.members.map { it.id }) return false
        }
        plan.forEachIndexed { index, piece ->
            val view = clusters[index]
            view.reposition(piece.members)
            runCatching {
                windows?.updateViewLayout(
                    view,
                    params(piece.width, piece.height, piece.gravity, piece.marginX, piece.marginY),
                )
            }
        }
        return true
    }

    /**
     * One window's worth of the layout, positioned the way the window manager thinks.
     *
     * **Anchored to an edge rather than placed at a coordinate**, and that is a bug fix rather than
     * a preference. Absolute positions were computed against the display and then handed to the
     * window manager, which places within the area left after the system bars — so with a status
     * bar showing, every control moved down by its height, bottom-anchored ones ran off the screen,
     * and the pad overlapped itself. Gravity and a margin from the nearest edge mean the same thing
     * in both coordinate spaces, which is what an anchor was always supposed to say.
     */
    private class Piece(
        val gravity: Int,
        val marginX: Int,
        val marginY: Int,
        val width: Int,
        val height: Int,
        val members: List<PlacedControl>,
    )

    private fun plan(): List<Piece>? {
        val surface = surface()
        val factor = scale.toDouble()
        val placed = layout.elements.map { element ->
            element.id to element.placement.scaledBy(factor).resolve(surface)
        }
        if (placed.isEmpty()) return null

        val byId = layout.elements.associateBy { it.id }
        val rects = placed.toMap()
        return Clustering.group(layout, placed).mapNotNull { cluster ->
            val bounds = padded(cluster)
            val anchor = byId[cluster.elementIds.first()]?.placement?.anchor ?: Anchor.TOP_LEFT
            val members = cluster.elementIds.mapNotNull { id ->
                val element = byId[id] ?: return@mapNotNull null
                val rect = rects[id] ?: return@mapNotNull null
                // Local to the window, so a view never needs to know where on the screen it is.
                PlacedControl(
                    element = element,
                    centerX = (rect.centerX - bounds.left).toFloat(),
                    centerY = (rect.centerY - bounds.top).toFloat(),
                    halfWidth = (rect.width / 2).toFloat(),
                    halfHeight = (rect.height / 2).toFloat(),
                )
            }
            if (members.isEmpty()) {
                null
            } else {
                // Which edge this window hangs from, taken from the anchor its controls were
                // authored against. A layout that says "bottom right" means it at any screen size.
                val horizontal = when (anchor.originX) {
                    0.0 -> Gravity.START to bounds.left
                    1.0 -> Gravity.END to (surface.usableWidth - bounds.right)
                    else -> Gravity.START to bounds.left
                }
                val vertical = when (anchor.originY) {
                    0.0 -> Gravity.TOP to bounds.top
                    1.0 -> Gravity.BOTTOM to (surface.usableHeight - bounds.bottom)
                    else -> Gravity.TOP to bounds.top
                }
                Piece(
                    gravity = horizontal.first or vertical.first,
                    marginX = horizontal.second.toInt().coerceAtLeast(0),
                    marginY = vertical.second.toInt().coerceAtLeast(0),
                    width = bounds.width.toInt(),
                    height = bounds.height.toInt(),
                    members = members,
                )
            }
        }
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    /**
     * Turns the layout into windows.
     *
     * Placements are scaled, resolved against this screen, grouped as the layout says, and each
     * group becomes one window holding the controls inside it. Every number comes from the
     * document; nothing about the arrangement is decided here.
     */
    private fun showControls() {
        if (controlsVisible) return
        val plan = plan() ?: return

        val added = mutableListOf<ClusterView>()
        val ok = plan.all { piece ->
            val view = ClusterView(context, engine, profile, piece.members)
            runCatching {
                windows?.addView(
                    view,
                    params(piece.width, piece.height, piece.gravity, piece.marginX, piece.marginY),
                )
                added += view
                true
            }.getOrElse { false }
        }

        if (!ok) {
            added.forEach { runCatching { windows?.removeView(it) } }
            return
        }

        clusters = added
        controlsVisible = true
    }

    /** Room for the outline, which is stroked centred on an edge and would be half clipped. */
    private fun padded(cluster: Cluster): PixelRect {
        val pad = unit * 0.012
        return PixelRect(
            centerX = cluster.bounds.centerX,
            centerY = cluster.bounds.centerY,
            width = cluster.bounds.width + pad * 2,
            height = cluster.bounds.height + pad * 2,
        )
    }

    private fun hideControls() {
        // Everything is released before anything is removed. A control that disappears mid-press
        // leaves nothing behind able to release it, and a stick removed at full deflection keeps
        // the platform emitting directional keys indefinitely.
        clusters.forEach { it.releaseAll() }
        engine.stick(0.0, 0.0, profile)
        engine.rightStick(0.0, 0.0, profile)
        engine.hat(0, 0)
        engine.trigger(0.0, profile, right = false)
        engine.trigger(0.0, profile, right = true)

        clusters.forEach { runCatching { windows?.removeView(it) } }
        clusters = emptyList()
        controlsVisible = false
    }

    private fun params(
        width: Int,
        height: Int,
        gravity: Int,
        marginX: Int,
        marginY: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        // NOT_FOCUSABLE is why any of this works: without it the overlay becomes the focused window
        // on touch and the controller's own events come back to Kestrel. NOT_TOUCH_MODAL lets
        // everything outside these windows reach whatever is underneath. SPLIT_TOUCH is what makes
        // them independent: without it the first window to see a finger owns the gesture, so
        // holding the stick froze every other control and froze the phone underneath with it.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        x = marginX
        y = marginY
    }

    public companion object {
        /** Whether the user has allowed drawing over other applications. */
        public fun permitted(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}

/** One control, resolved onto the window that holds it. */
private class PlacedControl(
    val element: LayoutElement,
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
) {
    val id: String get() = element.id
    val kind: ControlKind get() = element.kind
    val binds: GamepadControl? get() = element.binds
    val label: String get() = element.label ?: element.binds?.defaultLabel ?: ""

    val shape: ControlShape get() = when (element.shape) {
        // A stick and a pad are round things. A layout is free to say otherwise for a button, but
        // a circular stick is what the maths behind it assumes: deflection is a distance from a
        // centre, and a rectangular one would deflect further along its diagonal than its sides.
        ControlShape.CIRCLE -> ControlShape.CIRCLE
        else -> if (kind == ControlKind.STICK || kind == ControlKind.DPAD) {
            ControlShape.CIRCLE
        } else {
            element.shape
        }
    }

    /** The radius a round control is drawn at, and the reach every control is measured against. */
    val radius: Float get() = min(halfWidth, halfHeight)

    /** Half-extent in each axis, after the shape has had its say about squareness. */
    val extentX: Float get() = if (shape == ControlShape.RECTANGLE) halfWidth else radius
    val extentY: Float get() = if (shape == ControlShape.RECTANGLE) halfHeight else radius

    /** How round the corners are. Enough to look drawn rather than cut. */
    val corner: Float get() = min(extentX, extentY) * 0.35f

    /**
     * Whether a touch is on this control.
     *
     * The shape decides, not the bounding box. A rectangle hit-tested as a circle would have corners
     * that look pressable and are not — a fault a player feels and cannot describe.
     */
    fun contains(x: Float, y: Float, reach: Float): Boolean = when (shape) {
        ControlShape.CIRCLE -> hypot(x - centerX, y - centerY) <= radius * reach
        else -> kotlin.math.abs(x - centerX) <= extentX * reach &&
            kotlin.math.abs(y - centerY) <= extentY * reach
    }
}

/**
 * One palette for every control, and the reason it is built this way.
 *
 * The first answer to "invisible on a white screen" was a heavy dark ring around a pale shape. It
 * worked and it looked like a diagram. What commercial pads on this platform do — and what the
 * project owner asked for — is the opposite: **a dark translucent plate carries the cluster, and
 * the controls sit on it in a lighter grey**. The plate is what makes the cluster legible over a
 * white page, so no individual control needs a ring heavy enough to do that alone.
 *
 * Labels are still drawn twice, dark stroke then light fill, because a label is small enough to
 * fall on either tone within a single control.
 */
private object Ink {

    fun plate(): Paint = Paint().apply {
        color = Color.argb(122, 20, 22, 27)
        isAntiAlias = true
    }

    fun plateRim(): Paint = Paint().apply {
        color = Color.argb(70, 236, 240, 248)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    fun body(): Paint = Paint().apply {
        color = Color.argb(205, 92, 98, 108)
        isAntiAlias = true
    }

    fun rim(): Paint = Paint().apply {
        color = Color.argb(150, 12, 14, 18)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    /** What a held control looks like. Distinct enough to be seen at a glance mid-play. */
    fun active(): Paint = Paint().apply {
        color = Color.argb(230, 96, 186, 255)
        isAntiAlias = true
    }

    fun text(): Paint = Paint().apply {
        color = Color.argb(245, 238, 242, 250)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun textEdge(): Paint = Paint().apply {
        color = Color.argb(210, 8, 10, 14)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    /** A label that survives both a white menu and a black game. */
    fun label(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, fill: Paint, edge: Paint) {
        if (s.isEmpty()) return
        fill.textSize = size
        edge.textSize = size
        edge.strokeWidth = max(2f, size * 0.17f)
        canvas.drawText(s, cx, cy, edge)
        canvas.drawText(s, cx, cy, fill)
    }
}

/** The always-present way to make the controls appear and disappear. */
private class ToggleView(context: Context, private val onTap: () -> Unit) : View(context) {

    private val body = Paint().apply { color = Color.argb(150, 20, 20, 24); isAntiAlias = true }
    private val edge = Paint().apply {
        color = Color.argb(210, 240, 244, 250)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val mark = Ink.text()
    private val markEdge = Ink.textEdge()

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        edge.strokeWidth = max(2f, r * 0.07f)
        canvas.drawCircle(width / 2f, height / 2f, r * 0.9f, body)
        canvas.drawCircle(width / 2f, height / 2f, r * 0.9f, edge)
        Ink.label(canvas, "K", width / 2f, height / 2f + r / 3f, r, mark, markEdge)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onTap()
        return true
    }
}

/**
 * One window's worth of controls, of whatever kinds the layout put together.
 *
 * A single view rather than one class per kind, because the interesting behaviour is **between**
 * controls rather than inside them. A thumb rolling from one face button to the next, or holding a
 * stick press and then moving the stick, happens across two controls in one gesture, and code split
 * by control type has nowhere to put it.
 *
 * The touch model, which is the whole of it:
 *
 * - **A stick or a pad belongs to one finger** until that finger lifts. A second finger landing on
 *   it is ignored, because a stick is one thumb and a stray palm used to cancel the direction being
 *   held.
 * - **A button follows its finger onto another button**, which is what makes rolling across a
 *   diamond press each in turn.
 * - **A button does not release when its finger slides onto nothing, or onto a stick.** Sliding off
 *   is how a thumb holds `L3` and then moves the stick, which some titles need; and a press lost by
 *   drifting a few pixels is a press the player did not mean to lose. Lifting is what releases.
 */
private class ClusterView(
    context: Context,
    private val engine: InputEngine,
    var profile: AnalogProfile,
    private var controls: List<PlacedControl>,
) : View(context) {

    /** What this window holds, so the overlay can tell whether a plan still fits these windows. */
    val ids: List<String> get() = controls.map { it.id }

    /**
     * Takes the same controls at new positions.
     *
     * Live state is keyed by element id and left alone, so a control held while the size changes
     * stays held — which is the whole reason the windows are moved rather than replaced.
     */
    fun reposition(members: List<PlacedControl>) {
        controls = members
        crossPaths.clear()
        arrowPaths.clear()
        members.filter { it.kind == ControlKind.DPAD }.forEach { buildCross(it) }
        invalidate()
    }

    private val plate = Ink.plate()
    private val plateRim = Ink.plateRim()
    private val body = Ink.body()
    private val rim = Ink.rim()
    private val glow = Ink.active()
    private val ring = Ink.active().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arrow = Paint().apply { color = Color.argb(225, 16, 18, 23); isAntiAlias = true }
    private val hub = Paint().apply { color = Color.argb(70, 10, 12, 17); isAntiAlias = true }
    private val text = Ink.text()
    private val textEdge = Ink.textEdge()
    private val arc = RectF()

    /** Live state, by element id. The layout is the shape; none of this comes from it. */
    private val pressed = mutableMapOf<String, Boolean>()
    private val level = mutableMapOf<String, Float>()
    private val stickX = mutableMapOf<String, Float>()
    private val stickY = mutableMapOf<String, Float>()
    private val hat = mutableMapOf<String, Pair<Int, Int>>()

    /** Which finger is doing what. */
    private val pointerButton = mutableMapOf<Int, String>()
    private val pointerAxis = mutableMapOf<Int, String>()

    private var owned = false
    private var ramping = false
    private var lastFrameNanos = 0L

    private val crossPaths = mutableMapOf<String, Path>()
    private val arrowPaths = mutableMapOf<String, List<Path>>()

    init {
        controls.filter { it.kind == ControlKind.DPAD }.forEach { buildCross(it) }
    }

    // --- drawing ------------------------------------------------------------------------------

    private fun buildCross(control: PlacedControl) {
        val arm = control.radius * 0.94f
        val half = arm * 0.33f
        val cx = control.centerX
        val cy = control.centerY

        crossPaths[control.id] = Path().apply {
            moveTo(cx - half, cy - arm)
            lineTo(cx + half, cy - arm)
            lineTo(cx + half, cy - half)
            lineTo(cx + arm, cy - half)
            lineTo(cx + arm, cy + half)
            lineTo(cx + half, cy + half)
            lineTo(cx + half, cy + arm)
            lineTo(cx - half, cy + arm)
            lineTo(cx - half, cy + half)
            lineTo(cx - arm, cy + half)
            lineTo(cx - arm, cy - half)
            lineTo(cx - half, cy - half)
            close()
        }

        val reach = (arm + half) / 2f
        val size = half * 0.52f
        arrowPaths[control.id] = listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f).map { (ux, uy) ->
            val px = -uy
            val py = ux
            val ox = cx + ux * reach
            val oy = cy + uy * reach
            Path().apply {
                moveTo(ox + ux * size, oy + uy * size)
                lineTo(ox - ux * size * 0.55f + px * size * 0.85f, oy - uy * size * 0.55f + py * size * 0.85f)
                lineTo(ox - ux * size * 0.55f - px * size * 0.85f, oy - uy * size * 0.55f - py * size * 0.85f)
                close()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        controls.forEach { control ->
            when (control.kind) {
                ControlKind.STICK -> drawStick(canvas, control)
                ControlKind.DPAD -> drawPad(canvas, control)
                ControlKind.DECORATION -> drawDecoration(canvas, control)
                else -> drawButton(canvas, control)
            }
        }
    }

    private fun drawStick(canvas: Canvas, control: PlacedControl) {
        val r = control.radius
        plateRim.strokeWidth = max(2f, r * 0.030f)
        rim.strokeWidth = max(2f, r * 0.045f)
        canvas.drawCircle(control.centerX, control.centerY, r, plate)
        canvas.drawCircle(control.centerX, control.centerY, r, plateRim)

        val knobRadius = r * KNOB
        val travel = r - knobRadius
        val kx = control.centerX + (stickX[control.id] ?: 0f) * travel
        val ky = control.centerY + (stickY[control.id] ?: 0f) * travel
        // Lit while a thumb is on it, like every other control. A stick was once the one control
        // that gave no sign of being touched.
        val held = pointerAxis.containsValue(control.id)
        canvas.drawCircle(kx, ky, knobRadius, if (held) glow else body)
        canvas.drawCircle(kx, ky, knobRadius, rim)
    }

    private fun drawPad(canvas: Canvas, control: PlacedControl) {
        val cross = crossPaths[control.id] ?: return
        val r = control.radius
        val arm = r * 0.94f
        val half = arm * 0.33f
        val corner = CornerPathEffect(half * 0.5f)
        body.pathEffect = corner
        rim.pathEffect = corner
        rim.strokeWidth = max(2f, r * 0.05f)
        plateRim.strokeWidth = max(2f, r * 0.030f)

        canvas.drawCircle(control.centerX, control.centerY, r, plate)
        canvas.drawCircle(control.centerX, control.centerY, r, plateRim)
        canvas.drawPath(cross, body)

        val (hx, hy) = hat[control.id] ?: (0 to 0)
        if (hx != 0 || hy != 0) {
            val cx = control.centerX
            val cy = control.centerY
            canvas.save()
            // Clipped to the cross, so a highlight can be a plain rectangle and still land exactly
            // on the arm it belongs to — including both arms of a diagonal.
            canvas.clipPath(cross)
            if (hy < 0) canvas.drawRect(cx - arm, cy - arm, cx + arm, cy - half, glow)
            if (hy > 0) canvas.drawRect(cx - arm, cy + half, cx + arm, cy + arm, glow)
            if (hx < 0) canvas.drawRect(cx - arm, cy - arm, cx - half, cy + arm, glow)
            if (hx > 0) canvas.drawRect(cx + half, cy - arm, cx + arm, cy + arm, glow)
            canvas.restore()
        }

        canvas.drawPath(cross, rim)
        body.pathEffect = null
        rim.pathEffect = null
        canvas.drawCircle(control.centerX, control.centerY, half * 0.45f, hub)
        arrowPaths[control.id]?.forEach { canvas.drawPath(it, arrow) }
    }

    /** Draws a control's outline, whichever outline the layout gave it. */
    private fun outline(canvas: Canvas, control: PlacedControl, paint: Paint) {
        if (control.shape == ControlShape.CIRCLE) {
            canvas.drawCircle(control.centerX, control.centerY, control.radius, paint)
        } else {
            arc.set(
                control.centerX - control.extentX,
                control.centerY - control.extentY,
                control.centerX + control.extentX,
                control.centerY + control.extentY,
            )
            canvas.drawRoundRect(arc, control.corner, control.corner, paint)
        }
    }

    private fun drawButton(canvas: Canvas, control: PlacedControl) {
        val r = control.radius
        rim.strokeWidth = max(2f, r * 0.09f)
        ring.strokeWidth = max(3f, r * 0.14f)
        val cx = control.centerX
        val cy = control.centerY

        outline(canvas, control, body)

        val analog = control.kind == ControlKind.ANALOG_TRIGGER
        val value = level[control.id] ?: 0f
        if (analog && value > 0f) {
            canvas.save()
            // Fills from the bottom upward, in proportion to the value being sent — the same number
            // the target receives, shown where the thumb is.
            val top = cy + control.extentY - 2f * control.extentY * value
            canvas.clipRect(cx - control.extentX, top, cx + control.extentX, cy + control.extentY)
            outline(canvas, control, glow)
            canvas.restore()
            // And again as a ring closing clockwise, drawn just inside the edge: a fill inside a
            // small control is exactly the part of it a thumb is covering.
            val ringRadius = r - ring.strokeWidth * 0.6f
            arc.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
            canvas.drawArc(arc, -90f, 360f * value, false, ring)
        } else if (!analog && pressed[control.id] == true) {
            outline(canvas, control, glow)
        }

        outline(canvas, control, rim)
        val size = r * when {
            control.label.length > 2 -> 0.66f
            control.label.length > 1 -> 0.78f
            else -> 0.86f
        }
        Ink.label(canvas, control.label, cx, cy + size / 3f, size, text, textEdge)
    }

    private fun drawDecoration(canvas: Canvas, control: PlacedControl) {
        val size = control.radius * 0.6f
        Ink.label(canvas, control.label, control.centerX, control.centerY + size / 3f, size, text, textEdge)
    }

    // --- touch --------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A cluster window is a rectangle and its controls are not. A touch in the space
                // between them is refused rather than swallowed — which does not, on the reference
                // device, hand it to the application below, but costs nothing and says what the
                // window is for.
                owned = controlAt(event.getX(0), event.getY(0)) != null
                if (!owned) return false
                claim(event, 0)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!owned) return false
                claim(event, event.actionIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!owned) return false
                for (i in 0 until event.pointerCount) move(event, i)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!owned) return false
                lift(event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) owned = false
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                owned = false
            }
        }
        invalidate()
        return true
    }

    private fun claim(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val control = controlAt(event.getX(index), event.getY(index)) ?: return
        when (control.kind) {
            ControlKind.STICK, ControlKind.DPAD -> takeAxis(id, control, event, index)
            ControlKind.DECORATION -> Unit
            else -> holdButton(id, control)
        }
    }

    private fun move(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        pointerAxis[id]?.let { axisId ->
            controls.firstOrNull { it.id == axisId }?.let { aim(it, x, y) }
            return
        }

        val under = controlAt(x, y)
        when {
            under == null -> Unit // Keeps whatever this finger already holds.
            under.kind == ControlKind.STICK || under.kind == ControlKind.DPAD ->
                takeAxis(id, under, event, index)
            under.kind == ControlKind.DECORATION -> Unit
            under.id != pointerButton[id] -> holdButton(id, under)
        }
    }

    private fun takeAxis(id: Int, control: PlacedControl, event: MotionEvent, index: Int) {
        // One finger owns a stick until it lifts. A second used to take it over, so a stray palm
        // cancelled the direction being held.
        if (pointerAxis.containsValue(control.id)) return
        pointerAxis[id] = control.id
        aim(control, event.getX(index), event.getY(index))
    }

    private fun holdButton(id: Int, control: PlacedControl) {
        pointerButton[id]?.let { previous -> if (previous != control.id) release(previous) }
        pointerButton[id] = control.id
        press(control)
    }

    private fun lift(id: Int) {
        pointerButton.remove(id)?.let { release(it) }
        pointerAxis.remove(id)?.let { axisId ->
            controls.firstOrNull { it.id == axisId }?.let { centre(it) }
        }
    }

    fun releaseAll() {
        pointerButton.values.toList().forEach { release(it) }
        pointerButton.clear()
        pointerAxis.values.toList().forEach { axisId ->
            controls.firstOrNull { it.id == axisId }?.let { centre(it) }
        }
        pointerAxis.clear()
        // A trigger mid-ramp is dropped rather than drained: nothing is going to run the ramp once
        // the window is gone, and a trigger left part-pressed is one nobody can release.
        controls.filter { it.kind == ControlKind.ANALOG_TRIGGER }.forEach { control ->
            if ((level[control.id] ?: 0f) != 0f) {
                level[control.id] = 0f
                sendTrigger(control, 0.0)
            }
        }
        invalidate()
    }

    /** The last one wins, so a control drawn on top of another is the one a touch reaches. */
    private fun controlAt(x: Float, y: Float): PlacedControl? = controls.lastOrNull { control ->
        control.kind != ControlKind.DECORATION && control.contains(x, y, REACH)
    }

    // --- sending ------------------------------------------------------------------------------

    private fun press(control: PlacedControl) {
        pressed[control.id] = true
        when (control.kind) {
            ControlKind.ANALOG_TRIGGER -> startRamp()
            ControlKind.DIGITAL_TRIGGER -> sendTrigger(control, 1.0)
            else -> control.binds?.let { GamepadCodes.buttonCode(it) }?.let { engine.button(it, true) }
        }
    }

    private fun release(id: String) {
        val control = controls.firstOrNull { it.id == id } ?: return
        pressed[id] = false
        when (control.kind) {
            ControlKind.ANALOG_TRIGGER -> startRamp()
            ControlKind.DIGITAL_TRIGGER -> sendTrigger(control, 0.0)
            else -> control.binds?.let { GamepadCodes.buttonCode(it) }?.let { engine.button(it, false) }
        }
    }

    private fun aim(control: PlacedControl, x: Float, y: Float) {
        when (control.kind) {
            ControlKind.STICK -> {
                val knobRadius = control.radius * KNOB
                val travel = control.radius - knobRadius
                val dx = (x - control.centerX) / travel
                val dy = (y - control.centerY) / travel
                // Clamped as a circle, not per axis. Clamping each axis to ±1 separately lets a
                // diagonal reach 1.41 from centre, which is outside the ring the user can see and a
                // deflection no real stick can produce.
                val magnitude = hypot(dx, dy)
                val nx = if (magnitude > 1f) dx / magnitude else dx
                val ny = if (magnitude > 1f) dy / magnitude else dy
                stickX[control.id] = nx
                stickY[control.id] = ny
                sendStick(control, nx.toDouble(), ny.toDouble())
            }

            ControlKind.DPAD -> {
                val arm = control.radius * 0.94f
                val dx = x - control.centerX
                val dy = y - control.centerY
                val magnitude = hypot(dx, dy)
                var hx = 0
                var hy = 0
                if (magnitude > arm * CENTRE) {
                    val ux = dx / magnitude
                    val uy = dy / magnitude
                    if (ux > SECTOR) hx = 1 else if (ux < -SECTOR) hx = -1
                    if (uy > SECTOR) hy = 1 else if (uy < -SECTOR) hy = -1
                }
                if (hat[control.id] != (hx to hy)) {
                    hat[control.id] = hx to hy
                    engine.hat(hx, hy)
                }
            }

            else -> Unit
        }
        invalidate()
    }

    private fun centre(control: PlacedControl) {
        when (control.kind) {
            ControlKind.STICK -> {
                stickX[control.id] = 0f
                stickY[control.id] = 0f
                sendStick(control, 0.0, 0.0)
            }

            ControlKind.DPAD -> {
                hat[control.id] = 0 to 0
                engine.hat(0, 0)
            }

            else -> Unit
        }
        invalidate()
    }

    private fun sendStick(control: PlacedControl, x: Double, y: Double) {
        val binds = control.binds ?: return
        if (GamepadCodes.isRight(binds)) engine.rightStick(x, y, profile) else engine.stick(x, y, profile)
    }

    private fun sendTrigger(control: PlacedControl, value: Double) {
        val binds = control.binds ?: return
        engine.trigger(value, profile, GamepadCodes.isRight(binds))
    }

    // --- the trigger ramp ---------------------------------------------------------------------

    private fun startRamp() {
        if (ramping) return
        ramping = true
        lastFrameNanos = 0L
        postOnAnimation(ramp)
    }

    private val ramp = object : Runnable {
        override fun run() {
            // Timed, not counted. Stepping a fixed amount per frame ties the ramp to the display: a
            // trail from the reference device showed a press intended to take half a second taking
            // 0.31, because that panel runs at 120 Hz rather than the 60 the constant assumed.
            val now = System.nanoTime()
            val seconds = if (lastFrameNanos == 0L) {
                1f / 60f
            } else {
                // Capped, because a frame delayed by a stall would otherwise jump the value.
                min((now - lastFrameNanos) / 1_000_000_000f, 0.1f)
            }
            lastFrameNanos = now

            var busy = false
            controls.filter { it.kind == ControlKind.ANALOG_TRIGGER }.forEach { control ->
                val target = if (pressed[control.id] == true) 1f else 0f
                val current = level[control.id] ?: 0f
                if (current == target) return@forEach
                val next = if (target > current) {
                    min(target, current + seconds / RISE_SECONDS)
                } else {
                    max(target, current - seconds / FALL_SECONDS)
                }
                level[control.id] = next
                sendTrigger(control, next.toDouble())
                busy = true
            }
            invalidate()
            if (busy) {
                postOnAnimation(this)
            } else {
                ramping = false
                lastFrameNanos = 0L
            }
        }
    }

    private companion object {
        /** A little past the drawn edge, because a thumb's centre is not where it looks. */
        const val REACH = 1.15f

        /** The knob, as a fraction of the plate it sits on. */
        const val KNOB = 0.42f

        /** Inside this fraction of an arm, the thumb is on the pad's hub and no direction is held. */
        const val CENTRE = 0.20f

        /**
         * Where a cardinal ends and a diagonal begins.
         *
         * `sin(22.5°) ≈ 0.383` would make all eight sectors equal. Slightly above it gives the four
         * cardinals about 50° each and the diagonals about 40°, because a thumb aiming for "up"
         * misses more often than one aiming for a corner it can feel it is reaching for.
         */
        const val SECTOR = 0.42f

        /**
         * How long a trigger takes to travel, in seconds.
         *
         * Release is quicker than press on purpose: a control that lingers after the thumb has gone
         * feels broken, while one that takes a moment to reach full feels like a trigger.
         */
        const val RISE_SECONDS = 0.50f
        const val FALL_SECONDS = 0.30f
    }
}
