package io.github.zxaidman.kestrel.platform.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import io.github.zxaidman.kestrel.MainActivity
import io.github.zxaidman.kestrel.platform.input.InputEngine
import io.github.zxaidman.kestrel.platform.input.virtual.VirtualControllerBackend
import io.github.zxaidman.kestrel.platform.shizuku.ShizukuCapability

/** What the session is doing, for any screen that needs to say so. */
public object SessionState {
    public val open: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false)
    public val detail: androidx.compose.runtime.MutableState<String> = mutableStateOf("")

    /**
     * The path from an on-screen control to the controller, or null when there is no session.
     *
     * Null is the honest state and the screen shows it as such. An engine that silently accepted
     * input with nowhere to send it is exactly the failure this whole piece exists to fix.
     */
    @Volatile
    public var engine: InputEngine? = null
}

/**
 * Holds a controller session for as long as the user wants it, and no longer.
 *
 * A controller has to outlive the launcher's own screen — one that dies when you open something
 * else cannot be used to play anything. The same property once let a device survive uninstalling
 * the application, which is intolerable. The difference is not persistence but **who decides**, and
 * that decision point is this service plus the watchdog it arms
 * (`docs/phase0/results/tier5-orphan-report.md` §4a).
 *
 * The notification is not decoration. While a controller exists the user can see that it exists and
 * end it from anywhere.
 */
public class ControllerSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, notification())
        startSession()
        return START_STICKY
    }

    private fun startSession() {
        withShell("start") { shell ->
            val opened = VirtualControllerBackend.open(shell, packageName)
            val live = VirtualControllerBackend.holders(shell).isNotBlank()

            val engine = if (live) {
                InputEngine(shell).also { if (!it.start(VirtualControllerBackend.STREAM)) null }
            } else {
                null
            }
            SessionState.engine = engine
            SessionState.open.value = live
            SessionState.detail.value = opened + when {
                !live -> "\n\nNo controller was created, so nothing can be sent to one."
                engine == null -> "\n\nController created, but the write path did not open."
                else -> "\n\nOn-screen controls are connected to it."
            }
        }
    }

    /**
     * Closes the session, binding first if the connection is gone.
     *
     * The first version gave up silently when no shell was bound, and the consequence was the worst
     * kind: **stop appeared to work and the controller stayed**. A process restart is enough to
     * lose the binder — and restarting is exactly what happens when a user swipes the application
     * away — so stop must be able to reconnect rather than assume it is already connected.
     */
    private fun stopSession() {
        withShell("stop") { shell ->
            SessionState.engine?.stop()
            SessionState.engine = null
            SessionState.detail.value = VirtualControllerBackend.close(shell)
            SessionState.open.value = VirtualControllerBackend.holders(shell).isNotBlank()
        }
    }

    private fun withShell(what: String, work: (shell: io.github.zxaidman.kestrel.platform.shizuku.IPrivilegedShell) -> Unit) {
        val existing = ShizukuCapability.shell()
        if (existing != null) {
            Thread { work(existing) }.start()
            return
        }
        SessionState.detail.value = "Reconnecting to Shizuku to $what…"
        ShizukuCapability.bind(this) { shell ->
            if (shell == null) {
                SessionState.detail.value =
                    "Could not $what: no privileged shell. ${ShizukuCapability.state().advice}"
            } else {
                Thread { work(shell) }.start()
            }
        }
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Controller session", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "Shown while a controller exists, so it can always be stopped."
                        setShowBadge(false)
                    }
            )
        }

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ControllerSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Kestrel controller active")
            .setContentText("Tap to open Kestrel. The controller ends when you stop it.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    public companion object {
        public const val ACTION_START: String = "io.github.zxaidman.kestrel.SESSION_START"
        public const val ACTION_STOP: String = "io.github.zxaidman.kestrel.SESSION_STOP"

        private const val CHANNEL = "kestrel.session"
        private const val NOTIFICATION_ID = 1

        public fun start(context: Context) {
            context.startForegroundService(
                Intent(context, ControllerSessionService::class.java).setAction(ACTION_START)
            )
        }

        public fun stop(context: Context) {
            context.startService(
                Intent(context, ControllerSessionService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
