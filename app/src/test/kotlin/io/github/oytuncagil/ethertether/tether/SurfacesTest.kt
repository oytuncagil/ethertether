// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.testing.noAdapterStatus
import io.github.oytuncagil.ethertether.testing.servingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The notification, the widget content and the notification channels. */
@RunWith(AndroidJUnit4::class)
class SurfacesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ready = TetherState(readiness = EngineReadiness.Ready)

    @Test
    fun widgetFollowsTheState() {
        assertEquals(WidgetContent(R.string.widget_off, "Car", R.string.action_start, true), widgetContent(ready, "Car"))
        assertEquals(R.string.widget_on, widgetContent(ready.copy(armed = true, status = servingStatus()), "Car").title)
        assertEquals(R.string.action_stop, widgetContent(ready.copy(armed = true, status = servingStatus()), "Car").action)
        assertEquals(R.string.widget_waiting, widgetContent(ready.copy(armed = true, status = noAdapterStatus()), "Car").title)
        val noShizuku = widgetContent(TetherState(readiness = EngineReadiness.ShizukuNotRunning), "Car")
        assertEquals(R.string.widget_needs_shizuku, noShizuku.title)
        assertFalse(noShizuku.canToggle)
        assertTrue("before the first check the widget may toggle", widgetContent(TetherState(), "Car").canToggle)
    }

    @Test
    fun ongoingTitleFollowsTheState() {
        assertEquals(R.string.notification_starting, Notifications.ongoingTitle(ready.copy(operation = Operation.STARTING)))
        assertEquals(R.string.notification_stopping, Notifications.ongoingTitle(ready.copy(operation = Operation.STOPPING)))
        assertEquals(R.string.notification_on, Notifications.ongoingTitle(ready.copy(status = servingStatus())))
        assertEquals(R.string.notification_rearming, Notifications.ongoingTitle(ready.copy(rearming = true)))
        assertEquals(R.string.notification_waiting, Notifications.ongoingTitle(ready.copy(armed = true)))
    }

    @Test
    fun theOngoingNotificationHasStopAndStaysOn() {
        Notifications.createChannels(context)
        val notification = Notifications.ongoing(context, ready.copy(armed = true, status = servingStatus(ServingScope.GLOBAL)), "Car")
        val extras = notification.extras
        assertEquals("Ethernet tethering is on", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Car, eth0, sharing the internet connection", extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Stop", notification.actions.single().title.toString())
        assertNotNull(notification.actions.single().actionIntent)
        assertEquals(Notifications.CHANNEL_ONGOING, notification.channelId)
    }

    @Test
    fun channelsAreQuietForTheStatusAndNormalForAlerts() {
        Notifications.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(Notifications.CHANNEL_ONGOING).importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(Notifications.CHANNEL_ALERTS).importance)
    }

    @Test
    fun alertsAreShownForEachWatchdogEvent() {
        Notifications.createChannels(context)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        WatchdogEvent.entries.forEach { event ->
            Notifications.alert(context, event, "Car")
            val shown = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.last()
            assertEquals(Notifications.CHANNEL_ALERTS, shown.channelId)
            assertTrue(shown.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Car"))
        }
    }

    @Test
    fun noAlertWithoutPermission() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Notifications.alert(context, WatchdogEvent.STOPPED_OUTSIDE, "Car")
        assertTrue(shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
}
