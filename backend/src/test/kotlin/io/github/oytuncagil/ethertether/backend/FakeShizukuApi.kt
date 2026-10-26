// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.KnownUids

/** A [ShizukuApi] whose answers each test sets directly. */
data class FakeShizukuApi(
    val alive: Boolean = false,
    val sui: Boolean = false,
    val preV11: Boolean = false,
    val serverVersion: Int = 13,
    val granted: Boolean = false,
    val deniedForGood: Boolean = false,
    val serverUid: Int = KnownUids.SHELL,
    val grantOnRequest: Boolean = true,
) : ShizukuApi {
    /** How often the permission dialog was requested. */
    var requests = 0
        private set

    override fun pingBinder() = alive

    override fun isSui() = sui

    override fun isPreV11() = preV11

    override fun version() = serverVersion

    override fun isPermissionGranted() = granted

    override fun isPermissionDeniedForGood() = deniedForGood

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        requests++
        onResult(grantOnRequest)
    }

    override fun uid() = serverUid
}
