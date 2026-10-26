// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.oytuncagil.ethertether.EtherTetherApplication
import io.github.oytuncagil.ethertether.MainActivity
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.backend.EngineReadiness
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: Start or Stop of the selected profile. An active tile: it is updated when
 * the state changes, not polled. When Shizuku is not ready, a tap opens the app instead.
 */
class TetherTileService : TileService() {
    private val app get() = application as EtherTetherApplication

    private var watching: Job? = null

    /**
     * While the panel shows the tile, every state change is drawn at once. A tap starts
     * tethering while the tile already listens, so asking Android to listen again is not enough
     * (phone test 2026-10-09: the tile stayed dark after starting from it).
     */
    override fun onStartListening() {
        super.onStartListening()
        watching?.cancel()
        watching = app.appScope.launch { app.controller.state.collect { render() } }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val ready = app.controller.refreshReadiness() == EngineReadiness.Ready
        app.log.info("tile", "tap: Shizuku ${if (ready) "ready" else "not ready"}, active ${app.controller.state.value.active}")
        if (!ready) {
            openApp()
            return
        }
        if (isLocked) unlockAndRun(::toggle) else toggle()
    }

    /**
     * SystemUI binds a listening tile so that it may start a foreground service, but Android's
     * list of exemptions does not name tiles (docs/RESEARCH.md, "Auto-start"). If Android refuses,
     * the app opens instead, where Start works.
     */
    private fun toggle() {
        try {
            TetherService.toggle(this)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException.
            app.log.warn("tile", "foreground service not allowed from the tile: ${e.message}")
            openApp()
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val state = app.controller.state.value
        tile.state = if (state.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = getString(R.string.tile_label)
        tile.subtitle = subtitle(state)
        tile.updateTile()
    }

    private fun subtitle(state: TetherState): String = when {
        state.readiness != null && state.readiness != EngineReadiness.Ready -> getString(R.string.tile_needs_shizuku)
        state.serving -> app.profileName(state.profileId).orEmpty()
        state.waitingForAdapter -> getString(R.string.tile_waiting)
        else -> app.profileName(app.profiles.profiles.value.selected?.id).orEmpty()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** Updates. */
    companion object {
        /** Asks the system to let the tile update itself now. */
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, TetherTileService::class.java)) }
        }
    }
}
