// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusPresentationTest {
    private fun status(
        serving: Pair<String, ServingScope>? = null,
        error: Pair<String, Int>? = null,
        available: String? = null,
        interfaces: List<InterfaceInfo> = emptyList(),
    ) = EngineStatus(36, true, serving, error, available, interfaces)

    @Test
    fun servingShowsScopeAndInterface() {
        assertEquals(UiText(R.string.status_serving_global, listOf("eth0")), statusMessage(status("eth0" to ServingScope.GLOBAL), null).title)
    }

    @Test
    fun errorShowsTheCodeText() {
        val message = statusMessage(status(error = "eth0" to 12), null)
        assertEquals(UiText(R.string.code_12), message.next)
    }

    @Test
    fun offWithAndWithoutAdapter() {
        assertEquals(UiText(R.string.status_off_adapter, listOf("eth0")), statusMessage(status(available = "eth0"), null).title)
        assertEquals(Tone.ABSENT, statusMessage(status(), null).tone)
    }

    @Test
    fun adapterRemovedOnlyAfterServing() {
        val wasServing = status("eth0" to ServingScope.LOCAL)
        assertEquals(UiText(R.string.status_adapter_removed), statusMessage(status(), wasServing).title)
        assertEquals(UiText(R.string.status_off_no_adapter), statusMessage(status(), status()).title)
    }

    @Test
    fun interfaceLineNamesCableAndAddresses() {
        val line = InterfaceInfo("eth0", true, null, null, emptyList()).describe()
        assertEquals(
            UiText(R.string.status_interface, listOf("eth0", UiText(R.string.status_up), UiText(R.string.status_cable_unknown), UiText(R.string.status_no_address))),
            line,
        )
    }
}
