// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import io.github.oytuncagil.ethertether.backend.EngineReadiness
import io.github.oytuncagil.ethertether.backend.EngineUnavailable
import io.github.oytuncagil.ethertether.engine.EngineReport
import io.github.oytuncagil.ethertether.engine.EngineStatus

/** What EtherTether is doing right now. */
enum class Operation { STARTING, STOPPING }

/** Something the watchdog noticed. */
enum class WatchdogEvent {
    /** The adapter was removed while tethering ran; Android then ends Ethernet tethering. */
    ADAPTER_REMOVED,

    /** Tethering stopped although EtherTether did not stop it (Settings, another app, the system). */
    STOPPED_OUTSIDE,

    /** Keep armed could not start again after the adapter was removed. */
    REARM_FAILED,
}

/**
 * The whole tethering state the screens, the notification, the tile and the widget show.
 *
 * @property readiness whether Shizuku can carry the engine; null before the first check
 * @property status the latest status the engine pushed; null before the first one
 * @property operation a start or stop in progress
 * @property armed true while EtherTether keeps a request active: serving, or waiting for the adapter
 * @property profileId the profile that is serving or armed
 * @property lastReport the report of the last start or stop
 * @property lastError the last time the engine could not be reached
 * @property event the last watchdog event, cleared by the next start
 * @property rearming true while keep armed tries to start again after the adapter was removed
 * @property statusUnknown true after Shizuku stopped while tethering ran or was armed: Android may
 *   still serve, but EtherTether can neither see nor stop it until Shizuku runs again
 */
data class TetherState(
    val readiness: EngineReadiness? = null,
    val status: EngineStatus? = null,
    val operation: Operation? = null,
    val armed: Boolean = false,
    val profileId: String? = null,
    val lastReport: EngineReport? = null,
    val lastError: EngineUnavailable? = null,
    val event: WatchdogEvent? = null,
    val rearming: Boolean = false,
    val statusUnknown: Boolean = false,
) {
    /** True when Android reports Ethernet tethering as serving. */
    val serving: Boolean get() = status?.serving != null

    /** True while the foreground service must run: serving, armed, re-arming or busy. */
    val active: Boolean get() = serving || armed || rearming || operation != null

    /** True when armed but no adapter serves yet. */
    val waitingForAdapter: Boolean get() = armed && !serving && operation == null
}
