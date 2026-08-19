package io.github.zxaidman.kestrel.platform.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.min

/**
 * The controls, drawn over whatever the user is playing, in windows that never take focus.
 *
 * Two rules shape everything here, and the second was learned the hard way.
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
    private var buttons: ButtonsView? = null
    private var dpad: PadView? = null
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
        val buttonsView = ButtonsView(context, engine)
        val dpadView = PadView(context, PadView.dpad(engine))
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
        engine.hat(0, 0)
        engine.trigger(0.0, profile, right = false)
        engine.trigger(0.0, profile, right = true)
        buttons?.releaseAll()
        dpad?.releaseAll()
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
        // everything outside these small windows reach whatever is underneath.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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

/** The always-present way to make the controls appear and disappear. */
private class ToggleView(context: Context, private val onTap: () -> Unit) : View(context) {

    private val body = Paint().apply { color = Color.argb(130, 20, 20, 20); isAntiAlias = true }
    private val mark = Paint().apply {
        color = Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        mark.textSize = r
        canvas.drawCircle(width / 2f, height / 2f, r * 0.9f, body)
        canvas.drawText("K", width / 2f, height / 2f + r / 3f, mark)
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

    private val ring = Paint().apply { color = Color.argb(60, 255, 255, 255); isAntiAlias = true }
    private val knob = Paint().apply { color = Color.argb(150, 255, 255, 255); isAntiAlias = true }

    private var x = 0f
    private var y = 0f

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
        canvas.drawCircle(width / 2f, height / 2f, outerRadius, ring)
        canvas.drawCircle(
            width / 2f + x * travel,
            height / 2f + y * travel,
            knobRadius,
            knob,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - width / 2f) / travel
                val dy = (event.y - height / 2f) / travel

                // Clamped as a circle, not per axis. Clamping each axis to ±1 separately lets a
                // diagonal reach 1.41 from centre, which is both outside the ring the user can see
                // and a deflection a real stick cannot produce.
                val magnitude = hypot(dx, dy)
                if (magnitude > 1f) {
                    x = dx / magnitude
                    y = dy / magnitude
                } else {
                    x = dx
                    y = dy
                }
                send(x.toDouble(), y.toDouble())
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                x = 0f
                y = 0f
                send(0.0, 0.0)
            }
        }
        invalidate()
        return true
    }
}

/**
 * A cluster of controls that are not sticks — d-pad, shoulders, triggers, menu buttons.
 *
 * One class rather than four, because the difference between them is where they sit and what they
 * send, not how they behave. Each control says what to do when it is pressed and released, so a
 * trigger sending an analog value and a button sending a key code are the same thing here.
 */
private class PadView(context: Context, private val controls: List<Control>) : View(context) {

    class Control(
        val label: String,
        /** Position within the window, from -1 to 1 on each axis. */
        val dx: Float,
        val dy: Float,
        val onDown: () -> Unit,
        val onUp: () -> Unit,
    )

    private val face = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val held = mutableMapOf<Int, Control>()

    private val radius: Float
        get() = min(width, height) / (if (controls.size > 3) 4.2f else 3.2f)

    override fun onDraw(canvas: Canvas) {
        label.textSize = radius * 0.8f
        controls.forEach { c ->
            val cx = width / 2f + c.dx * (width / 2f - radius)
            val cy = height / 2f + c.dy * (height / 2f - radius)
            canvas.drawCircle(cx, cy, radius, face)
            canvas.drawText(c.label, cx, cy + radius / 3f, label)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                controlAt(event.getX(i), event.getY(i))?.let { c ->
                    held[event.getPointerId(i)] = c
                    c.onDown()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                held.remove(event.getPointerId(event.actionIndex))?.onUp()
            }
        }
        invalidate()
        return true
    }

    fun releaseAll() {
        held.values.forEach { it.onUp() }
        held.clear()
    }

