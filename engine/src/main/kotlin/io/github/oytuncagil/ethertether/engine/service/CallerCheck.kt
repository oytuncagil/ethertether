// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.engine.service

/** Who may call the engine user service. */
internal object CallerCheck {
    /** Uids per Android user (android.os.UserHandle.PER_USER_RANGE). */
    private const val PER_USER_RANGE = 100_000

    /**
     * True for the service's own uid (Shizuku calls destroy() from it) and for the app's uid in
     * any Android user. With no known app uid (a Shizuku server older than v13) every caller is
     * allowed, as before.
     */
    fun allowed(caller: Int, own: Int, appUid: Int?): Boolean =
        appUid == null || caller == own || caller % PER_USER_RANGE == appUid % PER_USER_RANGE
}
