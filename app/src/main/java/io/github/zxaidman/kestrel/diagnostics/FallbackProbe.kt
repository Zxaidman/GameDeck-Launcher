package io.github.zxaidman.kestrel.diagnostics

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.zxaidman.kestrel.platform.input.fallback.ProbeState
import io.github.zxaidman.kestrel.platform.input.fallback.TouchInjectionService
import io.github.zxaidman.kestrel.platform.shizuku.IPrivilegedShell

/**
 * Measures the fallback path so `ADR-006` can stop saying "untested".
 *
 * The record it produces has to answer four separate questions, and they are separate because a
 * yes to one says nothing about the others.
 *
 * 1. **Can the service be enabled without sending the user hunting through settings?** Three routes
 *    are tried and reported apart: writing the setting through a privileged shell, granting
 *    `WRITE_SECURE_SETTINGS` once so Kestrel can write it later on its own, and the manual route.
 *    The middle one is the interesting one — a grant that survives means the fallback can be turned
 *    on afterwards with Shizuku not running at all.
 * 2. **How long does an injected touch take to arrive?** Measured end to end, with no human in the
 *    loop: a tap is aimed at a window Kestrel owns, and the time from asking to the touch landing
 *    is the number. This is the figure a thumb would feel.
 * 3. **How finely can a movement be drawn?** A stick is a continuous thing. A drag is dispatched
 *    and the movements it produces are counted, because a drag that arrives as two points cannot
 *    simulate a stick however low its latency is.
 * 4. **Does any of it work while Kestrel's own overlay is up?** The measurement target *is* an
 *    overlay window, so every number above is already taken under that condition.
 *
 * **What none of it can answer:** whether a target that reads only controller input can be driven.
 * It cannot — nothing here makes a device. A good result here means a target's own touch controls
 * can be driven, and that is all it means.
 */
public object FallbackProbe {

    /** One measured run, in the shape the report and the screen both need. */
    public data class Result(
        val samples: List<Long>,
        val missed: Int,
        val accepted: Int,
        val cancelled: Int,
        val dragMoves: Int,
        val dragSpanMillis: Long,
        val dragAccepted: Boolean,
        val note: String,
    ) {
        val median: Long get() = samples.sorted().getOrNull(samples.size / 2) ?: -1L
        val best: Long get() = samples.minOrNull() ?: -1L
        val worst: Long get() = samples.maxOrNull() ?: -1L
    }

    @Volatile
    public var last: Result? = null
        private set

    @Volatile
    public var running: Boolean = false
        private set

    private fun component(context: Context) =
        ComponentName(context, TouchInjectionService::class.java).flattenToString()

    /** Whether the platform's own list says the service is on. Distinct from it being connected. */
    public fun enabledInSettings(context: Context): Boolean =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(':').any { it.equals(component(context), ignoreCase = true) }

    /** Whether Kestrel itself holds the permission that would let it write that list. */
    public fun holdsWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Turns the service on through a privileged shell.
     *
     * **Appends rather than replaces.** The setting is a shared list, and writing only Kestrel into
     * it would silently switch off every accessibility service the user actually depends on. That
     * is not an acceptable side effect of a diagnostic.
     */
    public fun enableViaShell(context: Context, shell: IPrivilegedShell): String {
        val mine = component(context)
        val existing = runCatching { shell.exec("settings get secure enabled_accessibility_services", 4000) }
            .getOrDefault("")
            .trim()
            .let { if (it == "null") "" else it }
        val parts = existing.split(':').filter { it.isNotBlank() }
        val updated = (parts + mine).distinct().joinToString(":")
        return buildString {
            appendLine(shell.exec("settings put secure enabled_accessibility_services $updated", 4000))
            appendLine(shell.exec("settings put secure accessibility_enabled 1", 4000))
            append("Requested: $updated")
        }
    }

    /**
     * Grants Kestrel the permission to write that list itself, once.
     *
     * This is the route the project owner asked about. If it holds, the fallback can be enabled on
     * a later day with Shizuku not running — which is the whole point of a fallback for a user who
     * does not have it.
     */
    public fun grantWriteSecureSettings(context: Context, shell: IPrivilegedShell): String {
        val out = runCatching {
            shell.exec(
                "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                6000,
            )
        }.getOrElse { "failed: ${it.javaClass.simpleName}" }
        val held = holdsWriteSecureSettings(context)
        return "pm grant said: ${out.trim().ifBlank { "(nothing)" }}\nHeld now: ${if (held) "yes" else "no"}"
    }

