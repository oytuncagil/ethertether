// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.home

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.model.BackendKind
import io.github.oytuncagil.ethertether.model.BackendState
import io.github.oytuncagil.ethertether.model.BackendStatus
import io.github.oytuncagil.ethertether.model.KnownUids
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendPresentationTest {
    private fun present(kind: BackendKind, state: BackendState) = BackendStatus(kind, state).present()

    @Test
    fun everyKindAndStateHasTexts() {
        val states = listOf(
            BackendState.NotFound, BackendState.NotRunning, BackendState.PermissionRequired,
            BackendState.Available, BackendState.Ready(KnownUids.SHELL), BackendState.Ready(KnownUids.ROOT),
            BackendState.Ready(1234), BackendState.Failed("x"),
        )
        BackendKind.entries.forEach { kind ->
            states.forEach { state ->
                val row = present(kind, state)
                assertTrue(row.state.id != 0 && row.hint.id != 0)
            }
        }
    }

    @Test
    fun shellShizukuIsReady() {
        val row = present(BackendKind.SHIZUKU, BackendState.Ready(KnownUids.SHELL))
        assertEquals(Tone.OK, row.tone)
        assertEquals(R.string.state_ready_shell, row.state.id)
    }

    @Test
    fun rootShizukuNeedsActionBecauseTheServiceRejectsIt() {
        val row = present(BackendKind.SHIZUKU, BackendState.Ready(KnownUids.ROOT))
        assertEquals(Tone.ACTION, row.tone)
        assertEquals(R.string.hint_shizuku_ready_root, row.hint.id)
    }

    @Test
    fun grantIsOfferedOnlyForShizukuApiBackends() {
        assertTrue(present(BackendKind.SHIZUKU, BackendState.PermissionRequired).canGrant)
        assertTrue(present(BackendKind.SUI, BackendState.PermissionRequired).canGrant)
        assertFalse(present(BackendKind.ROOT, BackendState.PermissionRequired).canGrant)
        assertFalse(present(BackendKind.SHIZUKU, BackendState.NotRunning).canGrant)
    }

    @Test
    fun failureCarriesTheReason() {
        val row = present(BackendKind.SUI, BackendState.Failed("binder died"))
        assertEquals(UiText(R.string.hint_failed, listOf("binder died")), row.hint)
    }

    @Test
    fun missingBackendsAreNeutral() {
        BackendKind.entries.forEach { assertEquals(Tone.ABSENT, present(it, BackendState.NotFound).tone) }
    }
}
