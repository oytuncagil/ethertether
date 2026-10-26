// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendStatusTest {
    @Test
    fun onlyReadyAndAvailableAreUsable() {
        val usable = listOf(
            BackendState.NotFound to false,
            BackendState.NotRunning to false,
            BackendState.PermissionRequired to false,
            BackendState.Available to true,
            BackendState.Ready(KnownUids.SHELL) to true,
            BackendState.Failed("boom") to false,
        )
        usable.forEach { (state, expected) ->
            assertEquals(state.toString(), expected, BackendStatus(BackendKind.SHIZUKU, state).isUsable)
        }
    }
}
