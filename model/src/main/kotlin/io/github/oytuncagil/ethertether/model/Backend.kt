// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** The privileged identities EtherTether can use to make the tethering request. */
enum class BackendKind {
    /** Shizuku started through ADB or wireless debugging (shell identity, uid 2000). */
    SHIZUKU,

    /** Sui: the Shizuku API served by a root module (uid 0). */
    SUI,

    /** Plain root through su, without Sui. */
    ROOT,
}

/** What a detector found for one backend. Detection never asks the user for anything. */
sealed interface BackendState {
    /** Nothing of this backend exists on the phone. */
    data object NotFound : BackendState

    /** The backend is installed but its service is not running. */
    data object NotRunning : BackendState

    /** The backend runs, but EtherTether has not been granted access yet. */
    data object PermissionRequired : BackendState

    /** The su binary exists; whether access is granted is only known when it is first used. */
    data object Available : BackendState

    /** The backend runs, access is granted, and calls run as [uid]. */
    data class Ready(val uid: Int) : BackendState

    /** Detection failed with a technical [reason], shown in the log. */
    data class Failed(val reason: String) : BackendState
}

/** The detection result for one [kind] of backend. */
data class BackendStatus(val kind: BackendKind, val state: BackendState) {
    /** True when this backend can be used now without any user action. */
    val isUsable: Boolean get() = state is BackendState.Ready || state is BackendState.Available
}

/** Well-known Linux uids that matter for the tethering service's caller checks. */
object KnownUids {
    /** The root user. */
    const val ROOT = 0

    /** The shell user that ADB and Shizuku (non-root) run as. */
    const val SHELL = 2000
}
