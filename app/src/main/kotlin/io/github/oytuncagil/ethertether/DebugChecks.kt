// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether

import android.os.Build
import android.os.StrictMode
import android.os.strictmode.Violation
import io.github.oytuncagil.ethertether.data.EventLog
import java.util.concurrent.Executors

/**
 * StrictMode for debug builds: disk or network work on the main thread, leaked closeables,
 * receivers, service connections and activities, file URIs and unsafe intents. Each kind of
 * violation is written to the app's own log once, so it shows up in an exported log from a
 * phone test without a computer. Release builds never call this.
 */
object DebugChecks {
    private val seen = mutableSetOf<String>()

    /** Turns StrictMode on with [log] as the place where violations go. */
    fun enable(log: EventLog) {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "strict-mode").apply { isDaemon = true } }
        val report = StrictMode.OnThreadViolationListener { record(log, it) }
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyListener(executor, report).build())
        val vm = StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedRegistrationObjects()
            .detectActivityLeaks()
            .detectFileUriExposure()
            .detectContentUriWithoutPermission()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) vm.detectUnsafeIntentLaunch()
        StrictMode.setVmPolicy(vm.penaltyListener(executor) { record(log, it) }.build())
    }

    private fun record(log: EventLog, violation: Violation) {
        val place = violation.stackTrace.firstOrNull { it.className.startsWith("io.github.oytuncagil") }
        val key = "${violation.javaClass.simpleName} at $place"
        synchronized(seen) { if (!seen.add(key)) return }
        log.warn("strictmode", key)
    }
}
