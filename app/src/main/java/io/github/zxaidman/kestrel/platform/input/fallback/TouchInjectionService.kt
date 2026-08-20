package io.github.zxaidman.kestrel.platform.input.fallback

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * The fallback path, under measurement. **Not a backend, and not used by anything yet.**
 *
 * `ADR-006` accepted touch simulation through an accessibility service as the *direction* for a
 * user without Shizuku, and accepted it **untested**. This service exists to end that: it is the
 * smallest thing that can answer whether the direction is viable, and it is deliberately shaped as
 * an experiment rather than as the beginning of an implementation.
 *
 * What it can prove and what it cannot is worth stating before any result is read.
 *
 * - It can dispatch a **touch**. A tap, a swipe, a held press. That is the whole of what
 *   `dispatchGesture` offers.
 * - It cannot produce a **controller**. Nothing here creates a device, and no target will list
 *   Kestrel as a gamepad because of it. A target that reads only controller input sees nothing at
 *   all, however well this works.
 * - So a passing result means "the touch controls a target already has can be driven by Kestrel's
 *   layout", and never "Kestrel works without Shizuku".
 *
 * `ARCHITECTURE.md` §16 is explicit that an accessibility service must not be used merely because
 * it is an easy way to inject taps, and requires the architecture to allow it to be removed without
 * affecting anything else. Keeping this self-contained — no product code refers to it — is that
 * requirement honoured while the question is open.
 */
public class TouchInjectionService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ProbeState.connected = true
        ProbeState.note = "Service connected. Gesture dispatch is available."
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        ProbeState.connected = false
        ProbeState.note = "Service disconnected."
    }

    override fun onInterrupt() {
        // Nothing is held across events, so there is nothing to abandon.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately empty. The service observes nothing: it is enabled to dispatch, and reading
        // window content would widen what it can see for no reason the experiment needs.
    }

    /**
     * Dispatches one tap and reports how long the platform took to accept and finish it.
     *
     * The returned time is dispatch-to-callback, which includes the gesture's own duration. It is
     * an upper bound on the platform's overhead, not the latency a thumb would feel — that is
     * measured separately, by aiming a tap at a window of ours and timing the touch it receives.
     */
    public fun tap(x: Float, y: Float, holdMillis: Long, onDone: (accepted: Boolean, millis: Long) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, holdMillis.coerceAtLeast(1L))
        run(GestureDescription.Builder().addStroke(stroke).build(), onDone)
    }

    /** Dispatches one straight drag, which is what a stick or a swipe control would need. */
    public fun drag(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMillis: Long,
        onDone: (accepted: Boolean, millis: Long) -> Unit,
    ) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceAtLeast(1L))
        run(GestureDescription.Builder().addStroke(stroke).build(), onDone)
    }

    private fun run(gesture: GestureDescription, onDone: (Boolean, Long) -> Unit) {
        val startedAt = System.nanoTime()
        fun finish(accepted: Boolean) {
            onDone(accepted, (System.nanoTime() - startedAt) / 1_000_000L)
        }

        val callback = object : GestureResultCallback() {
            override fun onCompleted(description: GestureDescription?) = finish(true)

            // Cancelled is a real answer, not an error to be swallowed. A platform that refuses a
            // gesture while a target is in the foreground is exactly the finding this is looking
            // for, and reporting it as "no result" would hide it.
            override fun onCancelled(description: GestureDescription?) = finish(false)
        }

        val submitted = runCatching {
            dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)

        // dispatchGesture returns false without ever calling back when it will not even try.
        if (!submitted) finish(false)
    }

    public companion object {
        @Volatile
        private var instance: TouchInjectionService? = null

        /** The running service, or null when it has not been enabled. */
        public fun current(): TouchInjectionService? = instance
    }
}

/** What the probe knows, for any screen or report that needs to say so. */
public object ProbeState {
    @Volatile public var connected: Boolean = false
    @Volatile public var note: String = "Not enabled."
}
