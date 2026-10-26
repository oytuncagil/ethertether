// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.debug

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.engine.EngineStatus
import io.github.oytuncagil.ethertether.engine.InterfaceInfo
import io.github.oytuncagil.ethertether.engine.ServingScope
import io.github.oytuncagil.ethertether.ui.common.Tone
import io.github.oytuncagil.ethertether.ui.common.UiText
import io.github.oytuncagil.ethertether.ui.result.Message
import io.github.oytuncagil.ethertether.ui.result.resultCodeMessage

/**
 * The message for [status]. When [previous] served and no adapter is left, the adapter was
 * removed, which ends Ethernet tethering for good; the message says to plug it in
 * and start again.
 */
fun statusMessage(status: EngineStatus, previous: EngineStatus?): Message {
    val serving = status.serving
    val error = status.error
    return when {
        serving != null -> {
            val title = if (serving.second == ServingScope.LOCAL) R.string.status_serving_local else R.string.status_serving_global
            Message(UiText(title, listOf(serving.first)), null, Tone.OK)
        }
        error != null -> Message(UiText(R.string.status_error, listOf(error.first)), resultCodeMessage(error.second).title, Tone.ACTION)
        previous?.serving != null && !status.adapterPresent ->
            Message(UiText(R.string.status_adapter_removed), null, Tone.ACTION)
        status.adapterPresent -> {
            val name = status.available ?: status.interfaces.firstOrNull()?.name.orEmpty()
            Message(UiText(R.string.status_off_adapter, listOf(name)), null, Tone.OK)
        }
        else -> Message(UiText(R.string.status_off_no_adapter), null, Tone.ABSENT)
    }
}

/** One line per kernel interface: name, up or down, cable, addresses. */
fun InterfaceInfo.describe(): UiText {
    val state = UiText(if (isUp) R.string.status_up else R.string.status_down)
    val cable = UiText(
        when (hasCarrier) {
            true -> R.string.status_cable_yes
            false -> R.string.status_cable_no
            null -> R.string.status_cable_unknown
        },
    )
    val addresses: Any = if (ipv4.isEmpty()) UiText(R.string.status_no_address) else ipv4.joinToString()
    return UiText(R.string.status_interface, listOf(name, state, cable, addresses))
}
