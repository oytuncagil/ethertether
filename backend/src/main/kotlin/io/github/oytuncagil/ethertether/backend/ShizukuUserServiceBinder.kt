// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.github.oytuncagil.ethertether.engine.service.EngineUserService
import rikka.shizuku.Shizuku

/**
 * Binds [EngineUserService] through Shizuku's documented user service API. Shizuku starts it in
 * its own process as the shell user. Not a daemon: it stops when the app process ends.
 *
 * @param packageName the app's package name
 * @param version changes whenever the APK changes, so Shizuku never reuses an old service
 * @param debuggable lets a debugger attach to the service process in debug builds
 */
class ShizukuUserServiceBinder(packageName: String, version: Int, debuggable: Boolean) : UserServiceBinder {
    private val args = Shizuku.UserServiceArgs(ComponentName(packageName, EngineUserService::class.java.name))
        .daemon(false)
        .processNameSuffix("engine")
        .tag("engine")
        .version(version)
        .debuggable(debuggable)

    private var connection: ServiceConnection? = null

    override fun bind(onConnected: (IBinder) -> Unit, onDisconnected: () -> Unit) {
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service != null && service.pingBinder()) onConnected(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) = onDisconnected()
        }
        connection?.let { runCatching { Shizuku.unbindUserService(args, it, false) } }
        connection = newConnection
        Shizuku.bindUserService(args, newConnection)
    }

    override fun unbind() {
        val old = connection ?: return
        connection = null
        Shizuku.unbindUserService(args, old, true)
    }
}
