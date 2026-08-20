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
import io.github.zxaidman.kestrel.platform.input.InputEngine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The controls, drawn over whatever the user is playing, in windows that never take focus.
 *
 * Three rules shape everything here, and all three were learned from a device rather than reasoned.
 *
 * **A control must not take focus.** The platform delivers a controller's events to the focused
 * window, so controls inside an ordinary activity send their input back to Kestrel — measured in
 * `docs/phase0/results/app-stick-focus-20260819-redmi-note-13-5g.json`.
 *
 * **A control must not cover anything it does not need.** The first version was one window the size
 * of the screen whose touch handler returned "handled" for every touch, so it consumed every touch
 * on the phone: the home screen, the recent list, settings, all of it. The device could not be
 * operated by finger at all and only a reboot recovered it. That is not a bug to be patched by
 * returning "not handled" more carefully — it is a reason not to put a window there. **Each control
 * cluster now gets its own window, sized to itself.** Everywhere the controls are not, there is no
 * window of ours, so nothing of ours can intercept anything.
 *
 * **Touches must split across those windows.** Small windows are not enough on their own: by
 * default the window that receives the first finger owns the whole gesture, so holding the stick
 * made every other control — and the rest of the phone — stop responding. `FLAG_SPLIT_TOUCH` is
 * what makes a second finger reach a second window, and without it a pad with separate clusters is
 * unplayable no matter how it is drawn.
 *
 * **A window's rectangle is dead everywhere its controls are not, and that cannot be fixed from
 * here.** Measured on the reference device: a view returning "not handled" for a touch does **not**
 * hand it to the application underneath — the window is chosen before the view hierarchy is
 * consulted, so refusing merely wastes the touch instead of using it. The gaps between the circles
 * in a cluster are therefore inert. The platform's own remedy is an irregular touchable region,
 * which is not public API and so is not available to this project (`CLAUDE.md` §8). The remaining
 * public option is one window per control, which would trade away sliding a thumb from one control
 * to the next — a thing that works and was asked for. The gaps stay; the tradeoff is recorded.
 *
 * Controls still refuse touches that miss them, because it costs nothing and expresses what the
 * window is for. It is not claimed to make the gaps transparent — it does not.
 */
