// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether

import android.app.Application
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import io.github.oytuncagil.ethertether.backend.BackendDetectors
import io.github.oytuncagil.ethertether.backend.EngineClient
import io.github.oytuncagil.ethertether.backend.EngineConnection
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.RikkaShizukuApi
import io.github.oytuncagil.ethertether.backend.SHIZUKU_PACKAGE
import io.github.oytuncagil.ethertether.backend.ShizukuDetector
import io.github.oytuncagil.ethertether.backend.ShizukuReadiness
import io.github.oytuncagil.ethertether.backend.ShizukuUserServiceBinder
import io.github.oytuncagil.ethertether.data.DataStoreProfiles
import io.github.oytuncagil.ethertether.data.DataStoreSettings
import io.github.oytuncagil.ethertether.data.EventLog
import io.github.oytuncagil.ethertether.data.ProfileStore
import io.github.oytuncagil.ethertether.data.SettingsStore
import io.github.oytuncagil.ethertether.tether.AdapterChanges
import io.github.oytuncagil.ethertether.tether.Notifications
import io.github.oytuncagil.ethertether.tether.TetherController
import io.github.oytuncagil.ethertether.tether.TetherService
import io.github.oytuncagil.ethertether.tether.TetherTileService
import io.github.oytuncagil.ethertether.tether.TetherWidget
import io.github.oytuncagil.ethertether.ui.common.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.io.File

/** Application entry point. Holds the app-wide objects; there is no DI framework. */
class EtherTetherApplication : Application() {
    /** Lives as long as the process. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The backend detectors in display order: Shizuku only in this version. */
    lateinit var detectors: BackendDetectors
        private set

    /** Whether the engine can run through Shizuku now. */
    lateinit var readiness: ShizukuReadiness
        private set

    /** The connection to the engine's user service. */
    lateinit var connection: EngineConnection
        private set

    /** The engine, reached through [connection]. */
    lateinit var engine: EngineClient
        private set

    /** The settings. */
    lateinit var settings: SettingsStore
        private set

    /** The saved profiles. */
    lateinit var profiles: ProfileStore
        private set

    /** The on-phone event log. */
    lateinit var log: EventLog
        private set

    /** Starts, stops and watches tethering. */
    lateinit var controller: TetherController
        private set

    private val shizukuChanges = MutableStateFlow(0)

    /** Changes whenever Shizuku's binder arrives or dies. */
    val shizukuEvents: StateFlow<Int> = shizukuChanges.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        // Sui hands its binder over only after this call; it is a no-op without Sui.
        Sui.init(packageName)
        // Only Shizuku (shell identity) is supported.
        detectors = BackendDetectors(listOf(ShizukuDetector(RikkaShizukuApi, ::isShizukuInstalled)))
        readiness = ShizukuReadiness(RikkaShizukuApi, ::isShizukuInstalled)
        connection = EngineConnection(ShizukuUserServiceBinder(packageName, engineVersion(), BuildConfig.DEBUG))
        engine = EngineClient(connection)
        log = EventLog(File(filesDir, "logs/events.log"))
        if (BuildConfig.DEBUG) DebugChecks.enable(log)
        // A log shared earlier is no longer needed once the share is done; nothing keeps it around.
        appScope.launch(Dispatchers.IO) { File(cacheDir, "exports").listFiles()?.forEach { it.delete() } }
        settings = DataStoreSettings(this, appScope)
        profiles = DataStoreProfiles(this, appScope)
        controller = TetherController(
            engine = engine,
            readinessCheck = readiness::check,
            profiles = profiles,
            settings = settings,
            log = log,
            foreground = ::ensureServiceRunning,
            alerts = { event, profileId -> Notifications.alert(this, event, profileName(profileId)) },
            scope = appScope,
        )
        Notifications.createChannels(this)
        watchShizuku()
        updateSurfaces()
        AdapterChanges(appScope, log) { controller.refreshAdapter() }.register(this)
        log.info("app", "started ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    /** True when the Shizuku app is installed (visible through the queries entry). */
    fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** The display name of the profile with [id], or null. */
    fun profileName(id: String?): String? = id?.let(profiles.profiles.value::find)?.displayName(this)

    private fun ensureServiceRunning() {
        if (TetherService.running) return
        try {
            TetherService.start(this)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: tethering still works while the app is open.
            log.warn("service", "foreground service not allowed now: ${e.message}")
        }
    }

    private fun watchShizuku() {
        Shizuku.addBinderReceivedListenerSticky {
            shizukuChanges.update { it + 1 }
            // Shizuku came (back): read the real state, which also ends an "unknown" state.
            if (controller.refreshReadiness() == EngineReadiness.Ready) appScope.launch { controller.refreshStatus() }
        }
        Shizuku.addBinderDeadListener {
            // A restarted Shizuku has none of the old user services; bind again on next use.
            connection.reset()
            shizukuChanges.update { it + 1 }
            log.warn("shizuku", "Shizuku stopped")
            controller.onShizukuLost()
        }
    }

    /** Keeps the tile and the widget in step with the state, without polling. */
    private fun updateSurfaces() {
        // Off the main thread: both calls end in system services that read from disk.
        appScope.launch(Dispatchers.Default) {
            combine(controller.state, profiles.profiles) { state, list ->
                listOf(state.active, state.serving, state.waitingForAdapter, state.readiness == EngineReadiness.Ready, list.selected?.id)
            }.distinctUntilChanged().collect {
                TetherTileService.requestUpdate(this@EtherTetherApplication)
                runCatching { TetherWidget().updateAll(this@EtherTetherApplication) }
            }
        }
    }

    /**
     * The user service version: changes with every installed APK, so Shizuku never keeps a
     * service started from older code (it replaces a service whose version differs).
     */
    private fun engineVersion(): Int {
        val info = packageManager.getPackageInfo(packageName, 0)
        return (info.longVersionCode * 31 + info.lastUpdateTime).hashCode()
    }
}
