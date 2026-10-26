// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.tether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.oytuncagil.ethertether.EtherTetherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After the phone starts, arms the selected profile when the user turned on "Start after
 * restart". The service then waits for Shizuku; without Shizuku nothing happens.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as EtherTetherApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = withTimeoutOrNull(5_000) { app.settings.settings.first { it.loaded } }
                if (settings?.startAtBoot == true) {
                    app.log.info("boot", "start after restart is on")
                    // BOOT_COMPLETED allows starting a connectedDevice foreground service.
                    TetherService.boot(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