public class ControllerOverlay(
    private val context: Context,
    private val engine: InputEngine,
    private var profile: AnalogProfile,
    private var scale: Float = DEFAULT_SCALE,
) {

    private val windows = context.getSystemService(WindowManager::class.java)
    private var stick: StickView? = null
    private var rightStick: StickView? = null
    private var buttons: PadView? = null
    private var dpad: DpadView? = null
    private var leftShoulders: PadView? = null
    private var rightShoulders: PadView? = null
    private var toggle: ToggleView? = null
    private var controlsVisible = false

    private val everyView: List<View?>
        get() = listOf(stick, rightStick, buttons, dpad, leftShoulders, rightShoulders)

    /** Roughly a thumb's reach, in pixels, from the shorter side of the screen. */
    private val unit: Int
        get() {
            val metrics = context.resources.displayMetrics
            return min(metrics.widthPixels, metrics.heightPixels)
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

    public fun update(profile: AnalogProfile) {
        this.profile = profile
        stick?.profile = profile
        rightStick?.profile = profile
    }

    /**
     * Where every cluster sits and how big its window is.
     *
     * One description, used by both the first layout and every resize, because the two drifting
     * apart is how a resize ends up putting a control somewhere the first layout never did.
     */
    private fun plan(): List<Pair<Int, WindowManager.LayoutParams>> {
        val big = clusterSize()
        val small = (big * 0.72f).toInt()
        val shoulderHeight = (big * 0.62f).toInt()
        val margin = marginSize()
        val above = margin + big + margin / 2

        return listOf(
            // Wider than tall: the plate hugs the outer edge and the stick press sits in the strip
            // left over on the inner side, in the same window so a thumb can travel between them.
            STICK to params(
                (big * StickView.WIDTH).toInt(), big,
                Gravity.BOTTOM or Gravity.START, margin, margin,
            ),
            FACES to params(big, big, Gravity.BOTTOM or Gravity.END, margin, margin),
            DPAD to params(small, small, Gravity.BOTTOM or Gravity.START, margin, above),
            // The same size as the left. It was smaller to save room, which cost the right stick
            // half its precision and its press button a third of its radius for no reason a hand
            // could feel; there is space for both at full size in either orientation.
            RIGHT_STICK to params(
                (big * StickView.WIDTH).toInt(), big,
                Gravity.BOTTOM or Gravity.END, margin, above,
            ),
            LEFT_SHOULDERS to
                params(big, shoulderHeight, Gravity.TOP or Gravity.START, margin, margin),
            RIGHT_SHOULDERS to
                params(big, shoulderHeight, Gravity.TOP or Gravity.END, margin, margin),
        )
    }

    private fun viewFor(slot: Int): View? = when (slot) {
        STICK -> stick
        FACES -> buttons
        DPAD -> dpad
        RIGHT_STICK -> rightStick
        LEFT_SHOULDERS -> leftShoulders
        RIGHT_SHOULDERS -> rightShoulders
        else -> null
    }

    /**
     * Resizes the controls without taking them away and putting them back.
     *
     * Removing and re-adding the windows would drop any control being held at that moment, so a
     * size change during play would leave a button stuck down. Existing windows are re-measured
     * instead.
     */
    public fun resize(scale: Float) {
        this.scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (!controlsVisible) return
        plan().forEach { (slot, layout) ->
            val view = viewFor(slot) ?: return@forEach
            runCatching { windows?.updateViewLayout(view, layout) }
        }
    }

    private fun clusterSize(): Int = (unit * 0.46f * scale).toInt()

    private fun marginSize(): Int = (unit * 0.04f).toInt()

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    /**
     * Every control a standard pad has, so what a target does with each can be tested.
     *
     * Laid out as a controller is: sticks and d-pad on the left half, face buttons on the right,
     * shoulders along the top edge where index fingers reach, with Select and Start beneath their
     * own bumpers and each stick press beside its own stick. Each cluster is a separate window, so
     * none of them covers anything it does not need to.
     */
    private fun showControls() {
        if (controlsVisible) return

        val stickView = StickView(context, engine, profile, right = false)
        val rightStickView = StickView(context, engine, profile, right = true)
        val buttonsView = PadView(context, PadView.face(engine), plate = true)
        val dpadView = DpadView(context, engine)
        val leftShoulderView = PadView(context, PadView.leftCluster(engine, profile))
        val rightShoulderView = PadView(context, PadView.rightCluster(engine, profile))

        val added = mutableListOf<View>()
        val ok = plan().all { (slot, layout) ->
            val view = when (slot) {
                STICK -> stickView
                FACES -> buttonsView
                DPAD -> dpadView
                RIGHT_STICK -> rightStickView
                LEFT_SHOULDERS -> leftShoulderView
                else -> rightShoulderView
            }
            runCatching {
                windows?.addView(view, layout)
                added += view
                true
            }.getOrElse { false }
        }

        if (!ok) {
            added.forEach { runCatching { windows?.removeView(it) } }
            return
        }

        stick = stickView
        rightStick = rightStickView
        buttons = buttonsView
        dpad = dpadView
        leftShoulders = leftShoulderView
        rightShoulders = rightShoulderView
        controlsVisible = true
    }

    private fun hideControls() {
        // Everything is released before anything is removed. A control that disappears mid-press
        // leaves nothing behind able to release it, and a stick removed at full deflection keeps
        // the platform emitting directional keys indefinitely.
        engine.stick(0.0, 0.0, profile)
        engine.rightStick(0.0, 0.0, profile)
        engine.hat(0, 0)
        engine.trigger(0.0, profile, right = false)
        engine.trigger(0.0, profile, right = true)
        buttons?.releaseAll()
        dpad?.release()
        stick?.releaseAll()
        rightStick?.releaseAll()
        leftShoulders?.releaseAll()
        rightShoulders?.releaseAll()

        everyView.filterNotNull().forEach { runCatching { windows?.removeView(it) } }
        stick = null
        rightStick = null
        buttons = null
        dpad = null
        leftShoulders = null
        rightShoulders = null
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
        // everything outside these small windows reach whatever is underneath. SPLIT_TOUCH is what
        // makes them independent: without it the first window to see a finger owns the gesture, so
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

        private const val STICK = 0
        private const val FACES = 1
        private const val DPAD = 2
        private const val RIGHT_STICK = 3
        private const val LEFT_SHOULDERS = 4
        private const val RIGHT_SHOULDERS = 5

        /**
         * How large the controls are, as a fraction of the size they were first drawn at.
         *
         * The first size was chosen by arithmetic — a fraction of the short side that seemed
         * thumb-sized — and looked too large on the reference device in both orientations. This is
         * the kind of number only a hand can settle, so it is a setting with a default rather than
         * a constant, and the default is what that hand asked for.
         */
        public const val DEFAULT_SCALE: Float = 0.65f
        public const val MIN_SCALE: Float = 0.35f
        public const val MAX_SCALE: Float = 1.3f

        /** Whether the user has allowed drawing over other applications. */
        public fun permitted(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}

/**
 * One palette for every control, and why it is built the way it is.
 *
 * The first attempt at "visible on a white screen" was a heavy dark ring around a pale shape. It
 * worked and it looked like a diagram. What commercial pads on this platform actually do — and what
 * the project owner asked for — is the opposite arrangement: **a dark translucent plate carries the
 * cluster, and the controls sit on it in a lighter grey**. The plate is what makes the whole cluster
 * legible over a white page, so each individual control no longer needs a ring heavy enough to do
 * that job alone.
 *
 * Labels are still drawn twice, dark stroke then light fill, because a label is small enough that
 * it can fall on either tone within a single control.
 */
private object Ink {

    /** The disc a cluster sits on. Dark enough to define the cluster against a white page. */
    fun plate(): Paint = Paint().apply {
        color = Color.argb(122, 20, 22, 27)
        isAntiAlias = true
    }

    fun plateRim(): Paint = Paint().apply {
        color = Color.argb(70, 236, 240, 248)
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /** A control itself: lighter than its plate, darker than its label. */
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
 * A stick and its press, in one window, because a thumb has to be able to travel between them.
 *
 * The press — `L3` or `R3` — could not be a separate window, and the reason is worth writing down
 * rather than rediscovering. A pointer belongs to the window that received its touch-down and stays
 * there for the life of the gesture; `FLAG_SPLIT_TOUCH` lets a *new* finger reach a *different*
 * window, but it does not hand an existing finger over. So a press button in its own window can be
 * held or the stick can be moved, never both by one thumb.
 *
 * Sharing a window makes the sequence the project owner asked for possible: **hold the press, slide
 * onto the stick, and both are live**. Some titles need exactly that — a stick press held while the
 * stick is being moved.
 *
 * Two rules follow from it:
 *
 * - **The press latches to its finger, not to its area.** Sliding off the button does not release
 *   it; only lifting does. Releasing on slide-off is right for a face button and wrong here, since
 *   sliding off is the whole point.
 * - **A press finger may take over the stick** if no other finger already has it, and keeps holding
 *   the press while it does.
 */
private class StickView(
    context: Context,
    private val engine: InputEngine,
    var profile: AnalogProfile,
    private val right: Boolean = false,
) : View(context) {

    private val plate = Ink.plate()
    private val plateRim = Ink.plateRim()
    private val knob = Ink.body().apply { color = Color.argb(215, 132, 139, 150) }
    private val body = Ink.body()
    private val glow = Ink.active()
    private val rim = Ink.rim()
    private val text = Ink.text()
    private val textEdge = Ink.textEdge()

    private var x = 0f
    private var y = 0f

    /**
     * Which finger owns the stick, and which fingers are holding the press.
     *
     * A stick is one thumb. Reading whichever pointer happens to be first in the event — which is
     * what `event.x` does — let a second finger landing anywhere in the window yank the stick to
     * itself, so a thumb on the stick and a thumb on a button fought each other.
     */
    private var pointer = -1
    private val pressing = mutableSetOf<Int>()

    /** Whether the touch that started this gesture landed on something of ours. */
    private var owned = false

    private val pressCode: Int get() = if (right) 318 else 317
    private val pressLabel: String get() = if (right) "R3" else "L3"

    /** Room left for the outline, which is stroked centred on the edge and would be half clipped. */
    private val inset: Float get() = height * 0.03f
    private val outerRadius: Float get() = height / 2f - inset
    private val knobRadius: Float get() = outerRadius * 0.42f

    /** The plate hugs the outer edge of the screen; the strip left over holds the press. */
    private val plateX: Float get() = if (right) width - height / 2f else height / 2f
    private val plateY: Float get() = height / 2f

    private val stripWidth: Float get() = width - height.toFloat()

    /**
     * The press is bounded by the strip it sits in **and** by the stick it belongs to.
     *
     * Without the second bound it grows with the strip and ends up larger than a face button, which
     * reads as the most important control on that side of the screen. It is not.
     */
    private val pressRadius: Float get() = min(stripWidth * 0.42f, height * 0.17f)
    private val pressX: Float get() = if (right) stripWidth / 2f else width - stripWidth / 2f
    private val pressY: Float get() = height / 2f

    /**
     * How far the knob's **centre** may travel.
     *
     * The first version moved the centre the full radius, so at full deflection half the knob hung
     * outside the window and was clipped — visible as a thumb sliced flat against the edge. A knob
     * that is drawn cannot be allowed further out than its own radius from the edge.
     */
    private val travel: Float get() = outerRadius - knobRadius

    private fun send(dx: Double, dy: Double) {
        if (right) engine.rightStick(dx, dy, profile) else engine.stick(dx, dy, profile)
    }

    override fun onDraw(canvas: Canvas) {
        val r = outerRadius
        if (r <= 0f) return
        plateRim.strokeWidth = max(2f, r * 0.030f)
        rim.strokeWidth = max(2f, r * 0.045f)

        canvas.drawCircle(plateX, plateY, r, plate)
        canvas.drawCircle(plateX, plateY, r, plateRim)
        val kx = plateX + x * travel
        val ky = plateY + y * travel
        // Lit while a thumb is on it, the same as every other control. A stick was the one thing on
        // the overlay that gave no sign it had been touched, so a thumb resting at centre looked
        // identical to a thumb that had missed it.
        canvas.drawCircle(kx, ky, knobRadius, if (pointer >= 0) glow else knob)
        canvas.drawCircle(kx, ky, knobRadius, rim)

        val pr = pressRadius
        if (pr > 0f) {
            canvas.drawCircle(pressX, pressY, pr, body)
            if (pressing.isNotEmpty()) canvas.drawCircle(pressX, pressY, pr, glow)
            canvas.drawCircle(pressX, pressY, pr, rim)
            val size = pr * 0.78f
            Ink.label(canvas, pressLabel, pressX, pressY + size / 3f, size, text, textEdge)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                owned = claim(event, event.actionIndex)
                return owned
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!owned) return false
                claim(event, event.actionIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!owned) return false
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id == pointer) {
                        aim(event, i)
                    } else if (pointer == -1 && id in pressing && onPlate(event.getX(i), event.getY(i))) {
                        // The finger holding the press has arrived on the stick. It drives the
                        // stick from here and goes on holding the press until it lifts.
                        pointer = id
                        aim(event, i)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!owned) return false
                val id = event.getPointerId(event.actionIndex)
                if (id == pointer) releaseStick()
                if (pressing.remove(id) && pressing.isEmpty()) {
                    engine.button(pressCode, false)
                    invalidate()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) owned = false
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                owned = false
            }
        }
        return true
    }

    /** Returns whether this touch landed on the stick or the press; anything else is not ours. */
    private fun claim(event: MotionEvent, index: Int): Boolean {
        val px = event.getX(index)
        val py = event.getY(index)
        val id = event.getPointerId(index)
        if (onPress(px, py)) {
            if (pressing.isEmpty()) engine.button(pressCode, true)
            pressing += id
            invalidate()
            return true
        }
        if (pointer == -1 && onPlate(px, py)) {
            pointer = id
            aim(event, index)
            return true
        }
        return false
    }

    private fun onPress(px: Float, py: Float): Boolean =
        pressRadius > 0f && hypot(px - pressX, py - pressY) <= pressRadius * 1.15f

    private fun onPlate(px: Float, py: Float): Boolean =
        hypot(px - plateX, py - plateY) <= outerRadius

    private fun aim(event: MotionEvent, index: Int) {
        val dx = (event.getX(index) - plateX) / travel
        val dy = (event.getY(index) - plateY) / travel

        // Clamped as a circle, not per axis. Clamping each axis to ±1 separately lets a diagonal
        // reach 1.41 from centre, which is both outside the ring the user can see and a deflection
        // a real stick cannot produce.
        val magnitude = hypot(dx, dy)
        if (magnitude > 1f) {
            x = dx / magnitude
            y = dy / magnitude
        } else {
            x = dx
            y = dy
        }
        send(x.toDouble(), y.toDouble())
        invalidate()
    }

    private fun releaseStick() {
        pointer = -1
        x = 0f
        y = 0f
        send(0.0, 0.0)
        invalidate()
    }

    fun releaseAll() {
        releaseStick()
        if (pressing.isNotEmpty()) {
            pressing.clear()
            engine.button(pressCode, false)
        }
        invalidate()
    }

    companion object {
        /**
         * How much wider than tall the window is.
         *
         * The extra is the strip the press sits in. It has to be wide enough for a thumb and narrow
         * enough that the window still covers little more than the controls in it.
         */
        const val WIDTH = 1.42f
    }
}

/**
 * The d-pad, drawn as one cross on a plate and read as a direction rather than as four buttons.
 *
 * Four circles was wrong in two ways at once. It looked like four buttons that happened to be
 * arranged in a diamond, which is not what a d-pad is; and it could only ever report the one circle
 * a finger landed in, so a thumb rolling from up into the corner produced up, then nothing, then
 * right — never up-and-right. **A cross with a direction read from the thumb's position** fixes
 * both: the shape says what it is, and the eight-way read means a diagonal is a place on the pad
 * rather than a pair of presses to be timed.
 */
private class DpadView(context: Context, private val engine: InputEngine) : View(context) {

    private val plate = Ink.plate()
    private val plateRim = Ink.plateRim()
    private val body = Ink.body()
    private val rim = Ink.rim()
    private val glow = Ink.active()
    private val arrow = Paint().apply { color = Color.argb(225, 16, 18, 23); isAntiAlias = true }
    private val hub = Paint().apply { color = Color.argb(70, 10, 12, 17); isAntiAlias = true }

    private val cross = Path()
    private val arrows = listOf(Path(), Path(), Path(), Path())
    private var plateRadius = 0f
    private var armLength = 0f
    private var armHalf = 0f

    private var hatX = 0
    private var hatY = 0
    private var pointer = -1

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        plateRadius = min(w, h) / 2f - min(w, h) * 0.03f
        armLength = plateRadius * 0.88f
        armHalf = armLength * 0.33f
        val l = armLength
        val a = armHalf

        cross.reset()
        cross.moveTo(cx - a, cy - l)
        cross.lineTo(cx + a, cy - l)
        cross.lineTo(cx + a, cy - a)
        cross.lineTo(cx + l, cy - a)
        cross.lineTo(cx + l, cy + a)
        cross.lineTo(cx + a, cy + a)
        cross.lineTo(cx + a, cy + l)
        cross.lineTo(cx - a, cy + l)
        cross.lineTo(cx - a, cy + a)
        cross.lineTo(cx - l, cy + a)
        cross.lineTo(cx - l, cy - a)
        cross.lineTo(cx - a, cy - a)
        cross.close()

        // Rounded corners on the whole outline at once, so the fill and the stroke agree. Rounding
        // each arm separately would leave seams where the arms meet.
        val corner = CornerPathEffect(a * 0.5f)
        body.pathEffect = corner
        rim.pathEffect = corner
        rim.strokeWidth = max(2f, armLength * 0.05f)
        plateRim.strokeWidth = max(2f, plateRadius * 0.030f)

        val reach = (l + a) / 2f
        val size = a * 0.52f
        listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f).forEachIndexed { i, (ux, uy) ->
            val px = -uy
            val py = ux
            val ox = cx + ux * reach
            val oy = cy + uy * reach
            arrows[i].reset()
            arrows[i].moveTo(ox + ux * size, oy + uy * size)
            arrows[i].lineTo(ox - ux * size * 0.55f + px * size * 0.85f, oy - uy * size * 0.55f + py * size * 0.85f)
            arrows[i].lineTo(ox - ux * size * 0.55f - px * size * 0.85f, oy - uy * size * 0.55f - py * size * 0.85f)
            arrows[i].close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(width / 2f, height / 2f, plateRadius, plate)
        canvas.drawCircle(width / 2f, height / 2f, plateRadius, plateRim)
        canvas.drawPath(cross, body)

        if (hatX != 0 || hatY != 0) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.save()
            // Clipped to the cross, so a highlight can be a plain rectangle and still land exactly
            // on the arm it belongs to — including both arms of a diagonal.
            canvas.clipPath(cross)
            if (hatY < 0) canvas.drawRect(cx - armLength, cy - armLength, cx + armLength, cy - armHalf, glow)
            if (hatY > 0) canvas.drawRect(cx - armLength, cy + armHalf, cx + armLength, cy + armLength, glow)
            if (hatX < 0) canvas.drawRect(cx - armLength, cy - armLength, cx - armHalf, cy + armLength, glow)
            if (hatX > 0) canvas.drawRect(cx + armHalf, cy - armLength, cx + armLength, cy + armLength, glow)
            canvas.restore()
        }

        canvas.drawPath(cross, rim)
        canvas.drawCircle(width / 2f, height / 2f, armHalf * 0.45f, hub)
        arrows.forEach { canvas.drawPath(it, arrow) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val onPlate = hypot(event.getX(i) - width / 2f, event.getY(i) - height / 2f) <=
                    plateRadius
                if (event.actionMasked == MotionEvent.ACTION_DOWN && !onPlate) return false
                // First finger down owns the pad until it lifts. A second finger used to take it
                // over, so a stray palm or a second thumb cancelled the direction being held.
                if (pointer == -1 && onPlate) {
                    pointer = event.getPointerId(i)
                    aim(event, i)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointer != -1) {
                    val i = event.findPointerIndex(pointer)
                    if (i >= 0) aim(event, i)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) release()
            }

            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    /** Eight directions from where the thumb is, read fresh on every move. */
    private fun aim(event: MotionEvent, index: Int) {
        val dx = event.getX(index) - width / 2f
        val dy = event.getY(index) - height / 2f
        val magnitude = hypot(dx, dy)
        var nx = 0
        var ny = 0
        if (magnitude > armLength * CENTRE) {
            val ux = dx / magnitude
            val uy = dy / magnitude
            if (ux > SECTOR) nx = 1 else if (ux < -SECTOR) nx = -1
            if (uy > SECTOR) ny = 1 else if (uy < -SECTOR) ny = -1
        }
        send(nx, ny)
    }

    private fun send(nx: Int, ny: Int) {
        if (nx == hatX && ny == hatY) return
        hatX = nx
        hatY = ny
        engine.hat(hatX, hatY)
        invalidate()
    }

    fun release() {
        pointer = -1
        send(0, 0)
        invalidate()
    }

    private companion object {
        /** Inside this fraction of an arm, the thumb is on the hub and no direction is held. */
        const val CENTRE = 0.20f

        /**
         * Where a cardinal ends and a diagonal begins.
         *
         * `sin(22.5°) ≈ 0.383` would make all eight sectors equal. Slightly above it gives the four
         * cardinals about 50° each and the diagonals about 40°, because a thumb aiming for "up"
         * misses more often than a thumb aiming for a corner it can feel it is reaching for.
         */
        const val SECTOR = 0.42f
    }
}

/**
 * A cluster of controls that are not sticks — face buttons, shoulders, triggers, menu buttons.
 *
 * One class rather than four, because the difference between them is where they sit and what they
 * send, not how they behave. Each control says what to do when it is pressed and released, so a
 * trigger sending an analog value and a button sending a key code are the same thing here.
 *
 * Three behaviours are shared by all of them and matter more than the drawing.
 *
 * **Nothing overlaps and nothing is clipped.** The first version picked a radius from a fixed
 * divisor and then placed each control a full half-window from centre, which put the outer edge of
 * every control exactly on the window boundary — so half of each outline was cut off — and left the
 * face buttons overlapping each other by a third of their width. The radius is now **solved for**:
 * the largest one at which no two controls come within a gap of each other and every control, with
 * its outline, still fits inside the window.
 *
 * **Every finger is read on every event, not only when it lands.** A press used to be decided once,
 * at touch-down, and never revisited — so sliding from one button into its neighbour kept the first
 * one held and never pressed the second. Now the set of controls under the fingers is recomputed on
 * every move and the difference is applied.
 *
 * **A trigger is a ramp, not a switch.** L2 and R2 sent 0 or 1 with nothing between, which is not
 * what those controls are on a pad. Holding one now raises its value over about half a second and
 * releasing drains it in about a third, slow enough to feel; the button fills from the bottom and a
 * ring closes around its edge, so the level is visible on the control itself.
 */
private class PadView(
    context: Context,
    private val controls: List<Control>,
    private val plate: Boolean = false,
) : View(context) {

    class Control(
        val label: String,
        /** Position within the window, from -1 to 1 on each axis. */
        val dx: Float,
        val dy: Float,
        /** Whether this control ramps between 0 and 1 instead of switching. */
        val analog: Boolean = false,
        val onDown: () -> Unit = {},
        val onUp: () -> Unit = {},
        val onLevel: (Double) -> Unit = {},
    )

    private val plateFill = Ink.plate()
    private val plateRim = Ink.plateRim()
    private val body = Ink.body()
    private val glow = Ink.active()
    private val ring = Ink.active().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rim = Ink.rim()
    private val text = Ink.text()
    private val textEdge = Ink.textEdge()
    private val arc = RectF()

    /** Whether a finger is on each control, and how far an analog one has travelled. */
    private val engaged = BooleanArray(controls.size)
    private val level = FloatArray(controls.size)
    private var ramping = false

    private var radius = 0f
    private var spreadX = 0f
    private var spreadY = 0f
    private var plateRadius = 0f

    /** Whether the touch that started this gesture landed on a control of ours. */
    private var owned = false

    private val ramp = object : Runnable {
        override fun run() {
            var busy = false
            controls.forEachIndexed { i, c ->
                if (!c.analog) return@forEachIndexed
                val target = if (engaged[i]) 1f else 0f
                if (level[i] == target) return@forEachIndexed
                level[i] = if (engaged[i]) {
                    min(target, level[i] + RISE)
                } else {
                    max(target, level[i] - FALL)
                }
                c.onLevel(level[i].toDouble())
                busy = true
            }
            invalidate()
            if (busy) postOnAnimation(this) else ramping = false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = min(w, h) * 0.04f
        plateRadius = min(w, h) / 2f - min(w, h) * 0.03f

        // Solved rather than assumed: the largest radius that keeps every control inside the window
        // and every pair of controls a visible gap apart. Twenty-four halvings settle it to well
        // under a pixel, which is as exact as a drawn edge can be.
        var low = 1f
        var high = min(w, h) / 2f - pad
        repeat(24) {
            val mid = (low + high) / 2f
            if (fits(mid, w, h, pad)) low = mid else high = mid
        }
        radius = low
        spreadX = w / 2f - radius - pad
        spreadY = h / 2f - radius - pad
    }

    private fun fits(r: Float, w: Int, h: Int, pad: Float): Boolean {
        val sx = w / 2f - r - pad
        val sy = h / 2f - r - pad
        if (sx < 0f || sy < 0f) return false
        for (i in controls.indices) {
            for (j in i + 1 until controls.size) {
                val dx = (controls[i].dx - controls[j].dx) * sx
                val dy = (controls[i].dy - controls[j].dy) * sy
                if (hypot(dx, dy) < 2f * r + r * GAP) return false
            }
        }
        return true
    }

    private fun centreX(c: Control): Float = width / 2f + c.dx * spreadX

    private fun centreY(c: Control): Float = height / 2f + c.dy * spreadY

    override fun onDraw(canvas: Canvas) {
        if (radius <= 0f) return
        val r = radius
        rim.strokeWidth = max(2f, r * 0.09f)
        plateRim.strokeWidth = max(2f, plateRadius * 0.030f)
        ring.strokeWidth = max(3f, r * 0.14f)

        if (plate) {
            canvas.drawCircle(width / 2f, height / 2f, plateRadius, plateFill)
            canvas.drawCircle(width / 2f, height / 2f, plateRadius, plateRim)
        }

        controls.forEachIndexed { i, c ->
            val cx = centreX(c)
            val cy = centreY(c)
            canvas.drawCircle(cx, cy, r, body)
            if (c.analog) {
                if (level[i] > 0f) {
                    canvas.save()
                    // Fills from the bottom of the button upward, in proportion to the value being
                    // sent — the same number the target receives, shown where the thumb is.
                    canvas.clipRect(cx - r, cy + r - 2f * r * level[i], cx + r, cy + r)
                    canvas.drawCircle(cx, cy, r, glow)
                    canvas.restore()
                    // And again as a ring closing clockwise, because a fill inside a small circle
                    // under a thumb is exactly the part of the control the thumb is covering. Drawn
                    // just inside the edge rather than around it: outside, the ring would reach past
                    // the radius the window was solved for and be clipped at full travel.
                    val ringRadius = r - ring.strokeWidth * 0.6f
                    arc.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
                    canvas.drawArc(arc, -90f, 360f * level[i], false, ring)
                }
            } else if (engaged[i]) {
                canvas.drawCircle(cx, cy, r, glow)
            }
            canvas.drawCircle(cx, cy, r, rim)
            val size = r * when {
                c.label.length > 2 -> 0.66f
                c.label.length > 1 -> 0.78f
                else -> 0.86f
            }
            Ink.label(canvas, c.label, cx, cy + size / 3f, size, text, textEdge)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A cluster window is a rectangle and its controls are not. A touch in the space
                // between them is refused rather than swallowed, so it has the chance to reach
                // whatever is underneath instead of becoming a dead patch of screen.
                owned = indexAt(event.x, event.y) >= 0
                if (!owned) return false
                sync(event, lifted = -1)
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                if (!owned) return false
                sync(event, lifted = -1)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!owned) return false
                sync(event, lifted = event.getPointerId(event.actionIndex))
                if (event.actionMasked == MotionEvent.ACTION_UP) owned = false
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                owned = false
            }
        }
        return true
    }

    /** Whatever the fingers are on now becomes what is held now. */
    private fun sync(event: MotionEvent, lifted: Int) {
        val now = BooleanArray(controls.size)
        for (i in 0 until event.pointerCount) {
            if (event.getPointerId(i) == lifted) continue
            val hit = indexAt(event.getX(i), event.getY(i))
            if (hit >= 0) now[hit] = true
        }
        apply(now)
    }

    private fun apply(now: BooleanArray) {
        var needsRamp = false
        controls.forEachIndexed { i, c ->
            if (now[i] == engaged[i]) {
                if (c.analog && level[i] != (if (engaged[i]) 1f else 0f)) needsRamp = true
                return@forEachIndexed
            }
            engaged[i] = now[i]
            if (c.analog) needsRamp = true else if (now[i]) c.onDown() else c.onUp()
        }
        if (needsRamp && !ramping) {
            ramping = true
            postOnAnimation(ramp)
        }
        invalidate()
    }

    fun releaseAll() {
        controls.forEachIndexed { i, c ->
            engaged[i] = false
            if (c.analog) {
                // Not drained — dropped. Nothing is going to run the ramp once the window is gone,
                // and a trigger left part-pressed is a trigger nobody can release.
                if (level[i] != 0f) {
                    level[i] = 0f
                    c.onLevel(0.0)
                }
            } else {
                c.onUp()
            }
        }
        invalidate()
    }

    private fun indexAt(px: Float, py: Float): Int {
        controls.forEachIndexed { i, c ->
            if (hypot(px - centreX(c), py - centreY(c)) <= radius * REACH) return i
        }
        return -1
    }

    companion object {

        /**
         * How fast a trigger travels, per frame at about sixty a second.
         *
         * Full press in roughly half a second, full release in roughly a third. The first attempt
         * used 0.2 s and 0.13 s, which is a ramp on paper and a switch in the hand — the reference
         * device could not feel the difference and the fill was gone before it could be read.
         */
        const val RISE = 0.030f
        const val FALL = 0.055f

        /** A little past the drawn edge, because a thumb's centre is not where it looks. */
        const val REACH = 1.15f

        /** Clear space between two controls, as a fraction of their radius. */
        const val GAP = 0.34f

        fun face(engine: InputEngine): List<Control> = listOf(
            Control("Y", 0f, -1f, onDown = { engine.button(308, true) }, onUp = { engine.button(308, false) }),
            Control("X", -1f, 0f, onDown = { engine.button(307, true) }, onUp = { engine.button(307, false) }),
            Control("B", 1f, 0f, onDown = { engine.button(305, true) }, onUp = { engine.button(305, false) }),
            Control("A", 0f, 1f, onDown = { engine.button(304, true) }, onUp = { engine.button(304, false) }),
        )

        /**
         * The left shoulder cluster, with Select beneath the bumper.
         *
         * Select and Start used to share a strip across the bottom of the screen with L3 and R3.
         * Four controls in one narrow row made every one of them the smallest thing on screen, and
         * put two of them where a thumb has no reason to be. Under the bumpers there is room, and
         * the two that left — the stick presses — went where they belong, on the sticks.
         */
        fun leftCluster(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("L1", -1f, -1f, onDown = { engine.button(310, true) }, onUp = { engine.button(310, false) }),
            // L2 is analog on a real pad, so it ramps rather than switching. A target that reads the
            // axis sees every value on the way; one that reads the button still sees the key the
            // platform derives once the axis crosses its threshold.
            Control("L2", 1f, -1f, analog = true, onLevel = { engine.trigger(it, profile, right = false) }),
            Control("SEL", -1f, 1f, onDown = { engine.button(314, true) }, onUp = { engine.button(314, false) }),
        )

        fun rightCluster(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("R2", -1f, -1f, analog = true, onLevel = { engine.trigger(it, profile, right = true) }),
            Control("R1", 1f, -1f, onDown = { engine.button(311, true) }, onUp = { engine.button(311, false) }),
            Control("STA", 1f, 1f, onDown = { engine.button(315, true) }, onUp = { engine.button(315, false) }),
        )
    }
}