    /** Turns the service on using Kestrel's own permission, with no shell involved. */
    @SuppressLint("MissingPermission")
    public fun enableWithOwnPermission(context: Context): String {
        if (!holdsWriteSecureSettings(context)) {
            return "Kestrel does not hold WRITE_SECURE_SETTINGS, so it cannot write the setting."
        }
        val mine = component(context)
        return runCatching {
            val existing = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val parts = existing.split(':').filter { it.isNotBlank() }
            val updated = (parts + mine).distinct().joinToString(":")
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated,
            )
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            "Written without a shell. Requested: $updated"
        }.getOrElse { "Refused: ${it.javaClass.simpleName} — ${it.message}" }
    }

    /** Takes Kestrel back out of the list, leaving every other service where it was. */
    public fun disableWithOwnPermission(context: Context): String {
        if (!holdsWriteSecureSettings(context)) return "No permission to write the setting."
        val mine = component(context)
        return runCatching {
            val remaining = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty().split(':').filter { it.isNotBlank() && !it.equals(mine, ignoreCase = true) }
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                remaining.joinToString(":"),
            )
            "Removed. Remaining: ${remaining.joinToString(":").ifBlank { "(none)" }}"
        }.getOrElse { "Refused: ${it.javaClass.simpleName}" }
    }

    /**
     * Runs the measurement and reports what happened.
     *
     * Sequential on purpose. Overlapping gestures would measure the platform's queue rather than
     * its latency, and a queue depth is not the number anyone is asking about.
     */
    public fun measure(context: Context, samples: Int = 12, onDone: (Result) -> Unit) {
        if (running) return
        val service = TouchInjectionService.current()
        if (service == null) {
            onDone(fail("The service is not connected. Enable it, then try again."))
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            onDone(fail("The measurement aims a touch at a window of Kestrel's, which needs the " +
                "draw-over-other-apps permission."))
            return
        }

        val windows = context.getSystemService(WindowManager::class.java)
        if (windows == null) {
            onDone(fail("No window manager."))
            return
        }

        val target = Target(context)
        val size = (context.resources.displayMetrics.widthPixels * 0.55f).toInt()
        val added = runCatching {
            windows.addView(target, targetParams(size))
            true
        }.getOrDefault(false)
        if (!added) {
            onDone(fail("Could not put the measurement target on screen."))
            return
        }

        running = true
        val handler = Handler(Looper.getMainLooper())
        val times = mutableListOf<Long>()
        var missed = 0
        var accepted = 0
        var cancelled = 0

        fun finish(dragMoves: Int, dragSpan: Long, dragOk: Boolean) {
            runCatching { windows.removeView(target) }
            running = false
            val result = Result(
                samples = times.toList(),
                missed = missed,
                accepted = accepted,
                cancelled = cancelled,
                dragMoves = dragMoves,
                dragSpanMillis = dragSpan,
                dragAccepted = dragOk,
                note = "Measured against an overlay window of Kestrel's, so these numbers are " +
                    "already taken with an overlay on screen.",
            )
            last = result
            onDone(result)
        }

        /** The drag, run once the taps are done: how many movements one stroke actually produces. */
        fun drag() {
            target.beginDrag()
            val bounds = IntArray(2).also { target.getLocationOnScreen(it) }
            val left = bounds[0] + target.width * 0.2f
            val right = bounds[0] + target.width * 0.8f
            val y = bounds[1] + target.height / 2f
            service.drag(left, y, right, y, DRAG_MILLIS) { ok, _ ->
                handler.postDelayed({ finish(target.dragMoves, target.dragSpanMillis, ok) }, SETTLE_MILLIS)
            }
        }

        fun tap(index: Int) {
            if (index >= samples) {
                drag()
                return
            }
            val bounds = IntArray(2).also { target.getLocationOnScreen(it) }
            val x = bounds[0] + target.width / 2f
            val y = bounds[1] + target.height / 2f

            val askedAt = System.nanoTime()
            var landed = false
            target.onTouch = { at ->
                if (!landed) {
                    landed = true
                    times += (at - askedAt) / 1_000_000L
                }
            }
            service.tap(x, y, TAP_MILLIS) { ok, _ -> if (ok) accepted += 1 else cancelled += 1 }

            handler.postDelayed({
                if (!landed) missed += 1
                target.onTouch = null
                tap(index + 1)
            }, PER_SAMPLE_MILLIS)
        }

        // A moment for the window to actually be on screen before anything is aimed at it.
        handler.postDelayed({ tap(0) }, SETTLE_MILLIS)
    }

    private fun fail(why: String) = Result(
        samples = emptyList(),
        missed = 0,
        accepted = 0,
        cancelled = 0,
        dragMoves = 0,
        dragSpanMillis = 0L,
        dragAccepted = false,
        note = why,
    ).also { last = it }

    private fun targetParams(size: Int) = WindowManager.LayoutParams(
        size,
        size,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.CENTER }

    /** How long each part of the run takes. Chosen to be long enough to be honest, not brisk. */
    private const val TAP_MILLIS = 1L
    private const val DRAG_MILLIS = 400L
    private const val PER_SAMPLE_MILLIS = 350L
    private const val SETTLE_MILLIS = 250L

    /** The thing a measured touch is aimed at: visible, so a person can see where it will land. */
    private class Target(context: Context) : View(context) {

        var onTouch: ((nanos: Long) -> Unit)? = null
        var dragMoves = 0
            private set
        var dragSpanMillis = 0L
            private set

        private var dragging = false
        private var dragStart = 0L

        private val fill = Paint().apply { color = Color.argb(90, 20, 22, 27); isAntiAlias = true }
        private val edge = Paint().apply {
            color = Color.argb(200, 116, 196, 255)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        fun beginDrag() {
            dragging = true
            dragMoves = 0
            dragSpanMillis = 0L
            dragStart = 0L
        }

        override fun onDraw(canvas: Canvas) {
            edge.strokeWidth = width * 0.02f
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), edge)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onTouch?.invoke(System.nanoTime())
                    if (dragging) {
                        dragStart = System.nanoTime()
                        dragMoves = 0
                    }
                }

                MotionEvent.ACTION_MOVE -> if (dragging) {
                    // Batched movements count individually: a stroke delivered as one event with
                    // twenty historical points is twenty points of resolution, not one.
                    dragMoves += 1 + event.historySize
                    dragSpanMillis = (System.nanoTime() - dragStart) / 1_000_000L
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    dragSpanMillis = (System.nanoTime() - dragStart) / 1_000_000L
                    dragging = false
                }
            }
            return true
        }
    }
}