    private fun controlAt(px: Float, py: Float): Control? = controls.firstOrNull { c ->
        val cx = width / 2f + c.dx * (width / 2f - radius)
        val cy = height / 2f + c.dy * (height / 2f - radius)
        hypot(px - cx, py - cy) <= radius * 1.2f
    }

    companion object {

        /**
         * The d-pad, sent as hat axes rather than as four separate keys.
         *
         * A real pad reports a hat, and Phase 0 measured the platform synthesising `DPAD_*` keys
         * from it — so sending the hat produces both, while sending keys produces only the keys.
         */
        fun dpad(engine: InputEngine): List<Control> = listOf(
            Control("↑", 0f, -1f, { engine.hat(0, -1) }, { engine.hat(0, 0) }),
            Control("↓", 0f, 1f, { engine.hat(0, 1) }, { engine.hat(0, 0) }),
            Control("←", -1f, 0f, { engine.hat(-1, 0) }, { engine.hat(0, 0) }),
            Control("→", 1f, 0f, { engine.hat(1, 0) }, { engine.hat(0, 0) }),
        )

        fun leftShoulders(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("L1", -1f, 0f, { engine.button(310, true) }, { engine.button(310, false) }),
            // L2 is analog on a real pad, so it is sent as a trigger at full travel rather than as
            // a button. A target that reads the axis sees it; one that reads the button also sees
            // the key the platform derives.
            Control("L2", 1f, 0f, { engine.trigger(1.0, profile, right = false) }, { engine.trigger(0.0, profile, right = false) }),
        )

        fun rightShoulders(engine: InputEngine, profile: AnalogProfile): List<Control> = listOf(
            Control("R2", -1f, 0f, { engine.trigger(1.0, profile, right = true) }, { engine.trigger(0.0, profile, right = true) }),
            Control("R1", 1f, 0f, { engine.button(311, true) }, { engine.button(311, false) }),
        )

        fun menu(engine: InputEngine): List<Control> = listOf(
            Control("SEL", -1f, 0f, { engine.button(314, true) }, { engine.button(314, false) }),
            Control("L3", -0.33f, 0f, { engine.button(317, true) }, { engine.button(317, false) }),
            Control("R3", 0.33f, 0f, { engine.button(318, true) }, { engine.button(318, false) }),
            Control("STA", 1f, 0f, { engine.button(315, true) }, { engine.button(315, false) }),
        )
    }
}

/** The face buttons, in a window the size of the buttons. */
private class ButtonsView(context: Context, private val engine: InputEngine) : View(context) {

    private val face = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private data class Face(val name: String, val code: Int, val dx: Float, val dy: Float)

    private val faces = listOf(
        Face("Y", 308, 0f, -1f),
        Face("X", 307, -1f, 0f),
        Face("B", 305, 1f, 0f),
        Face("A", 304, 0f, 1f),
    )

    /** Which pointer is holding which button, so two can be held at once. */
    private val held = mutableMapOf<Int, Int>()

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        val radius = r * 0.30f
        val spread = r * 0.55f
        label.textSize = radius
        faces.forEach { f ->
            val cx = width / 2f + f.dx * spread
            val cy = height / 2f + f.dy * spread
            canvas.drawCircle(cx, cy, radius, face)
            canvas.drawText(f.name, cx, cy + radius / 3f, label)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                faceAt(event.getX(i), event.getY(i))?.let { f ->
                    held[event.getPointerId(i)] = f.code
                    engine.button(f.code, true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                held.remove(event.getPointerId(event.actionIndex))?.let { engine.button(it, false) }
            }
        }
        return true
    }

    fun releaseAll() {
        held.values.forEach { engine.button(it, false) }
        held.clear()
    }

    private fun faceAt(px: Float, py: Float): Face? {
        val r = min(width, height) / 2f
        val radius = r * 0.30f
        val spread = r * 0.55f
        return faces.firstOrNull { f ->
            val cx = width / 2f + f.dx * spread
            val cy = height / 2f + f.dy * spread
            hypot(px - cx, py - cy) <= radius * 1.2f
        }
    }
}
