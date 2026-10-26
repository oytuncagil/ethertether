// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState

/** Package name of the Shizuku manager app. */
const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/**
 * Detects the Shizuku app's server (started with ADB or wireless debugging, or with root).
 * When Sui is active its binder replaces Shizuku's in this process, so Shizuku is then only
 * reported as installed or not.
 */
class ShizukuDetector(
    private val api: ShizukuApi,
    private val isShizukuInstalled: () -> Boolean,
) : BackendDetector {
    override val kind = BackendKind.SHIZUKU

    override fun detect(): BackendState {
        if (!api.pingBinder() || api.isSui()) {
            return if (isShizukuInstalled()) BackendState.NotRunning else BackendState.NotFound
        }
        return grantedState(api)
    }
}

/** Detects Sui, the root module that serves the Shizuku API. */
class SuiDetector(private val api: ShizukuApi) : BackendDetector {
    override val kind = BackendKind.SUI

    override fun detect(): BackendState {
        if (!api.isSui()) return BackendState.NotFound
        if (!api.pingBinder()) return BackendState.NotRunning
        return grantedState(api)
    }
}

private fun grantedState(api: ShizukuApi): BackendState = when {
    api.isPreV11() -> BackendState.Failed("Server is older than Shizuku 11")
    !api.isPermissionGranted() -> BackendState.PermissionRequired
    else -> BackendState.Ready(api.uid())
}
