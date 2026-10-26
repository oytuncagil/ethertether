// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.backend

import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.KnownUids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuDetectorsTest {
    private fun shizuku(api: FakeShizukuApi, installed: Boolean = true) =
        ShizukuDetector(api) { installed }.detect()

    @Test
    fun shizukuNotInstalled() {
        assertEquals(BackendState.NotFound, shizuku(FakeShizukuApi(), installed = false))
    }

    @Test
    fun shizukuInstalledButNotRunning() {
        assertEquals(BackendState.NotRunning, shizuku(FakeShizukuApi()))
    }

    @Test
    fun shizukuRunningWithoutPermission() {
        assertEquals(BackendState.PermissionRequired, shizuku(FakeShizukuApi(alive = true)))
    }

    @Test
    fun shizukuReadyReportsServerUid() {
        val shell = shizuku(FakeShizukuApi(alive = true, granted = true))
        assertEquals(BackendState.Ready(KnownUids.SHELL), shell)
        val root = shizuku(FakeShizukuApi(alive = true, granted = true, serverUid = KnownUids.ROOT))
        assertEquals(BackendState.Ready(KnownUids.ROOT), root)
    }

    @Test
    fun oldShizukuIsReportedAsFailed() {
        assertTrue(shizuku(FakeShizukuApi(alive = true, granted = true, preV11 = true)) is BackendState.Failed)
    }

    @Test
    fun suiBinderIsNotReportedAsShizuku() {
        val api = FakeShizukuApi(alive = true, sui = true, granted = true)
        assertEquals(BackendState.NotFound, shizuku(api, installed = false))
        assertEquals(BackendState.NotRunning, shizuku(api, installed = true))
    }

    @Test
    fun suiStates() {
        assertEquals(BackendState.NotFound, SuiDetector(FakeShizukuApi(alive = true)).detect())
        assertEquals(BackendState.NotRunning, SuiDetector(FakeShizukuApi(sui = true)).detect())
        assertEquals(BackendState.PermissionRequired, SuiDetector(FakeShizukuApi(alive = true, sui = true)).detect())
        val ready = SuiDetector(FakeShizukuApi(alive = true, sui = true, granted = true, serverUid = 0)).detect()
        assertEquals(BackendState.Ready(KnownUids.ROOT), ready)
    }
}
