// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.KnownUids
import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuReadinessTest {
    private fun check(api: ShizukuApi, installed: Boolean = true) = ShizukuReadiness(api, { installed }).check()

    @Test
    fun notInstalledAndNotRunningAreTold() {
        assertEquals(EngineReadiness.ShizukuNotInstalled, check(FakeShizukuApi(alive = false), installed = false))
        assertEquals(EngineReadiness.ShizukuNotRunning, check(FakeShizukuApi(alive = false), installed = true))
    }

    @Test
    fun suiIsInformationOnly() {
        assertEquals(EngineReadiness.SuiNotSupported, check(FakeShizukuApi(alive = true, sui = true, granted = true)))
    }

    @Test
    fun oldServersAreRefused() {
        assertEquals(EngineReadiness.ShizukuTooOld(10), check(FakeShizukuApi(alive = true, serverVersion = 10)))
        assertEquals(EngineReadiness.ShizukuTooOld(13), check(FakeShizukuApi(alive = true, preV11 = true)))
    }

    @Test
    fun permissionStatesAreTold() {
        assertEquals(EngineReadiness.PermissionRequired, check(FakeShizukuApi(alive = true)))
        assertEquals(EngineReadiness.PermissionDenied, check(FakeShizukuApi(alive = true, deniedForGood = true)))
    }

    @Test
    fun onlyShellIsReady() {
        assertEquals(EngineReadiness.Ready, check(FakeShizukuApi(alive = true, granted = true)))
        assertEquals(EngineReadiness.RunsAsRoot, check(FakeShizukuApi(alive = true, granted = true, serverUid = KnownUids.ROOT)))
        assertEquals(EngineReadiness.UnexpectedUid(1000), check(FakeShizukuApi(alive = true, granted = true, serverUid = 1000)))
    }

    @Test
    fun shizukuThrowingCountsAsNotRunning() {
        val throwing = object : ShizukuApi by FakeShizukuApi(alive = true) {
            override fun version(): Int = throw IllegalStateException("binder haven't been received")
        }
        assertEquals(EngineReadiness.ShizukuNotRunning, check(throwing))
    }

    @Test
    fun permissionRequestReportsTheResult() {
        val api = FakeShizukuApi(alive = true, grantOnRequest = false)
        var result: Boolean? = null
        api.requestPermission { result = it }
        assertEquals(false, result)
        assertEquals(1, api.requests)
    }
}
