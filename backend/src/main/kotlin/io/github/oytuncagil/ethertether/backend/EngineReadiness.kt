// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.KnownUids

/** Whether the engine can run through Shizuku now, and if not, what the user must do. */
sealed interface EngineReadiness {
    /** Shizuku is not installed. Next step: the guided setup. */
    data object ShizukuNotInstalled : EngineReadiness

    /** Shizuku is installed but not started (always the case after a reboot). */
    data object ShizukuNotRunning : EngineReadiness

    /** Sui serves the Shizuku API; EtherTether does not use it. */
    data object SuiNotSupported : EngineReadiness

    /** The Shizuku server is older than [ShizukuReadiness.MIN_VERSION]. */
    data class ShizukuTooOld(val version: Int) : EngineReadiness

    /** EtherTether has not been granted access yet; the dialog can be shown. */
    data object PermissionRequired : EngineReadiness

    /** The user denied access for good; it can only be allowed again in the Shizuku app. */
    data object PermissionDenied : EngineReadiness

    /** Shizuku runs as root; this version needs the shell identity (wireless debugging or ADB). */
    data object RunsAsRoot : EngineReadiness

    /** Shizuku runs as a uid that is neither shell nor root. */
    data class UnexpectedUid(val uid: Int) : EngineReadiness

    /** Shizuku runs as shell and access is granted. */
    data object Ready : EngineReadiness
}

/** Decides [EngineReadiness] from the Shizuku API. Read-only: asks the user nothing. */
class ShizukuReadiness(private val api: ShizukuApi, private val isShizukuInstalled: () -> Boolean) {
    /** The current readiness. Any exception from Shizuku counts as not running. */
    fun check(): EngineReadiness = runCatching(::evaluate).getOrDefault(EngineReadiness.ShizukuNotRunning)

    private fun evaluate(): EngineReadiness {
        if (!api.pingBinder()) {
            return if (isShizukuInstalled()) EngineReadiness.ShizukuNotRunning else EngineReadiness.ShizukuNotInstalled
        }
        if (api.isSui()) return EngineReadiness.SuiNotSupported
        val version = api.version()
        if (api.isPreV11() || version < MIN_VERSION) return EngineReadiness.ShizukuTooOld(version)
        if (!api.isPermissionGranted()) {
            return if (api.isPermissionDeniedForGood()) EngineReadiness.PermissionDenied else EngineReadiness.PermissionRequired
        }
        return when (val uid = api.uid()) {
            KnownUids.SHELL -> EngineReadiness.Ready
            KnownUids.ROOT -> EngineReadiness.RunsAsRoot
            else -> EngineReadiness.UnexpectedUid(uid)
        }
    }

    /** Version limits. */
    companion object {
        /**
         * Oldest Shizuku server API this app supports. User services need 10; 11 is the first
         * version of the current permission model, which the Shizuku API 13 library expects.
         */
        const val MIN_VERSION = 11
    }
}
