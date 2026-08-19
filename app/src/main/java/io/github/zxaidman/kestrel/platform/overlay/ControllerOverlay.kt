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
 * The controls, drawn over whatever the user is playing, in a window that never takes focus.
 *
 * This exists because of a measurement rather than a preference. With the controls inside an
 * ordinary activity, touching them makes that activity the focused window — and the platform
 * delivers a controller's events to the **focused** window. So pressing a control moved focus to
 * Kestrel, and the input Kestrel produced then arrived back at Kestrel. The export
 * `docs/phase0/results/app-stick-focus-20260819-redmi-note-13-5g.json` caught it exactly: 2005
 * motion events, source `Kestrel Virtual Controller (id 14)`, received by Kestrel while the target
 * received nothing.
 *
 * `FLAG_NOT_FOCUSABLE` is therefore not a detail of this class, it is the reason the class exists.
 * The window accepts touches and never becomes the focused window, so the target keeps focus and
 * the controller's events go where the player is looking.
 *
 * Drawn with a plain `View` rather than Compose: a window put up by a service has no lifecycle
 * owner, and giving it one is more machinery than a stick and four buttons are worth.
 */
public class ControllerOverlay(
    context: Context,
    private val engine: InputEngine,
    private var profile: AnalogProfile,
) : View(context) {

    private val ring = Paint().apply { color = Color.argb(60, 255, 255, 255); isAntiAlias = true }
    private val knob = Paint().apply { color = Color.argb(140, 255, 255, 255); isAntiAlias = true }
    private val face = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    /** Which pointer is on the stick, so a thumb on a button never moves it. */
    private var stickPointer = -1
    private var stickX = 0f
    private var stickY = 0f

    private val pressed = mutableMapOf<Int, Int>()

    private var stickCentreX = 0f
    private var stickCentreY = 0f
    private var stickRadius = 0f
    private val buttons = mutableListOf<FaceButton>()

    private data class FaceButton(
        val name: String,
        val keyCode: Int,
        var cx: Float = 0f,
        var cy: Float = 0f,
        var r: Float = 0f,
    )

    init {
        buttons += FaceButton("A", 304)
        buttons += FaceButton("B", 305)
        buttons += FaceButton("X", 307)
        buttons += FaceButton("Y", 308)
    }

    public fun update(profile: AnalogProfile) {
        this.profile = profile
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val unit = min(w, h).toFloat()
        label.textSize = unit * 0.06f

        // Anchored to the corners a thumb reaches, in units of the short side — the same rule
        // `core/layout/` uses, so what is drawn here matches what a layout would describe.
        stickRadius = unit * 0.20f
        stickCentreX = unit * 0.28f
        stickCentreY = h - unit * 0.28f

        val faceR = unit * 0.09f
        val cx = w - unit * 0.28f
        val cy = h - unit * 0.28f
        val spread = unit * 0.14f
        buttons[0].apply { this.cx = cx; this.cy = cy + spread; r = faceR }  // A, low
        buttons[1].apply { this.cx = cx + spread; this.cy = cy; r = faceR }  // B, right
        buttons[2].apply { this.cx = cx - spread; this.cy = cy; r = faceR }  // X, left
        buttons[3].apply { this.cx = cx; this.cy = cy - spread; r = faceR }  // Y, high
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(stickCentreX, stickCentreY, stickRadius, ring)
        canvas.drawCircle(
            stickCentreX + stickX * stickRadius,
            stickCentreY + stickY * stickRadius,
            stickRadius * 0.35f,
            knob,
        )
        buttons.forEach { button ->
            canvas.drawCircle(button.cx, button.cy, button.r, face)
            canvas.drawText(button.name, button.cx, button.cy + label.textSize / 3, label)
        }
    }

    /**
     * Multi-touch by pointer id, because holding a direction while pressing a button is the
     * ordinary case rather than an advanced one. Tracking only the first pointer would make the
     * stick jump to a button the moment one was pressed.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                claim(event.getPointerId(index), event.getX(index), event.getY(index))
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    if (event.getPointerId(index) == stickPointer) {
                        moveStick(event.getX(index), event.getY(index))
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(event.actionIndex)
                release(id)
            }
        }
        invalidate()
        return true
    }

    private fun claim(pointerId: Int, x: Float, y: Float) {
        val button = buttons.firstOrNull { hypot(x - it.cx, y - it.cy) <= it.r * 1.25f }
        if (button != null) {
            pressed[pointerId] = button.keyCode
            engine.button(button.keyCode, true)
            return
        }
        if (hypot(x - stickCentreX, y - stickCentreY) <= stickRadius * 1.6f && stickPointer == -1) {
            stickPointer = pointerId
            moveStick(x, y)
        }
    }

    private fun moveStick(x: Float, y: Float) {
        stickX = ((x - stickCentreX) / stickRadius).coerceIn(-1f, 1f)
        stickY = ((y - stickCentreY) / stickRadius).coerceIn(-1f, 1f)
        engine.stick(stickX.toDouble(), stickY.toDouble(), profile)
    }

    private fun release(pointerId: Int) {
        pressed.remove(pointerId)?.let { engine.button(it, false) }
        if (pointerId == stickPointer) {
            stickPointer = -1
            stickX = 0f
            stickY = 0f
            // Centred on the device as well as on screen. A stick left deflected keeps the platform
            // emitting directional keys without stopping.
            engine.stick(0.0, 0.0, profile)
        }
    }

    public companion object {

        /** Whether the user has allowed drawing over other applications. */
        public fun permitted(context: Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * The window flags that make this work, and why each is there.
         *
         * `NOT_FOCUSABLE` is the one that matters: without it the overlay becomes the focused
         * window on touch and the controller's own events return to Kestrel instead of reaching
         * the target. `NOT_TOUCH_MODAL` lets touches outside the controls pass through to whatever
         * is underneath, so the parts of the screen the player is looking at stay usable.
         */
        public fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }
}
