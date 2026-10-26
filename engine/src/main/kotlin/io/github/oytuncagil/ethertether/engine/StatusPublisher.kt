// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pushes the Ethernet tethering status to one receiver: the current status first, then every
 * change Android reports. Event driven, no polling. A receiver that throws (for example a dead
 * app process) is dropped.
 */
class StatusPublisher(private val engine: TetheringEngine, private val scope: CoroutineScope) {
    private var job: Job? = null

    /** Sends status changes to [send] from now on, replacing the previous receiver; null stops. */
    @Synchronized
    fun publishTo(send: ((EngineStatus) -> Unit)?) {
        job?.cancel()
        job = null
        if (send == null) return
        job = scope.launch {
            engine.attachEvents()
            try {
                engine.states.map { engine.status() }.distinctUntilChanged().collect { send(it) }
            } catch (_: Exception) {
                // The receiver is gone; it registers again when it comes back.
            }
        }
    }
}
