// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.oytuncagil.ethertether.MainActivity
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.engine.ServingScope

/** The notification channels and notifications of EtherTether. */
object Notifications {
    /** Channel of the ongoing notification while tethering runs or is armed. */
    const val CHANNEL_ONGOING = "tethering"

    /** Channel of the watchdog alerts. */
    const val CHANNEL_ALERTS = "alerts"

    /** Id of the ongoing notification. */
    const val ID_ONGOING = 1

    private const val ID_ALERT = 2

    /** Creates both channels; safe to call often. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_ongoing_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_alerts_description)
            },
        )
    }

    /** The ongoing notification for [state], with a Stop action; [profileName] names the profile. */
    fun ongoing(context: Context, state: TetherState, profileName: String?): Notification {
        val title = context.getString(ongoingTitle(state))
        val serving = state.status?.serving
        val text = when {
            serving != null -> context.getString(
                if (serving.second == ServingScope.LOCAL) R.string.notification_text_local else R.string.notification_text_shared,
                profileName.orEmpty(),
                serving.first,
            )
            else -> profileName.orEmpty()
        }
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .addAction(stopAction(context))
            .build()
    }

    /**
     * Stop, which needs the phone unlocked on Android 12 and later, as the tile does: a stranger
     * holding the locked phone cannot stop tethering and disarm it from the lock screen.
     */
    private fun stopAction(context: Context) =
        NotificationCompat.Action.Builder(R.drawable.ic_notification, context.getString(R.string.action_stop), TetherService.pendingStop(context))
            .setAuthenticationRequired(true)
            .build()

    /** Shows the alert for a watchdog [event], if the user allows notifications. */
    fun alert(context: Context, event: WatchdogEvent, profileName: String?) {
        if (!canNotify(context)) return
        val (title, text) = when (event) {
            WatchdogEvent.ADAPTER_REMOVED -> R.string.alert_adapter_removed to R.string.alert_adapter_removed_text
            WatchdogEvent.STOPPED_OUTSIDE -> R.string.alert_stopped_outside to R.string.alert_stopped_outside_text
            WatchdogEvent.REARM_FAILED -> R.string.alert_rearm_failed to R.string.alert_rearm_failed_text
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text, profileName.orEmpty()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text, profileName.orEmpty())))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the call.
        }
    }

    /** True when notifications may be shown (POST_NOTIFICATIONS on Android 13 and later). */
    fun canNotify(context: Context): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The title resource for the ongoing notification. */
    fun ongoingTitle(state: TetherState): Int = when {
        state.operation == Operation.STARTING -> R.string.notification_starting
        state.operation == Operation.STOPPING -> R.string.notification_stopping
        state.serving -> R.string.notification_on
        state.rearming -> R.string.notification_rearming
        else -> R.string.notification_waiting
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
