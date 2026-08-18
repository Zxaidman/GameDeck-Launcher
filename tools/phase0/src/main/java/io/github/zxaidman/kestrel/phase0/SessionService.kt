package io.github.zxaidman.kestrel.phase0

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * EXPERIMENTAL — Phase 0 harness only.
 *
 * Keeps a controller session alive while the operator is somewhere else, and makes sure it dies
 * when they say so.
 *
 * Two facts from the reference device shaped this, and they pull in opposite directions:
 *
 * 1. A created controller is held by a process that is not the application's, so leaving the
 *    harness — or having the system reclaim it in the background — does not end the session. That
 *    is exactly what a controller needs to be: it has to outlive the launcher's own screen, or it
 *    is useless for playing anything.
 * 2. The same property meant a device survived force-stop, cleared data and **uninstalling the
 *    application**, and only a reboot ended it. See `docs/phase0/results/tier5-orphan-report.md`.
 *
 * The difference between the feature and the fault is who decides. This service is that decision
 * point: while it runs it renews a lease, and a watchdog in the privileged process destroys the
 * device once the lease goes stale. Stopping the session, force-stopping the application, clearing
 * its data or uninstalling it all stop the renewals, and the device follows within seconds — with
 * no cooperation needed from the application, which is essential, because an application being
 * uninstalled never gets to run any code.
 *
 * The notification is not decoration. It is the visible handle on something that would otherwise be
 * invisible: while a controller exists, the user can see it exists and can end it from anywhere.
 */
class SessionService : Service() {

    private var heartbeat: Thread? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ShizukuProbe.stopSession(this)
                stopSelfCompletely()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                ShizukuProbe.pauseCycle(this)
                notify(paused = true)
                return START_STICKY
            }
            ACTION_RESUME -> {
                ShizukuProbe.resumeCycle(this)
                notify(paused = false)
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, build(paused = false))
        startHeartbeat()
        return START_STICKY
    }

    /**
     * Renews the lease on a fixed cadence.
     *
     * Deliberately shorter than the watchdog's patience, so an ordinary pause in scheduling never
     * looks like the application having died. If the privileged service is gone, the renewal fails
     * and the device is torn down — the safe direction to fail in.
     */
    private fun startHeartbeat() {
        if (running) return
        running = true
        heartbeat = Thread {
            while (running) {
                if (!ShizukuProbe.renewLease()) {
                    EventLog.note("SESSION: lease renewal failed — the watchdog will close the device")
                }
                try {
                    Thread.sleep(HEARTBEAT_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopSelfCompletely() {
        running = false
        heartbeat?.interrupt()
        heartbeat = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        heartbeat?.interrupt()
    }

    private fun notify(paused: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, build(paused))
    }

    private fun build(paused: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Controller session",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while a virtual controller exists, so it can always be stopped."
                    setShowBadge(false)
                }
            )
        }

        fun action(label: String, act: String) = Notification.Action.Builder(
            null,
            label,
            PendingIntent.getService(
                this,
                act.hashCode(),
                Intent(this, SessionService::class.java).setAction(act),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Phase0Activity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kestrel virtual controller active")
            .setContentText(
                if (paused) {
                    "Device open, input paused. It closes if this notification goes away."
                } else {
                    "Device open and sending input. It closes if this notification goes away."
                }
            )
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                if (paused) action("Resume", ACTION_RESUME) else action("Pause", ACTION_PAUSE)
            )
            .addAction(action("Stop", ACTION_STOP))
            .build()
    }

    companion object {
        const val ACTION_START = "io.github.zxaidman.kestrel.phase0.START"
        const val ACTION_STOP = "io.github.zxaidman.kestrel.phase0.STOP"
        const val ACTION_PAUSE = "io.github.zxaidman.kestrel.phase0.PAUSE"
        const val ACTION_RESUME = "io.github.zxaidman.kestrel.phase0.RESUME"

        private const val CHANNEL_ID = "kestrel.session"
        private const val NOTIFICATION_ID = 1

        /** Renewal cadence. The watchdog tolerates several missed renewals before acting. */
        private const val HEARTBEAT_MS = 4000L

        fun start(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
