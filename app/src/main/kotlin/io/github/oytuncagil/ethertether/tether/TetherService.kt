// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.oytuncagil.ethertether.EtherTetherApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The foreground service (type connectedDevice) that runs while tethering is on, armed or busy.
 * It keeps the app process, and with it the Shizuku engine connection, alive; shows the
 * ongoing notification with Stop; and carries starts from the tile, the widget and the boot
 * receiver. It stops itself as soon as tethering is inactive.
 */
class TetherService : LifecycleService() {
    private val app get() = application as EtherTetherApplication
    private var commands = 0
    private var seenActive = false
    private var graceJob: Job? = null
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifications.createChannels(this)
        val state = app.controller.state.value
        ServiceCompat.startForeground(
            this,
            Notifications.ID_ONGOING,
            Notifications.ongoing(this, state, app.profileName(state.profileId)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        lifecycleScope.launch { app.controller.state.collect(::render) }
        // Started by the controller just before it marks itself busy: give it a moment.
        graceJob = lifecycleScope.launch {
            delay(GRACE_MS)
            stopIfIdle(app.controller.state.value)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> command("stop") { app.controller.stop() }
            ACTION_START -> command("start") { app.controller.start(intent.getStringExtra(EXTRA_PROFILE)) }
            ACTION_TOGGLE -> command("toggle") { app.controller.toggle() }
            ACTION_BOOT -> command("boot") { startAfterBoot() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        destroyed = true
        super.onDestroy()
    }

    /**
     * Runs [block] in the app's scope, not the service's: if Android ends the service early, the
     * operation still finishes and the controller's state stays right. The count keeps the
     * service from stopping itself while a command runs.
     */
    private fun command(name: String, block: suspend () -> Unit) {
        commands++
        app.log.info(TAG, "command: $name")
        app.appScope.launch {
            try {
                block()
            } finally {
                commands--
                if (!destroyed) stopIfIdle(app.controller.state.value)
            }
        }
    }

    /** After a reboot Shizuku may need a while (or the user) to start; wait for it, then arm. */
    private suspend fun startAfterBoot() {
        app.controller.startAfterBoot(app.shizukuEvents, BOOT_WAIT_MS)
    }

    private fun render(state: TetherState) {
        if (state.active) {
            seenActive = true
            graceJob?.cancel()
        }
        if (stopIfIdle(state)) return
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(Notifications.ID_ONGOING, Notifications.ongoing(this, state, app.profileName(state.profileId)))
    }

    /** Stops the service when nothing is active and no command runs. True when it stopped. */
    private fun stopIfIdle(state: TetherState): Boolean {
        if (state.active || commands > 0 || (!seenActive && graceJob?.isActive == true)) return false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return true
    }

    /** Intents for the service. */
    companion object {
        /** True while the service runs in this process. */
        @Volatile
        var running = false
            private set

        private const val TAG = "service"
        private const val GRACE_MS = 10_000L
        private const val BOOT_WAIT_MS = 5 * 60_000L
        private const val ACTION_STOP = "io.github.oytuncagil.ethertether.action.STOP"
        private const val ACTION_START = "io.github.oytuncagil.ethertether.action.START"
        private const val ACTION_TOGGLE = "io.github.oytuncagil.ethertether.action.TOGGLE"
        private const val ACTION_BOOT = "io.github.oytuncagil.ethertether.action.BOOT"
        private const val EXTRA_PROFILE = "profile"

        /** Starts the service as a foreground service, optionally with [action]. */
        fun start(context: Context, action: String? = null) {
            ContextCompat.startForegroundService(context, Intent(context, TetherService::class.java).setAction(action))
        }

        /** Starts the service and then the selected profile. */
        fun startProfile(context: Context) = start(context, ACTION_START)

        /** Starts the service and toggles tethering (tile and widget). */
        fun toggle(context: Context) = start(context, ACTION_TOGGLE)

        /** Starts the service after boot; it waits for Shizuku. */
        fun boot(context: Context) = start(context, ACTION_BOOT)

        /** The intent of the toggle action, for the widget. */
        fun toggleIntent(context: Context): Intent = Intent(context, TetherService::class.java).setAction(ACTION_TOGGLE)

        /** The Stop action of the notification. */
        fun pendingStop(context: Context): PendingIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, TetherService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
