// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui

import io.github.oytuncagil.ethertether.data.LogEntry

/** What the screens ask of the activity: other apps, system screens and permissions. */
interface Platform {
    /** Opens [url] in the user's browser (EtherTether itself has no network access). */
    fun openUrl(url: String)

    /** Opens the Shizuku app. */
    fun openShizuku()

    /** Opens the system Developer options. */
    fun openDeveloperOptions()

    /** Opens the system page of this app (autostart, battery, notifications). */
    fun openAppSettings()

    /** Opens Android's own tethering settings, where Ethernet tethering can be turned off by hand. */
    fun openTetherSettings()

    /** Opens the page where Android's removal of permissions from unused apps is turned off for this app. */
    fun openUnusedAppSettings()

    /** Asks for the notification permission, or opens the notification settings when asking is no longer possible. */
    fun allowNotifications()

    /** Asks for the notification permission once per app start, only if it is missing (Android 13+). */
    fun requestNotificationsIfNeeded()

    /** Shows Shizuku's permission dialog. */
    fun requestShizukuPermission()

    /** Copies [text] to the clipboard and confirms it. */
    fun copy(label: String, text: String)

    /** Writes [entries] to a text file and opens the share sheet for it. */
    fun shareLog(entries: List<LogEntry>)
}
