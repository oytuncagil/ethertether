// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.oytuncagil.ethertether.backend.RikkaShizukuApi
import io.github.oytuncagil.ethertether.backend.SHIZUKU_PACKAGE
import io.github.oytuncagil.ethertether.data.LogEntry
import io.github.oytuncagil.ethertether.data.ThemeChoice
import io.github.oytuncagil.ethertether.ui.AppNavigation
import io.github.oytuncagil.ethertether.ui.Platform
import io.github.oytuncagil.ethertether.ui.exportFileName
import io.github.oytuncagil.ethertether.ui.settings.AppLocales
import io.github.oytuncagil.ethertether.ui.theme.EtherTetherTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

/** The single activity: theme, language, navigation, and what screens need from the system. */
class MainActivity : ComponentActivity(), Platform {
    private val app get() = application as EtherTetherApplication
    private var askedForNotifications = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.log.info("app", "notification permission ${if (granted) "granted" else "denied"}")
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }
            EtherTetherTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                // Wait for the stored settings, so the first-run guide never flashes for returning users.
                if (settings.loaded) AppNavigation(app, this, showOnboarding = !settings.onboardingDone)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.controller.refreshReadiness()
        // The adapter may have changed while the app was away.
        app.appScope.launch { app.controller.refreshAdapter() }
    }

    override fun openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, url.toUri()), R.string.setup_no_browser)

    override fun openShizuku() {
        packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { launch(it, R.string.setup_no_settings) }
    }

    override fun openDeveloperOptions() = launch(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), R.string.setup_no_settings)

    override fun openAppSettings() =
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()), R.string.setup_no_settings)

    /**
     * Android's Settings app handles the tethering page with this action, but there is no public
     * constant for it; phones without it get the general network settings instead.
     */
    override fun openTetherSettings() {
        val page = systemPage(Intent(TETHER_SETTINGS)) ?: systemPage(Intent(Settings.ACTION_WIRELESS_SETTINGS)) ?: Intent(Settings.ACTION_WIRELESS_SETTINGS)
        launch(page, R.string.setup_no_settings)
    }

    /** The documented page for permissions of unused apps (Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Android 11). */
    override fun openUnusedAppSettings() {
        val page = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, "package:$packageName".toUri())
        launch(systemPage(page) ?: page, R.string.setup_no_settings)
    }

    /**
     * [intent] sent straight to the system app that answers it, so another installed app that
     * declares the same action cannot pose as the Settings page; null if no system app does.
     */
    private fun systemPage(intent: Intent): Intent? {
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY)
        }
        val match = matches.firstOrNull()?.activityInfo ?: return null
        return Intent(intent).setClassName(match.packageName, match.name)
    }

    override fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && !askedForNotifications) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName), R.string.setup_no_settings)
    }

    override fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || askedForNotifications) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askedForNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Shizuku and Sui share one permission dialog, shown by the server. */
    override fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) return
        RikkaShizukuApi.requestPermission { runOnUiThread { app.controller.refreshReadiness() } }
    }

    override fun copy(label: String, text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun shareLog(entries: List<LogEntry>) {
        val folder = File(cacheDir, "exports").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, exportFileName("ethertether-log", "txt"))
        file.writeText(app.log.asText(entries) + "\n")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        app.log.info("log", "exported ${entries.size} entries")
        launch(Intent.createChooser(send, getString(R.string.log_export)), R.string.setup_no_settings)
    }

    private fun launch(intent: Intent, failure: Int) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        }
    }
}

/** The action of the tethering page in Android's Settings app. */
private const val TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
