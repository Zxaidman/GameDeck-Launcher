package io.github.zxaidman.kestrel.platform.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
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
    private var menu: PadView? = null
    private var toggle: ToggleView? = null
    private var controlsVisible = false

    private val everyView: List<View?>
        get() = listOf(stick, rightStick, buttons, dpad, leftShoulders, rightShoulders, menu)

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
     * Resizes the controls without taking them away and putting them back.
     *
     * Removing and re-adding the windows would drop any control being held at that moment, so a
     * size change during play would leave a button stuck down. Existing windows are re-measured
     * instead.
     */
    public fun resize(scale: Float) {
        this.scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (!controlsVisible) return
        // Simplest correct approach: lay them out again from the new size. Nothing is removed, so
        // no control being held is dropped.
        val big = clusterSize()
        val small = (clusterSize() * 0.62f).toInt()
        val strip = (clusterSize() * 0.55f).toInt()
        val margin = marginSize()
        val above = margin + big + margin / 2

        fun move(view: View?, w: Int, h: Int, gravity: Int, x: Int, y: Int) {
            view ?: return
            runCatching { windows?.updateViewLayout(view, params(w, h, gravity, x, y)) }
        }

        move(stick, big, big, Gravity.BOTTOM or Gravity.START, margin, margin)
        move(buttons, big, big, Gravity.BOTTOM or Gravity.END, margin, margin)
        move(dpad, small, small, Gravity.BOTTOM or Gravity.START, margin, above)
        move(rightStick, small, small, Gravity.BOTTOM or Gravity.END, margin, above)
        move(leftShoulders, strip, strip / 2, Gravity.TOP or Gravity.START, margin, margin)
        move(rightShoulders, strip, strip / 2, Gravity.TOP or Gravity.END, margin, margin)
        move(menu, strip, strip / 3, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, margin)
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
     * shoulders along the top edge where index fingers reach, menu buttons in the middle. Each
     * cluster is a separate window, so none of them covers anything it does not need to.
     */
    private fun showControls() {
        if (controlsVisible) return
        val big = clusterSize()
        val small = (clusterSize() * 0.62f).toInt()
        val strip = (clusterSize() * 0.55f).toInt()
        val margin = marginSize()
        val above = margin + big + margin / 2

        val added = mutableListOf<View>()
        fun place(view: View, w: Int, h: Int, gravity: Int, x: Int, y: Int): Boolean =
            runCatching {
                windows?.addView(view, params(w, h, gravity, x, y))
                added += view
                true
            }.getOrElse { false }

        val stickView = StickView(context, engine, profile, right = false)
        val rightStickView = StickView(context, engine, profile, right = true)
        val buttonsView = PadView(context, PadView.face(engine))
        val dpadView = DpadView(context, engine)
        val leftShoulderView = PadView(context, PadView.leftShoulders(engine, profile))
        val rightShoulderView = PadView(context, PadView.rightShoulders(engine, profile))
        val menuView = PadView(context, PadView.menu(engine))

        val ok = place(stickView, big, big, Gravity.BOTTOM or Gravity.START, margin, margin) &&
            place(buttonsView, big, big, Gravity.BOTTOM or Gravity.END, margin, margin) &&
            place(dpadView, small, small, Gravity.BOTTOM or Gravity.START, margin, above) &&
            place(rightStickView, small, small, Gravity.BOTTOM or Gravity.END, margin, above) &&
            place(leftShoulderView, strip, strip / 2, Gravity.TOP or Gravity.START, margin, margin) &&
            place(rightShoulderView, strip, strip / 2, Gravity.TOP or Gravity.END, margin, margin) &&
            place(menuView, strip, strip / 3, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, margin)

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
        menu = menuView
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
        leftShoulders?.releaseAll()
        rightShoulders?.releaseAll()
        menu?.releaseAll()

        everyView.filterNotNull().forEach { runCatching { windows?.removeView(it) } }
        stick = null
        rightStick = null
        buttons = null
        dpad = null
        leftShoulders = null
        rightShoulders = null
        menu = null
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
        // holding the stick froze every other control and the phone underneath with it.
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
 * One palette for every control, and the reason it is dark-edged.
 *
 * The controls were pale shapes with pale labels, which is legible over a dark game and invisible
 * over a white screen — a menu, a browser, a settings page. Translucency alone cannot solve that:
 * anything light enough to sit over black disappears over white. **Every shape therefore carries a
 * dark outline and every label is drawn twice**, dark stroke first, light fill on top, so the
 * control is defined by its edge rather than by its fill and reads on any background.
 */
private object Ink {

    fun body(): Paint = Paint().apply {
        color = Color.argb(105, 244, 247, 252)
        isAntiAlias = true
    }

    fun edge(): Paint = Paint().apply {
        color = Color.argb(225, 10, 12, 17)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    /** What a held control looks like. Distinct enough to be seen at a glance mid-play. */
    fun active(): Paint = Paint().apply {
        color = Color.argb(185, 116, 196, 255)
        isAntiAlias = true
    }

    fun text(): Paint = Paint().apply {
        color = Color.argb(240, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    fun textEdge(): Paint = Paint().apply {
        color = Color.argb(235, 8, 10, 14)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    /** A label that survives both a white menu and a black game. */
    fun label(canvas: Canvas, s: String, cx: Float, cy: Float, size: Float, fill: Paint, edge: Paint) {
        fill.textSize = size
        edge.textSize = size
        edge.strokeWidth = max(2f, size * 0.18f)
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

/** The stick, in a window the size of the stick. */
private class StickView(
    context: Context,
    private val engine: InputEngine,
    var profile: AnalogProfile,
    private val right: Boolean = false,
) : View(context) {

    private val ring = Paint().apply { color = Color.argb(70, 244, 247, 252); isAntiAlias = true }
    private val knob = Ink.body().apply { color = Color.argb(165, 244, 247, 252) }
    private val edge = Ink.edge()

    private var x = 0f
    private var y = 0f

    /**
     * Which finger owns the stick.
     *
     * A stick is one thumb. Reading whichever pointer happens to be first in the event — which is
     * what `event.x` does — let a second finger landing anywhere in the window yank the stick to
     * itself, so a thumb on the stick and a thumb on a button fought each other.
     */
    private var pointer = -1

    private fun send(dx: Double, dy: Double) {
        if (right) engine.rightStick(dx, dy, profile) else engine.stick(dx, dy, profile)
    }

    private val outerRadius: Float get() = min(width, height) / 2f * 0.95f
    private val knobRadius: Float get() = min(width, height) / 2f * 0.32f

    /**
     * How far the knob's **centre** may travel.
     *
     * The first version moved the centre the full radius, so at full deflection half the knob hung
     * outside the window and was clipped — visible as a thumb sliced flat against the edge. A knob
     * that is drawn cannot be allowed further out than its own radius from the edge.
     */
    private val travel: Float get() = outerRadius - knobRadius

    override fun onDraw(canvas: Canvas) {
        edge.strokeWidth = max(3f, outerRadius * 0.055f)
        canvas.drawCircle(width / 2f, height / 2f, outerRadius, ring)
        canvas.drawCircle(width / 2f, height / 2f, outerRadius, edge)
        val kx = width / 2f + x * travel
        val ky = height / 2f + y * travel
        canvas.drawCircle(kx, ky, knobRadius, knob)
        canvas.drawCircle(kx, ky, knobRadius, edge)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointer == -1) {
                    pointer = event.getPointerId(event.actionIndex)
                    aim(event, event.actionIndex)
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

    private fun aim(event: MotionEvent, index: Int) {
        val dx = (event.getX(index) - width / 2f) / travel
        val dy = (event.getY(index) - height / 2f) / travel

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

    private fun release() {
        pointer = -1
        x = 0f
        y = 0f
        send(0.0, 0.0)
        invalidate()
    }
}

/**
 * The d-pad, drawn as one cross and read as a direction rather than as four separate buttons.
 *
 * Four circles was wrong in two ways at once. It looked like four buttons that happened to be
 * arranged in a diamond, which is not what a d-pad is; and it could only ever report the one circle
 * a finger landed in, so a thumb rolling from up into the corner produced up, then nothing, then
 * right — never up-and-right. **A cross with a direction read from the thumb's position** fixes
 * both: the shape says what it is, and the eight-way read means a diagonal is a place on the pad
 * rather than a pair of presses to be timed.
 */
private class DpadView(context: Context, private val engine: InputEngine) : View(context) {

    private val body = Ink.body()
    private val edge = Ink.edge()
    private val glow = Ink.active()
    private val arrow = Paint().apply { color = Color.argb(215, 12, 14, 19); isAntiAlias = true }
    private val hub = Paint().apply { color = Color.argb(60, 10, 12, 17); isAntiAlias = true }

    private val cross = Path()
    private val arrows = listOf(Path(), Path(), Path(), Path())
    private var armLength = 0f
    private var armHalf = 0f

    private var hatX = 0
    private var hatY = 0
    private var pointer = -1

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        armLength = min(w, h) / 2f * 0.94f
        armHalf = armLength * 0.34f
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
        edge.pathEffect = corner
        edge.strokeWidth = max(3f, armLength * 0.055f)

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

        canvas.drawPath(cross, edge)
        canvas.drawCircle(width / 2f, height / 2f, armHalf * 0.45f, hub)
        arrows.forEach { canvas.drawPath(it, arrow) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // First finger down owns the pad until it lifts. A second finger used to take it
                // over, so a stray palm or a second thumb cancelled the direction being held.
                if (pointer == -1) {
                    pointer = event.getPointerId(event.actionIndex)
                    aim(event, event.actionIndex)
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
 * Two behaviours are shared by all of them and matter more than the drawing.
 *
 * **Every finger is read on every event, not only when it lands.** A press used to be decided once,
 * at touch-down, and never revisited — so sliding from one button into its neighbour kept the first
 * one held and never pressed the second. Now the set of controls under the fingers is recomputed on
 * every move and the difference is applied, which is what makes a thumb rolling across two face
 * buttons press both.
 *
 * **A trigger is a ramp, not a switch.** L2 and R2 sent 0 or 1 with nothing between, which is not
 * what those controls are on a pad. Holding one now raises its value over about a fifth of a second
 * and releasing drains it slightly faster, and the button fills as it goes so the level is visible
 * on the control itself rather than only inside the game.
 */
private class PadView(context: Context, private val controls: List<Control>) : View(context) {

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

    private val body = Ink.body()
    private val glow = Ink.active()
    private val edge = Ink.edge()
    private val text = Ink.text()
    private val textEdge = Ink.textEdge()

    /** Whether a finger is on each control, and how far an analog one has travelled. */
    private val engaged = BooleanArray(controls.size)
    private val level = FloatArray(controls.size)
    private var ramping = false

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

    private val radius: Float
        get() = min(width, height) / (if (controls.size > 3) 4.2f else 3.2f)

    private fun centreX(c: Control): Float = width / 2f + c.dx * (width / 2f - radius)

    private fun centreY(c: Control): Float = height / 2f + c.dy * (height / 2f - radius)

    override fun onDraw(canvas: Canvas) {
        val r = radius
        edge.strokeWidth = max(3f, r * 0.10f)
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
                }
            } else if (engaged[i]) {
                canvas.drawCircle(cx, cy, r, glow)
            }
            canvas.drawCircle(cx, cy, r, edge)
            val size = r * when {
                c.label.length > 2 -> 0.44f
                c.label.length > 1 -> 0.60f
                else -> 0.80f
            }
            Ink.label(canvas, c.label, cx, cy + size / 3f, size, text, textEdge)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> sync(event, lifted = -1)

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                sync(event, lifted = event.getPointerId(event.actionIndex))

            MotionEvent.ACTION_CANCEL -> releaseAll()
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
        val r = radius
        controls.forEachIndexed { i, c ->
            if (hypot(px - centreX(c), py - centreY(c)) <= r * REACH) return i
        }
        return -1
    }

    companion object {

        /**
         * How fast a trigger travels, per frame at about sixty a second.
         *
         * Full press in roughly 0.2 s and full release in roughly 0.13 s. Release is quicker on
         * purpose: a control that lingers after the thumb has gone feels broken, while one that
         * takes a moment to reach full feels like a trigger.
         */
        const val RISE = 0.08f
        const val FALL = 0.13f

        /** A little past the drawn edge, because a thumb's centre is not where it looks. */
        const val REACH = 1.2f

        fun face(engine: InputEngine): List<Control> = listOf(
            Control("Y", 0f, -1f, onDown = { engine.button(308, true) }, onUp = { engine.button(308, false) }),
            Control("X", -1f, 0f, onDown = { engine.button(307, true) }, onUp = { engine.button(307, false) }),
            Control("B", 1f, 0f, onDown = { engine.button(305, true) }, onUp = { engine.button(305, false) }),
            Control("A", 0f, 1f, onDown = { engine.button(304, true) }, onUp = { engine.button(304, false) }),
        )

        fun leftShoulders(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("L1", -1f, 0f, onDown = { engine.button(310, true) }, onUp = { engine.button(310, false) }),
            // L2 is analog on a real pad, so it ramps rather than switching. A target that reads the
            // axis sees every value on the way; one that reads the button still sees the key the
            // platform derives once the axis crosses its threshold.
            Control("L2", 1f, 0f, analog = true, onLevel = { engine.trigger(it, profile, right = false) }),
        )

        fun rightShoulders(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("R2", -1f, 0f, analog = true, onLevel = { engine.trigger(it, profile, right = true) }),
            Control("R1", 1f, 0f, onDown = { engine.button(311, true) }, onUp = { engine.button(311, false) }),
        )

        fun menu(engine: InputEngine): List<Control> = listOf(
            Control("SEL", -1f, 0f, onDown = { engine.button(314, true) }, onUp = { engine.button(314, false) }),
            Control("L3", -0.33f, 0f, onDown = { engine.button(317, true) }, onUp = { engine.button(317, false) }),
            Control("R3", 0.33f, 0f, onDown = { engine.button(318, true) }, onUp = { engine.button(318, false) }),
            Control("STA", 1f, 0f, onDown = { engine.button(315, true) }, onUp = { engine.button(315, false) }),
        )
    }
}
