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
) {

    private val windows = context.getSystemService(WindowManager::class.java)
    private var stick: StickView? = null
    private var buttons: ButtonsView? = null
    private var toggle: ToggleView? = null
    private var controlsVisible = false

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
        val size = (unit * 0.11f).toInt()
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

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        if (controlsVisible) return
        val stickSize = (unit * 0.46f).toInt()
        val buttonSize = (unit * 0.46f).toInt()
        val margin = (unit * 0.04f).toInt()

        val stickView = StickView(context, engine, profile)
        val buttonsView = ButtonsView(context, engine)

        runCatching {
            windows?.addView(
                stickView,
                params(stickSize, stickSize, Gravity.BOTTOM or Gravity.START, margin, margin),
            )
            windows?.addView(
                buttonsView,
                params(buttonSize, buttonSize, Gravity.BOTTOM or Gravity.END, margin, margin),
            )
        }.onFailure {
            runCatching { windows?.removeView(stickView) }
            runCatching { windows?.removeView(buttonsView) }
            return
        }

        stick = stickView
        buttons = buttonsView
        controlsVisible = true
    }

    private fun hideControls() {
        stick?.let { view ->
            // Never leave a control held when it disappears. A stick removed at full deflection
            // keeps the platform emitting directional keys with nothing left to release it.
            engine.stick(0.0, 0.0, profile)
            runCatching { windows?.removeView(view) }
        }
        buttons?.let { view ->
            view.releaseAll()
            runCatching { windows?.removeView(view) }
        }
        stick = null
        buttons = null
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
) : View(context) {

    private val ring = Paint().apply { color = Color.argb(60, 255, 255, 255); isAntiAlias = true }
    private val knob = Paint().apply { color = Color.argb(150, 255, 255, 255); isAntiAlias = true }

    private var x = 0f
    private var y = 0f

    override fun onDraw(canvas: Canvas) {
        val r = min(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, r * 0.95f, ring)
        canvas.drawCircle(width / 2f + x * r, height / 2f + y * r, r * 0.32f, knob)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val r = min(width, height) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                x = ((event.x - width / 2f) / r).coerceIn(-1f, 1f)
                y = ((event.y - height / 2f) / r).coerceIn(-1f, 1f)
                engine.stick(x.toDouble(), y.toDouble(), profile)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                x = 0f
                y = 0f
                engine.stick(0.0, 0.0, profile)
            }
        }
        invalidate()
        return true
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
